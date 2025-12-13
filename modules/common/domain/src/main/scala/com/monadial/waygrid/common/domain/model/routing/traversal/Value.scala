package com.monadial.waygrid.common.domain.model.routing.traversal

import com.monadial.waygrid.common.domain.model.routing.Value.TraversalId
import com.monadial.waygrid.common.domain.model.traversal.dag.{Dag, Node}
import com.monadial.waygrid.common.domain.value.Address.NodeAddress

import scala.concurrent.duration.FiniteDuration

object Value:

  sealed trait TraversalInput:
    val id: TraversalId
    val actor: NodeAddress
    val dag: Dag

  object TraversalInput:
    final case class Start(
      id: TraversalId,
      actor: NodeAddress,
      dag: Dag
    ) extends TraversalInput

    final case class Continue(
      id: TraversalId,
      actor: NodeAddress,
      dag: Dag
    ) extends TraversalInput

    final case class Success(
      id: TraversalId,
      actor: NodeAddress,
      dag: Dag
    ) extends TraversalInput

    final case class Failure(
      id: TraversalId,
      actor: NodeAddress,
      dag: Dag
    ) extends TraversalInput

  sealed trait TraversalOutcome

  final case class Started(
    id: TraversalId,
    nextNode: Node
  ) extends TraversalOutcome

  final case class Next(
    id: TraversalId,
    nextNode: Node
  ) extends TraversalOutcome

  final case class Failed(
    id: TraversalId
  ) extends TraversalOutcome

  final case class Scheduled(
    id: TraversalId,
    delay: FiniteDuration
  ) extends TraversalOutcome

  final case class Ignored(
    id: TraversalId
  ) extends TraversalOutcome

  final case class NoAction(
    id: TraversalId
  ) extends TraversalOutcome

  final case class Completed(
    id: TraversalId
  ) extends TraversalOutcome

  final case class NoHandler() extends TraversalOutcome

  final case class Errored(
    id: TraversalId,
    error: String
  ) extends TraversalOutcome
