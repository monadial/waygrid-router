package com.monadial.waygrid.common.domain.model.traversal.dag

import com.monadial.waygrid.common.domain.model.traversal.dag.Value.ForkId

import scala.concurrent.duration.FiniteDuration

/**
 * Defines the type of a node in a DAG, determining its behavior during traversal.
 *
 * - Standard: A regular processing node that executes and passes control to successors
 * - Fork: A fan-out node that initiates parallel branch execution
 * - Join: A fan-in node that waits for branches to complete before continuing
 */
enum NodeType:
  /**
   * Standard processing node. Executes its task and passes control
   * to successor nodes based on edge guards.
   */
  case Standard

  /**
   * Fork node that initiates parallel execution of multiple branches.
   * Each outgoing edge starts a separate branch that can execute concurrently.
   *
   * @param forkId Unique identifier for this fork scope, used to match with
   *               corresponding Join nodes
   */
  case Fork(forkId: ForkId)

  /**
   * Join node that synchronizes multiple branches back together.
   * The join strategy determines when the traversal can continue.
   *
   * @param forkId Must match the forkId of the corresponding Fork node
   * @param strategy Determines completion semantics (AND, OR, Quorum)
   * @param timeout Optional timeout for branch completion
   */
  case Join(
    forkId: ForkId,
    strategy: JoinStrategy,
    timeout: Option[FiniteDuration] = None
  )

/**
 * Defines how a Join node waits for branches to complete.
 */
enum JoinStrategy:
  /**
   * Wait for ALL branches to complete successfully.
   * If any branch fails, the join fails (unless there's a failure edge).
   */
  case And

  /**
   * Continue as soon as ANY single branch completes.
   * Remaining branches are canceled once the first completes.
   */
  case Or

  /**
   * Continue when at least N branches complete successfully.
   * Useful for quorum-based decisions.
   *
   * @param n Minimum number of branches that must complete
   */
  case Quorum(n: Int)