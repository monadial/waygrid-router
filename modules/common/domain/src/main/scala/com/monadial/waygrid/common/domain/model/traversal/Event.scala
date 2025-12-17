package com.monadial.waygrid.common.domain.model.traversal

import com.monadial.waygrid.common.domain.algebra.messaging.event.Event
import com.monadial.waygrid.common.domain.algebra.messaging.message.Groupable
import com.monadial.waygrid.common.domain.algebra.messaging.message.Value.{ MessageGroupId, MessageId }
import com.monadial.waygrid.common.domain.model.routing.Value.TraversalId
import com.monadial.waygrid.common.domain.model.traversal.dag.Value.NodeId
import com.monadial.waygrid.common.domain.syntax.ULIDSyntax.mapValue

object Event:

  sealed trait TraversalEvent extends Event with Groupable:
    val traversalId: TraversalId

    override def groupId: MessageGroupId =
      traversalId
        .mapValue[MessageGroupId]

  final case class TraversalRequested(
    id: MessageId,
    traversalId: TraversalId
  ) extends TraversalEvent

  final case class TraversalScheduled(
    id: MessageId,
    traversalId: TraversalId
  ) extends TraversalEvent

  final case class TraversalResumed(
    id: MessageId,
    traversalId: TraversalId,
    nodeId: NodeId
  ) extends TraversalEvent

  final case class TraversalFailed(
    id: MessageId,
    traversalId: TraversalId
  ) extends TraversalEvent

  final case class TraversalCancelled(
    id: MessageId,
    traversalId: TraversalId
  ) extends TraversalEvent

  final case class NodeTraversalRequested(
    id: MessageId,
    traversalId: TraversalId,
    nodeId: NodeId
  ) extends TraversalEvent

  final case class NodeTraversalSucceeded(
    id: MessageId,
    traversalId: TraversalId,
    nodeId: NodeId
  ) extends TraversalEvent

  final case class NodeTraversalFailed(
    id: MessageId,
    traversalId: TraversalId,
    nodeId: NodeId
  ) extends TraversalEvent
