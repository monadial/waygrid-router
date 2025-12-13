package com.monadial.waygrid.common.domain.model.traversal.fsm

import cats.Applicative
import cats.implicits.*
import com.monadial.waygrid.common.domain.model.fsm.FSM
import com.monadial.waygrid.common.domain.model.fsm.Value.Result
import com.monadial.waygrid.common.domain.model.resiliency.{ Backoff, RetryPolicy }
import com.monadial.waygrid.common.domain.model.routing.Value.DeliveryStrategy
import com.monadial.waygrid.common.domain.model.traversal.dag.{ Dag, JoinStrategy, NodeType }
import com.monadial.waygrid.common.domain.model.traversal.dag.Value.{ BranchId, EdgeGuard, ForkId, NodeId }
import com.monadial.waygrid.common.domain.model.traversal.fsm.TraversalEffect.*
import com.monadial.waygrid.common.domain.model.traversal.state.{ BranchResult, PendingJoin, TraversalState }
import com.monadial.waygrid.common.domain.model.traversal.state.Value.RetryAttempt
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
 */
object TraversalFSM:

  private type TraversalResult = Result[TraversalState, TraversalError, TraversalEffect]

  /**
   * Creates a stateless FSM for DAG traversal.
   *
   * @param dag         The DAG to traverse
   * @param nodeAddress The address of this node (actor)
   * @return FSM that processes TraversalSignals and produces TraversalEffects
   */
  def stateless[F[+_]: Applicative](dag: Dag, nodeAddress: NodeAddress): FSM[F, TraversalState, TraversalSignal, TraversalError, TraversalEffect] =

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
      if scheduleAt.isAfter(Instant.now()) then
        successResult(
          state.schedule(nodeId, nodeAddress, scheduleAt, None),
          Schedule(state.traversalId, scheduleAt, nodeId)
        )
      else
        successResult(
          state.start(nodeId, nodeAddress, None),
          DispatchNode(state.traversalId, nodeId)
        )

    def transitionToNextNode(state: TraversalState, fromNodeId: NodeId, guard: EdgeGuard): TraversalResult =
      dag.nextNodes(fromNodeId, guard).headOption match
        case None =>
          // No more nodes - traversal is complete or failed based on guard
          guard match
            case EdgeGuard.OnSuccess =>
              val completed = state.complete(fromNodeId, nodeAddress, None)
              successResult(completed, Complete(state.traversalId))
            case EdgeGuard.OnFailure =>
              val failed = state.fail(fromNodeId, nodeAddress, None, Some("No failure edge available"))
              successResult(failed, Fail(state.traversalId))

        case Some(nextNode) =>
          nextNode.deliveryStrategy match
            case DeliveryStrategy.Immediate =>
              val nextState = state.start(nextNode.id, nodeAddress, None)
              successResult(nextState, DispatchNode(state.traversalId, nextNode.id))
            case DeliveryStrategy.ScheduleAfter(delay) =>
              scheduleStartOrStartNow(state, nextNode.id, Instant.now().plusMillis(delay.toMillis))
            case DeliveryStrategy.ScheduleAt(time) =>
              scheduleStartOrStartNow(state, nextNode.id, time)

    // -------------------------------------------------------------------------
    // Signal Handlers
    // -------------------------------------------------------------------------

    def onBegin(state: TraversalState, signal: TraversalSignal.Begin): TraversalResult =
      if dag.nodes.isEmpty then
        errorResult(state, EmptyDag(signal.traversalId))
      else if state.hasActiveWork || state.hasProgress then
        errorResult(state, AlreadyInProgress(signal.traversalId))
      else
        dag.entryNode match
          case None =>
            errorResult(state, MissingEntryNode(signal.traversalId))
          case Some(entry) =>
            entry.deliveryStrategy match
              case DeliveryStrategy.Immediate =>
                successResult(
                  state.start(entry.id, nodeAddress, None),
                  DispatchNode(signal.traversalId, entry.id)
                )
              case DeliveryStrategy.ScheduleAfter(delay) =>
                scheduleStartOrStartNow(state, entry.id, Instant.now().plusMillis(delay.toMillis))
              case DeliveryStrategy.ScheduleAt(time) =>
                scheduleStartOrStartNow(state, entry.id, time)

    def onResume(state: TraversalState, signal: TraversalSignal.Resume): TraversalResult =
      // Resume is called when a scheduled node timer fires
      dag.nodeOf(signal.nodeId) match
        case None =>
          errorResult(state, NodeNotFound(signal.traversalId, signal.nodeId))
        case Some(node) =>
          if state.isFinished(node.id) then
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
      state.current match
        case None =>
          // No active node, just mark as canceled
          val canceled = state.cancel(dag.entry, nodeAddress, None)
          successResult(canceled, Cancel(signal.traversalId))
        case Some(nodeId) =>
          val canceled = state.cancel(nodeId, nodeAddress, None)
          successResult(canceled, Cancel(signal.traversalId))

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

    def onNodeSuccess(state: TraversalState, signal: TraversalSignal.NodeSuccess): TraversalResult =
      dag.nodeOf(signal.nodeId) match
        case None =>
          errorResult(state, NodeNotFound(signal.traversalId, signal.nodeId))
        case Some(node) =>
          if state.isCompleted(node.id) then
            // Already completed, no-op
            noOpResult(state)
          else if !state.isStarted(node.id) && !state.current.contains(node.id) then
            // Node wasn't being processed
            errorResult(
              state,
              InvalidNodeState(signal.traversalId, node.id, "started", "not started")
            )
          else
            // Mark success and transition to next node
            val succeeded = state.successNode(node.id, nodeAddress, None)
            if succeeded.isTraversalComplete then
              val completed = succeeded.complete(node.id, nodeAddress, None)
              successResult(completed, Complete(signal.traversalId))
            else
              transitionToNextNode(succeeded, node.id, EdgeGuard.OnSuccess)

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
                val scheduledAt = Instant.now().plusMillis(delay.toMillis)
                successResult(
                  failed,
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
                        scheduleStartOrStartNow(failed, nextNode.id, Instant.now().plusMillis(d.toMillis))
                      case DeliveryStrategy.ScheduleAt(time) =>
                        scheduleStartOrStartNow(failed, nextNode.id, time)
                  case None =>
                    // No failure edge, traversal fails
                    val finalState = failed.fail(node.id, nodeAddress, None, signal.reason)
                    successResult(finalState, Fail(signal.traversalId))

    def onCompleted(state: TraversalState, signal: TraversalSignal.Completed): TraversalResult =
      // External completion signal - verify state and mark complete
      if state.isTraversalComplete then
        state.current.orElse(state.completed.lastOption) match
          case Some(nodeId) =>
            val completed = state.complete(nodeId, nodeAddress, None)
            successResult(completed, Complete(signal.traversalId))
          case None =>
            successResult(state, Complete(signal.traversalId))
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
      val failed = state.fail(nodeId, nodeAddress, None, signal.reason)
      successResult(failed, Fail(signal.traversalId))

    // -------------------------------------------------------------------------
    // Fork/Join Signal Handlers
    // -------------------------------------------------------------------------

    def onForkReached(state: TraversalState, signal: TraversalSignal.ForkReached): TraversalResult =
      dag.nodeOf(signal.forkNodeId) match
        case None =>
          errorResult(state, NodeNotFound(signal.traversalId, signal.forkNodeId))
        case Some(forkNode) =>
          forkNode.nodeType match
            case NodeType.Fork(forkId) =>
              // Get all outgoing edges from the fork node
              val outgoingEdges = dag.outgoingEdges(signal.forkNodeId)
              if outgoingEdges.isEmpty then
                // No branches - just complete the fork
                val completed = state.successNode(signal.forkNodeId, nodeAddress, None)
                if completed.isTraversalComplete then
                  successResult(completed.complete(signal.forkNodeId, nodeAddress, None), Complete(signal.traversalId))
                else
                  noOpResult(completed)
              else
                // Create branches for each outgoing edge
                val branchEntries: Map[BranchId, NodeId] = outgoingEdges.map { edge =>
                  BranchId.fromStringUnsafe[cats.Id](java.util.UUID.randomUUID().toString) -> edge.to
                }.toMap

                // Determine parent fork scope (for nested forks)
                val parentScope = state.branchForNode(signal.forkNodeId).map(_.forkId)

                // Get timeout from join node if it exists
                val timeout = dag.nodes.values
                  .find(n => n.nodeType match
                    case NodeType.Join(id, _, t) if id == forkId => true
                    case _ => false
                  )
                  .flatMap(_.nodeType match
                    case NodeType.Join(_, _, t) => t.map(d => Instant.now().plusMillis(d.toMillis))
                    case _ => None
                  )

                // Start the fork
                val forkedState = state.startFork(
                  signal.forkNodeId,
                  forkId,
                  branchEntries,
                  parentScope,
                  timeout,
                  nodeAddress,
                  None
                )

                // Dispatch all branch entry nodes
                val nodesToDispatch = branchEntries.toList.map { case (branchId, nodeId) =>
                  (nodeId, branchId)
                }

                successResult(forkedState, DispatchNodes(signal.traversalId, nodesToDispatch))

            case _ =>
              // Not a fork node
              errorResult(
                state,
                InvalidNodeState(signal.traversalId, signal.forkNodeId, "Fork", "not Fork")
              )

    def onBranchComplete(state: TraversalState, signal: TraversalSignal.BranchComplete): TraversalResult =
      state.branchStates.get(signal.branchId) match
        case None =>
          errorResult(state, BranchNotFound(signal.traversalId, signal.branchId))
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

    def onJoinReached(state: TraversalState, signal: TraversalSignal.JoinReached): TraversalResult =
      dag.nodeOf(signal.joinNodeId) match
        case None =>
          errorResult(state, NodeNotFound(signal.traversalId, signal.joinNodeId))
        case Some(joinNode) =>
          joinNode.nodeType match
            case NodeType.Join(forkId, strategy, timeout) =>
              // Verify the fork scope exists
              state.forkScopes.get(forkId) match
                case None =>
                  errorResult(state, JoinWithoutFork(signal.traversalId, signal.joinNodeId, forkId))
                case Some(forkScope) =>
                  // Get or create pending join
                  val pendingJoin = state.pendingJoins.getOrElse(
                    signal.joinNodeId,
                    PendingJoin.create(
                      signal.joinNodeId,
                      forkId,
                      strategy,
                      forkScope.branches,
                      timeout.map(d => Instant.now().plusMillis(d.toMillis))
                    )
                  ).branchCompleted(signal.branchId)

                  val stateWithJoin = state.registerJoinArrival(
                    signal.joinNodeId,
                    signal.branchId,
                    forkId,
                    pendingJoin,
                    nodeAddress,
                    None
                  )

                  checkJoinCompletion(stateWithJoin, signal.joinNodeId, pendingJoin)

            case _ =>
              errorResult(
                state,
                InvalidNodeState(signal.traversalId, signal.joinNodeId, "Join", "not Join")
              )

    def onTimeout(state: TraversalState, signal: TraversalSignal.Timeout): TraversalResult =
      signal.branchId match
        case Some(branchId) =>
          // Branch timeout
          state.branchStates.get(branchId) match
            case None =>
              errorResult(state, BranchNotFound(signal.traversalId, branchId))
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
                  successResult(started, DispatchNode(signal.traversalId, edge.to))
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
                      successResult(started, DispatchNode(signal.traversalId, edge.to))
                    case None =>
                      successResult(timedOut, Fail(signal.traversalId))
                case _ =>
                  errorResult(state, UnsupportedSignal(signal.traversalId))
            case _ =>
              errorResult(state, NodeNotFound(signal.traversalId, signal.nodeId))

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

    def checkJoinsAfterBranchUpdate(state: TraversalState, forkId: ForkId): TraversalResult =
      // Find pending join for this fork
      state.pendingJoins.find { case (_, pj) => pj.forkId == forkId } match
        case Some((joinNodeId, pendingJoin)) =>
          checkJoinCompletion(state, joinNodeId, pendingJoin)
        case None =>
          noOpResult(state)

    def checkJoinCompletion(state: TraversalState, joinNodeId: NodeId, pendingJoin: PendingJoin): TraversalResult =
      if pendingJoin.isSatisfied then
        // Join is complete - cancel remaining branches for OR joins
        val branchesToCancel = pendingJoin.branchesToCancel
        val stateWithCancels = if branchesToCancel.nonEmpty && pendingJoin.strategy == JoinStrategy.Or then
          branchesToCancel.foldLeft(state) { (s, branchId) =>
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

        // Complete the join
        val joinedState = stateWithCancels.completeJoin(
          joinNodeId,
          pendingJoin.forkId,
          pendingJoin.completedBranches,
          nodeAddress,
          None
        )

        // Transition to next node after join
        transitionToNextNode(joinedState, joinNodeId, EdgeGuard.OnSuccess)

      else if pendingJoin.hasFailed then
        // Join failed - transition via failure edge or fail traversal
        dag.outgoingEdges(joinNodeId, EdgeGuard.OnFailure).headOption match
          case Some(edge) =>
            val started = state.start(edge.to, nodeAddress, None)
            successResult(started, DispatchNode(state.traversalId, edge.to))
          case None =>
            val failed = state.fail(joinNodeId, nodeAddress, None, Some("Join failed: not enough branches completed"))
            successResult(failed, Fail(state.traversalId))
      else
        // Join not yet complete - wait for more branches
        noOpResult(state)

    // -------------------------------------------------------------------------
    // FSM Definition
    // -------------------------------------------------------------------------

    FSM[F, TraversalState, TraversalSignal, TraversalError, TraversalEffect]: (state, signal) =>
      Applicative[F].pure:
        signal match
          // Linear traversal signals
          case s: TraversalSignal.Begin       => onBegin(state, s)
          case s: TraversalSignal.Resume      => onResume(state, s)
          case s: TraversalSignal.Cancel      => onCancel(state, s)
          case s: TraversalSignal.Retry       => onRetry(state, s)
          case s: TraversalSignal.NodeStart   => onNodeStart(state, s)
          case s: TraversalSignal.NodeSuccess => onNodeSuccess(state, s)
          case s: TraversalSignal.NodeFailure => onNodeFailure(state, s)
          case s: TraversalSignal.Completed   => onCompleted(state, s)
          case s: TraversalSignal.Failed      => onFailed(state, s)
          // Fork/Join signals
          case s: TraversalSignal.ForkReached    => onForkReached(state, s)
          case s: TraversalSignal.BranchComplete => onBranchComplete(state, s)
          case s: TraversalSignal.JoinReached    => onJoinReached(state, s)
          case s: TraversalSignal.Timeout        => onTimeout(state, s)
          case s: TraversalSignal.CancelBranches => onCancelBranches(state, s)
