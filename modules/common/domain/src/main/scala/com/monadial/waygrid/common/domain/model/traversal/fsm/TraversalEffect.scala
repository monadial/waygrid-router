package com.monadial.waygrid.common.domain.model.traversal.fsm

import java.time.Instant

import com.monadial.waygrid.common.domain.model.routing.Value.TraversalId
import com.monadial.waygrid.common.domain.model.traversal.dag.Value.{ BranchId, ForkId, NodeId }
import com.monadial.waygrid.common.domain.model.traversal.state.TraversalState
import com.monadial.waygrid.common.domain.model.traversal.state.Value.RetryAttempt

sealed trait TraversalEffect:
  val traversalId: TraversalId

object TraversalEffect:

  // ---------------------------------------------------------------------------
  // Linear Traversal Effects (existing)
  // ---------------------------------------------------------------------------

  /**
   * Dispatch a single node for execution.
   *
   * @param traversalId The traversal this effect belongs to
   * @param nodeId The node to dispatch for execution
   * @param branchesToCancel Optional set of (BranchId, ForkId) pairs to cancel.
   *                         Used when OR join completes and remaining branches should be stopped.
   * @param scheduleTraversalTimeout Optional traversal-level timeout to schedule.
   *                                  Tuple of (timeoutId, deadline). Used on Begin when DAG has timeout.
   */
  final case class DispatchNode(
    traversalId: TraversalId,
    nodeId: NodeId,
    branchesToCancel: Set[(BranchId, ForkId)] = Set.empty,
    scheduleTraversalTimeout: Option[(String, Instant)] = None
  ) extends TraversalEffect

  /**
   * Schedule a node for future execution.
   */
  final case class Schedule(
    traversalId: TraversalId,
    scheduledAt: Instant,
    nodeId: NodeId
  ) extends TraversalEffect

  /**
   * Schedule a retry for a failed node.
   */
  final case class ScheduleRetry(
    traversalId: TraversalId,
    scheduledAt: Instant,
    retryAttempt: RetryAttempt,
    nodeId: NodeId
  ) extends TraversalEffect

  /**
   * Mark traversal as complete.
   *
   * @param traversalId The traversal this effect belongs to
   * @param cancelTimeoutId Optional timeout ID to cancel (if traversal had a timeout scheduled)
   */
  final case class Complete(
    traversalId: TraversalId,
    cancelTimeoutId: Option[String] = None
  ) extends TraversalEffect

  /**
   * Mark traversal as failed.
   *
   * @param traversalId The traversal this effect belongs to
   * @param cancelTimeoutId Optional timeout ID to cancel (if traversal had a timeout scheduled)
   */
  final case class Fail(
    traversalId: TraversalId,
    cancelTimeoutId: Option[String] = None
  ) extends TraversalEffect

  /**
   * Cancel the traversal.
   *
   * @param traversalId The traversal this effect belongs to
   * @param cancelTimeoutId Optional timeout ID to cancel (if traversal had a timeout scheduled)
   */
  final case class Cancel(
    traversalId: TraversalId,
    cancelTimeoutId: Option[String] = None
  ) extends TraversalEffect

  /**
   * No operation - state unchanged.
   */
  final case class NoOp(
    traversalId: TraversalId
  ) extends TraversalEffect

  // ---------------------------------------------------------------------------
  // Fork/Join Effects (new)
  // ---------------------------------------------------------------------------

  /**
   * Dispatch multiple nodes in parallel (fan-out from Fork node).
   * Each node is associated with its branch context.
   *
   * @param traversalId The traversal this effect belongs to
   * @param nodes List of (NodeId, BranchId) pairs to dispatch
   * @param joinTimeout Optional timeout deadline for the corresponding join node.
   *                    If present, the executor should schedule a Timeout signal.
   * @param joinNodeId The join node ID (if timeout is set, for the Timeout signal)
   */
  final case class DispatchNodes(
    traversalId: TraversalId,
    nodes: List[(NodeId, BranchId)],
    joinTimeout: Option[Instant] = None,
    joinNodeId: Option[NodeId] = None
  ) extends TraversalEffect

  /**
   * A join condition has been satisfied, continue traversal.
   */
  final case class JoinComplete(
    traversalId: TraversalId,
    joinNodeId: NodeId,
    forkId: ForkId,
    completedBranches: Set[BranchId]
  ) extends TraversalEffect

  /**
   * Request cancellation of running branches (e.g., OR join completed).
   */
  final case class CancelBranches(
    traversalId: TraversalId,
    forkId: ForkId,
    branchIds: Set[BranchId],
    reason: String
  ) extends TraversalEffect

  /**
   * Schedule a timeout for a fork/join operation.
   */
  final case class ScheduleTimeout(
    traversalId: TraversalId,
    nodeId: NodeId,
    branchId: Option[BranchId],
    deadline: Instant
  ) extends TraversalEffect

  /**
   * Persist state to storage (for recovery).
   */
  final case class PersistState(
    traversalId: TraversalId,
    state: TraversalState
  ) extends TraversalEffect

  // ---------------------------------------------------------------------------
  // Traversal-Level Effects
  // ---------------------------------------------------------------------------

  /**
   * Schedule a traversal-level timeout with the scheduler service.
   * The scheduler should send a TraversalTimeout signal when the deadline passes.
   *
   * The executor should:
   * 1. Send a schedule request to the scheduler service
   * 2. Store the timeoutId for later cancellation
   * 3. On traversal completion, send a cancel request to the scheduler
   *
   * @param traversalId The traversal this timeout belongs to
   * @param deadline Absolute timestamp when timeout should fire
   * @param timeoutId Unique identifier for this timeout (for cancellation)
   */
  final case class ScheduleTraversalTimeout(
    traversalId: TraversalId,
    deadline: Instant,
    timeoutId: String
  ) extends TraversalEffect

  /**
   * Cancel a previously scheduled traversal timeout.
   * Sent when the traversal completes successfully before the timeout.
   *
   * @param traversalId The traversal this cancellation belongs to
   * @param timeoutId The timeout identifier to cancel
   */
  final case class CancelTraversalTimeout(
    traversalId: TraversalId,
    timeoutId: String
  ) extends TraversalEffect
