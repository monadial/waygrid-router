package com.monadial.waygrid.common.domain.algebra

import scala.collection.mutable

import cats.data.NonEmptyList
import cats.effect.{ Resource, Sync }
import cats.implicits.*
import com.monadial.waygrid.common.domain.interpreter.cryptography.HasherInterpreter
import com.monadial.waygrid.common.domain.model.cryptography.hashing.Value.LongHash
import com.monadial.waygrid.common.domain.model.routing.Value.RouteSalt
import com.monadial.waygrid.common.domain.model.traversal.dag.Value.{ DagHash, EdgeGuard, ForkId, NodeId }
import com.monadial.waygrid.common.domain.model.traversal.dag.{ Edge as DagEdge, Node as DagNode, * }
import com.monadial.waygrid.common.domain.model.traversal.spec.Node.NodeParameters
import com.monadial.waygrid.common.domain.model.traversal.spec.{ Node as SpecNode, Spec }

final case class RouteCompilerError(msg: String) extends RuntimeException(msg)

type DagId  = Long
type DagHex = String

/**
 * DAG compiler that transforms a high-level routing spec into a unique,
 * address-aware DAG with stable node IDs and deterministic hashing.
 *
 * Supports:
 * - Linear nodes with success/failure edges
 * - Fork nodes for parallel branch execution
 * - Join nodes for branch synchronization
 * - Multiple entry points for multi-origin routing
 *
 * @tparam F Effect type (e.g., IO, Sync)
 */
trait DagCompiler[F[_]]:
  /**
   * Compiles the provided routing specification into a directed acyclic graph.
   *
   * The result separates the cacheable DAG structure from variable parameters:
   * - DAG: The structural definition (nodes, edges, policies) - cacheable by hash
   * - Parameters: Node-to-parameters mapping - variable per invocation
   *
   * This separation allows DAG caching while supporting different parameter
   * configurations per traversal (different API keys, model settings, etc.)
   *
   * @param spec High-level routing specification
   * @param salt Optional salt to differentiate structurally identical specs
   * @return A compiled DAG with separate parameters mapping
   */
  def compile(spec: Spec, salt: RouteSalt): F[CompiledDag]

