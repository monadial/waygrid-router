package com.monadial.waygrid.common.domain.model.traversal.fsm

import com.monadial.waygrid.common.domain.model.routing.Value.TraversalId
import com.monadial.waygrid.common.domain.model.traversal.dag.Value.{ BranchId, ForkId, NodeId }
import com.monadial.waygrid.common.domain.model.traversal.state.{ TraversalState, Value }
import com.monadial.waygrid.common.domain.model.traversal.state.Value.RetryAttempt

import java.time.Instant

sealed trait TraversalEffect:
  val traversalId: TraversalId

object TraversalEffect:

  // ---------------------------------------------------------------------------
  // Linear Traversal Effects (existing)
  // ---------------------------------------------------------------------------

  /**
   * Dispatch a single node for execution.
   */
  final case class DispatchNode(
    traversalId: TraversalId,
    nodeId: NodeId
  ) extends TraversalEffect

  /**
   * Schedule a node for future execution.
   */
  final case class Schedule(
    traversalId: TraversalId,
    scheduledAt: Instant,
    nodeId: NodeId,
  ) extends TraversalEffect

  /**
   * Schedule a retry for a failed node.
   */
  final case class ScheduleRetry(
    traversalId: TraversalId,
    scheduledAt: Instant,
    retryAttempt: RetryAttempt,
    nodeId: NodeId,
  ) extends TraversalEffect

  /**
   * Mark traversal as complete.
   */
  final case class Complete(
    traversalId: TraversalId
  ) extends TraversalEffect

  /**
   * Mark traversal as failed.
   */
  final case class Fail(
    traversalId: TraversalId
  ) extends TraversalEffect

  /**
   * Cancel the traversal.
   */
  final case class Cancel(
    traversalId: TraversalId
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
   */
  final case class DispatchNodes(
    traversalId: TraversalId,
    nodes: List[(NodeId, BranchId)]
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
