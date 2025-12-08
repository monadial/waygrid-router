package com.monadial.waygrid.common.domain.model.traversal.fsm

import com.monadial.waygrid.common.domain.model.routing.Value.TraversalId

sealed trait TraversalSignal:
  def traversalId: TraversalId

object TraversalSignal:
  final case class Begin(
    traversalId: TraversalId
  ) extends TraversalSignal

  final case class Resume(
    traversalId: TraversalId
  ) extends TraversalSignal

  final case class Cancel(
    traversalId: TraversalId
  ) extends TraversalSignal

  final case class Retry(
    traversalId: TraversalId
  ) extends TraversalSignal

  final case class NodeStart(
    traversalId: TraversalId
  ) extends TraversalSignal

  final case class NodeSuccess(
    traversalId: TraversalId
  ) extends TraversalSignal

  final case class NodeFailure(
    traversalId: TraversalId
  ) extends TraversalSignal

  final case class Completed(
    traversalId: TraversalId
  ) extends TraversalSignal

  final case class Failed(
    traversalId: TraversalId
  ) extends TraversalSignal
