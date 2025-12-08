package com.monadial.waygrid.common.domain.model.traversal.fsm

import com.monadial.waygrid.common.domain.model.routing.Value.TraversalId

sealed trait TraversalError:
  val traversalId: TraversalId

final case class UnsupportedSignal(
  traversalId: TraversalId
) extends TraversalError

final case class EmptyDag(
  traversalId: TraversalId
) extends TraversalError

final case class AlreadyInProgress(
  traversalId: TraversalId
) extends TraversalError

final case class MissingEntryNode(
  traversalId: TraversalId
) extends TraversalError
