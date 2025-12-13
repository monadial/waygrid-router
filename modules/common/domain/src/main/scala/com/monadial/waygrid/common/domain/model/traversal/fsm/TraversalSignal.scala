package com.monadial.waygrid.common.domain.model.traversal.fsm

import com.monadial.waygrid.common.domain.model.routing.Value.TraversalId
import com.monadial.waygrid.common.domain.model.traversal.dag.Value.{ BranchId, ForkId, NodeId }
import com.monadial.waygrid.common.domain.model.traversal.state.BranchResult

/**
 * Signals that drive the TraversalFSM state machine.
 * Each signal represents an external event that triggers a state transition.
 */
sealed trait TraversalSignal:
  def traversalId: TraversalId

object TraversalSignal:

  // ---------------------------------------------------------------------------
  // Linear Traversal Signals (existing)
  // ---------------------------------------------------------------------------

  /**
   * Begin a new traversal from the DAG's entry node.
   * This is the initial signal to start processing.
   */
  final case class Begin(
    traversalId: TraversalId
  ) extends TraversalSignal

  /**
   * Resume execution of a scheduled node.
   * Sent when a scheduled timer fires.
   */
  final case class Resume(
    traversalId: TraversalId,
    nodeId: NodeId
  ) extends TraversalSignal

  /**
   * Cancel the current traversal.
   */
  final case class Cancel(
    traversalId: TraversalId
  ) extends TraversalSignal

  /**
   * Retry a failed node.
   * Sent when a retry timer fires after a failure.
   */
  final case class Retry(
    traversalId: TraversalId,
    nodeId: NodeId
  ) extends TraversalSignal

  /**
   * A node has started processing.
   * This is an acknowledgment signal from the node executor.
   */
  final case class NodeStart(
    traversalId: TraversalId,
    nodeId: NodeId
  ) extends TraversalSignal

  /**
   * A node has completed successfully.
   * Triggers transition to the next node via OnSuccess edge.
   */
  final case class NodeSuccess(
    traversalId: TraversalId,
    nodeId: NodeId
  ) extends TraversalSignal

  /**
   * A node has failed.
   * Triggers retry logic or transition via OnFailure edge.
   */
  final case class NodeFailure(
    traversalId: TraversalId,
    nodeId: NodeId,
    reason: Option[String] = None
  ) extends TraversalSignal

  /**
   * The traversal has completed successfully.
   * All nodes have been processed.
   */
  final case class Completed(
    traversalId: TraversalId
  ) extends TraversalSignal

  /**
   * The traversal has failed terminally.
   * No more retries or failure edges available.
   */
  final case class Failed(
    traversalId: TraversalId,
    reason: Option[String] = None
  ) extends TraversalSignal

  // ---------------------------------------------------------------------------
  // Fork/Join Signals (new)
  // ---------------------------------------------------------------------------

  /**
   * A Fork node has been reached, initiating parallel branches.
   * The FSM should create branch states and dispatch all branch entry nodes.
   */
  final case class ForkReached(
    traversalId: TraversalId,
    forkNodeId: NodeId
  ) extends TraversalSignal

  /**
   * A branch within a fork has completed (success or failure).
   * The FSM should update the pending join and check completion conditions.
   */
  final case class BranchComplete(
    traversalId: TraversalId,
    branchId: BranchId,
    forkId: ForkId,
    result: BranchResult
  ) extends TraversalSignal

  /**
   * A branch has reached a Join node.
   * The FSM should register the arrival and check if join is satisfied.
   */
  final case class JoinReached(
    traversalId: TraversalId,
    joinNodeId: NodeId,
    branchId: BranchId
  ) extends TraversalSignal

  /**
   * A timeout has occurred for a branch or join.
   * The FSM should handle timeout logic (fail, OnTimeout edge, etc.)
   */
  final case class Timeout(
    traversalId: TraversalId,
    nodeId: NodeId,
    branchId: Option[BranchId]
  ) extends TraversalSignal

  /**
   * Request to cancel specific branches (e.g., when OR join completes).
   * The FSM should mark specified branches as canceled.
   */
  final case class CancelBranches(
    traversalId: TraversalId,
    forkId: ForkId,
    branchIds: Set[BranchId],
    reason: String
  ) extends TraversalSignal
