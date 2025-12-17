package com.monadial.waygrid.common.domain.model.traversal.dag

import cats.data.NonEmptyList
import com.monadial.waygrid.common.domain.model.routing.Value.RepeatPolicy
import com.monadial.waygrid.common.domain.model.traversal.dag.Value.{ DagHash, EdgeGuard, ForkId, NodeId }

import scala.concurrent.duration.FiniteDuration

/**
 * A Directed Acyclic Graph representing a workflow/traversal definition.
 *
 * @param hash Content-based hash for cache/dedup
 * @param entryPoints Valid starting nodes (supports multi-origin DAGs)
 * @param repeatPolicy How/if the traversal should repeat
 * @param nodes All nodes in the DAG
 * @param edges All edges connecting nodes
 * @param timeout Optional maximum duration for the entire traversal.
 *                If set, a TraversalTimeout signal will be scheduled when traversal begins.
 *                If the traversal doesn't complete within this duration, it will be canceled.
 */
final case class Dag(
  hash: DagHash,
  entryPoints: NonEmptyList[NodeId],
  repeatPolicy: RepeatPolicy,
  nodes: Map[NodeId, Node],
  edges: List[Edge],
  timeout: Option[FiniteDuration] = None
):
  inline def entry: NodeId = entryPoints.head

  inline def isEntryPoint(nodeId: NodeId): Boolean =
    entryPoints.toList.contains(nodeId)

  /** True when traversal never has more than one active node (no fork/join, no multi-successor fan-out). */
  def isLinear: Boolean =
    val hasForkOrJoin = nodes.values.exists(n => n.isFork || n.isJoin)
    if hasForkOrJoin then false
    else
      // Ensure each (from, guard) has at most one successor, and avoid conditionals.
      val grouped = edges.groupBy(e => (e.from, e.guard))
      grouped.forall { case ((_, guard), es) =>
        es.size <= 1 && (guard match
          case Value.EdgeGuard.Conditional(_) => false
          case _                              => true)
      }

  /** Returns the node corresponding to a given ID, if present. */
  def nodeOf(id: NodeId): Option[Node] =
    nodes.get(id)

  /** All edges starting from a given node. */
  def outgoingEdges(from: NodeId): List[Edge] =
    edges.filter(_.from == from)

  /** Edges that match a given guard (OnSuccess / OnFailure). */
  def outgoingEdges(from: NodeId, guard: EdgeGuard): List[Edge] =
    edges.filter(e => e.from == from && e.guard == guard)

  /** Find next node IDs based on the traversal guard result. */
  def nextNodeIds(from: NodeId, guard: EdgeGuard): List[NodeId] =
    outgoingEdges(from, guard).map(_.to)

  /** Find next nodes directly. */
  def nextNodes(from: NodeId, guard: EdgeGuard): List[Node] =
    nextNodeIds(from, guard).flatMap(nodes.get)

  /** Returns true if this DAG has no outgoing edges from a node. */
  def isTerminalNode(node: NodeId): Boolean =
    edges.forall(_.from != node)

  /** Returns entry node, if present. */
  def entryNode: Option[Node] =
    nodes.get(entry)

  /** Find a Join node id for a given fork id (if present). */
  def joinNodeIdFor(forkId: ForkId): Option[NodeId] =
    nodes.values.collectFirst {
      case n if n.nodeType match
            case NodeType.Join(fid, _, _) if fid == forkId => true
            case _ =>
              false
          => n.id
    }

  // ---------------------------------------------------------------------------
  // DAG Validation
  // ---------------------------------------------------------------------------

  /**
   * Validate the DAG structure and return a list of validation errors.
   * This should be called before using the DAG for traversal to catch
   * configuration issues early.
   *
   * Validates:
   * - Each Fork has exactly one matching Join
   * - Each Join references an existing Fork
   * - Fork nodes have at least 2 outgoing edges (otherwise it's not a real fork)
   * - No circular dependencies exist
   * - All edge targets exist as nodes
   */
  def validate: List[DagValidationError] =
    val errors = List.newBuilder[DagValidationError]

    // 1. Validate fork/join pairing
    val forkNodes = nodes.values.collect { case n if n.isFork => n }.toList
    val joinNodes = nodes.values.collect { case n if n.isJoin => n }.toList

    // Extract fork IDs from Fork nodes
    val forkIdToNode: Map[ForkId, Node] = forkNodes.flatMap { n =>
      n.nodeType match
        case NodeType.Fork(forkId) => Some(forkId -> n)
        case _                     => None
    }.toMap

    // Extract fork IDs from Join nodes
    val joinsByForkId: Map[ForkId, List[Node]] = joinNodes.flatMap { n =>
      n.nodeType match
        case NodeType.Join(forkId, _, _) => Some(forkId -> n)
        case _                           => None
    }.groupMap(_._1)(_._2)

    // Check each Fork has exactly one Join
    forkIdToNode.foreach { case (forkId, forkNode) =>
      joinsByForkId.get(forkId) match
        case None =>
          errors += DagValidationError.ForkWithoutJoin(forkId, forkNode.id)
        case Some(joins) if joins.size > 1 =>
          errors += DagValidationError.MultiplJoinsForFork(forkId, joins.map(_.id))
        case _ => // Exactly one join - OK
    }

    // Check each Join references an existing Fork
    joinsByForkId.foreach { case (forkId, joinList) =>
      if !forkIdToNode.contains(forkId) then
        joinList.foreach { joinNode =>
          errors += DagValidationError.JoinWithoutMatchingFork(forkId, joinNode.id)
        }
    }

    // 2. Validate Fork nodes have at least 2 outgoing edges
    forkNodes.foreach { forkNode =>
      val outEdges = outgoingEdges(forkNode.id, EdgeGuard.OnSuccess) ++
        outgoingEdges(forkNode.id, EdgeGuard.Always)
      val edgeCount = if outEdges.nonEmpty then outEdges.size else outgoingEdges(forkNode.id).size
      if edgeCount < 2 then
        errors += DagValidationError.ForkWithInsufficientBranches(forkNode.id, edgeCount)
    }

    // 3. Validate all edge targets exist
    edges.foreach { edge =>
      if !nodes.contains(edge.to) then
        errors += DagValidationError.EdgeTargetNotFound(edge.from, edge.to, edge.guard)
      if !nodes.contains(edge.from) then
        errors += DagValidationError.EdgeSourceNotFound(edge.from, edge.to, edge.guard)
    }

    // 4. Check for cycles using DFS
    detectCycles().foreach { cycle =>
      errors += DagValidationError.CycleDetected(cycle)
    }

    errors.result()

  /**
   * Detect cycles in the DAG using depth-first search.
   * Returns Some(cycle) if a cycle is found, None otherwise.
   */
  private def detectCycles(): Option[List[NodeId]] =
    import scala.collection.mutable

    val visited = mutable.Set.empty[NodeId]
    val inStack = mutable.Set.empty[NodeId]
    val parent  = mutable.Map.empty[NodeId, NodeId]

    def dfs(nodeId: NodeId): Option[List[NodeId]] =
      if inStack.contains(nodeId) then
        // Found a cycle - reconstruct the path
        Some(reconstructCycle(nodeId, parent))
      else if visited.contains(nodeId) then
        None
      else
        visited += nodeId
        inStack += nodeId

        val result = outgoingEdges(nodeId).view.flatMap { edge =>
          parent(edge.to) = nodeId
          dfs(edge.to)
        }.headOption

        inStack -= nodeId
        result

    def reconstructCycle(cycleNode: NodeId, parent: mutable.Map[NodeId, NodeId]): List[NodeId] =
      val cycle = List.newBuilder[NodeId]
      cycle += cycleNode
      var current = parent.get(cycleNode)
      while current.isDefined && current.get != cycleNode do
        cycle += current.get
        current = parent.get(current.get)
      cycle += cycleNode // Close the cycle
      cycle.result().reverse

    // Start DFS from all entry points
    entryPoints.toList.view.flatMap(dfs).headOption

  /** Returns true if the DAG is valid (no validation errors). */
  def isValid: Boolean = validate.isEmpty

/**
 * Validation errors that can be detected in DAG structure.
 */
enum DagValidationError:
  /** Fork node has no matching Join node */
  case ForkWithoutJoin(forkId: ForkId, forkNodeId: NodeId)

  /** Multiple Join nodes reference the same Fork ID */
  case MultiplJoinsForFork(forkId: ForkId, joinNodeIds: List[NodeId])

  /** Join node references a Fork ID that doesn't exist */
  case JoinWithoutMatchingFork(forkId: ForkId, joinNodeId: NodeId)

  /** Fork node has fewer than 2 outgoing edges (not a real fork) */
  case ForkWithInsufficientBranches(forkNodeId: NodeId, branchCount: Int)

  /** Edge target node doesn't exist in the DAG */
  case EdgeTargetNotFound(from: NodeId, to: NodeId, guard: EdgeGuard)

  /** Edge source node doesn't exist in the DAG */
  case EdgeSourceNotFound(from: NodeId, to: NodeId, guard: EdgeGuard)

  /** Cycle detected in the DAG (should be acyclic) */
  case CycleDetected(cycle: List[NodeId])
