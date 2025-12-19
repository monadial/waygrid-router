package com.monadial.waygrid.common.domain.model.traversal.state

import java.time.Instant

import com.monadial.waygrid.common.domain.model.traversal.dag.JoinStrategy
import com.monadial.waygrid.common.domain.model.traversal.dag.Value.{ BranchId, ForkId, NodeId }
import io.circe.Json

/**
 * Tracks an active fork scope during DAG traversal.
 * Created when a Fork node is reached and destroyed when the corresponding Join completes.
 *
 * @param forkId Unique identifier for this fork scope
 * @param forkNodeId The Fork node that created this scope
 * @param branches All branch IDs that belong to this fork
 * @param parentScope For nested forks, the parent fork's ID
 * @param parentBranchId For nested forks, the branch ID of the parent branch that spawned this fork.
 *                       This is stored explicitly to avoid reverse-lookup issues when the inner fork's
 *                       join completes and the nodeToBranch index has been cleared.
 * @param startedAt When the fork was initiated
 * @param timeout Optional deadline for all branches to complete
 */
final case class ForkScope(
  forkId: ForkId,
  forkNodeId: NodeId,
  branches: Set[BranchId],
  parentScope: Option[ForkId],
  parentBranchId: Option[BranchId],
  startedAt: Instant,
  timeout: Option[Instant]
):
  /** Returns true if all branches are accounted for in the given sets */
  def allBranchesResolved(completed: Set[BranchId], failed: Set[BranchId], canceled: Set[BranchId]): Boolean =
    branches.forall(b => completed.contains(b) || failed.contains(b) || canceled.contains(b))

  /** Returns true if the timeout has been exceeded */
  def isTimedOut(now: Instant): Boolean =
    timeout.exists(_.isBefore(now))

  /** Returns the number of pending branches */
  def pendingCount(completed: Set[BranchId], failed: Set[BranchId], canceled: Set[BranchId]): Int =
    branches.count(b => !completed.contains(b) && !failed.contains(b) && !canceled.contains(b))

/**
 * Tracks the state of an individual branch within a fork.
 *
 * @param branchId Unique identifier for this branch
 * @param forkId The fork scope this branch belongs to
 * @param entryNode First node in this branch (immediately after fork)
 * @param currentNode Currently executing node in this branch (None if not started or finished)
 * @param status Current status of the branch
 * @param result Final result once branch completes
 * @param history Sequence of nodes traversed in this branch
 */
final case class BranchState(
  branchId: BranchId,
  forkId: ForkId,
  entryNode: NodeId,
  currentNode: Option[NodeId],
  status: BranchStatus,
  result: Option[BranchResult],
  history: Vector[NodeId]
):
  /** Returns true if the branch is still executing */
  inline def isActive: Boolean = status == BranchStatus.Running

  /** Returns true if the branch has finished (any terminal state) */
  inline def isTerminal: Boolean = status match
    case BranchStatus.Completed | BranchStatus.Failed |
        BranchStatus.Canceled | BranchStatus.TimedOut => true
    case _ => false

  /** Transition to running state with the given node */
  def start(node: NodeId): BranchState =
    copy(
      currentNode = Some(node),
      status = BranchStatus.Running,
      history = history :+ node
    )

  /** Move to next node within the branch */
  def advanceTo(node: NodeId): BranchState =
    copy(
      currentNode = Some(node),
      history = history :+ node
    )

  /** Complete the branch successfully */
  def complete(output: Option[Json] = None): BranchState =
    copy(
      currentNode = None,
      status = BranchStatus.Completed,
      result = Some(BranchResult.Success(output))
    )

  /** Fail the branch */
  def fail(reason: String): BranchState =
    copy(
      currentNode = None,
      status = BranchStatus.Failed,
      result = Some(BranchResult.Failure(reason))
    )

  /** Cancel the branch (e.g., when OR join completes) */
  def cancel: BranchState =
    copy(
      currentNode = None,
      status = BranchStatus.Canceled,
      result = None
    )

  /** Mark the branch as timed out */
  def timeout: BranchState =
    copy(
      currentNode = None,
      status = BranchStatus.TimedOut,
      result = Some(BranchResult.Timeout)
    )

