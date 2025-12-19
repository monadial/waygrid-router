package com.monadial.waygrid.system.waystation.model.traversal

import java.time.Instant

import scala.concurrent.duration.FiniteDuration

import com.monadial.waygrid.common.domain.model.routing.Value.{ DeliveryStrategy, TraversalId }
import com.monadial.waygrid.common.domain.model.routing.traversal.TraversalState
import com.monadial.waygrid.common.domain.model.traversal.dag.Node
import com.monadial.waygrid.common.domain.model.traversal.dag.Value.NodeId

sealed trait TraversalResult

/** Dispatch message to node immediately */
final case class Dispatch(node: Node, strategy: DeliveryStrategy) extends TraversalResult

/** Schedule retry for a failed node */
final case class ScheduleRetry(node: Node, attempt: Int, delay: FiniteDuration) extends TraversalResult

/** Schedule delayed dispatch based on node's delivery strategy */
final case class ScheduleDelayedDispatch(node: Node, delay: FiniteDuration) extends TraversalResult

/** Record permanent failure for a node */
final case class RecordFailure(nodeId: NodeId, reason: String) extends TraversalResult

/** Traversal is complete */
final case class CompleteTraversal(state: TraversalState) extends TraversalResult

/** Schedule repeat traversal based on repeat policy */
final case class ScheduleRepeatTraversal(traversalId: TraversalId, delay: FiniteDuration) extends TraversalResult

/** Schedule repeat traversal until specific time */
final case class ScheduleRepeatUntil(traversalId: TraversalId, delay: FiniteDuration, until: Instant)
    extends TraversalResult

/** Schedule repeat traversal for N times */
final case class ScheduleRepeatTimes(traversalId: TraversalId, delay: FiniteDuration, remaining: Long)
    extends TraversalResult
