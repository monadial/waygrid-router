package com.monadial.waygrid.common.domain.model.traversal.fsm

import com.monadial.waygrid.common.domain.model.routing.Value.TraversalId
import com.monadial.waygrid.common.domain.model.traversal.dag.Value.{ BranchId, ForkId, NodeId }
import com.monadial.waygrid.common.domain.model.traversal.state.Value.StateVersion

/**
 * Errors that can occur during DAG traversal.
 */
sealed trait TraversalError:
  val traversalId: TraversalId

// ---------------------------------------------------------------------------
// Linear Traversal Errors (existing)
// ---------------------------------------------------------------------------

/**
 * The signal is not supported in the current state.
 */
final case class UnsupportedSignal(
  traversalId: TraversalId
) extends TraversalError

/**
 * The DAG has no nodes to traverse.
 */
final case class EmptyDag(
  traversalId: TraversalId
) extends TraversalError

/**
 * A traversal is already in progress.
 */
final case class AlreadyInProgress(
  traversalId: TraversalId
) extends TraversalError

/**
 * The DAG entry node could not be found.
 */
final case class MissingEntryNode(
  traversalId: TraversalId
) extends TraversalError

/**
 * The specified node was not found in the DAG.
 */
final case class NodeNotFound(
  traversalId: TraversalId,
  nodeId: NodeId
) extends TraversalError

/**
 * The node is not in the expected state for this operation.
 */
final case class InvalidNodeState(
  traversalId: TraversalId,
  nodeId: NodeId,
  expected: String,
  actual: String
) extends TraversalError

/**
 * No active node is being processed.
 */
final case class NoActiveNode(
  traversalId: TraversalId
) extends TraversalError

/**
 * The traversal is already complete.
 */
final case class TraversalAlreadyComplete(
  traversalId: TraversalId
) extends TraversalError

/**
 * The traversal has already failed.
 */
final case class TraversalAlreadyFailed(
  traversalId: TraversalId
) extends TraversalError

/**
 * The traversal was canceled.
 */
final case class TraversalCanceled(
  traversalId: TraversalId
) extends TraversalError

/**
 * The node cannot be retried (retry policy doesn't allow it or max retries exceeded).
 */
final case class CannotRetry(
  traversalId: TraversalId,
  nodeId: NodeId,
  reason: String
) extends TraversalError

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
) extends TraversalError

/**
 * A Join node was reached without a corresponding active Fork scope.
 */
final case class JoinWithoutFork(
  traversalId: TraversalId,
  joinNodeId: NodeId,
  forkId: ForkId
) extends TraversalError

/**
 * The specified branch was not found.
 */
final case class BranchNotFound(
  traversalId: TraversalId,
  branchId: BranchId
) extends TraversalError

/**
 * The join timed out waiting for branches.
 */
final case class JoinTimeout(
  traversalId: TraversalId,
  joinNodeId: NodeId,
  pendingBranches: Set[BranchId]
) extends TraversalError

/**
 * Nested fork depth exceeded the maximum allowed.
 */
final case class NestedForkDepthExceeded(
  traversalId: TraversalId,
  maxDepth: Int,
  currentDepth: Int
) extends TraversalError

/**
 * Concurrent modification detected during storage operation.
 */
final case class ConcurrentModification(
  traversalId: TraversalId,
  expectedVersion: StateVersion,
  actualVersion: StateVersion
) extends TraversalError

/**
 * The fork scope was not found.
 */
final case class ForkScopeNotFound(
  traversalId: TraversalId,
  forkId: ForkId
) extends TraversalError

/**
 * The branch is not in the expected state.
 */
final case class InvalidBranchState(
  traversalId: TraversalId,
  branchId: BranchId,
  expected: String,
  actual: String
) extends TraversalError
