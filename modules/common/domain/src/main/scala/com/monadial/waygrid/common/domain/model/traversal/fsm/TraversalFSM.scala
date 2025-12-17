package com.monadial.waygrid.common.domain.model.traversal.fsm

import cats.Monad
import cats.implicits.*
import com.monadial.waygrid.common.domain.model.fsm.FSM
import com.monadial.waygrid.common.domain.model.fsm.Value.Result
import com.monadial.waygrid.common.domain.model.resiliency.{Backoff, RetryPolicy}
import com.monadial.waygrid.common.domain.model.routing.Value.{DeliveryStrategy, TraversalId}
import com.monadial.waygrid.common.domain.model.traversal.dag.{Dag, JoinStrategy, Node as DagNode, NodeType}
import com.monadial.waygrid.common.domain.model.traversal.dag.Value.{BranchId, EdgeGuard, ForkId, NodeId}
import com.monadial.waygrid.common.domain.model.traversal.fsm.TraversalEffect.*
import com.monadial.waygrid.common.domain.model.traversal.condition.Condition
import com.monadial.waygrid.common.domain.model.traversal.state.{BranchResult, PendingJoin, TraversalState}
import com.monadial.waygrid.common.domain.model.traversal.state.Value.RetryAttempt
import com.monadial.waygrid.common.domain.value.Address.NodeAddress

import java.time.Instant

/**
 * ==TraversalFSM - Pure Deterministic DAG Traversal State Machine==
 *
 * A functional, deterministic finite state machine for traversing Directed Acyclic Graphs (DAGs).
 * The FSM is the core component of Waygrid's event routing system, responsible for:
 *
 *   - '''Linear traversal''': Sequential node-by-node execution with success/failure routing
 *   - '''Fork/Join patterns''': Parallel branch execution with synchronization strategies
 *   - '''Retry handling''': Exponential/linear backoff with configurable retry policies
 *   - '''Conditional routing''': Dynamic edge selection based on condition evaluation
 *   - '''Timeout management''': Node, join, and traversal-level timeouts
 *
 * ==Design Philosophy==
 *
 * The FSM follows a '''pure functional''' design:
 *
 *   1. '''Deterministic''': Given the same state and signal, always produces the same result
 *   2. '''Side-effect free''': All effects are described as data (TraversalEffect), not executed
 *   3. '''Time as parameter''': Current time is passed explicitly for reproducible testing
 *   4. '''Event-sourced compatible''': State transitions can be replayed from event history
 *
 * ==Architecture Overview==
 *
 * {{{
 *   ┌───────────────────────────────────────────────────────────────────┐
 *   │                        TraversalFSM                               │
 *   ├───────────────────────────────────────────────────────────────────┤
 *   │                                                                   │
 *   │  Input:  (TraversalState, TraversalSignal) ──┐                    │
 *   │                                              │                    │
 *   │                                              ▼                    │
 *   │  ┌───────────────────────────────────────────────────┐            │
 *   │  │              Signal Router                        │            │
 *   │  │  ┌─────────────┬─────────────┬─────────────┐      │            │
 *   │  │  │   Linear    │  Fork/Join  │   Timeout   │      │            │
 *   │  │  │  Handlers   │  Handlers   │  Handlers   │      │            │
 *   │  │  └─────────────┴─────────────┴─────────────┘      │            │
 *   │  └───────────────────────────────────────────────────┘            │
 *   │                                              │                    │
 *   │                                              ▼                    │
 *   │  Output: Result[TraversalState, TraversalError, TraversalEffect]  │
 *   │                                                                   │
 *   └───────────────────────────────────────────────────────────────────┘
 * }}}
 *
 * ==Signal Categories==
 *
 * ===Linear Traversal Signals===
 *   - `Begin`: Start traversal from entry node
 *   - `Resume`: Resume scheduled node execution
 *   - `Cancel`: Cancel active traversal
 *   - `Retry`: Retry a failed node
 *   - `NodeStart`: Acknowledge node has started processing
 *   - `NodeSuccess`: Node completed successfully
 *   - `NodeFailure`: Node execution failed
 *   - `Completed`: External completion signal
 *   - `Failed`: External failure signal
 *
 * ===Fork/Join Signals===
 *   - `ForkReached`: Fork node reached, fan-out to branches
 *   - `BranchComplete`: Branch finished with result
 *   - `JoinReached`: Branch reached join node
 *   - `Timeout`: Node or join timeout occurred
 *   - `CancelBranches`: Cancel specified branches
 *
 * ===Traversal-Level Signals===
 *   - `TraversalTimeout`: Entire traversal has timed out
 *
 * ==Fork/Join Strategies==
 *
 * The FSM supports three join synchronization strategies:
 *
 *   - '''AND''': All branches must complete successfully
 *   - '''OR''': Any single branch completing triggers join (others canceled)
 *   - '''Quorum(n)''': At least n branches must complete successfully
 *
 * ==Usage Example==
 *
 * {{{
 * import cats.effect.IO
 * import com.monadial.waygrid.common.domain.model.traversal.fsm._
 *
 * // Create FSM instance
 * val fsm = TraversalFSM.stateless[IO](dag, nodeAddress, Instant.now())
 *
 * // Process a signal
 * val initialState = TraversalState.initial(traversalId)
 * val beginSignal = TraversalSignal.Begin(traversalId)
 *
 * fsm.transition(initialState, beginSignal).map { result =>
 *   result.effect match {
 *     case Right(DispatchNode(_, nodeId, _, _)) =>
 *       // Executor should dispatch this node for processing
 *       println(s"Dispatch node: $nodeId")
 *
 *     case Right(Schedule(_, scheduledAt, nodeId)) =>
 *       // Executor should schedule this node for later
 *       println(s"Schedule node $nodeId at $scheduledAt")
 *
 *     case Right(Complete(_, cancelTimeoutId)) =>
 *       // Traversal completed successfully
 *       cancelTimeoutId.foreach(id => cancelTimeout(id))
 *
 *     case Right(Fail(_, cancelTimeoutId)) =>
 *       // Traversal failed
 *       cancelTimeoutId.foreach(id => cancelTimeout(id))
 *
 *     case Left(error) =>
 *       // Handle error
 *       println(s"Error: ${error.getMessage}")
 *   }
 *
 *   // Always use the new state for subsequent signals
 *   val newState = result.state
 * }
 * }}}
 *
 * ==Executor Responsibilities==
 *
 * The executor (outside this FSM) is responsible for:
 *
 *   1. '''Dispatching nodes''': Send work to the appropriate service
 *   2. '''Scheduling timers''': Set up timers for scheduled nodes/retries
 *   3. '''Sending signals''': Convert service responses back to FSM signals
 *   4. '''Persisting state''': Store state for recovery if needed
 *   5. '''Canceling branches''': Handle branch cancellation for OR joins
 *
 * ==Thread Safety==
 *
 * The FSM itself is stateless and thread-safe. State is passed explicitly.
 * The executor must ensure proper synchronization when updating shared state.
 *
 * ==Error Handling==
 *
 * Errors are returned as `Left[TraversalError]` in the result. The state in the result
 * is the unchanged input state (errors don't modify state). Common errors include:
 *
 *   - `EmptyDag`: DAG has no nodes
 *   - `NodeNotFound`: Referenced node doesn't exist
 *   - `InvalidNodeState`: Node not in expected state
 *   - `BranchNotFound`: Referenced branch doesn't exist
 *   - `JoinWithoutFork`: Join reached without active fork
 *
 * @see [[TraversalState]] for the state model
 * @see [[TraversalSignal]] for all signal types
 * @see [[TraversalEffect]] for all effect types
 * @see [[TraversalError]] for all error types
 */
