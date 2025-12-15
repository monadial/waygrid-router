package com.monadial.waygrid.common.domain.model.traversal.fsm

import cats.Monad
import cats.implicits.*
import com.monadial.waygrid.common.domain.model.fsm.FSM
import com.monadial.waygrid.common.domain.model.fsm.Value.Result
import com.monadial.waygrid.common.domain.model.resiliency.{Backoff, RetryPolicy}
import com.monadial.waygrid.common.domain.model.routing.Value.DeliveryStrategy
import com.monadial.waygrid.common.domain.model.traversal.dag.{Dag, JoinStrategy, NodeType}
import com.monadial.waygrid.common.domain.model.traversal.dag.Value.{BranchId, EdgeGuard, ForkId, NodeId}
import com.monadial.waygrid.common.domain.model.traversal.fsm.TraversalEffect.*
import com.monadial.waygrid.common.domain.model.traversal.condition.Condition
import com.monadial.waygrid.common.domain.model.traversal.state.{BranchResult, PendingJoin, TraversalState}
import com.monadial.waygrid.common.domain.model.traversal.state.Value.RetryAttempt
import com.monadial.waygrid.common.domain.model.vectorclock.VectorClock
import com.monadial.waygrid.common.domain.value.Address.NodeAddress

import java.time.Instant

/**
 * ==TraversalFSM==
 *
 * Deterministic FSM for DAG traversal supporting both linear and forking patterns.
 * Handles retries, scheduling, success/failure transitions, fork/join synchronization,
 * and completion.
 *
 * The FSM processes signals and produces effects that describe what actions
 * should be taken by the executor (dispatch nodes, schedule retries, etc.).
 *
 * ==Fork/Join Support==
 * - Fork nodes initiate parallel branch execution
 * - Join nodes synchronize branches with AND/OR/Quorum strategies
 * - Nested forks are supported with proper scope tracking
 *
 * ==Purity==
 * The FSM is pure and deterministic. Time is passed explicitly via `now` parameter
 * to enable reproducible testing and replay.
 */
