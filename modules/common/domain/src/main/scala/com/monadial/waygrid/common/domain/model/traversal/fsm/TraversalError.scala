package com.monadial.waygrid.common.domain.model.traversal.fsm

import com.monadial.waygrid.common.domain.model.routing.Value.TraversalId
import com.monadial.waygrid.common.domain.model.traversal.dag.Value.{ BranchId, ForkId, NodeId }
import com.monadial.waygrid.common.domain.model.traversal.state.Value.StateVersion

/**
 * Errors that can occur during DAG traversal.
 *
 * Extends Throwable to allow these errors to be raised in effect types.
 */
sealed trait TraversalError extends Throwable:
  val traversalId: TraversalId

  /** Subclasses should override this to provide error details without calling toString */
  def errorMessage: String

  override def getMessage: String = s"${getClass.getSimpleName}(traversalId=$traversalId): $errorMessage"

// ---------------------------------------------------------------------------
// Linear Traversal Errors (existing)
// ---------------------------------------------------------------------------

/**
 * The signal is not supported in the current state.
 */
final case class UnsupportedSignal(
  traversalId: TraversalId
) extends TraversalError:
  def errorMessage: String = "signal not supported in current state"

/**
 * The DAG has no nodes to traverse.
 */
final case class EmptyDag(
  traversalId: TraversalId
) extends TraversalError:
  def errorMessage: String = "DAG has no nodes to traverse"

/**
 * A traversal is already in progress.
 */
final case class AlreadyInProgress(
  traversalId: TraversalId
) extends TraversalError:
  def errorMessage: String = "traversal already in progress"

/**
 * The DAG entry node could not be found.
 */
final case class MissingEntryNode(
  traversalId: TraversalId
) extends TraversalError:
  def errorMessage: String = "DAG entry node not found"

/**
 * The specified node was not found in the DAG.
 */
final case class NodeNotFound(
  traversalId: TraversalId,
  nodeId: NodeId
) extends TraversalError:
  def errorMessage: String = s"node $nodeId not found in DAG"

/**
 * The node is not in the expected state for this operation.
 */
final case class InvalidNodeState(
  traversalId: TraversalId,
  nodeId: NodeId,
  expected: String,
  actual: String
) extends TraversalError:
  def errorMessage: String = s"node $nodeId in invalid state: expected=$expected, actual=$actual"

/**
 * No active node is being processed.
 */
final case class NoActiveNode(
  traversalId: TraversalId
) extends TraversalError:
  def errorMessage: String = "no active node being processed"

/**
 * The traversal is already complete.
 */
final case class TraversalAlreadyComplete(
  traversalId: TraversalId
) extends TraversalError:
  def errorMessage: String = "traversal already complete"

/**
 * The traversal has already failed.
 */
final case class TraversalAlreadyFailed(
  traversalId: TraversalId
) extends TraversalError:
  def errorMessage: String = "traversal already failed"

/**
 * The traversal was canceled.
 */
final case class TraversalCanceled(
  traversalId: TraversalId
) extends TraversalError:
  def errorMessage: String = "traversal was canceled"

/**
 * The node cannot be retried (retry policy doesn't allow it or max retries exceeded).
 */
final case class CannotRetry(
  traversalId: TraversalId,
  nodeId: NodeId,
  reason: String
) extends TraversalError:
  def errorMessage: String = s"node $nodeId cannot be retried: $reason"

// ---------------------------------------------------------------------------
// Fork/Join Errors (new)
// ---------------------------------------------------------------------------

/**
 * Fork ID mismatch - the join references a different fork than expected.
 */
final case class ForkMismatch(
  traversalId: TraversalId,
  expectedForkId: ForkId,
  actualForkId: ForkId
) extends TraversalError:
  def errorMessage: String = s"fork mismatch: expected=$expectedForkId, actual=$actualForkId"

/**
 * A Join node was reached without a corresponding active Fork scope.
 */
final case class JoinWithoutFork(
  traversalId: TraversalId,
  joinNodeId: NodeId,
  forkId: ForkId
) extends TraversalError:
  def errorMessage: String = s"join node $joinNodeId reached without active fork scope $forkId"

/**
 * The specified branch was not found.
 */
final case class BranchNotFound(
  traversalId: TraversalId,
  branchId: BranchId
) extends TraversalError:
  def errorMessage: String = s"branch $branchId not found"

/**
 * The join timed out waiting for branches.
 */
final case class JoinTimeout(
  traversalId: TraversalId,
  joinNodeId: NodeId,
  pendingBranches: Set[BranchId]
) extends TraversalError:
  def errorMessage: String = s"join $joinNodeId timed out waiting for branches: ${pendingBranches.mkString(", ")}"

/**
 * Nested fork depth exceeded the maximum allowed.
 */
final case class NestedForkDepthExceeded(
  traversalId: TraversalId,
  maxDepth: Int,
  currentDepth: Int
) extends TraversalError:
  def errorMessage: String = s"nested fork depth exceeded: max=$maxDepth, current=$currentDepth"

/**
 * Concurrent modification detected during storage operation.
 */
final case class ConcurrentModification(
  traversalId: TraversalId,
  expectedVersion: StateVersion,
  actualVersion: StateVersion
) extends TraversalError:
  def errorMessage: String = s"concurrent modification: expected version=$expectedVersion, actual=$actualVersion"

/**
 * The fork scope was not found.
 */
final case class ForkScopeNotFound(
  traversalId: TraversalId,
  forkId: ForkId
) extends TraversalError:
  def errorMessage: String = s"fork scope $forkId not found"

/**
 * The branch is not in the expected state.
 */
final case class InvalidBranchState(
  traversalId: TraversalId,
  branchId: BranchId,
  expected: String,
  actual: String
) extends TraversalError:
  def errorMessage: String = s"branch $branchId in invalid state: expected=$expected, actual=$actual"
