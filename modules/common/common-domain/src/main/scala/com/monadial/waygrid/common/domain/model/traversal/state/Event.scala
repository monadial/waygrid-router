package com.monadial.waygrid.common.domain.model.traversal.state

import com.monadial.waygrid.common.domain.model.node.Value.NodeId
import com.monadial.waygrid.common.domain.model.vectorclock.VectorClock
import com.monadial.waygrid.common.domain.value.Address.NodeAddress

object Event:

  sealed trait StateEvent:
    def node: NodeId
    def actor: NodeAddress
    def vectorClock: VectorClock

  final case class TraversalStarted(
    node: NodeId,
    actor: NodeAddress,
    vectorClock: VectorClock
  ) extends StateEvent

  final case class TraversalResumed(
    node: NodeId,
    actor: NodeAddress,
    vectorClock: VectorClock
  ) extends StateEvent

  final case class TraversalCompleted(
    node: NodeId,
    actor: NodeAddress,
    vectorClock: VectorClock
  ) extends StateEvent

  final case class TraversalFailed(
    node: NodeId,
    actor: NodeAddress,
    vectorClock: VectorClock
  ) extends StateEvent

  final case class NodeTraversalRetried(
    node: NodeId,
    actor: NodeAddress,
    vectorClock: VectorClock
  ) extends StateEvent

  final case class NodeTraversalSucceeded(
    node: NodeId,
    actor: NodeAddress,
    vectorClock: VectorClock
  ) extends StateEvent

  final case class NodeTraversalFailed(
    node: NodeId,
    actor: NodeAddress,
    vectorClock: VectorClock
  ) extends StateEvent