object TraversalFSM:

  private type TraversalResult = Result[TraversalState, TraversalError, TraversalEffect]

  /**
   * Creates a stateless FSM for DAG traversal.
   *
   * @param dag         The DAG to traverse
   * @param nodeAddress The address of this node (actor)
   * @param now         Current timestamp for scheduling decisions (passed explicitly for purity)
   * @return FSM that processes TraversalSignals and produces TraversalEffects
   */
  def stateless[F[+_]: Monad](dag: Dag, nodeAddress: NodeAddress, now: Instant, foreignVectorClock: Option[VectorClock]): FSM[F, TraversalState, TraversalSignal, TraversalError, TraversalEffect] =

    // -------------------------------------------------------------------------
    // Helper Functions
    // -------------------------------------------------------------------------

    def successResult(state: TraversalState, effect: TraversalEffect): TraversalResult =
      Result(state, effect.asRight)

    def errorResult(state: TraversalState, error: TraversalError): TraversalResult =
      Result(state, error.asLeft)

    def noOpResult(state: TraversalState): TraversalResult =
      Result(state, NoOp(state.traversalId).asRight)

    def scheduleStartOrStartNow(state: TraversalState, nodeId: NodeId, scheduleAt: Instant): TraversalResult =
      if scheduleAt.isAfter(now) then
        successResult(
          state.schedule(nodeId, nodeAddress, scheduleAt, None),
          Schedule(state.traversalId, scheduleAt, nodeId)
        )
      else
        successResult(
          state.start(nodeId, nodeAddress, None),
          DispatchNode(state.traversalId, nodeId)
        )

    /**
     * Transition to the next node(s) after completing a node.
     *
     * @param state Current traversal state
     * @param fromNodeId Node that just completed
     * @param guard Edge guard to follow (OnSuccess, OnFailure, etc.)
     * @param output Optional JSON output from the completed node (for conditional routing)
     * @param branchesToCancel Optional branches to cancel (for OR join completion)
     */
    def transitionToNextNode(
      state: TraversalState,
      fromNodeId: NodeId,
      guard: EdgeGuard,
      output: Option[io.circe.Json] = None,
      branchesToCancel: Set[(BranchId, ForkId)] = Set.empty
    ): F[TraversalResult] =
      val nextNodes =
        guard match
          case EdgeGuard.OnSuccess =>
            val matched =
              dag.outgoingEdges(fromNodeId).collect {
                case e if e.guard match
                    case EdgeGuard.Conditional(cond) => Condition.eval(cond, output)
                    case _                           => false
                  => dag.nodes.get(e.to)
              }.flatten

            if matched.nonEmpty then matched
            else dag.nextNodes(fromNodeId, EdgeGuard.OnSuccess)
          case other =>
            dag.nextNodes(fromNodeId, other)
      nextNodes match
        case Nil =>
          // No more nodes - traversal is complete or failed based on guard
          guard match
            case EdgeGuard.OnSuccess | EdgeGuard.Always | EdgeGuard.OnAny =>
              val completed = state.complete(fromNodeId, nodeAddress, None).clearTraversalTimeout
              successResult(completed, Complete(state.traversalId, state.traversalTimeoutId)).pure[F]
            case EdgeGuard.OnFailure | EdgeGuard.OnTimeout =>
              val failed = state.fail(fromNodeId, nodeAddress, None, Some("No failure/timeout edge available")).clearTraversalTimeout
              successResult(failed, Fail(state.traversalId, state.traversalTimeoutId)).pure[F]
            case EdgeGuard.Conditional(_) =>
              val completed = state.complete(fromNodeId, nodeAddress, None).clearTraversalTimeout
              successResult(completed, Complete(state.traversalId, state.traversalTimeoutId)).pure[F]

        case head :: Nil =>
          // Join nodes are internal synchronization points; don't dispatch them as work.
          if head.isJoin then
            state.branchForNode(fromNodeId) match
              case None =>
                errorResult(
                  state,
                  InvalidNodeState(state.traversalId, fromNodeId, "branch context for Join", "no branch context")
                ).pure[F]
              case Some(branch) =>
                onJoinReached(state, TraversalSignal.JoinReached(state.traversalId, head.id, branch.branchId))
          // Fork nodes are control flow nodes - trigger fork handling immediately, don't dispatch as work
          else if head.isFork then
            // First, advance the branch to include the fork node (preserves parent branch context for nested forks)
            val stateWithBranchAdvanced = state.branchForNode(fromNodeId) match
              case Some(branch) =>
                state.advanceBranch(head.id, branch.branchId, branch.forkId, nodeAddress, None)
              case None =>
                state
            // Mark the fork node as "completed" (it's a pass-through control flow node)
            val stateWithForkCompleted = stateWithBranchAdvanced.successNode(head.id, nodeAddress, None)
            onForkReached(stateWithForkCompleted, TraversalSignal.ForkReached(state.traversalId, head.id))
          else
            head.deliveryStrategy match
              case DeliveryStrategy.Immediate =>
                val nextState =
                  state.branchForNode(fromNodeId) match
                    case Some(branch) =>
                      state
                        .start(head.id, nodeAddress, None)
                        .advanceBranch(head.id, branch.branchId, branch.forkId, nodeAddress, None)
                    case None =>
                      state.start(head.id, nodeAddress, None)
                // Include branches to cancel in the effect (for OR join completion)
                successResult(nextState, DispatchNode(state.traversalId, head.id, branchesToCancel)).pure[F]

              case DeliveryStrategy.ScheduleAfter(delay) =>
                scheduleStartOrStartNow(state, head.id, now.plusMillis(delay.toMillis)).pure[F]

              case DeliveryStrategy.ScheduleAt(time) =>
                scheduleStartOrStartNow(state, head.id, time).pure[F]

        case many =>
          // Multiple successors - this should only happen from Fork nodes now
          // (since we handle Fork detection above, this is a fallback/error case)
          dag.nodeOf(fromNodeId) match
            case Some(node) if node.isFork && guard == EdgeGuard.OnSuccess =>
              // Fork node being explicitly re-processed (idempotent)
              onForkReached(state, TraversalSignal.ForkReached(state.traversalId, fromNodeId))
            case _ =>
              errorResult(
                state,
                InvalidNodeState(
                  state.traversalId,
                  fromNodeId,
                  "single successor (linear) or Fork node",
                  s"${many.size} successors without Fork node type"
                )
              ).pure[F]

    // -------------------------------------------------------------------------
    // Signal Handlers
    // -------------------------------------------------------------------------

    def onBegin(state: TraversalState, signal: TraversalSignal.Begin): TraversalResult =
      if dag.nodes.isEmpty then
        errorResult(state, EmptyDag(signal.traversalId))
      else if state.hasActiveWork || state.hasProgress then
        errorResult(state, AlreadyInProgress(signal.traversalId))
      else
        val entryId = signal.entryNodeId.getOrElse(dag.entry)
        if !dag.entryPoints.toList.contains(entryId) then
          errorResult(state, InvalidNodeState(signal.traversalId, entryId, "valid entry point", "unknown entry point"))
        else
          dag.nodeOf(entryId) match
            case None =>
              errorResult(state, MissingEntryNode(signal.traversalId))
            case Some(entry) =>
              // Check if DAG has a traversal-level timeout
              val timeoutInfo: Option[(String, Instant)] = dag.timeout.map { duration =>
                val timeoutId = s"traversal-timeout-${signal.traversalId.unwrap}"
                val deadline = now.plusMillis(duration.toMillis)
                (timeoutId, deadline)
              }

              entry.deliveryStrategy match
                case DeliveryStrategy.Immediate =>
                  // Update state with timeout tracking if applicable
                  val stateWithStart = state.start(entry.id, nodeAddress, None)
                  val finalState = timeoutInfo match
                    case Some((timeoutId, deadline)) =>
                      stateWithStart.scheduleTraversalTimeout(entry.id, timeoutId, deadline, nodeAddress, None)
                    case None =>
                      stateWithStart

                  successResult(
                    finalState,
                    DispatchNode(signal.traversalId, entry.id, scheduleTraversalTimeout = timeoutInfo)
                  )
                case DeliveryStrategy.ScheduleAfter(delay) =>
                  // For scheduled starts, timeout still applies from now
                  val stateWithTimeout = timeoutInfo match
                    case Some((timeoutId, deadline)) =>
                      state.scheduleTraversalTimeout(entry.id, timeoutId, deadline, nodeAddress, None)
                    case None =>
                      state
                  scheduleStartOrStartNow(stateWithTimeout, entry.id, now.plusMillis(delay.toMillis))
                case DeliveryStrategy.ScheduleAt(time) =>
                  val stateWithTimeout = timeoutInfo match
                    case Some((timeoutId, deadline)) =>
                      state.scheduleTraversalTimeout(entry.id, timeoutId, deadline, nodeAddress, None)
                    case None =>
                      state
                  scheduleStartOrStartNow(stateWithTimeout, entry.id, time)

    def onResume(state: TraversalState, signal: TraversalSignal.Resume): TraversalResult =
      // Resume is called when a scheduled node timer fires
      dag.nodeOf(signal.nodeId) match
        case None =>
          errorResult(state, NodeNotFound(signal.traversalId, signal.nodeId))
        case Some(node) =>
          // If the node is failed, treat resume as a retry timer firing.
          if state.isFailed(node.id) && !state.isStarted(node.id) then
            val retried = state.retryNode(node.id, nodeAddress, None)
            val restarted = retried.start(node.id, nodeAddress, None)
            successResult(restarted, DispatchNode(signal.traversalId, node.id))
          else if state.isFinished(node.id) then
            // Node already processed, nothing to do
            noOpResult(state)
          else if state.isStarted(node.id) then
            // Already started
            noOpResult(state)
          else
            // Resume the scheduled node by starting it
            val resumed = state.resume(node.id, nodeAddress, None)
            successResult(resumed, DispatchNode(signal.traversalId, node.id))

    def onCancel(state: TraversalState, signal: TraversalSignal.Cancel): TraversalResult =
      val timeoutToCancel = state.traversalTimeoutId
      state.current match
        case None =>
          // No active node, just mark as canceled
          val canceled = state.cancel(dag.entry, nodeAddress, None).clearTraversalTimeout
          successResult(canceled, Cancel(signal.traversalId, timeoutToCancel))
        case Some(nodeId) =>
          val canceled = state.cancel(nodeId, nodeAddress, None).clearTraversalTimeout
          successResult(canceled, Cancel(signal.traversalId, timeoutToCancel))

    def onRetry(state: TraversalState, signal: TraversalSignal.Retry): TraversalResult =
      dag.nodeOf(signal.nodeId) match
        case None =>
          errorResult(state, NodeNotFound(signal.traversalId, signal.nodeId))
        case Some(node) =>
          if !state.isFailed(node.id) then
            errorResult(
              state,
              InvalidNodeState(signal.traversalId, node.id, "failed", "not failed")
            )
          else
            // Retry: mark the retry, then restart the node
            val retried = state.retryNode(node.id, nodeAddress, None)
            val restarted = retried.start(node.id, nodeAddress, None)
            successResult(restarted, DispatchNode(signal.traversalId, node.id))

    def onNodeStart(state: TraversalState, signal: TraversalSignal.NodeStart): TraversalResult =
      // NodeStart is an acknowledgment that a node has begun processing
      // The state should already have the node as current (from Begin/Resume/Retry)
      if state.isStarted(signal.nodeId) then
        noOpResult(state)
      else
        dag.nodeOf(signal.nodeId) match
          case None =>
            errorResult(state, NodeNotFound(signal.traversalId, signal.nodeId))
          case Some(_) =>
            // Start the node if not already started
            val started = state.start(signal.nodeId, nodeAddress, None)
            noOpResult(started)

    def onNodeSuccess(state: TraversalState, signal: TraversalSignal.NodeSuccess): F[TraversalResult] =
      dag.nodeOf(signal.nodeId) match
        case None =>
          errorResult(state, NodeNotFound(signal.traversalId, signal.nodeId)).pure[F]
        case Some(node) =>
          if state.isCompleted(node.id) then
            // Already completed, no-op
            noOpResult(state).pure[F]
          else if !state.isStarted(node.id) && !state.current.contains(node.id) then
            // Node wasn't being processed
            errorResult(
              state,
              InvalidNodeState(signal.traversalId, node.id, "started", "not started")
            ).pure[F]
          else
            // Mark success and transition to next node
            val succeeded = state.successNode(node.id, nodeAddress, None)
            if succeeded.isTraversalComplete then
              val timeoutToCancel = succeeded.traversalTimeoutId
              val completed = succeeded.complete(node.id, nodeAddress, None).clearTraversalTimeout
              successResult(completed, Complete(signal.traversalId, timeoutToCancel)).pure[F]
            else
              transitionToNextNode(succeeded, node.id, EdgeGuard.OnSuccess, signal.output)

    def onNodeFailure(state: TraversalState, signal: TraversalSignal.NodeFailure): TraversalResult =
      dag.nodeOf(signal.nodeId) match
        case None =>
          errorResult(state, NodeNotFound(signal.traversalId, signal.nodeId))
        case Some(node) =>
          if state.isFailed(node.id) && !state.isStarted(node.id) then
            // Already failed and not being retried, no-op
            noOpResult(state)
          else
            // Mark the node as failed
            val failed = state.failNode(node.id, nodeAddress, None, signal.reason)

            // Check if we can retry
            val currentAttempt = failed.retryCount(node.id)
            val nextAttempt = currentAttempt + 1

            Backoff[RetryPolicy].nextDelay(node.retryPolicy, nextAttempt, None) match
              case Some(delay) =>
                // Schedule a retry
                val scheduledAt = now.plusMillis(delay.toMillis)
                val scheduledState = failed.schedule(node.id, nodeAddress, scheduledAt, None)
                successResult(
                  scheduledState,
                  ScheduleRetry(signal.traversalId, scheduledAt, RetryAttempt(nextAttempt), node.id)
                )
              case None =>
                // No more retries, check for failure edge
                dag.nextNodes(node.id, EdgeGuard.OnFailure).headOption match
                  case Some(nextNode) =>
                    // Transition via failure edge
                    nextNode.deliveryStrategy match
                      case DeliveryStrategy.Immediate =>
                        val nextState = failed.start(nextNode.id, nodeAddress, None)
                        successResult(nextState, DispatchNode(signal.traversalId, nextNode.id))
                      case DeliveryStrategy.ScheduleAfter(d) =>
                        scheduleStartOrStartNow(failed, nextNode.id, now.plusMillis(d.toMillis))
                      case DeliveryStrategy.ScheduleAt(time) =>
                        scheduleStartOrStartNow(failed, nextNode.id, time)
                  case None =>
                    // No failure edge, traversal fails
                    val timeoutToCancel = failed.traversalTimeoutId
                    val finalState = failed.fail(node.id, nodeAddress, None, signal.reason).clearTraversalTimeout
                    successResult(finalState, Fail(signal.traversalId, timeoutToCancel))

    def onCompleted(state: TraversalState, signal: TraversalSignal.Completed): TraversalResult =
      // External completion signal - verify state and mark complete
      if state.isTraversalComplete then
        val timeoutToCancel = state.traversalTimeoutId
        state.current.orElse(state.completed.lastOption) match
          case Some(nodeId) =>
            val completed = state.complete(nodeId, nodeAddress, None).clearTraversalTimeout
            successResult(completed, Complete(signal.traversalId, timeoutToCancel))
          case None =>
            successResult(state.clearTraversalTimeout, Complete(signal.traversalId, timeoutToCancel))
      else
        errorResult(state, InvalidNodeState(
          signal.traversalId,
          state.current.getOrElse(dag.entry),
          "traversal complete",
          "traversal not complete"
        ))

    def onFailed(state: TraversalState, signal: TraversalSignal.Failed): TraversalResult =
      // External failure signal - mark traversal as failed
      val nodeId = state.current.getOrElse(dag.entry)
      val timeoutToCancel = state.traversalTimeoutId
      val failed = state.fail(nodeId, nodeAddress, None, signal.reason).clearTraversalTimeout
      successResult(failed, Fail(signal.traversalId, timeoutToCancel))

    def onTraversalTimeout(state: TraversalState, signal: TraversalSignal.TraversalTimeout): TraversalResult =
      // Check if traversal is already complete (race condition - timeout fired but traversal finished)
      if state.isTraversalComplete then
        // Traversal already completed, ignore the timeout (log in executor)
        noOpResult(state.clearTraversalTimeout)
      else if !state.hasActiveWork && !state.hasProgress then
        // Traversal hasn't even started, just cancel
        noOpResult(state.clearTraversalTimeout)
      else
        // Timeout the traversal - cancel all active work
        val timedOut = state.timeoutTraversal(dag.entry, nodeAddress, None)
        successResult(timedOut, Fail(signal.traversalId, None)) // Timeout already cleared in state

    // -------------------------------------------------------------------------
    // Fork/Join Signal Handlers
    // -------------------------------------------------------------------------

    def onForkReached(state: TraversalState, signal: TraversalSignal.ForkReached): F[TraversalResult] =
      dag.nodeOf(signal.forkNodeId) match
        case None =>
          errorResult(state, NodeNotFound(signal.traversalId, signal.forkNodeId)).pure[F]
        case Some(forkNode) =>
          forkNode.nodeType match
            case NodeType.Fork(forkId) =>
              // Fork fan-out is defined by all outgoing edges from the fork node.
              // Use only OnSuccess/Always edges by preference; fall back to all edges for backward compatibility.
              val preferredEdges = dag.outgoingEdges(signal.forkNodeId, EdgeGuard.OnSuccess) ++
                dag.outgoingEdges(signal.forkNodeId, EdgeGuard.Always)
              val outgoingEdges = if preferredEdges.nonEmpty then preferredEdges else dag.outgoingEdges(signal.forkNodeId)

              if outgoingEdges.isEmpty then
                // No branches - fork is effectively a terminal/synchronization node.
                if state.isTraversalComplete then
                  val timeoutToCancel = state.traversalTimeoutId
                  val completed = state.complete(signal.forkNodeId, nodeAddress, None).clearTraversalTimeout
                  successResult(completed, Complete(signal.traversalId, timeoutToCancel)).pure[F]
                else
                  noOpResult(state).pure[F]
              else
                // Create branches for each outgoing edge (stable ULIDs).
                outgoingEdges
                  .traverse(edge => BranchId.next[F].map(_ -> edge.to))
                  .map(_.toMap)
                  .map { branchEntries =>
                    val parentScope = state.branchForNode(signal.forkNodeId).map(_.forkId)

                    // Find join node and its timeout configuration
                    val joinNodeIdOpt = dag.joinNodeIdFor(forkId)
                    val timeout = joinNodeIdOpt
                      .flatMap(dag.nodeOf)
                      .flatMap(_.nodeType match
                        case NodeType.Join(_, _, t) => t.map(d => now.plusMillis(d.toMillis))
                        case _                      => None
                      )

                    val forkedState = state.startFork(
                      signal.forkNodeId,
                      forkId,
                      branchEntries,
                      parentScope,
                      timeout,
                      nodeAddress,
                      None,
                      now
                    )

                    val startedBranches = branchEntries.foldLeft(forkedState) { case (s, (branchId, nodeId)) =>
                      s
                        .start(nodeId, nodeAddress, None)
                        .startBranch(nodeId, branchId, forkId, nodeAddress, None)
                    }

                    val nodesToDispatch = branchEntries.toList.map { case (branchId, nodeId) =>
                      (nodeId, branchId)
                    }

                    // Include timeout info in the effect so executor can schedule timeout signal
                    successResult(
                      startedBranches,
                      DispatchNodes(signal.traversalId, nodesToDispatch, timeout, joinNodeIdOpt)
                    )
                  }

            case _ =>
              // Not a fork node
              errorResult(state, InvalidNodeState(signal.traversalId, signal.forkNodeId, "Fork", "not Fork")).pure[F]

    def onBranchComplete(state: TraversalState, signal: TraversalSignal.BranchComplete): F[TraversalResult] =
      state.branchStates.get(signal.branchId) match
        case None =>
          errorResult(state, BranchNotFound(signal.traversalId, signal.branchId)).pure[F]
        case Some(branch) =>
          val updatedState = signal.result match
            case BranchResult.Success(_) =>
              state.completeBranch(
                branch.currentNode.getOrElse(branch.entryNode),
                signal.branchId,
                signal.forkId,
                nodeAddress,
                None
              )
            case BranchResult.Failure(reason) =>
              state.failBranch(
                branch.currentNode.getOrElse(branch.entryNode),
                signal.branchId,
                signal.forkId,
                reason,
                nodeAddress,
                None
              )
            case BranchResult.Timeout =>
              state.timeoutBranch(
                branch.currentNode.getOrElse(branch.entryNode),
                signal.branchId,
                signal.forkId,
                nodeAddress,
                None
              )

          // Check if any join is now ready
          checkJoinsAfterBranchUpdate(updatedState, signal.forkId)

    def onJoinReached(state: TraversalState, signal: TraversalSignal.JoinReached): F[TraversalResult] =
      dag.nodeOf(signal.joinNodeId) match
        case None =>
          errorResult(state, NodeNotFound(signal.traversalId, signal.joinNodeId)).pure[F]
        case Some(joinNode) =>
          joinNode.nodeType match
            case NodeType.Join(forkId, strategy, timeout) =>
              // Verify the fork scope exists
              state.forkScopes.get(forkId) match
                case None =>
                  errorResult(state, JoinWithoutFork(signal.traversalId, signal.joinNodeId, forkId)).pure[F]
                case Some(forkScope) =>
                  val stateAfterBranch =
                    state.branchStates.get(signal.branchId) match
                      case Some(branch) =>
                        state.completeBranch(
                          branch.currentNode.getOrElse(branch.entryNode),
                          signal.branchId,
                          forkId,
                          nodeAddress,
                          None
                        )
                      case None => state

                  // Get or create pending join
                  val pendingJoin = stateAfterBranch.pendingJoins.getOrElse(
                    signal.joinNodeId,
                    PendingJoin.create(
                      signal.joinNodeId,
                      forkId,
                      strategy,
                      forkScope.branches,
                      timeout.map(d => now.plusMillis(d.toMillis))
                    )
                  ).branchCompleted(signal.branchId)

                  val stateWithJoin = stateAfterBranch.registerJoinArrival(
                    signal.joinNodeId,
                    signal.branchId,
                    forkId,
                    pendingJoin,
                    nodeAddress,
                    None
                  )

                  checkJoinCompletion(stateWithJoin, signal.joinNodeId, pendingJoin)

            case _ =>
              errorResult(state, InvalidNodeState(signal.traversalId, signal.joinNodeId, "Join", "not Join")).pure[F]

    def onTimeout(state: TraversalState, signal: TraversalSignal.Timeout): F[TraversalResult] =
      signal.branchId match
        case Some(branchId) =>
          // Branch timeout
          state.branchStates.get(branchId) match
            case None =>
              errorResult(state, BranchNotFound(signal.traversalId, branchId)).pure[F]
            case Some(branch) =>
              val timedOut = state.timeoutBranch(
                signal.nodeId,
                branchId,
                branch.forkId,
                nodeAddress,
                None
              )
              // Check for OnTimeout edges
              dag.outgoingEdges(signal.nodeId, EdgeGuard.OnTimeout).headOption match
                case Some(edge) =>
                  val started = timedOut.start(edge.to, nodeAddress, None)
                  successResult(started, DispatchNode(signal.traversalId, edge.to)).pure[F]
                case None =>
                  checkJoinsAfterBranchUpdate(timedOut, branch.forkId)

        case None =>
          // Join timeout
          dag.nodeOf(signal.nodeId) match
            case Some(node) if node.isJoin =>
              node.nodeType match
                case NodeType.Join(forkId, _, _) =>
                  val pendingBranches = state.pendingJoins.get(signal.nodeId)
                    .map(_.branchesToCancel)
                    .getOrElse(Set.empty)

                  val timedOut = state.timeoutJoin(
                    signal.nodeId,
                    forkId,
                    pendingBranches,
                    nodeAddress,
                    None
                  )

                  // Check for OnTimeout edge from join
                  dag.outgoingEdges(signal.nodeId, EdgeGuard.OnTimeout).headOption match
                    case Some(edge) =>
                      val started = timedOut.start(edge.to, nodeAddress, None)
                      successResult(started, DispatchNode(signal.traversalId, edge.to)).pure[F]
                    case None =>
                      val timeoutToCancel = timedOut.traversalTimeoutId
                      val finalState = timedOut.clearTraversalTimeout
                      successResult(finalState, Fail(signal.traversalId, timeoutToCancel)).pure[F]
                case _ =>
                  errorResult(state, UnsupportedSignal(signal.traversalId)).pure[F]
            case _ =>
              errorResult(state, NodeNotFound(signal.traversalId, signal.nodeId)).pure[F]

    def onCancelBranches(state: TraversalState, signal: TraversalSignal.CancelBranches): TraversalResult =
      // Cancel all specified branches
      val canceledState = signal.branchIds.foldLeft(state) { (s, branchId) =>
        s.branchStates.get(branchId) match
          case Some(branch) if branch.isActive =>
            s.cancelBranch(
              branch.currentNode.getOrElse(branch.entryNode),
              branchId,
              signal.forkId,
              signal.reason,
              nodeAddress,
              None
            )
          case _ => s
      }
      noOpResult(canceledState)

    // -------------------------------------------------------------------------
    // Fork/Join Helper Functions
    // -------------------------------------------------------------------------

    def checkJoinsAfterBranchUpdate(state: TraversalState, forkId: ForkId): F[TraversalResult] =
      // Find pending join for this fork
      state.pendingJoins.find { case (_, pj) => pj.forkId == forkId } match
        case Some((joinNodeId, pendingJoin)) =>
          checkJoinCompletion(state, joinNodeId, pendingJoin)
        case None =>
          noOpResult(state).pure[F]

    def checkJoinCompletion(state: TraversalState, joinNodeId: NodeId, pendingJoin: PendingJoin): F[TraversalResult] =
      if pendingJoin.isSatisfied then
        // Join is complete - collect branches to cancel for OR joins
        val branchesToCancelIds = pendingJoin.branchesToCancel
        val branchesToCancelWithForkId: Set[(BranchId, ForkId)] =
          if branchesToCancelIds.nonEmpty && pendingJoin.strategy == JoinStrategy.Or then
            branchesToCancelIds.map(bid => (bid, pendingJoin.forkId))
          else
            Set.empty

        // Update state: cancel branches and complete join
        val stateWithCancels = if branchesToCancelIds.nonEmpty && pendingJoin.strategy == JoinStrategy.Or then
          branchesToCancelIds.foldLeft(state) { (s, branchId) =>
            s.branchStates.get(branchId) match
              case Some(branch) if branch.isActive =>
                s.cancelBranch(
                  branch.currentNode.getOrElse(branch.entryNode),
                  branchId,
                  pendingJoin.forkId,
                  "OR join completed",
                  nodeAddress,
                  None
                )
              case _ => s
          }
        else state

        // Before completing the join, look up the fork scope to find the parent branch context
        // (needed for nested forks where we must continue the parent branch after inner join completes)
        val forkScope = stateWithCancels.forkScopes.get(pendingJoin.forkId)
        val parentBranchContext = forkScope.flatMap(fs => stateWithCancels.branchForNode(fs.forkNodeId))

        // Complete the join
        val joinedState = stateWithCancels.completeJoin(
          joinNodeId,
          pendingJoin.forkId,
          pendingJoin.completedBranches,
          nodeAddress,
          None
        )

        // For nested forks, restore the parent branch context before transitioning
        // This ensures the next node continues in the outer fork's branch
        val stateForTransition = parentBranchContext match
          case Some(parentBranch) =>
            // Re-add the join node to the parent branch's node mapping
            // so that transitionToNextNode can find the branch context
            joinedState.copy(nodeToBranch = joinedState.nodeToBranch + (joinNodeId -> parentBranch.branchId))
          case None =>
            joinedState

        // Transition to next node after join, passing branches to cancel to the executor
        transitionToNextNode(
          stateForTransition,
          joinNodeId,
          EdgeGuard.OnSuccess,
          branchesToCancel = branchesToCancelWithForkId
        )

      else if pendingJoin.hasFailed then
        // Join failed - cancel remaining active branches to avoid wasted work
        val branchesToCancelIds = pendingJoin.branchesToCancel
        val stateWithCancels = branchesToCancelIds.foldLeft(state) { (s, branchId) =>
          s.branchStates.get(branchId) match
            case Some(branch) if branch.isActive =>
              s.cancelBranch(
                branch.currentNode.getOrElse(branch.entryNode),
                branchId,
                pendingJoin.forkId,
                "Join failed - canceling remaining branches",
                nodeAddress,
                None
              )
            case _ => s
        }

        // Transition via failure edge or fail traversal
        dag.outgoingEdges(joinNodeId, EdgeGuard.OnFailure).headOption match
          case Some(edge) =>
            val started = stateWithCancels.start(edge.to, nodeAddress, None)
            val branchesToCancelWithForkId = branchesToCancelIds.map(bid => (bid, pendingJoin.forkId))
            successResult(started, DispatchNode(stateWithCancels.traversalId, edge.to, branchesToCancelWithForkId)).pure[F]
          case None =>
            val timeoutToCancel = stateWithCancels.traversalTimeoutId
            val failed = stateWithCancels.fail(joinNodeId, nodeAddress, None, Some("Join failed: not enough branches completed")).clearTraversalTimeout
            successResult(failed, Fail(stateWithCancels.traversalId, timeoutToCancel)).pure[F]
      else
        // Join not yet complete - wait for more branches
        noOpResult(state).pure[F]

    // -------------------------------------------------------------------------
    // FSM Definition
    // -------------------------------------------------------------------------

    FSM[F, TraversalState, TraversalSignal, TraversalError, TraversalEffect]: (state, signal) =>
      signal match
        // Linear traversal signals
        case s: TraversalSignal.Begin       => Monad[F].pure(onBegin(state, s))
        case s: TraversalSignal.Resume      => Monad[F].pure(onResume(state, s))
        case s: TraversalSignal.Cancel      => Monad[F].pure(onCancel(state, s))
        case s: TraversalSignal.Retry       => Monad[F].pure(onRetry(state, s))
        case s: TraversalSignal.NodeStart   => Monad[F].pure(onNodeStart(state, s))
        case s: TraversalSignal.NodeSuccess => onNodeSuccess(state, s)
        case s: TraversalSignal.NodeFailure => Monad[F].pure(onNodeFailure(state, s))
        case s: TraversalSignal.Completed   => Monad[F].pure(onCompleted(state, s))
        case s: TraversalSignal.Failed      => Monad[F].pure(onFailed(state, s))
        // Fork/Join signals
        case s: TraversalSignal.ForkReached    => onForkReached(state, s)
        case s: TraversalSignal.BranchComplete => onBranchComplete(state, s)
        case s: TraversalSignal.JoinReached    => onJoinReached(state, s)
        case s: TraversalSignal.Timeout        => onTimeout(state, s)
        case s: TraversalSignal.CancelBranches => Monad[F].pure(onCancelBranches(state, s))
        // Traversal-level timeout
        case s: TraversalSignal.TraversalTimeout => Monad[F].pure(onTraversalTimeout(state, s))
