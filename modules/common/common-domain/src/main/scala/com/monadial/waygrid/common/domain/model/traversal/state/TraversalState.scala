package com.monadial.waygrid.common.domain.model.traversal.state

import com.monadial.waygrid.common.domain.model.routing.Value.TraversalId
import com.monadial.waygrid.common.domain.model.traversal.dag.Dag
import com.monadial.waygrid.common.domain.model.traversal.dag.Value.NodeId
import com.monadial.waygrid.common.domain.model.traversal.state.Event.StateEvent
import com.monadial.waygrid.common.domain.model.traversal.state.Value.{RemainingNodes, RetryAttempt}
import com.monadial.waygrid.common.domain.model.vectorclock.VectorClock
import com.monadial.waygrid.common.domain.value.Address.NodeAddress

final case class TraversalState(
  traversalId: TraversalId,
  current: Option[NodeId],
  completed: Set[NodeId],
  failed: Set[NodeId],
  retries: Map[NodeId, RetryAttempt],
  vectorClock: VectorClock,
  history: Vector[StateEvent],
  remainingNodes: RemainingNodes
):

  def start(
    node: NodeId,
    actor: NodeAddress,
    foreignVectorClock: Option[VectorClock]
  ): TraversalState = ???

  def resume(
    node: NodeId,
    actor: NodeAddress,
    foreignVectorClock: Option[VectorClock]
  ) = ???

  def success(
    node: NodeId,
    actor: NodeAddress,
    foreignVectorClock: Option[VectorClock]
  ): TraversalState = ???

  def complete(
    node: NodeId,
    actor: NodeAddress,
    foreignVectorClock: Option[VectorClock]
  ) = ???

  def retryNode(
    node: NodeId,
    actor: NodeAddress,
    foreignVectorClock: Option[VectorClock]
  ): TraversalState = ???

  def successNode(
    node: NodeId,
    actor: NodeAddress,
    foreignVectorClock: Option[VectorClock]
  ): TraversalState = ???

  def failNode(
    node: NodeId,
    actor: NodeAddress,
    foreignVectorClock: Option[VectorClock]
  ): TraversalState = ???

  def hasProgress: Boolean = completed.nonEmpty || failed.nonEmpty

object TraversalState:
  def initial(id: TraversalId, actor: NodeAddress, dag: Dag): TraversalState = TraversalState(
    traversalId = id,
    current = None,
    completed = Set.empty,
    failed = Set.empty,
    retries = Map.empty,
    vectorClock = VectorClock.initial(actor),
    history = Vector.empty,
    remainingNodes = RemainingNodes(dag.nodes.size)
  )