object TraversalFSM:

  // ===========================================================================
  // Type Aliases
  // ===========================================================================

  /** Internal type alias for FSM result with state, error, and effect */
  private type TraversalResult = Result[TraversalState, TraversalError, TraversalEffect]

  // ===========================================================================
  // Factory Method
  // ===========================================================================

  /**
   * Creates a stateless FSM for DAG traversal.
   *
   * The FSM is parameterized by:
   *   - The DAG structure to traverse
   *   - The node address (actor identity for event sourcing)
   *   - Current time (for pure, deterministic scheduling)
   *
   * ===Example===
   * {{{
   * val fsm = TraversalFSM.stateless[IO](dag, nodeAddress, Instant.now())
   *
   * // Process signals
   * for {
   *   result1 <- fsm.transition(state, TraversalSignal.Begin(traversalId))
   *   result2 <- fsm.transition(result1.state, TraversalSignal.NodeSuccess(traversalId, nodeId))
   * } yield result2
   * }}}
   *
   * @tparam F Effect type (e.g., IO, SyncIO, Id)
   * @param dag The DAG to traverse (must be valid - use DagCompiler.compile)
   * @param nodeAddress The address of this node (used for event attribution)
   * @param now Current timestamp for scheduling decisions (passed explicitly for purity)
   * @return FSM instance that processes TraversalSignals and produces TraversalEffects
   */
  def stateless[F[+_]: Monad](dag: Dag, nodeAddress: NodeAddress, now: Instant): FSM[F, TraversalState, TraversalSignal, TraversalError, TraversalEffect] =

    // =========================================================================
    // Section 1: Result Constructors
    // =========================================================================
    // Unified helpers to construct FSM results with consistent patterns.

    /**
     * Constructs a successful result with new state and effect.
     *
     * @param state The new traversal state after transition
     * @param effect The effect to be executed by the executor
     */
    inline def ok(state: TraversalState, effect: TraversalEffect): F[TraversalResult] =
      Result(state, effect.asRight).pure[F]

    /**
     * Constructs an error result. State remains unchanged on errors.
     *
     * @param state The unchanged traversal state
     * @param error The error that occurred
     */
    inline def err(state: TraversalState, error: TraversalError): F[TraversalResult] =
      Result(state, error.asLeft).pure[F]

    /**
     * Constructs a no-operation result. Used when signal is valid but requires no action.
     *
     * @param state The current (possibly updated) traversal state
     */
    inline def noop(state: TraversalState): F[TraversalResult] =
      Result(state, NoOp(state.traversalId).asRight).pure[F]

    // =========================================================================
    // Section 1b: Common Helper Functions
    // =========================================================================
    //
    // PURPOSE: Centralized utility functions that eliminate code duplication
    // and improve readability across multiple handlers.
    //
    // MAINTENANCE NOTES:
    //   - All helpers are `inline def` for zero runtime overhead
    //   - These functions should remain pure (no side effects)
    //   - When adding new helpers, ensure they follow the same patterns
    //
    // DEPENDENCIES:
    //   - `dag`: DAG structure (injected via factory method)
    //   - `now`: Current timestamp (injected via factory method)
    //   - `nodeAddress`: Actor identity (injected via factory method)

    /**
     * Builds traversal timeout info from the DAG's timeout configuration.
     *
     * This helper constructs a tuple of (timeoutId, deadline) for scheduling
     * a traversal-level timeout. The timeoutId is deterministically generated
     * from the traversalId for idempotent cancellation.
     *
     * ===Timeout ID Format===
     * The timeout ID follows the pattern: `traversal-timeout-{traversalId}`
     * This ensures:
     *   - Uniqueness per traversal
     *   - Idempotent cancellation (same ID always)
     *   - Easy correlation in logs/debugging
     *
     * ===Usage===
     * Called by `handleBegin` to set up traversal-level safety timeout.
     * The executor should schedule a `TraversalTimeout` signal for the deadline.
     *
     * @param traversalId The current traversal identifier
     * @return Optional tuple of (timeoutId, deadline) if DAG has timeout configured;
     *         None if DAG has no timeout
     */
    inline def buildTimeoutInfo(traversalId: TraversalId): Option[(String, Instant)] =
      dag.timeout.map { duration =>
        val timeoutId = s"traversal-timeout-${traversalId.unwrap}"
        val deadline = now.plusMillis(duration.toMillis)
        (timeoutId, deadline)
      }

    /**
     * Retrieves the branch context (branchId, forkId) for a given node.
     *
     * This helper provides O(1) lookup of the fork/branch context that
     * a node belongs to. Essential for maintaining correct branch association
     * when transitioning between nodes in fork/join patterns.
     *
     * ===Why This Matters===
     * In fork/join patterns, each node must know which branch it belongs to.
     * This context is used for:
     *   - Updating branch state when node completes
     *   - Correct join arrival registration
     *   - Proper branch cancellation in OR joins
     *
     * ===Performance===
     * Uses `state.nodeToBranch` Map for O(1) lookup.
     *
     * @param state Current traversal state
     * @param nodeId Node to look up branch context for
     * @return Optional tuple of (branchId, forkId) if node is in a branch;
     *         None if node is not part of any fork
     */
    inline def branchContextFor(state: TraversalState, nodeId: NodeId): Option[(BranchId, ForkId)] =
      state.branchForNode(nodeId).map(b => (b.branchId, b.forkId))

    /**
     * Computes the next retry delay and schedules the retry if available.
     *
     * This helper encapsulates the complete retry decision flow, reducing
     * complexity in `handleNodeFailure`. It follows a clear decision tree:
     *
     * ===Decision Flow===
     * {{{
     * 1. Calculate next attempt number from state
     * 2. Consult Backoff policy for delay
     *    ├── Delay available → Schedule retry
     *    └── No delay (exhausted) →
     *        ├── OnFailure edge exists → Dispatch failure handler
     *        └── No failure edge → Fail traversal
     * }}}
     *
     * ===Retry Policies===
     *   - `RetryPolicy.None`: No retries (always returns None)
     *   - `RetryPolicy.Linear(delay, max)`: Fixed delay, up to max attempts
     *   - `RetryPolicy.Exponential(base, max)`: Doubling delay, up to max
     *
     * ===Maintenance===
     * If retry logic changes, update this function only.
     * All retry behavior flows through here.
     *
     * @param state Current traversal state (node already marked as failed)
     * @param node The node that failed (contains retry policy)
     * @param signal The failure signal (contains traversalId and reason)
     * @return Effectful result:
     *         - `ScheduleRetry` if retries available
     *         - `DispatchNode` if failure edge exists
     *         - `Fail` if no recovery path
     */
    def scheduleRetryOrFail(
      state: TraversalState,
      node: DagNode,
      signal: TraversalSignal.NodeFailure
    ): F[TraversalResult] =
      // Calculate which attempt this would be (1-indexed for human readability)
      val nextAttempt = state.retryCount(node.id) + 1

      // Consult the backoff policy for the next delay
      Backoff[RetryPolicy].nextDelay(node.retryPolicy, nextAttempt, None) match
        case Some(delay) =>
          // Retry available: schedule for future execution
          val scheduledAt = now.plusMillis(delay.toMillis)
          val scheduled = state.schedule(node.id, nodeAddress, scheduledAt, None)
          ok(scheduled, ScheduleRetry(signal.traversalId, scheduledAt, RetryAttempt(nextAttempt), node.id))

        case None =>
          // Retries exhausted: check for failure edge as fallback
          dag.nextNodes(node.id, EdgeGuard.OnFailure).headOption match
            case Some(nextNode) =>
              // Failure edge exists: transition to failure handler node
              dispatchWithStrategy(state, nextNode.id, nextNode.deliveryStrategy, None, Set.empty)
            case None =>
              // No recovery path: fail the entire traversal
              val timeoutToCancel = state.traversalTimeoutId
              val finalState = state.fail(node.id, nodeAddress, None, signal.reason).clearTraversalTimeout
              ok(finalState, Fail(signal.traversalId, timeoutToCancel))

    // =========================================================================
    // Section 1c: Fork/Join Helper Functions
    // =========================================================================
    //
    // PURPOSE: Centralized logic for fork/join patterns, extracted from handlers
    // to improve readability and enable focused unit testing.
    //
    // FORK/JOIN PATTERN OVERVIEW:
    //   Fork: Splits execution into parallel branches (fan-out)
    //   Join: Synchronizes branches back together (fan-in)
    //
    //   ┌─────────────┐
    //   │    Fork     │ ← Starts all branches simultaneously
    //   └──────┬──────┘
    //      ┌───┼───┐
    //      ▼   ▼   ▼
    //     [A] [B] [C]   ← Branches execute in parallel
    //      │   │   │
    //      └───┼───┘
    //          ▼
    //   ┌─────────────┐
    //   │    Join     │ ← Waits for strategy (AND/OR/Quorum)
    //   └─────────────┘
    //
    // MAINTENANCE NOTES:
    //   - Changes here affect all fork/join behavior
    //   - Ensure nested fork support is preserved
    //   - Keep helpers pure and stateless where possible

    /**
     * Gets the outgoing edges for a fork node.
     *
     * Determines which edges to use for creating fork branches. The selection
     * logic ensures deterministic branch creation:
     *
     * ===Edge Selection Priority===
     *   1. OnSuccess + Always edges (preferred for explicit fork routing)
     *   2. All outgoing edges (fallback for legacy DAGs)
     *
     * ===Why This Logic===
     * Fork nodes typically use OnSuccess edges to define branches, but some
     * DAG definitions might use other guards. This fallback ensures compatibility
     * while preferring explicit intent.
     *
     * @param forkNodeId The fork node to get edges from
     * @return List of outgoing edges representing fork branches
     */
    inline def getForkOutgoingEdges(forkNodeId: NodeId): List[com.monadial.waygrid.common.domain.model.traversal.dag.Edge] =
      // Prefer explicit OnSuccess/Always edges for branch definition
      val preferredEdges = dag.outgoingEdges(forkNodeId, EdgeGuard.OnSuccess) ++
        dag.outgoingEdges(forkNodeId, EdgeGuard.Always)
      // Fallback to all edges if no preferred edges found
      if preferredEdges.nonEmpty then preferredEdges
      else dag.outgoingEdges(forkNodeId)

    /**
     * Gets the join timeout configuration for a fork.
     *
     * Looks up the corresponding join node and extracts its timeout,
     * converting the relative duration to an absolute deadline.
     *
     * ===Join-Fork Relationship===
     * Each Fork has exactly one corresponding Join (validated by DagCompiler).
     * The join's timeout applies to waiting for all branches to arrive.
     *
     * ===Timeout Semantics===
     *   - If set: `TraversalSignal.Timeout` fires if branches don't arrive in time
     *   - If not set: No timeout (wait indefinitely for branches)
     *
     * @param forkId The fork identifier (links to corresponding join)
     * @return Optional deadline instant if join has timeout configured;
     *         None if join has no timeout or join not found
     */
    inline def getJoinTimeoutForFork(forkId: ForkId): Option[Instant] =
      dag.joinNodeIdFor(forkId)
        .flatMap(dag.nodeOf)
        .flatMap(_.nodeType match
          case NodeType.Join(_, _, t) => t.map(d => now.plusMillis(d.toMillis))
          case _ => None
        )

    /**
     * Initializes fork scope and starts all branches atomically.
     *
     * This helper encapsulates the complete fork initialization sequence,
     * ensuring all state transformations happen together. Critical for
     * maintaining consistency in fork/join patterns.
     *
     * ===Initialization Steps===
     * {{{
     * 1. Capture parent context (for nested forks)
     *    └── Saves parentScope and parentBranchId
     *
     * 2. Create fork scope
     *    └── Registers all branches with the fork
     *
     * 3. Start all branches
     *    └── Each branch: start entry node + register in branchStates
     * }}}
     *
     * ===Nested Fork Support===
     * When a fork occurs inside another fork's branch, we preserve:
     *   - `parentScope`: The outer fork's ID
     *   - `parentBranchId`: The branch we're nested in
     *
     * This context is restored when the inner join completes, allowing
     * execution to continue in the correct outer branch.
     *
     * ===State Changes===
     *   - `forkScopes`: New entry for this fork
     *   - `branchStates`: New entry for each branch
     *   - `active`: All branch entry nodes added
     *   - `nodeToBranch`: Mapping from entry nodes to branches
     *
     * @param state Current traversal state
     * @param forkNodeId The fork node being processed
     * @param forkId The unique fork identifier
     * @param branchEntries Map of branch IDs to their entry node IDs
     * @param timeout Optional join timeout deadline
     * @return Updated state with fork scope and all branches started
     */
    def initializeForkScope(
      state: TraversalState,
      forkNodeId: NodeId,
      forkId: ForkId,
      branchEntries: Map[BranchId, NodeId],
      timeout: Option[Instant]
    ): TraversalState =
      // Step 1: Capture parent branch context (critical for nested forks)
      val parentBranchState = state.branchForNode(forkNodeId)
      val parentScope = parentBranchState.map(_.forkId)
      val parentBranchId = parentBranchState.map(_.branchId)

      // Step 2: Create fork scope with all branch entries
      val forkedState = state.startFork(
        forkNodeId, forkId, branchEntries,
        parentScope, parentBranchId, timeout,
        nodeAddress, None, now
      )

      // Step 3: Start all branches atomically
      // Each branch gets its entry node started and registered in branchStates
      branchEntries.foldLeft(forkedState) { case (s, (branchId, nodeId)) =>
        s.start(nodeId, nodeAddress, None)
         .startBranch(nodeId, branchId, forkId, nodeAddress, None)
      }

    // =========================================================================
    // Section 2: Scheduling Helpers
    // =========================================================================
    //
    // PURPOSE: Centralized logic for node dispatch timing decisions.
    // Handles the complexity of "when to execute" vs "what to execute".
    //
    // DELIVERY STRATEGIES OVERVIEW:
    //   - Immediate: Execute now (most common)
    //   - ScheduleAfter(delay): Execute after duration elapses
    //   - ScheduleAt(time): Execute at specific timestamp
    //
    // EXECUTOR RESPONSIBILITY:
    // When the FSM returns a `Schedule` effect, the executor must:
    //   1. Store the scheduled execution somewhere (timer service, queue)
    //   2. Send a `Resume` signal when the scheduled time arrives
    //   3. Handle timer cancellation if traversal completes early
    //
    // MAINTENANCE NOTES:
    //   - Scheduling affects state differently than dispatch
    //   - Scheduled nodes are NOT marked as `active` until Resume
    //   - Branch context is deferred for scheduled nodes

    /**
     * Schedules a node for future execution or starts it immediately if time has passed.
     *
     * Implements the "schedule or dispatch" decision, handling edge cases where
     * the scheduled time might already be in the past (e.g., catch-up scenarios).
     *
     * ===Decision Logic===
     * {{{
     * scheduleAt.isAfter(now)?
     *    ├── true  → Schedule effect (executor sets timer)
     *    └── false → DispatchNode effect (execute immediately)
     * }}}
     *
     * ===Why Time Comparison===
     * In event-sourced systems, replays might process old events.
     * Checking `isAfter(now)` ensures correct behavior during:
     *   - Normal execution (schedules for future)
     *   - Replay/recovery (dispatches immediately if past)
     *
     * ===State Changes===
     *   - Schedule: Node added to `scheduled` set
     *   - Dispatch: Node added to `active` set
     *
     * @param state Current traversal state
     * @param nodeId Node to schedule or dispatch
     * @param scheduleAt Target execution time
     * @return Result with Schedule effect (future) or DispatchNode effect (past/now)
     */
    def scheduleOrDispatch(state: TraversalState, nodeId: NodeId, scheduleAt: Instant): F[TraversalResult] =
      if scheduleAt.isAfter(now) then
        // Future execution: schedule timer
        ok(
          state.schedule(nodeId, nodeAddress, scheduleAt, None),
          Schedule(state.traversalId, scheduleAt, nodeId)
        )
      else
        // Time has passed: execute immediately
        ok(
          state.start(nodeId, nodeAddress, None),
          DispatchNode(state.traversalId, nodeId)
        )

    /**
     * Dispatches a node based on its delivery strategy configuration.
     *
     * This is the primary dispatch function, handling all timing variations
     * and maintaining proper branch context for fork/join patterns.
     *
     * ===Delivery Strategies===
     * {{{
     * Immediate:
     *   └── Start now, dispatch to executor immediately
     *       State: node marked active, branch updated
     *
     * ScheduleAfter(delay):
     *   └── Schedule for `now + delay`
     *       State: node marked scheduled (not active yet)
     *
     * ScheduleAt(time):
     *   └── Schedule for specific absolute time
     *       State: node marked scheduled (not active yet)
     * }}}
     *
     * ===Branch Context Handling===
     * When `branchContext` is provided (fork/join patterns):
     *   - Immediate: Updates branch state with new current node
     *   - Scheduled: Deferred until Resume signal (branch context lost)
     *
     * ===Important Limitation===
     * Scheduled delivery in fork branches loses branch context.
     * This is by design: the scheduled timer fires with `Resume`,
     * which doesn't carry branch info. Consider using Immediate
     * delivery for fork branches.
     *
     * @param state Current traversal state
     * @param nodeId Node to dispatch
     * @param strategy The node's configured delivery strategy
     * @param branchContext Optional (branchId, forkId) for fork branch tracking
     * @param branchesToCancel Branches to cancel (passed to effect for OR joins)
     * @return Effectful result with DispatchNode or Schedule effect
     */
    def dispatchWithStrategy(
      state: TraversalState,
      nodeId: NodeId,
      strategy: DeliveryStrategy,
      branchContext: Option[(BranchId, ForkId)],
      branchesToCancel: Set[(BranchId, ForkId)]
    ): F[TraversalResult] =
      strategy match
        case DeliveryStrategy.Immediate =>
          // Immediate execution: update state and dispatch now
          val startedState = state.start(nodeId, nodeAddress, None)
          val finalState = branchContext match
            case Some((branchId, forkId)) =>
              // Update branch to track this node as current
              startedState.advanceBranch(nodeId, branchId, forkId, nodeAddress, None)
            case None =>
              startedState
          ok(finalState, DispatchNode(state.traversalId, nodeId, branchesToCancel))

        case DeliveryStrategy.ScheduleAfter(delay) =>
          // CAVEAT: branchContext is NOT preserved for scheduled delivery
          // The Resume signal handler doesn't restore branch context
          scheduleOrDispatch(state, nodeId, now.plusMillis(delay.toMillis))

        case DeliveryStrategy.ScheduleAt(time) =>
          // CAVEAT: branchContext is NOT preserved for scheduled delivery
          scheduleOrDispatch(state, nodeId, time)

    // =========================================================================
    // Section 3: Edge Routing
    // =========================================================================
    //
    // PURPOSE: Core DAG navigation logic. Determines which nodes to visit next
    // based on the current node, traversal outcome, and edge guards.
    //
    // EDGE GUARDS OVERVIEW:
    //   - OnSuccess: Follow when node completes successfully
    //   - OnFailure: Follow when node fails (after retries exhausted)
    //   - OnTimeout: Follow when node/join times out
    //   - Always: Follow regardless of outcome
    //   - Conditional(cond): Follow when condition evaluates to true
    //
    // CONDITIONAL ROUTING:
    // Conditional edges are evaluated FIRST when guard is OnSuccess.
    // If any condition evaluates to true, those edges are followed.
    // Otherwise, falls back to OnSuccess edges.
    //
    // MAINTENANCE NOTES:
    //   - `findNextNodes` is the source of truth for edge selection
    //   - `transitionToNext` handles all node type variations
    //   - Changes here affect the entire traversal flow

    /**
     * Finds the next nodes to transition to based on edge guard.
     *
     * For `OnSuccess` guard, first checks conditional edges:
     *   1. Evaluate all Conditional edges
     *   2. If any condition evaluates to true, use those targets
     *   3. Otherwise, fall back to OnSuccess edges
     *
     * Note: Currently supports `Always`, `Not`, `And`, and `Or` conditions.
     * JSON-based conditions may be added in a future implementation.
     *
     * @param fromNodeId Node we're transitioning from
     * @param guard Edge type to follow (OnSuccess, OnFailure, OnTimeout)
     * @return List of next nodes (usually single, multiple only for legacy fork handling)
     */
    def findNextNodes(fromNodeId: NodeId, guard: EdgeGuard): List[DagNode] =
      guard match
        case EdgeGuard.OnSuccess =>
          // First try conditional edges
          val matchedConditionals = dag.outgoingEdges(fromNodeId).collect {
            case e if e.guard match
              case EdgeGuard.Conditional(cond) => Condition.eval(cond)
              case _ => false
            => dag.nodes.get(e.to)
          }.flatten

          if matchedConditionals.nonEmpty then matchedConditionals
          else dag.nextNodes(fromNodeId, EdgeGuard.OnSuccess)

        case other =>
          dag.nextNodes(fromNodeId, other)

    /**
     * Transitions to the next node(s) after completing the current node.
     *
     * This is the core routing function that handles:
     *   - '''Terminal nodes''': Complete or fail the traversal
     *   - '''Fork nodes''': Trigger fork handling for parallel execution
     *   - '''Join nodes''': Register branch arrival for synchronization
     *   - '''Standard nodes''': Dispatch based on delivery strategy
     *
     * ===Flow Diagram===
     * {{{
     *                    ┌──────────────────────┐
     *                    │  transitionToNext    │
     *                    └──────────┬───────────┘
     *                               │
     *                    ┌──────────▼───────────┐
     *                    │  findNextNodes()     │
     *                    └──────────┬───────────┘
     *                               │
     *           ┌───────────────────┼───────────────────┐
     *           │                   │                   │
     *           ▼                   ▼                   ▼
     *      [No nodes]         [Single node]      [Multiple nodes]
     *           │                   │                   │
     *           ▼                   │                   ▼
     *      Complete/Fail            │              onForkReached
     *                               │
     *              ┌────────────────┼────────────────┐
     *              │                │                │
     *              ▼                ▼                ▼
     *           [Join]           [Fork]         [Standard]
     *              │                │                │
     *              ▼                ▼                ▼
     *        onJoinReached   onForkReached   dispatchWithStrategy
     * }}}
     *
     * @param state Current traversal state
     * @param fromNodeId Node that just completed
     * @param guard Edge type to follow
     * @param branchesToCancel Branches to cancel (passed through to effect)
     * @return Effectful result with next state and effect
     */
    def transitionToNext(
      state: TraversalState,
      fromNodeId: NodeId,
      guard: EdgeGuard,
      branchesToCancel: Set[(BranchId, ForkId)] = Set.empty
    ): F[TraversalResult] =

      val nextNodes = findNextNodes(fromNodeId, guard)

      nextNodes match
        // -----------------------------------------------------------------
        // Case 1: No successor nodes - terminal state
        // -----------------------------------------------------------------
        case Nil =>
          val timeoutToCancel = state.traversalTimeoutId
          guard match
            case EdgeGuard.OnSuccess | EdgeGuard.Always | EdgeGuard.OnAny | EdgeGuard.Conditional(_) =>
              val completed = state.complete(fromNodeId, nodeAddress, None).clearTraversalTimeout
              ok(completed, Complete(state.traversalId, timeoutToCancel))

            case EdgeGuard.OnFailure | EdgeGuard.OnTimeout =>
              val failed = state.fail(fromNodeId, nodeAddress, None, Some("No failure/timeout edge")).clearTraversalTimeout
              ok(failed, Fail(state.traversalId, timeoutToCancel))

        // -----------------------------------------------------------------
        // Case 2: Single successor - most common case
        // -----------------------------------------------------------------
        case head :: Nil =>
          head.nodeType match
            // Join node: register arrival and check completion
            case NodeType.Join(forkId, _, _) =>
              state.branchForNode(fromNodeId) match
                case None =>
                  err(state, InvalidNodeState(state.traversalId, fromNodeId, "branch context", "none"))
                case Some(branch) =>
                  handleJoinReached(state, TraversalSignal.JoinReached(state.traversalId, head.id, branch.branchId))

            // Fork node: trigger parallel fan-out
            case NodeType.Fork(_) =>
              val stateWithFork = state.branchForNode(fromNodeId) match
                case Some(branch) =>
                  state.advanceBranch(head.id, branch.branchId, branch.forkId, nodeAddress, None)
                case None =>
                  state
              val finalState = stateWithFork.successNode(head.id, nodeAddress, None)
              handleForkReached(finalState, TraversalSignal.ForkReached(state.traversalId, head.id))

            // Standard node: dispatch based on delivery strategy
            case NodeType.Standard =>
              dispatchWithStrategy(state, head.id, head.deliveryStrategy, branchContextFor(state, fromNodeId), branchesToCancel)

        // -----------------------------------------------------------------
        // Case 3: Multiple successors - only valid for Fork nodes
        // -----------------------------------------------------------------
        case many =>
          dag.nodeOf(fromNodeId) match
            case Some(node) if node.isFork && guard == EdgeGuard.OnSuccess =>
              handleForkReached(state, TraversalSignal.ForkReached(state.traversalId, fromNodeId))
            case _ =>
              err(state, InvalidNodeState(
                state.traversalId, fromNodeId,
                "single successor or Fork", s"${many.size} successors"
              ))

    // =========================================================================
    // Section 4: Linear Traversal Handlers
    // =========================================================================
    //
    // PURPOSE: Signal handlers for sequential (non-forking) traversal patterns.
    // These handlers form the foundation of DAG traversal, supporting simple
    // chains like: A → B → C → D
    //
    // HANDLER INDEX:
    //   handleBegin          - Start a new traversal
    //   handleResume         - Resume scheduled/retried node
    //   handleCancel         - Abort traversal
    //   handleRetry          - Manually retry failed node
    //   handleNodeStart      - Acknowledge node started (idempotent)
    //   handleNodeSuccess    - Node completed successfully
    //   handleNodeFailure    - Node failed (triggers retry/failure edge)
    //   handleCompleted      - External completion signal
    //   handleFailed         - External failure signal
    //   handleTraversalTimeout - Global traversal timeout
    //
    // IDEMPOTENCY:
    // Most handlers are idempotent - sending the same signal multiple times
    // produces the same result. This is critical for reliable messaging
    // systems where duplicates may occur.
    //
    // STATE MACHINE INVARIANTS:
    //   - A node can only succeed if it was started
    //   - A failed node can be retried (becomes started again)
    //   - A completed traversal ignores further node signals
    //   - Active work count must match parallel branch count

    /**
     * Handles the Begin signal to start a new traversal.
     *
     * ===Preconditions===
     *   - DAG must not be empty
     *   - Traversal must not already be in progress
     *   - Entry node must exist in DAG
     *
     * ===Effects===
     *   - `DispatchNode`: Start processing the entry node (Immediate delivery)
     *   - `Schedule`: Schedule the entry node for later (Scheduled delivery)
     *
     * ===Timeout Handling===
     * If the DAG has a traversal-level timeout, includes timeout scheduling
     * info in the effect. The executor should schedule a `TraversalTimeout`
     * signal for the deadline.
     *
     * @param state Current (initial) traversal state
     * @param signal Begin signal with optional entry node override
     * @return Result with DispatchNode or Schedule effect
     */
    def handleBegin(state: TraversalState, signal: TraversalSignal.Begin): F[TraversalResult] =
      // Validate preconditions
      if dag.nodes.isEmpty then
        return err(state, EmptyDag(signal.traversalId))

      if state.hasActiveWork || state.hasProgress then
        return err(state, AlreadyInProgress(signal.traversalId))

      val entryId = signal.entryNodeId.getOrElse(dag.entry)

      if !dag.entryPoints.toList.contains(entryId) then
        return err(state, InvalidNodeState(signal.traversalId, entryId, "valid entry point", "unknown"))

      dag.nodeOf(entryId) match
        case None =>
          err(state, MissingEntryNode(signal.traversalId))

        case Some(entry) =>
          // Calculate traversal timeout if DAG has one
          val timeoutInfo = buildTimeoutInfo(signal.traversalId)

          entry.deliveryStrategy match
            case DeliveryStrategy.Immediate =>
              val stateWithStart = state.start(entry.id, nodeAddress, None)
              val finalState = timeoutInfo match
                case Some((timeoutId, deadline)) =>
                  stateWithStart.scheduleTraversalTimeout(entry.id, timeoutId, deadline, nodeAddress, None)
                case None =>
                  stateWithStart
              ok(finalState, DispatchNode(signal.traversalId, entry.id, scheduleTraversalTimeout = timeoutInfo))

            case DeliveryStrategy.ScheduleAfter(delay) =>
              val stateWithTimeout = timeoutInfo match
                case Some((timeoutId, deadline)) =>
                  state.scheduleTraversalTimeout(entry.id, timeoutId, deadline, nodeAddress, None)
                case None => state
              scheduleOrDispatch(stateWithTimeout, entry.id, now.plusMillis(delay.toMillis))

            case DeliveryStrategy.ScheduleAt(time) =>
              val stateWithTimeout = timeoutInfo match
                case Some((timeoutId, deadline)) =>
                  state.scheduleTraversalTimeout(entry.id, timeoutId, deadline, nodeAddress, None)
                case None => state
              scheduleOrDispatch(stateWithTimeout, entry.id, time)

    /**
     * Handles the Resume signal when a scheduled node timer fires.
     *
     * Resume can occur in two scenarios:
     *   1. '''Scheduled node''': Node was scheduled for future execution
     *   2. '''Retry timer''': Node failed and retry was scheduled
     *
     * @param state Current traversal state
     * @param signal Resume signal with target node ID
     * @return Result with DispatchNode or NoOp effect
     */
    def handleResume(state: TraversalState, signal: TraversalSignal.Resume): F[TraversalResult] =
      dag.nodeOf(signal.nodeId) match
        case None =>
          err(state, NodeNotFound(signal.traversalId, signal.nodeId))

        case Some(node) =>
          // Check if this is a retry timer
          if state.isFailed(node.id) && !state.isStarted(node.id) then
            val restarted = state.retryNode(node.id, nodeAddress, None).start(node.id, nodeAddress, None)
            ok(restarted, DispatchNode(signal.traversalId, node.id))
          // Already finished - idempotent
          else if state.isFinished(node.id) then
            noop(state)
          // Already started - idempotent
          else if state.isStarted(node.id) then
            noop(state)
          // Resume scheduled node
          else
            val resumed = state.resume(node.id, nodeAddress, None)
            ok(resumed, DispatchNode(signal.traversalId, node.id))

    /**
     * Handles the Cancel signal to abort the traversal.
     *
     * Cancellation is always successful. Any active work should be
     * abandoned by the executor.
     *
     * @param state Current traversal state
     * @param signal Cancel signal
     * @return Result with Cancel effect (includes timeout ID to cancel)
     */
    def handleCancel(state: TraversalState, signal: TraversalSignal.Cancel): F[TraversalResult] =
      val timeoutToCancel = state.traversalTimeoutId
      val nodeToCancel = state.current.getOrElse(dag.entry)
      val canceled = state.cancel(nodeToCancel, nodeAddress, None).clearTraversalTimeout
      ok(canceled, Cancel(signal.traversalId, timeoutToCancel))

    /**
     * Handles the Retry signal to retry a failed node.
     *
     * ===Preconditions===
     *   - Node must exist in DAG
     *   - Node must be in failed state
     *
     * @param state Current traversal state
     * @param signal Retry signal with target node ID
     * @return Result with DispatchNode effect
     */
    def handleRetry(state: TraversalState, signal: TraversalSignal.Retry): F[TraversalResult] =
      dag.nodeOf(signal.nodeId) match
        case None =>
          err(state, NodeNotFound(signal.traversalId, signal.nodeId))

        case Some(node) =>
          if !state.isFailed(node.id) then
            err(state, InvalidNodeState(signal.traversalId, node.id, "failed", "not failed"))
          else
            val restarted = state.retryNode(node.id, nodeAddress, None).start(node.id, nodeAddress, None)
            ok(restarted, DispatchNode(signal.traversalId, node.id))

    /**
     * Handles NodeStart acknowledgment from the executor.
     *
     * This is an idempotent operation. If the node is already started,
     * returns NoOp. Used by executors to confirm work has begun.
     *
     * @param state Current traversal state
     * @param signal NodeStart signal with node ID
     * @return Result with NoOp effect (acknowledgment only)
     */
    def handleNodeStart(state: TraversalState, signal: TraversalSignal.NodeStart): F[TraversalResult] =
      if state.isStarted(signal.nodeId) then
        noop(state)
      else
        dag.nodeOf(signal.nodeId) match
          case None =>
            err(state, NodeNotFound(signal.traversalId, signal.nodeId))
          case Some(_) =>
            noop(state.start(signal.nodeId, nodeAddress, None))

    /**
     * Handles NodeSuccess when a node completes successfully.
     *
     * This is the primary transition handler that routes to the next node
     * based on:
     *   - Conditional edges (evaluated for matching conditions)
     *   - OnSuccess edges (default fallback)
     *   - Fork/Join node handling
     *
     * ===Flow===
     * {{{
     * NodeSuccess
     *      │
     *      ▼
     * [Already complete?] ──yes──▶ NoOp
     *      │ no
     *      ▼
     * [Not started?] ──yes──▶ InvalidNodeState error
     *      │ no
     *      ▼
     * Mark node as successful
     *      │
     *      ▼
     * [Traversal complete?] ──yes──▶ Complete effect
     *      │ no
     *      ▼
     * transitionToNext(OnSuccess)
     * }}}
     *
     * @param state Current traversal state
     * @param signal NodeSuccess signal (output field reserved for future use)
     * @return Effectful result with next transition
     */
    def handleNodeSuccess(state: TraversalState, signal: TraversalSignal.NodeSuccess): F[TraversalResult] =
      dag.nodeOf(signal.nodeId) match
        case None =>
          err(state, NodeNotFound(signal.traversalId, signal.nodeId))

        case Some(node) =>
          // Idempotent: already completed
          if state.isCompleted(node.id) then
            noop(state)
          // Precondition: node must be started
          else if !state.isStarted(node.id) && !state.current.contains(node.id) then
            err(state, InvalidNodeState(signal.traversalId, node.id, "started", "not started"))
          else
            val succeeded = state.successNode(node.id, nodeAddress, None)

            // Check if traversal is complete
            if succeeded.isTraversalComplete then
              val timeoutToCancel = succeeded.traversalTimeoutId
              val completed = succeeded.complete(node.id, nodeAddress, None).clearTraversalTimeout
              ok(completed, Complete(signal.traversalId, timeoutToCancel))
            else
              transitionToNext(succeeded, node.id, EdgeGuard.OnSuccess)

    /**
     * Handles NodeFailure when a node execution fails.
     *
     * ===Retry Logic===
     * {{{
     * NodeFailure
     *      │
     *      ▼
     * [Already failed?] ──yes──▶ NoOp (idempotent)
     *      │ no
     *      ▼
     * Mark node as failed
     *      │
     *      ▼
     * [Retry available?] ──yes──▶ ScheduleRetry
     *      │ no
     *      ▼
     * [OnFailure edge?] ──yes──▶ Dispatch failure handler
     *      │ no
     *      ▼
     * Fail traversal
     * }}}
     *
     * @param state Current traversal state
     * @param signal NodeFailure signal with optional reason
     * @return Result with ScheduleRetry, DispatchNode, or Fail effect
     */
    def handleNodeFailure(state: TraversalState, signal: TraversalSignal.NodeFailure): F[TraversalResult] =
      dag.nodeOf(signal.nodeId) match
        case None =>
          err(state, NodeNotFound(signal.traversalId, signal.nodeId))

        case Some(node) =>
          // Idempotent: already failed and not retrying
          if state.isFailed(node.id) && !state.isStarted(node.id) then
            return noop(state)

          val failed = state.failNode(node.id, nodeAddress, None, signal.reason)

          // Delegate to retry helper for cleaner separation of concerns
          scheduleRetryOrFail(failed, node, signal)

    /**
     * Handles external Completed signal.
     *
     * This is for explicit completion signaling from external sources.
     * The traversal must be in a completable state.
     *
     * @param state Current traversal state
     * @param signal Completed signal
     * @return Result with Complete effect or error
     */
    def handleCompleted(state: TraversalState, signal: TraversalSignal.Completed): F[TraversalResult] =
      if state.isTraversalComplete then
        val timeoutToCancel = state.traversalTimeoutId
        val nodeId = state.current.orElse(state.completed.lastOption)
        nodeId match
          case Some(id) =>
            ok(state.complete(id, nodeAddress, None).clearTraversalTimeout, Complete(signal.traversalId, timeoutToCancel))
          case None =>
            ok(state.clearTraversalTimeout, Complete(signal.traversalId, timeoutToCancel))
      else
        err(state, InvalidNodeState(
          signal.traversalId,
          state.current.getOrElse(dag.entry),
          "traversal complete", "not complete"
        ))

    /**
     * Handles external Failed signal.
     *
     * Forces the traversal into a failed state, regardless of current progress.
     *
     * @param state Current traversal state
     * @param signal Failed signal with optional reason
     * @return Result with Fail effect
     */
    def handleFailed(state: TraversalState, signal: TraversalSignal.Failed): F[TraversalResult] =
      val nodeId = state.current.getOrElse(dag.entry)
      val timeoutToCancel = state.traversalTimeoutId
      val failed = state.fail(nodeId, nodeAddress, None, signal.reason).clearTraversalTimeout
      ok(failed, Fail(signal.traversalId, timeoutToCancel))

    /**
     * Handles TraversalTimeout when the entire traversal times out.
     *
     * This is a safety net timeout that fires when the traversal takes
     * too long overall. It cancels all active work and fails the traversal.
     *
     * ===Race Condition Handling===
     * If the traversal completed just before the timeout fired,
     * returns NoOp (timeout is ignored).
     *
     * @param state Current traversal state
     * @param signal TraversalTimeout signal
     * @return Result with Fail effect or NoOp if already complete
     */
    def handleTraversalTimeout(state: TraversalState, signal: TraversalSignal.TraversalTimeout): F[TraversalResult] =
      // Race condition: traversal completed just before timeout
      if state.isTraversalComplete then
        noop(state.clearTraversalTimeout)
      // Not started yet
      else if !state.hasActiveWork && !state.hasProgress then
        noop(state.clearTraversalTimeout)
      // Timeout the traversal
      else
        val timedOut = state.timeoutTraversal(dag.entry, nodeAddress, None)
        ok(timedOut, Fail(signal.traversalId, None))

    // =========================================================================
    // Section 5: Fork/Join Handlers
    // =========================================================================
    //
    // PURPOSE: Signal handlers for parallel execution patterns. Enable complex
    // workflows with concurrent branches and synchronization.
    //
    // HANDLER INDEX:
    //   handleForkReached    - Start parallel branches (fan-out)
    //   handleBranchComplete - Branch finished with result
    //   handleJoinReached    - Branch arrived at synchronization point
    //   handleTimeout        - Branch or join timed out
    //   handleCancelBranches - Cancel specific branches
    //
    // SYNCHRONIZATION STRATEGIES:
    //   AND:      All branches must succeed. Any failure fails the join.
    //   OR:       First completion wins. Other branches are canceled.
    //   Quorum:   N branches must succeed. Remaining may continue or cancel.
    //
    // BRANCH LIFECYCLE:
    //   Pending → Running → Completed|Failed|Canceled|TimedOut
    //
    // NESTED FORK SUPPORT:
    // Forks can occur within branches of other forks. The FSM tracks:
    //   - parentScope: The outer fork's ID
    //   - parentBranchId: Which outer branch we're in
    // This context is restored when the inner join completes.
    //
    // IDEMPOTENCY:
    // Fork/join handlers handle duplicate signals gracefully:
    //   - Duplicate BranchComplete → NoOp (branch already terminal)
    //   - Duplicate JoinReached → Still evaluates join (may trigger)
    //   - Out-of-order signals → Handled via terminal state checks
    //
    // MAINTENANCE NOTES:
    //   - Branch IDs are generated fresh for each fork (not deterministic)
    //   - Join evaluation happens after ANY branch state change
    //   - Cancel propagation must handle nested forks correctly

    /**
     * Handles ForkReached when a fork node is encountered.
     *
     * Creates parallel branches for all outgoing edges and dispatches
     * all branch entry nodes simultaneously.
     *
     * ===Fork Fan-out===
     * {{{
     *                    ┌──────────┐
     *                    │   Fork   │
     *                    └────┬─────┘
     *           ┌─────────────┼─────────────┐
     *           │             │             │
     *           ▼             ▼             ▼
     *      [Branch A]    [Branch B]    [Branch C]
     *           │             │             │
     *           └─────────────┼─────────────┘
     *                         ▼
     *                    ┌──────────┐
     *                    │   Join   │
     *                    └──────────┘
     * }}}
     *
     * ===Nested Fork Support===
     * When a fork occurs within a branch of another fork, the FSM
     * tracks the parent branch context. This ensures that when the
     * inner join completes, execution continues in the correct
     * outer branch.
     *
     * @param state Current traversal state
     * @param signal ForkReached signal with fork node ID
     * @return Effectful result with DispatchNodes effect
     */
    def handleForkReached(state: TraversalState, signal: TraversalSignal.ForkReached): F[TraversalResult] =
      dag.nodeOf(signal.forkNodeId) match
        case None =>
          err(state, NodeNotFound(signal.traversalId, signal.forkNodeId))

        case Some(forkNode) =>
          forkNode.nodeType match
            case NodeType.Fork(forkId) =>
              // Get outgoing edges (branches) using helper
              val outgoingEdges = getForkOutgoingEdges(signal.forkNodeId)

              // No branches - degenerate case
              if outgoingEdges.isEmpty then
                if state.isTraversalComplete then
                  val timeoutToCancel = state.traversalTimeoutId
                  val completed = state.complete(signal.forkNodeId, nodeAddress, None).clearTraversalTimeout
                  ok(completed, Complete(signal.traversalId, timeoutToCancel))
                else
                  noop(state)

              // Create branches with unique IDs and dispatch
              else
                outgoingEdges
                  .traverse(edge => BranchId.next[F].map(_ -> edge.to))
                  .map(_.toMap)
                  .flatMap { branchEntries =>
                    // Get join configuration using helpers
                    val joinNodeIdOpt = dag.joinNodeIdFor(forkId)
                    val timeout = getJoinTimeoutForFork(forkId)

                    // Initialize fork scope and start all branches using helper
                    val startedBranches = initializeForkScope(state, signal.forkNodeId, forkId, branchEntries, timeout)

                    // Build dispatch list
                    val nodesToDispatch = branchEntries.toList.map((branchId, nodeId) => (nodeId, branchId))

                    ok(startedBranches, DispatchNodes(signal.traversalId, nodesToDispatch, timeout, joinNodeIdOpt))
                  }

            case _ =>
              err(state, InvalidNodeState(signal.traversalId, signal.forkNodeId, "Fork", "not Fork"))

    /**
     * Handles BranchComplete when a branch finishes execution.
     *
     * This handler is '''idempotent''': if the branch is already in a
     * terminal state, we skip the state update but still check if
     * the join condition is satisfied.
     *
     * ===Idempotency Rationale===
     * Both BranchComplete and JoinReached can mark a branch as complete.
     * Out-of-order signal delivery is possible, so we handle duplicates
     * gracefully.
     *
     * @param state Current traversal state
     * @param signal BranchComplete signal with result (Success/Failure/Timeout)
     * @return Effectful result (may trigger join completion)
     */
    def handleBranchComplete(state: TraversalState, signal: TraversalSignal.BranchComplete): F[TraversalResult] =
      state.branchStates.get(signal.branchId) match
        case None =>
          err(state, BranchNotFound(signal.traversalId, signal.branchId))

        case Some(branch) =>
          // Idempotent: branch already terminal
          if branch.isTerminal then
            checkJoinsForFork(state, signal.forkId)
          else
            val updatedState = signal.result match
              case BranchResult.Success(_) =>
                state.completeBranch(branch.currentNode.getOrElse(branch.entryNode),
                  signal.branchId, signal.forkId, nodeAddress, None)
              case BranchResult.Failure(reason) =>
                state.failBranch(branch.currentNode.getOrElse(branch.entryNode),
                  signal.branchId, signal.forkId, reason, nodeAddress, None)
              case BranchResult.Timeout =>
                state.timeoutBranch(branch.currentNode.getOrElse(branch.entryNode),
                  signal.branchId, signal.forkId, nodeAddress, None)

            checkJoinsForFork(updatedState, signal.forkId)

    /**
     * Handles JoinReached when a branch arrives at the join node.
     *
     * This is the canonical signal for branch completion in fork/join patterns.
     *
     * ===Validation===
     * Performs critical validations to prevent incorrect join completion:
     *   1. Join node must exist in DAG
     *   2. Join node must be a Join type
     *   3. Fork scope must exist for this fork ID
     *   4. Branch must belong to this fork (prevents cross-fork contamination)
     *   5. Join must be the expected join for this fork
     *
     * @param state Current traversal state
     * @param signal JoinReached signal with join node ID and branch ID
     * @return Effectful result (may trigger join completion)
     */
    def handleJoinReached(state: TraversalState, signal: TraversalSignal.JoinReached): F[TraversalResult] =
      dag.nodeOf(signal.joinNodeId) match
        case None =>
          err(state, NodeNotFound(signal.traversalId, signal.joinNodeId))

        case Some(joinNode) =>
          joinNode.nodeType match
            case NodeType.Join(forkId, strategy, timeout) =>
              // Validate fork scope exists
              state.forkScopes.get(forkId) match
                case None =>
                  err(state, JoinWithoutFork(signal.traversalId, signal.joinNodeId, forkId))

                case Some(forkScope) =>
                  // Validate branch belongs to this fork
                  if !forkScope.branches.contains(signal.branchId) then
                    err(state, InvalidBranchState(
                      signal.traversalId, signal.branchId,
                      s"member of fork $forkId",
                      s"not in fork branches"
                    ))
                  // Validate this is the correct join for this fork
                  else if dag.joinNodeIdFor(forkId).exists(_ != signal.joinNodeId) then
                    err(state, InvalidNodeState(
                      signal.traversalId, signal.joinNodeId,
                      s"join for fork $forkId",
                      s"expected ${dag.joinNodeIdFor(forkId)}"
                    ))
                  else
                    // Mark branch complete (idempotent)
                    val stateAfterBranch = state.branchStates.get(signal.branchId) match
                      case Some(branch) if !branch.isTerminal =>
                        state.completeBranch(branch.currentNode.getOrElse(branch.entryNode),
                          signal.branchId, forkId, nodeAddress, None)
                      case _ => state

                    // Register or update pending join
                    val pendingJoin = stateAfterBranch.pendingJoins.getOrElse(
                      signal.joinNodeId,
                      PendingJoin.create(signal.joinNodeId, forkId, strategy, forkScope.branches,
                        timeout.map(d => now.plusMillis(d.toMillis)))
                    ).branchCompleted(signal.branchId)

                    val stateWithJoin = stateAfterBranch.registerJoinArrival(
                      signal.joinNodeId, signal.branchId, forkId, pendingJoin, nodeAddress, None)

                    evaluateJoinCompletion(stateWithJoin, signal.joinNodeId, pendingJoin)

            case _ =>
              err(state, InvalidNodeState(signal.traversalId, signal.joinNodeId, "Join", "not Join"))

    /**
     * Handles Timeout signals for branches or joins.
     *
     * Two timeout types:
     *   1. '''Branch timeout''': Individual branch execution timeout
     *   2. '''Join timeout''': Timeout waiting for branches to arrive
     *
     * If an OnTimeout edge exists, transitions via that edge.
     * Otherwise, fails the appropriate scope.
     *
     * @param state Current traversal state
     * @param signal Timeout signal with node ID and optional branch ID
     * @return Effectful result with transition or failure
     */
    def handleTimeout(state: TraversalState, signal: TraversalSignal.Timeout): F[TraversalResult] =
      signal.branchId match
        // Branch timeout
        case Some(branchId) =>
          state.branchStates.get(branchId) match
            case None =>
              err(state, BranchNotFound(signal.traversalId, branchId))

            case Some(branch) =>
              val timedOut = state.timeoutBranch(signal.nodeId, branchId, branch.forkId, nodeAddress, None)

              // Check for OnTimeout edge
              dag.outgoingEdges(signal.nodeId, EdgeGuard.OnTimeout).headOption match
                case Some(edge) =>
                  dag.nodeOf(edge.to) match
                    case Some(_) =>
                      ok(timedOut.start(edge.to, nodeAddress, None),
                        DispatchNode(signal.traversalId, edge.to))
                    case None =>
                      err(timedOut, NodeNotFound(signal.traversalId, edge.to))

                case None =>
                  checkJoinsForFork(timedOut, branch.forkId)

        // Join timeout
        case None =>
          dag.nodeOf(signal.nodeId) match
            case Some(node) if node.isJoin =>
              node.nodeType match
                case NodeType.Join(forkId, _, _) =>
                  val pendingBranches = state.pendingJoins.get(signal.nodeId)
                    .map(_.branchesToCancel).getOrElse(Set.empty)

                  val timedOut = state.timeoutJoin(signal.nodeId, forkId, pendingBranches, nodeAddress, None)

                  // Check for OnTimeout edge
                  dag.outgoingEdges(signal.nodeId, EdgeGuard.OnTimeout).headOption match
                    case Some(edge) =>
                      dag.nodeOf(edge.to) match
                        case Some(_) =>
                          ok(timedOut.start(edge.to, nodeAddress, None),
                            DispatchNode(signal.traversalId, edge.to))
                        case None =>
                          val timeoutToCancel = timedOut.traversalTimeoutId
                          val failed = timedOut.fail(signal.nodeId, nodeAddress, None,
                            Some(s"OnTimeout target ${edge.to} not found")).clearTraversalTimeout
                          ok(failed, Fail(signal.traversalId, timeoutToCancel))

                    case None =>
                      val timeoutToCancel = timedOut.traversalTimeoutId
                      ok(timedOut.clearTraversalTimeout, Fail(signal.traversalId, timeoutToCancel))

                case _ =>
                  err(state, UnsupportedSignal(signal.traversalId))

            case _ =>
              err(state, NodeNotFound(signal.traversalId, signal.nodeId))

    /**
     * Handles CancelBranches request to cancel specific branches.
     *
     * Used when:
     *   - OR join completes and remaining branches should be stopped
     *   - External request to abort specific branches
     *
     * @param state Current traversal state
     * @param signal CancelBranches signal with branch IDs to cancel
     * @return Result with NoOp effect (state updated internally)
     */
    def handleCancelBranches(state: TraversalState, signal: TraversalSignal.CancelBranches): F[TraversalResult] =
      val canceledState = signal.branchIds.foldLeft(state) { (s, branchId) =>
        s.branchStates.get(branchId) match
          case Some(branch) if branch.isActive =>
            s.cancelBranch(branch.currentNode.getOrElse(branch.entryNode),
              branchId, signal.forkId, signal.reason, nodeAddress, None)
          case _ => s
      }
      noop(canceledState)

    // =========================================================================
    // Section 6: Join Completion Logic
    // =========================================================================
    //
    // PURPOSE: Centralized logic for evaluating join conditions and handling
    // the transition after synchronization completes.
    //
    // JOIN EVALUATION FLOW:
    //   1. Check if join condition is satisfied (strategy-dependent)
    //   2. If satisfied → complete join, transition to next node
    //   3. If failed → cancel remaining branches, try OnFailure edge
    //   4. If neither → wait for more branches (NoOp)
    //
    // STRATEGY SATISFACTION CONDITIONS:
    //   AND:      completedBranches == requiredBranches (all succeeded)
    //   OR:       completedBranches.nonEmpty (any succeeded)
    //   Quorum:   completedBranches.size >= n (enough succeeded)
    //
    // FAILURE CONDITIONS:
    //   AND:      Any branch failed
    //   OR:       All branches failed
    //   Quorum:   completedBranches.size + pendingCount < n (can't reach quorum)
    //
    // NESTED FORK CONTEXT RESTORATION:
    // When a join completes inside an outer fork's branch:
    //   1. Look up parentBranchId from forkScope
    //   2. Add joinNodeId → parentBranchId mapping to nodeToBranch
    //   3. Subsequent nodes continue in the correct outer branch
    //
    // MAINTENANCE NOTES:
    //   - `evaluateJoinCompletion` is called after EVERY branch state change
    //   - Branch cancellation for OR joins happens here
    //   - OnFailure edge provides recovery path for failed joins

    /**
     * Checks if any pending join for the given fork is ready for completion.
     *
     * @param state Current traversal state
     * @param forkId Fork ID to check joins for
     * @return Effectful result (may trigger transition)
     */
    def checkJoinsForFork(state: TraversalState, forkId: ForkId): F[TraversalResult] =
      state.pendingJoins.find { case (_, pj) => pj.forkId == forkId } match
        case Some((joinNodeId, pendingJoin)) =>
          evaluateJoinCompletion(state, joinNodeId, pendingJoin)
        case None =>
          noop(state)

    /**
     * Evaluates whether a join condition is satisfied and handles the result.
     *
     * ===Join Strategies===
     *
     * {{{
     * AND:     All branches must complete successfully
     *          Fails if any branch fails
     *
     * OR:      Any branch completing succeeds the join
     *          Remaining branches are canceled
     *
     * Quorum:  N branches must complete successfully
     *          where N is configured per join
     * }}}
     *
     * ===State Transitions===
     * {{{
     * pendingJoin.isSatisfied?
     *      │
     *      ├──yes──▶ Complete join, transition to next node
     *      │
     *      ▼
     * pendingJoin.hasFailed?
     *      │
     *      ├──yes──▶ Cancel remaining branches, try OnFailure edge
     *      │
     *      ▼
     * Wait for more branches (NoOp)
     * }}}
     *
     * @param state Current traversal state
     * @param joinNodeId Join node being evaluated
     * @param pendingJoin Current pending join state
     * @return Effectful result with next transition or NoOp
     */
    def evaluateJoinCompletion(state: TraversalState, joinNodeId: NodeId, pendingJoin: PendingJoin): F[TraversalResult] =
      // -----------------------------------------------------------------------
      // Case 1: Join is satisfied - complete and continue
      // -----------------------------------------------------------------------
      if pendingJoin.isSatisfied then
        // Collect branches to cancel (for OR joins)
        val branchesToCancelIds = pendingJoin.branchesToCancel
        val branchesToCancelWithForkId: Set[(BranchId, ForkId)] =
          if branchesToCancelIds.nonEmpty && pendingJoin.strategy == JoinStrategy.Or then
            branchesToCancelIds.map(bid => (bid, pendingJoin.forkId))
          else Set.empty

        // Cancel remaining branches for OR join
        val stateWithCancels =
          if branchesToCancelIds.nonEmpty && pendingJoin.strategy == JoinStrategy.Or then
            branchesToCancelIds.foldLeft(state) { (s, branchId) =>
              s.branchStates.get(branchId) match
                case Some(branch) if branch.isActive =>
                  s.cancelBranch(branch.currentNode.getOrElse(branch.entryNode),
                    branchId, pendingJoin.forkId, "OR join completed", nodeAddress, None)
                case _ => s
            }
          else state

        // Get parent branch context for nested forks
        val forkScope = stateWithCancels.forkScopes.get(pendingJoin.forkId)
        val parentBranchId = forkScope.flatMap(_.parentBranchId)

        // Complete the join
        val joinedState = stateWithCancels.completeJoin(
          joinNodeId, pendingJoin.forkId, pendingJoin.completedBranches, nodeAddress, None)

        // Restore parent branch context for nested forks
        val stateForTransition = parentBranchId match
          case Some(branchId) =>
            joinedState.copy(nodeToBranch = joinedState.nodeToBranch + (joinNodeId -> branchId))
          case None =>
            joinedState

        transitionToNext(stateForTransition, joinNodeId, EdgeGuard.OnSuccess,
          branchesToCancel = branchesToCancelWithForkId)

      // -----------------------------------------------------------------------
      // Case 2: Join has failed - cancel branches and handle failure
      // -----------------------------------------------------------------------
      else if pendingJoin.hasFailed then
        val branchesToCancelIds = pendingJoin.branchesToCancel

        // Cancel remaining active branches
        val stateWithCancels = branchesToCancelIds.foldLeft(state) { (s, branchId) =>
          s.branchStates.get(branchId) match
            case Some(branch) if branch.isActive =>
              s.cancelBranch(branch.currentNode.getOrElse(branch.entryNode),
                branchId, pendingJoin.forkId, "Join failed", nodeAddress, None)
            case _ => s
        }

        // Try OnFailure edge
        dag.outgoingEdges(joinNodeId, EdgeGuard.OnFailure).headOption match
          case Some(edge) =>
            val started = stateWithCancels.start(edge.to, nodeAddress, None)
            val branchesToCancelWithForkId = branchesToCancelIds.map(bid => (bid, pendingJoin.forkId))
            ok(started, DispatchNode(stateWithCancels.traversalId, edge.to, branchesToCancelWithForkId))

          case None =>
            val timeoutToCancel = stateWithCancels.traversalTimeoutId
            val failed = stateWithCancels.fail(joinNodeId, nodeAddress, None,
              Some("Join failed: insufficient branch completions")).clearTraversalTimeout
            ok(failed, Fail(stateWithCancels.traversalId, timeoutToCancel))

      // -----------------------------------------------------------------------
      // Case 3: Join not yet satisfied - wait for more branches
      // -----------------------------------------------------------------------
      else
        noop(state)

    // =========================================================================
    // Section 7: FSM Definition
    // =========================================================================
    //
    // PURPOSE: Main signal router that dispatches to appropriate handlers.
    // This is the FSM's entry point for all signal processing.
    //
    // ROUTING STRATEGY:
    // Pattern matching on sealed trait `TraversalSignal` ensures exhaustive
    // coverage. The compiler will warn if a signal type is not handled.
    //
    // SIGNAL ORDERING:
    // Signals are grouped by category for readability:
    //   1. Linear traversal signals (Begin, Resume, Cancel, etc.)
    //   2. Fork/Join signals (ForkReached, BranchComplete, etc.)
    //
    // EXECUTOR CONTRACT:
    // The executor calling this FSM should:
    //   1. Maintain the current TraversalState
    //   2. Call transition(state, signal) for each event
    //   3. Execute the returned TraversalEffect
    //   4. Use the returned state for subsequent calls
    //   5. Handle errors gracefully (Left side of result)
    //
    // ADDING NEW SIGNALS:
    // To add a new signal type:
    //   1. Add case class to TraversalSignal.scala
    //   2. Add handler method in appropriate section above
    //   3. Add case clause in the match below
    //   4. Update tests in TraversalFSMSuite.scala

    FSM[F, TraversalState, TraversalSignal, TraversalError, TraversalEffect]: (state, signal) =>
      signal match
        // ---------------------------------------------------------------------
        // Linear Traversal Signals
        // ---------------------------------------------------------------------
        // These signals handle sequential node-by-node execution.
        // Most common flow: Begin → NodeSuccess → NodeSuccess → ... → Complete

        case s: TraversalSignal.Begin            => handleBegin(state, s)
        case s: TraversalSignal.Resume           => handleResume(state, s)
        case s: TraversalSignal.Cancel           => handleCancel(state, s)
        case s: TraversalSignal.Retry            => handleRetry(state, s)
        case s: TraversalSignal.NodeStart        => handleNodeStart(state, s)
        case s: TraversalSignal.NodeSuccess      => handleNodeSuccess(state, s)
        case s: TraversalSignal.NodeFailure      => handleNodeFailure(state, s)
        case s: TraversalSignal.Completed        => handleCompleted(state, s)
        case s: TraversalSignal.Failed           => handleFailed(state, s)
        case s: TraversalSignal.TraversalTimeout => handleTraversalTimeout(state, s)

        // ---------------------------------------------------------------------
        // Fork/Join Signals
        // ---------------------------------------------------------------------
        // These signals handle parallel branch execution and synchronization.
        // Flow: ForkReached → (parallel branches) → JoinReached → Continue

        case s: TraversalSignal.ForkReached      => handleForkReached(state, s)
        case s: TraversalSignal.BranchComplete   => handleBranchComplete(state, s)
        case s: TraversalSignal.JoinReached      => handleJoinReached(state, s)
        case s: TraversalSignal.Timeout          => handleTimeout(state, s)
        case s: TraversalSignal.CancelBranches   => handleCancelBranches(state, s)