object BranchState:
  /** Create initial branch state for a new branch */
  def initial(branchId: BranchId, forkId: ForkId, entryNode: NodeId): BranchState =
    BranchState(
      branchId = branchId,
      forkId = forkId,
      entryNode = entryNode,
      currentNode = None,
      status = BranchStatus.Pending,
      result = None,
      history = Vector.empty
    )

/**
 * Status of a branch within a fork scope.
 */
enum BranchStatus:
  /** Branch created but not yet started */
  case Pending

  /** Branch is currently executing */
  case Running

  /** Branch completed successfully */
  case Completed

  /** Branch failed (after retries exhausted) */
  case Failed

  /** Branch was canceled (e.g., OR join completed with another branch) */
  case Canceled

  /** Branch exceeded its timeout */
  case TimedOut

/**
 * Result of a completed branch.
 */
enum BranchResult:
  /** Branch completed successfully, optionally with output data */
  case Success(output: Option[Json])

  /** Branch failed with a reason */
  case Failure(reason: String)

  /** Branch timed out */
  case Timeout

/**
 * Tracks a Join node waiting for branches to complete.
 *
 * @param joinNodeId The Join node being waited on
 * @param forkId The fork scope being joined
 * @param strategy How to determine when the join is satisfied
 * @param requiredBranches All branches that must be considered
 * @param completedBranches Branches that completed successfully
 * @param failedBranches Branches that failed
 * @param canceledBranches Branches that were canceled
 * @param timeout Optional deadline for the join
 */
final case class PendingJoin(
  joinNodeId: NodeId,
  forkId: ForkId,
  strategy: JoinStrategy,
  requiredBranches: Set[BranchId],
  completedBranches: Set[BranchId],
  failedBranches: Set[BranchId],
  canceledBranches: Set[BranchId],
  timeout: Option[Instant]
):
  /** Total number of branches that have reached a terminal state */
  def resolvedCount: Int =
    completedBranches.size + failedBranches.size + canceledBranches.size

  /** Number of branches still pending */
  def pendingCount: Int =
    requiredBranches.size - resolvedCount

  /** Check if the join condition is satisfied based on strategy */
  def isSatisfied: Boolean = strategy match
    case JoinStrategy.And =>
      // All branches must complete successfully
      completedBranches == requiredBranches

    case JoinStrategy.Or =>
      // At least one branch must complete
      completedBranches.nonEmpty

    case JoinStrategy.Quorum(n) =>
      // At least n branches must complete successfully
      completedBranches.size >= n

  /** Check if the join has failed (cannot be satisfied anymore) */
  def hasFailed: Boolean = strategy match
    case JoinStrategy.And =>
      // AND fails if any branch failed (and won't be retried)
      failedBranches.nonEmpty

    case JoinStrategy.Or =>
      // OR fails only if ALL branches failed
      failedBranches.size == requiredBranches.size

    case JoinStrategy.Quorum(n) =>
      // Quorum fails if not enough branches can succeed
      val maxPossibleSuccess = completedBranches.size + pendingCount
      maxPossibleSuccess < n

  /** Check if timeout has been exceeded */
  def isTimedOut(now: Instant): Boolean =
    timeout.exists(_.isBefore(now))

  /** Register a branch completion */
  def branchCompleted(branchId: BranchId): PendingJoin =
    copy(completedBranches = completedBranches + branchId)

  /** Register a branch failure */
  def branchFailed(branchId: BranchId): PendingJoin =
    copy(failedBranches = failedBranches + branchId)

  /** Register a branch cancellation */
  def branchCanceled(branchId: BranchId): PendingJoin =
    copy(canceledBranches = canceledBranches + branchId)

  /** Get branches that should be canceled (for OR joins) */
  def branchesToCancel: Set[BranchId] =
    requiredBranches -- completedBranches -- failedBranches -- canceledBranches

object PendingJoin:
  /** Create a new pending join */
  def create(
    joinNodeId: NodeId,
    forkId: ForkId,
    strategy: JoinStrategy,
    branches: Set[BranchId],
    timeout: Option[Instant]
  ): PendingJoin =
    PendingJoin(
      joinNodeId = joinNodeId,
      forkId = forkId,
      strategy = strategy,
      requiredBranches = branches,
      completedBranches = Set.empty,
      failedBranches = Set.empty,
      canceledBranches = Set.empty,
      timeout = timeout
    )
