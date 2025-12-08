package com.monadial.waygrid.common.domain.model.traversal.fsm

import com.monadial.waygrid.common.domain.model.routing.Value.TraversalId
import com.monadial.waygrid.common.domain.model.traversal.state.Value.RetryAttempt

import java.time.Instant

sealed trait TraversalEffect:
  val traversalId: TraversalId

object TraversalEffect:
  final case class DispatchNode(
    traversalId: TraversalId
  ) extends TraversalEffect

  final case class Schedule(
    traversalId: TraversalId,
    scheduledAt: Instant
  ) extends TraversalEffect

  final case class ScheduleRetry(
    traversalId: TraversalId,
    scheduledAt: Instant,
    retryAttempt: RetryAttempt
  ) extends TraversalEffect

  final case class Complete(
    traversalId: TraversalId
  ) extends TraversalEffect

  final case class Fail(
    traversalId: TraversalId
  ) extends TraversalEffect

  final case class Cancel(
    traversalId: TraversalId
  ) extends TraversalEffect

  final case class NoOp(
    traversalId: TraversalId
  ) extends TraversalEffect
