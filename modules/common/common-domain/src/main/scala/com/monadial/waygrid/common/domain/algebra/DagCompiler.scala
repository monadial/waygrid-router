package com.monadial.waygrid.common.domain.algebra

import cats.effect.{ Resource, Sync }
import cats.syntax.all.*
import com.monadial.waygrid.common.domain.cryptography.hashing.Hasher
import com.monadial.waygrid.common.domain.model.cryptography.hashing.Value.LongHash
import com.monadial.waygrid.common.domain.model.routing.Value.RouteSalt
import com.monadial.waygrid.common.domain.model.routing.dag.{ Dag, Edge as DagEdge, Node as DagNode }
import com.monadial.waygrid.common.domain.model.routing.dag.Value.{ DagHash, EdgeGuard, NodeId }
import com.monadial.waygrid.common.domain.model.routing.spec.{ Node as SpecNode, Spec }

import scala.collection.mutable

final case class RouteCompilerError(msg: String) extends RuntimeException(msg)

type DagId  = Long
type DagHex = String

/**
 * DAG compiler that transforms a high-level routing spec into a unique,
 * address-aware DAG with stable node IDs and deterministic hashing.
 *
 * @tparam F Effect type (e.g., IO, Sync)
 */
trait DagCompiler[F[_]]:
  /**
   * Compiles the provided routing specification into a directed acyclic graph.
   *
   * @param spec High-level routing specification
   * @param salt Optional salt to differentiate structurally identical specs
   * @return A compiled and hashed DAG
   */
  def compile(spec: Spec, salt: RouteSalt): F[Dag]

object DagCompiler:

  /**
   * Default implementation of DagCompiler using XXH3 hashing.
   * Ensures stable and unique DAG node identifiers across compiles.
   */
  def default[F[+_]: Sync]: Resource[F, DagCompiler[F]] =
    for hasher <- Hasher.xxh3[F]
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
       * Converts a SpecNode into a DagNode with a known ID and metadata.
       */
      private def toDagNode(id: NodeId, node: SpecNode): DagNode =
        DagNode(
          id = id,
          label = node.label,
          retryPolicy = node.retryPolicy,
          deliveryStrategy = node.deliveryStrategy,
          address = node.address
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

      // --- Internal Types for Traversal ---

      private final case class Frame(n: SpecNode, bits: Long, depth: Int)      // DFS stack frame
      private final case class EdgeL(from: DagId, to: DagId, guard: EdgeGuard) // Internal edge
      private final case class CompilerState(
        nodes: mutable.LongMap[DagNode],   // Collected nodes (deduplicated)
        edges: mutable.ArrayBuffer[EdgeL], // Collected edges
        stack: mutable.ArrayDeque[Frame]   // DFS traversal stack
      )

      /**
       * Builds the DAG from the given Spec and salt hash.
       * Ensures stable, deterministic node IDs and detects hash collisions.
       */
      private def buildDagFromSpec(spec: Spec, saltH: LongHash): F[Dag] =
        val state = CompilerState(
          nodes = mutable.LongMap.empty,
          edges = mutable.ArrayBuffer.empty,
          stack = mutable.ArrayDeque.empty
        )

        /**
         * Ensures a node is registered in the internal state.
         * Assigns unique hash-based ID and detects collisions.
         */
        def ensureNode(bits: Long, depth: Int, n: SpecNode): F[DagId] =
          if depth > MaxDepth then
            fail(s"Max recursion depth ($MaxDepth) exceeded at node '${n.address.show}'")
          else
            for
              id    <- nodeIdF(saltH, n, bits, depth)
              hexId <- toHex(HashLen, id)
              dagNode = toDagNode(NodeId(hexId), n)
              _ <- Sync[F].defer:
                  state.nodes.get(id) match
                    case Some(existing) if existing.address != n.address =>
                      fail(
                        "Hash collision detected between nodes " +
                          s"'${existing.id.show}' and '${dagNode.id.show}' with differing addresses: " +
                          s"'${existing.address.show}' vs '${dagNode.address.show}'"
                      )
                    case Some(_) =>
                      Sync[F].unit // already present
                    case None =>
                      state.nodes.update(id, dagNode)
                      Sync[F].unit
            yield id

        /**
         * Depth-first traversal using a mutable stack.
         * Visits each node and pushes children (onFailure / onSuccess) recursively.
         */
        def loop: F[Unit] =
          Sync[F].tailRecM(()): _ =>
              if state.stack.isEmpty then Sync[F].pure(Right(()))
              else
                val Frame(n, bits, depth) = state.stack.removeHead()
                for
                  me <- ensureNode(bits, depth, n)

                  // Traverse onFailure branch (left)
                  _ <- n.onFailure.traverse { f =>
                    val b = bits << 1
                    for
                      cid <- ensureNode(b, depth + 1, f)
                      _   <- Sync[F].delay(state.edges += EdgeL(me, cid, EdgeGuard.OnFailure))
                      _   <- Sync[F].delay(state.stack.prepend(Frame(f, b, depth + 1)))
                    yield ()
                  }

                  // Traverse onSuccess branch (right)
                  _ <- n.onSuccess.traverse { s =>
                    val b = (bits << 1) | 1L
                    for
                      cid <- ensureNode(b, depth + 1, s)
                      _   <- Sync[F].delay(state.edges += EdgeL(me, cid, EdgeGuard.OnSuccess))
                      _   <- Sync[F].delay(state.stack.prepend(Frame(s, b, depth + 1)))
                    yield ()
                  }
                yield Left(())

        for
          // Initialize stack with entry point
          _          <- Sync[F].delay(state.stack.prepend(Frame(spec.entryPoint, 1L, 0)))
          entryRawId <- ensureNode(1L, 0, spec.entryPoint)
          _          <- loop

          // Convert numeric IDs → external NodeId
          idPairs <- state.nodes.iterator.map { case (k, node) =>
            toHex(HashLen, k).map(hex => (k, NodeId(hex), node))
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

          // Resolve entry point NodeId
          entryId <- Sync[F].fromOption(
            idMap.get(entryRawId),
            RouteCompilerError(s"Entry point NodeId mapping failed for ID $entryRawId")
          )
        yield Dag(
          entry = entryId,
          hash = dagHash,
          repeatPolicy = spec.repeatPolicy,
          nodes = nodesM.result().toMap,
          edges = edgesM
        )

      override def compile(spec: Spec, salt: RouteSalt): F[Dag] =
        for
          saltH <- hasher.hashChars(salt)
          dag   <- buildDagFromSpec(spec, saltH)
        yield dag
