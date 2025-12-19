package com.monadial.waygrid.common.domain.model.scheduling

import java.time.Instant

import scala.concurrent.duration.FiniteDuration

import com.monadial.waygrid.common.domain.algebra.messaging.event.Event
import com.monadial.waygrid.common.domain.algebra.messaging.message.Groupable
import com.monadial.waygrid.common.domain.algebra.messaging.message.Value.{ MessageGroupId, MessageId }
import com.monadial.waygrid.common.domain.model.routing.Value.TraversalId
import com.monadial.waygrid.common.domain.model.scheduling.Value.TaskId
import com.monadial.waygrid.common.domain.model.traversal.dag.Value.NodeId
import com.monadial.waygrid.common.domain.syntax.ULIDSyntax.mapValue

object Event:

  sealed trait SchedulingEvent extends Event with Groupable:
    def traversalId: TraversalId
    def taskId: TaskId
    override def groupId: MessageGroupId = traversalId.mapValue[MessageGroupId]

  final case class TaskSchedulingRequested(
    id: MessageId,
    traversalId: TraversalId,
    taskId: TaskId,
    scheduledAt: Instant,
    nodeId: NodeId
  ) extends SchedulingEvent

  object TaskSchedulingRequested:
    def apply(
      id: MessageId,
      traversalId: TraversalId,
      taskId: TaskId,
      delay: FiniteDuration,
      nodeId: NodeId
    ): TaskSchedulingRequested =
      TaskSchedulingRequested(id, traversalId, taskId, Instant.now().plusMillis(delay.toMillis), nodeId)
