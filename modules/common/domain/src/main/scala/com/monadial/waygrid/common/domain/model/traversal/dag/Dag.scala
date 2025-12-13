package com.monadial.waygrid.common.domain.model.traversal.dag

import com.monadial.waygrid.common.domain.model.routing.Value.RepeatPolicy
import com.monadial.waygrid.common.domain.model.traversal.dag.Value.{DagHash, EdgeGuard, NodeId}

final case class Dag(
  hash: DagHash,
  entry: NodeId,
  repeatPolicy: RepeatPolicy,
  nodes: Map[NodeId, Node],
  edges: List[Edge]
):
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