object DagCompiler:

  /**
   * Default implementation of DagCompiler using XXH3 hashing.
   * Ensures stable and unique DAG node identifiers across compiles.
   */
  def default[F[+_]: Sync]: Resource[F, DagCompiler[F]] =
    for
      hasher <- HasherInterpreter.xxh3[F]
    yield new DagCompiler[F]:

      // --- Constants for internal logic ---
      inline val PHI      = 0x9e3779b97f4a7c15L // Golden ratio constant for hash mixing
      inline val MaxDepth = 128                 // Max recursion depth allowed during traversal
      inline val HashLen  = 16                  // Length (in hex characters) of each node ID
      inline val Radix    = 16                  // Hexadecimal radix

      // Helper to raise consistent typed compiler errors
      private inline def fail(msg: String): F[Nothing] =
        Sync[F].raiseError(RouteCompilerError(msg))

      // Pads a hex string on the left to length `n` using zeros
      private def padLeftHex(s: String, n: Int): String =
        if s.length >= n then s.take(n)
        else "0" * (n - s.length) + s

      /**
       * Converts a numeric ID to a fixed-length hexadecimal string.
       * If desired length exceeds `HashLen`, mixes in PHI to generate an extended secondary hash.
       */
      private inline def toHex(idLen: Int, id: Long): F[DagHex] =
        if idLen <= HashLen then
          val raw = java.lang.Long.toUnsignedString(id, Radix)
          padLeftHex(raw, idLen).pure[F]
        else
          for
            h2 <- hasher.hashLong(id ^ PHI)
          yield
            val a = padLeftHex(java.lang.Long.toUnsignedString(id, HashLen), Radix)
            val b = padLeftHex(java.lang.Long.toUnsignedString(h2.unwrap, HashLen), Radix)
            (a + b).take(idLen)

      /**
       * Generate a deterministic ForkId from a joinNodeId string.
       * Uses hashing to produce a stable 16-character hex identifier.
       */
      private def forkIdFromJoinNodeId(joinNodeId: String, salt: LongHash): F[ForkId] =
        for
          hash <- hasher.hashChars(joinNodeId + salt.unwrap.toString)
          hex  <- toHex(HashLen, hash.unwrap) // 16-character hex string
        yield ForkId.unsafeFrom(hex.toUpperCase)

      /**
       * Converts a SpecNode into a DagNode with a known ID and metadata.
       */
      private def toDagNode(id: NodeId, node: SpecNode, nodeType: NodeType): DagNode =
        DagNode(
          id = id,
          label = node.label,
          retryPolicy = node.retryPolicy,
          deliveryStrategy = node.deliveryStrategy,
          address = node.address,
          nodeType = nodeType
        )

      /**
       * Computes a deterministic numeric DagId for a given node using:
       *  - its address hash
       *  - its depth in traversal
       *  - traversal path bits
       *  - routing salt
       */
      private inline def nodeIdF(
        salt: LongHash,
        n: SpecNode,
        bits: Long,
        depth: Int
      ): F[DagId] =
        for
          addrH <- hasher.hashUri(n.address)
          idH   <- hasher.hashLong(bits ^ (depth.toLong << 32) ^ addrH.unwrap ^ salt.unwrap)
        yield idH.unwrap

      /**
       * Computes a deterministic numeric DagId for Join nodes.
       * Uses joinNodeId instead of path bits to ensure all paths to the same
       * logical join converge to a single node.
       */
      private def joinNodeIdF(salt: LongHash, j: SpecNode.Join): F[DagId] =
        for
          addrH <- hasher.hashUri(j.address)
          joinH <- hasher.hashChars(j.joinNodeId)
          idH   <- hasher.hashLong(addrH.unwrap ^ joinH.unwrap ^ salt.unwrap)
        yield idH.unwrap

      /**
       * Computes a deterministic numeric DagId for Fork nodes.
       * Uses joinNodeId instead of path bits to ensure consistent fork identification.
       */
      private def forkNodeIdF(salt: LongHash, f: SpecNode.Fork): F[DagId] =
        for
          addrH <- hasher.hashUri(f.address)
          forkH <- hasher.hashChars(f.joinNodeId)
          idH   <- hasher.hashLong(addrH.unwrap ^ forkH.unwrap ^ PHI ^ salt.unwrap)
        yield idH.unwrap

      // --- Internal Types for Traversal ---

      private final case class Frame(n: SpecNode, bits: Long, depth: Int)      // DFS stack frame
      private final case class EdgeL(from: DagId, to: DagId, guard: EdgeGuard) // Internal edge
      private final case class NodeInfo(node: DagNode, rawId: DagId)           // Node with its raw ID

      private final case class CompilerState(
        nodes: mutable.LongMap[NodeInfo],             // Collected nodes (deduplicated)
        edges: mutable.ArrayBuffer[EdgeL],            // Collected edges
        stack: mutable.ArrayDeque[Frame],             // DFS traversal stack
        forkIdMap: mutable.Map[String, ForkId],       // joinNodeId -> ForkId mapping
        parameters: mutable.Map[Long, NodeParameters] // rawId -> parameters mapping
      )

      /**
       * Builds the DAG from the given Spec and salt hash.
       * Ensures stable, deterministic node IDs and detects hash collisions.
       * Returns both the DAG structure and the extracted parameters mapping.
       */
      private def buildDagFromSpec(spec: Spec, saltH: LongHash): F[(Dag, Map[NodeId, NodeParameters])] =
        val state = CompilerState(
          nodes = mutable.LongMap.empty,
          edges = mutable.ArrayBuffer.empty,
          stack = mutable.ArrayDeque.empty,
          forkIdMap = mutable.Map.empty,
          parameters = mutable.Map.empty
        )

        /**
         * Get or create a ForkId for the given joinNodeId.
         * Ensures Fork and Join nodes with the same joinNodeId get the same ForkId.
         */
        def getForkId(joinNodeId: String): F[ForkId] =
          state.forkIdMap.get(joinNodeId) match
            case Some(fid) => fid.pure[F]
            case None =>
              for
                fid <- forkIdFromJoinNodeId(joinNodeId, saltH)
                _   <- Sync[F].delay(state.forkIdMap.update(joinNodeId, fid))
              yield fid

        /**
         * Determines the NodeType for a SpecNode.
         */
        def nodeTypeFor(n: SpecNode): F[NodeType] =
          n match
            case _: SpecNode.Standard => NodeType.Standard.pure[F]
            case f: SpecNode.Fork =>
              getForkId(f.joinNodeId).map(NodeType.Fork(_))
            case j: SpecNode.Join =>
              getForkId(j.joinNodeId).map(fid => NodeType.Join(fid, j.strategy, j.timeout))

        /**
         * Ensures a node is registered in the internal state.
         * Assigns unique hash-based ID and detects collisions.
         * Also extracts and stores parameters for the node.
         *
         * Fork and Join nodes use identity-based IDs (based on joinNodeId) to ensure
         * all paths converge to the same logical node. Standard nodes use path-based IDs.
         */
        def ensureNode(bits: Long, depth: Int, n: SpecNode): F[DagId] =
          if depth > MaxDepth then
            fail(s"Max recursion depth ($MaxDepth) exceeded at node '${n.address.show}'")
          else
            for
              id <- n match
                case j: SpecNode.Join => joinNodeIdF(saltH, j)
                case f: SpecNode.Fork => forkNodeIdF(saltH, f)
                case _                => nodeIdF(saltH, n, bits, depth)
              hexId    <- toHex(HashLen, id)
              nodeType <- nodeTypeFor(n)
              dagNode = toDagNode(NodeId(hexId), n, nodeType)
              _ <- Sync[F].defer:
                  state.nodes.get(id) match
                    case Some(existing) if existing.node.address != n.address =>
                      fail(
                        "Hash collision detected between nodes " +
                          s"'${existing.node.id.show}' and '${dagNode.id.show}' with differing addresses: " +
                          s"'${existing.node.address.show}' vs '${dagNode.address.show}'"
                      )
                    case Some(_) =>
                      Sync[F].unit // already present
                    case None =>
                      state.nodes.update(id, NodeInfo(dagNode, id))
                      // Store parameters if non-empty (extracted from spec node)
                      if n.parameters.nonEmpty then
                        state.parameters.update(id, n.parameters)
                      Sync[F].unit
            yield id

        /**
         * Process children of a node based on its type.
         */
        def processChildren(me: DagId, bits: Long, depth: Int, n: SpecNode): F[Unit] =
          n match
            case std: SpecNode.Standard =>
              // Standard node: traverse onSuccess, onFailure, and conditional edges
              for
                // Traverse onFailure branch (left)
                _ <- std.onFailure.traverse { f =>
                  val b = bits << 1
                  for
                    cid <- ensureNode(b, depth + 1, f)
                    _   <- Sync[F].delay(state.edges += EdgeL(me, cid, EdgeGuard.OnFailure))
                    _   <- Sync[F].delay(state.stack.prepend(Frame(f, b, depth + 1)))
                  yield ()
                }
                // Traverse onSuccess branch (right)
                _ <- std.onSuccess.traverse { s =>
                  val b = (bits << 1) | 1L
                  for
                    cid <- ensureNode(b, depth + 1, s)
                    _   <- Sync[F].delay(state.edges += EdgeL(me, cid, EdgeGuard.OnSuccess))
                    _   <- Sync[F].delay(state.stack.prepend(Frame(s, b, depth + 1)))
                  yield ()
                }
                // Traverse conditional branches (stable per index)
                _ <- std.onConditions.zipWithIndex.traverse_ { case (c, idx) =>
                  val b = (bits << 8) | (64L + idx.toLong)
                  for
                    cid <- ensureNode(b, depth + 1, c.to)
                    _   <- Sync[F].delay(state.edges += EdgeL(me, cid, EdgeGuard.Conditional(c.condition)))
                    _   <- Sync[F].delay(state.stack.prepend(Frame(c.to, b, depth + 1)))
                  yield ()
                }
              yield ()

            case fork: SpecNode.Fork =>
              // Fork node: create edges to all branches
              fork.branches.toList.sortBy(_._1).zipWithIndex.traverse_ { case ((branchName, branchNode), idx) =>
                val branchBits = (bits << 8) | idx.toLong // Support up to 256 branches
                for
                  cid <- ensureNode(branchBits, depth + 1, branchNode)
                  // Fork branches use OnSuccess guard - they all execute on fork completion
                  _ <- Sync[F].delay(state.edges += EdgeL(me, cid, EdgeGuard.OnSuccess))
                  _ <- Sync[F].delay(state.stack.prepend(Frame(branchNode, branchBits, depth + 1)))
                yield ()
              }

            case join: SpecNode.Join =>
              // Join node: traverse onSuccess, onFailure, and onTimeout
              for
                // Traverse onSuccess branch
                _ <- join.onSuccess.traverse { s =>
                  val b = (bits << 1) | 1L
                  for
                    cid <- ensureNode(b, depth + 1, s)
                    _   <- Sync[F].delay(state.edges += EdgeL(me, cid, EdgeGuard.OnSuccess))
                    _   <- Sync[F].delay(state.stack.prepend(Frame(s, b, depth + 1)))
                  yield ()
                }
                // Traverse onFailure branch
                _ <- join.onFailure.traverse { f =>
                  val b = bits << 1
                  for
                    cid <- ensureNode(b, depth + 1, f)
                    _   <- Sync[F].delay(state.edges += EdgeL(me, cid, EdgeGuard.OnFailure))
                    _   <- Sync[F].delay(state.stack.prepend(Frame(f, b, depth + 1)))
                  yield ()
                }
                // Traverse onTimeout branch
                _ <- join.onTimeout.traverse { t =>
                  val b = (bits << 2) | 2L
                  for
                    cid <- ensureNode(b, depth + 1, t)
                    _   <- Sync[F].delay(state.edges += EdgeL(me, cid, EdgeGuard.OnTimeout))
                    _   <- Sync[F].delay(state.stack.prepend(Frame(t, b, depth + 1)))
                  yield ()
                }
              yield ()

        /**
         * Depth-first traversal using a mutable stack.
         * Visits each node and pushes children recursively.
         */
        def loop: F[Unit] =
          Sync[F].tailRecM(()): _ =>
              if state.stack.isEmpty then Sync[F].pure(Right(()))
              else
                val Frame(n, bits, depth) = state.stack.removeHead()
                for
                  me <- ensureNode(bits, depth, n)
                  _  <- processChildren(me, bits, depth, n)
                yield Left(())

        // Process all entry points
        for
          // Initialize stack with all entry points
          entryRawIds <- spec.entryPoints.toList.zipWithIndex.traverse { case (entryNode, idx) =>
            val entryBits = 1L | (idx.toLong << 56) // High bits for entry point index
            for
              _   <- Sync[F].delay(state.stack.prepend(Frame(entryNode, entryBits, 0)))
              eid <- ensureNode(entryBits, 0, entryNode)
            yield eid
          }

          // Run the DFS loop
          _ <- loop

          // Convert numeric IDs → external NodeId
          idPairs <- state.nodes.iterator.map { case (k, info) =>
            toHex(HashLen, k).map(hex => (k, NodeId(hex), info.node))
          }.toList.sequence

          // Rebuild final ID ↔ Node maps
          idMap  = mutable.LongMap.empty[NodeId]
          nodesM = mutable.Map.newBuilder[NodeId, DagNode]
          _ <- Sync[F].delay:
              idPairs.foreach: (k, nid, node) =>
                  idMap.update(k, nid)
                  nodesM += (nid -> node)

          // Compute final DAG hash as XOR of all node keys + salt
          dagHash <- Sync[F]
            .delay:
              idMap.keysIterator.toList.sorted.foldLeft(0L)(_ ^ _) ^ saltH.unwrap
            .flatMap(toHex(HashLen, _))
            .map(DagHash(_))

          // Resolve all edges to use final NodeIds
          edgesM <- Sync[F].delay:
              state.edges.iterator.map(e =>
                DagEdge(idMap(e.from), idMap(e.to), e.guard)
              ).toList

          // Resolve first entry point NodeId (primary entry)
          entryPointsNel <- Sync[F].fromOption(
            NonEmptyList.fromList(entryRawIds.flatMap(idMap.get)),
            RouteCompilerError("Entry point NodeId mapping failed")
          )

          // Build final parameters map with NodeId keys
          paramsM <- Sync[F].delay:
              state.parameters.iterator.flatMap { case (rawId, params) =>
                idMap.get(rawId).map(nodeId => nodeId -> params)
              }.toMap
        yield (
          Dag(
            entryPoints = entryPointsNel,
            hash = dagHash,
            repeatPolicy = spec.repeatPolicy,
            nodes = nodesM.result().toMap,
            edges = edgesM
          ),
          paramsM
        )

      override def compile(spec: Spec, salt: RouteSalt): F[CompiledDag] =
        for
          saltH            <- hasher.hashChars(salt)
          (dag, paramsMap) <- buildDagFromSpec(spec, saltH)
          // Validate the compiled DAG - FSM expects only valid DAGs
          _ <- dag.validate match
            case Nil => Sync[F].unit
            case errors =>
              val errorMessages = errors.map {
                case DagValidationError.ForkWithoutJoin(forkId, forkNodeId) =>
                  s"Fork ${forkNodeId.show} (forkId=${forkId.show}) has no matching Join node"
                case DagValidationError.MultiplJoinsForFork(forkId, joinNodeIds) =>
                  s"Fork ${forkId.show} has multiple Join nodes: ${joinNodeIds.map(_.show).mkString(", ")}"
                case DagValidationError.JoinWithoutMatchingFork(forkId, joinNodeId) =>
                  s"Join ${joinNodeId.show} references non-existent Fork ${forkId.show}"
                case DagValidationError.ForkWithInsufficientBranches(forkNodeId, branchCount) =>
                  s"Fork ${forkNodeId.show} has only ${branchCount} branch(es) - must have at least 2"
                case DagValidationError.EdgeTargetNotFound(from, to, _) =>
                  s"Edge from ${from.show} to non-existent node ${to.show}"
                case DagValidationError.EdgeSourceNotFound(from, to, _) =>
                  s"Edge from non-existent node ${from.show} to ${to.show}"
                case DagValidationError.CycleDetected(cycle) =>
                  s"Cycle detected: ${cycle.map(_.show).mkString(" -> ")}"
              }
              fail(s"Invalid DAG structure:\n  ${errorMessages.mkString("\n  ")}")
        yield CompiledDag(dag, paramsMap)
