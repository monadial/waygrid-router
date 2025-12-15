package com.monadial.waygrid.common.domain.model.traversal.dag

import cats.data.NonEmptyList
import com.monadial.waygrid.common.domain.model.routing.Value.RepeatPolicy
import com.monadial.waygrid.common.domain.model.traversal.dag.Value.{DagHash, EdgeGuard, ForkId, NodeId}

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
          case _                              => true
        )
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
          case _                                         => false
        => n.id
    }
