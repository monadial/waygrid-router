package com.monadial.waygrid.common.domain.model.traversal.state

import com.monadial.waygrid.common.domain.model.traversal.dag.Value.{ BranchId, ForkId, NodeId }
import com.monadial.waygrid.common.domain.model.traversal.state.Value.RetryAttempt
import com.monadial.waygrid.common.domain.model.vectorclock.VectorClock
import com.monadial.waygrid.common.domain.value.Address.NodeAddress

import java.time.Instant

object Event:

  sealed trait StateEvent:
    def node: NodeId
    def actor: NodeAddress
    def vectorClock: VectorClock

  // ---------------------------------------------------------------------------
  // Linear Traversal Events (existing)
  // ---------------------------------------------------------------------------

  final case class TraversalStarted(
    node: NodeId,
    actor: NodeAddress,
    vectorClock: VectorClock
  ) extends StateEvent

  final case class TraversalScheduled(
    node: NodeId,
    actor: NodeAddress,
    scheduledAt: Instant,
    vectorClock: VectorClock
  ) extends StateEvent

  final case class TraversalResumed(
    node: NodeId,
    actor: NodeAddress,
    vectorClock: VectorClock
  ) extends StateEvent

  final case class TraversalCompleted(
    node: NodeId,
    actor: NodeAddress,
    vectorClock: VectorClock
  ) extends StateEvent

  final case class TraversalFailed(
    node: NodeId,
    actor: NodeAddress,
    vectorClock: VectorClock,
    reason: Option[String] = None
  ) extends StateEvent

  final case class TraversalCanceled(
    node: NodeId,
    actor: NodeAddress,
    vectorClock: VectorClock
  ) extends StateEvent

  final case class NodeTraversalRetried(
    node: NodeId,
    actor: NodeAddress,
    attempt: RetryAttempt,
    vectorClock: VectorClock
  ) extends StateEvent

  final case class NodeTraversalSucceeded(
    node: NodeId,
    actor: NodeAddress,
    vectorClock: VectorClock
  ) extends StateEvent

  final case class NodeTraversalFailed(
    node: NodeId,
    actor: NodeAddress,
    vectorClock: VectorClock,
    reason: Option[String] = None
  ) extends StateEvent

  // ---------------------------------------------------------------------------
  // Fork/Join Events (new)
  // ---------------------------------------------------------------------------

  /**
   * A Fork node was reached, initiating parallel branch execution.
   */
  final case class ForkStarted(
    node: NodeId,
    forkId: ForkId,
    branches: Set[BranchId],
    actor: NodeAddress,
    vectorClock: VectorClock
  ) extends StateEvent

  /**
   * A branch within a fork has started execution.
   */
  final case class BranchStarted(
    node: NodeId,
    branchId: BranchId,
    forkId: ForkId,
    actor: NodeAddress,
    vectorClock: VectorClock
  ) extends StateEvent

  /**
   * A branch within a fork has completed (successfully or with failure).
   */
  final case class BranchCompleted(
    node: NodeId,
    branchId: BranchId,
    forkId: ForkId,
    result: BranchResult,
    actor: NodeAddress,
    vectorClock: VectorClock
  ) extends StateEvent

  /**
   * A branch was canceled (e.g., when OR join completes with another branch).
   */
  final case class BranchCanceled(
    node: NodeId,
    branchId: BranchId,
    forkId: ForkId,
    reason: String,
    actor: NodeAddress,
    vectorClock: VectorClock
  ) extends StateEvent

  /**
   * A branch has timed out.
   */
  final case class BranchTimedOut(
    node: NodeId,
    branchId: BranchId,
    forkId: ForkId,
    actor: NodeAddress,
    vectorClock: VectorClock
  ) extends StateEvent

  /**
   * A branch has reached a Join node.
   */
  final case class JoinReached(
    node: NodeId,
    branchId: BranchId,
    forkId: ForkId,
    actor: NodeAddress,
    vectorClock: VectorClock
  ) extends StateEvent

  /**
   * A Join node's condition has been satisfied and traversal continues.
   */
  final case class JoinCompleted(
    node: NodeId,
    forkId: ForkId,
    completedBranches: Set[BranchId],
    actor: NodeAddress,
    vectorClock: VectorClock
  ) extends StateEvent

  /**
   * A Join node has timed out waiting for branches.
   */
  final case class JoinTimedOut(
    node: NodeId,
    forkId: ForkId,
    pendingBranches: Set[BranchId],
    actor: NodeAddress,
    vectorClock: VectorClock
  ) extends StateEvent
