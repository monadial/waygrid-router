package com.monadial.waygrid.common.domain.model.scheduling

import com.monadial.waygrid.common.domain.algebra.messaging.event.Event
import com.monadial.waygrid.common.domain.algebra.messaging.message.Groupable
import com.monadial.waygrid.common.domain.algebra.messaging.message.Value.{ MessageGroupId, MessageId }
import com.monadial.waygrid.common.domain.model.routing.Value.TraversalId
import com.monadial.waygrid.common.domain.model.scheduling.Value.TaskId
import com.monadial.waygrid.common.domain.syntax.ULIDSyntax.mapValue

import java.time.Instant
import scala.concurrent.duration.FiniteDuration

object Event:

  sealed trait SchedulingEvent extends Event with Groupable:
    def traversalId: TraversalId
    def taskId: TaskId
    override def groupId: MessageGroupId = traversalId.mapValue[MessageGroupId]

  final case class TaskSchedulingRequested(
    id: MessageId,
    traversalId: TraversalId,
    taskId: TaskId,
    scheduledAt: Instant
  ) extends SchedulingEvent

  object TaskSchedulingRequested:
    def apply(
      id: MessageId,
      traversalId: TraversalId,
      taskId: TaskId,
      delay: FiniteDuration
    ): TaskSchedulingRequested = TaskSchedulingRequested(id, traversalId, taskId, Instant.now().plusMillis(delay.toMillis))
