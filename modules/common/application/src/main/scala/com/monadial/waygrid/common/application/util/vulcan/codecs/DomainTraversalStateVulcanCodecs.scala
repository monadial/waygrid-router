package com.monadial.waygrid.common.application.util.vulcan.codecs

import cats.syntax.all.*
import com.monadial.waygrid.common.application.util.vulcan.codecs.DomainForkJoinVulcanCodecs.given
import com.monadial.waygrid.common.application.util.vulcan.codecs.DomainPrimitivesVulcanCodecs.given
import com.monadial.waygrid.common.application.util.vulcan.codecs.DomainStateEventVulcanCodecs.given
import com.monadial.waygrid.common.application.util.vulcan.codecs.DomainVectorClockVulcanCodecs.given
import com.monadial.waygrid.common.domain.model.traversal.dag.Value.NodeId
import com.monadial.waygrid.common.domain.model.traversal.state.Event.StateEvent
import com.monadial.waygrid.common.domain.model.traversal.state.TraversalState
import com.monadial.waygrid.common.domain.model.traversal.state.Value.RetryAttempt
import vulcan.Codec

/**
 * Vulcan Avro codec for TraversalState.
 *
 * TraversalState is the main state container for DAG traversal tracking.
 * It contains:
 * - Core state: traversalId, active/completed/failed nodes, retries
 * - Fork/Join state: forkScopes, branchStates, pendingJoins, nodeToBranch
 * - Vector clock for causal ordering
 * - Event history for replay
 *
 * Import `DomainTraversalStateVulcanCodecs.given` to bring this codec into scope.
 */
object DomainTraversalStateVulcanCodecs:

  /**
   * Map[NodeId, RetryAttempt] codec as list of entries.
   */
  private case class RetryEntry(nodeId: NodeId, attempt: RetryAttempt)
  private given Codec[RetryEntry] = Codec.record(
    name = "RetryEntry",
    namespace = "com.monadial.waygrid.common.domain.model.traversal.state"
  ) { field =>
    (
      field("nodeId", _.nodeId),
      field("attempt", _.attempt)
    ).mapN(RetryEntry.apply)
  }

  given Codec[Map[NodeId, RetryAttempt]] =
    Codec.list[RetryEntry].imap(entries => entries.map(e => e.nodeId -> e.attempt).toMap)(_.toList.map { case (k, v) =>
      RetryEntry(k, v)
    })

  /**
   * Vector[StateEvent] codec as list.
   */
  given Codec[Vector[StateEvent]] =
    Codec.list[StateEvent].imap(_.toVector)(_.toList)

  /**
   * TraversalState encoded as Avro record.
   */
  given Codec[TraversalState] = Codec.record(
    name = "TraversalState",
    namespace = "com.monadial.waygrid.common.domain.model.traversal.state"
  ) { field =>
    (
      field("traversalId", _.traversalId),
      field("active", _.active),
      field("completed", _.completed),
      field("failed", _.failed),
      field("retries", _.retries),
      field("vectorClock", _.vectorClock),
      field("history", _.history),
      field("remainingNodes", _.remainingNodes),
      field("forkScopes", _.forkScopes),
      field("branchStates", _.branchStates),
      field("pendingJoins", _.pendingJoins),
      field("nodeToBranch", _.nodeToBranch),
      field("traversalTimeoutId", _.traversalTimeoutId),
      field("stateVersion", _.stateVersion)
    ).mapN(TraversalState.apply)
  }
