---
sidebar_position: 4
---

# Finite State Machine (FSM)

The TraversalFSM is the pure, deterministic core of Waygrid Router's traversal system. It processes signals, updates state, and produces effects without any side effects.

## Design Philosophy

### Pure Functions

The FSM is designed as a pure function:

```scala
Signal + State → (NewState, Either[Error, Effect])
```

**Benefits:**
- **Testability**: No mocks needed, just assert on outputs
- **Reproducibility**: Same inputs always produce same outputs
- **Replay**: Reconstruct any state from event history
- **Concurrency**: No shared mutable state

### Time as Parameter

Time is passed explicitly to maintain purity:

```scala
def stateless[F[+_]: Monad](
  dag: Dag,
  nodeAddress: NodeAddress,
  now: Instant  // Explicit time parameter
): FSM[F, TraversalState, TraversalSignal, TraversalError, TraversalEffect]
```

This allows:
- Deterministic testing with controlled time
- Replay of historical events
- Time-travel debugging

## FSM Structure

```scala
object TraversalFSM:
  private type TraversalResult = Result[TraversalState, TraversalError, TraversalEffect]

  def stateless[F[+_]: Monad](...): FSM[...] =

    // Helper functions
    def successResult(...): TraversalResult
    def errorResult(...): TraversalResult
    def transitionToNextNode(...): F[TraversalResult]

    // Signal handlers
    def onBegin(...): TraversalResult
    def onNodeSuccess(...): F[TraversalResult]
    def onForkReached(...): F[TraversalResult]
    // ... more handlers

    // FSM definition
    FSM[F, TraversalState, TraversalSignal, TraversalError, TraversalEffect]:
      (state, signal) => signal match
        case s: Begin       => Monad[F].pure(onBegin(state, s))
        case s: NodeSuccess => onNodeSuccess(state, s)
        // ... pattern matching on all signals
```

## Signal Processing Flow

### 1. Begin Signal

Starts a new traversal:

```scala
def onBegin(state: TraversalState, signal: Begin): TraversalResult =
  if dag.nodes.isEmpty then
    errorResult(state, EmptyDag(signal.traversalId))
  else if state.hasActiveWork || state.hasProgress then
    errorResult(state, AlreadyInProgress(signal.traversalId))
  else
    // Find entry node and start
    dag.nodeOf(entryId) match
      case None => errorResult(state, MissingEntryNode(...))
      case Some(entry) =>
        entry.deliveryStrategy match
          case Immediate =>
            val started = state.start(entry.id, nodeAddress, None)
            successResult(started, DispatchNode(signal.traversalId, entry.id))
          case ScheduleAfter(delay) =>
            scheduleStartOrStartNow(state, entry.id, now.plusMillis(delay.toMillis))
```

### 2. NodeSuccess Signal

Handles successful node completion:

```scala
def onNodeSuccess(state: TraversalState, signal: NodeSuccess): F[TraversalResult] =
  dag.nodeOf(signal.nodeId) match
    case None =>
      errorResult(state, NodeNotFound(...)).pure[F]
    case Some(node) =>
      if state.isCompleted(node.id) then
        noOpResult(state).pure[F]  // Idempotent
      else
        val succeeded = state.successNode(node.id, nodeAddress, None)
        if succeeded.isTraversalComplete then
          successResult(succeeded.complete(...), Complete(...)).pure[F]
        else
          transitionToNextNode(succeeded, node.id, EdgeGuard.OnSuccess, signal.output)
```

### 3. NodeFailure Signal

Handles node failures with retry logic:

```scala
def onNodeFailure(state: TraversalState, signal: NodeFailure): TraversalResult =
  dag.nodeOf(signal.nodeId) match
    case Some(node) =>
      val failed = state.failNode(node.id, nodeAddress, None, signal.reason)
      val nextAttempt = failed.retryCount(node.id) + 1

      Backoff[RetryPolicy].nextDelay(node.retryPolicy, nextAttempt, None) match
        case Some(delay) =>
          // Schedule retry
          val scheduledAt = now.plusMillis(delay.toMillis)
          successResult(
            failed.schedule(node.id, nodeAddress, scheduledAt, None),
            ScheduleRetry(signal.traversalId, scheduledAt, RetryAttempt(nextAttempt), node.id)
          )
        case None =>
          // No more retries - check failure edge or fail traversal
          dag.nextNodes(node.id, EdgeGuard.OnFailure).headOption match
            case Some(nextNode) =>
              // Transition via failure edge
              successResult(failed.start(nextNode.id, ...), DispatchNode(...))
            case None =>
              // Traversal fails
              successResult(failed.fail(...), Fail(...))
```

## Node Transition Logic

The `transitionToNextNode` function handles routing after node completion:

```scala
def transitionToNextNode(
  state: TraversalState,
  fromNodeId: NodeId,
  guard: EdgeGuard,
  output: Option[Json] = None,
  branchesToCancel: Set[(BranchId, ForkId)] = Set.empty
): F[TraversalResult] =

  // Find next nodes based on guard
  val nextNodes = guard match
    case OnSuccess =>
      // Check conditional edges first
      val matched = dag.outgoingEdges(fromNodeId).collect {
        case e if e.guard match
          case Conditional(cond) => Condition.eval(cond, output)
          case _ => false
        => dag.nodes.get(e.to)
      }.flatten
      if matched.nonEmpty then matched
      else dag.nextNodes(fromNodeId, OnSuccess)
    case other =>
      dag.nextNodes(fromNodeId, other)

  nextNodes match
    case Nil =>
      // Terminal - complete or fail based on guard
      guard match
        case OnSuccess | Always | OnAny =>
          successResult(state.complete(...), Complete(...)).pure[F]
        case OnFailure | OnTimeout =>
          successResult(state.fail(...), Fail(...)).pure[F]

    case head :: Nil =>
      // Single successor
      if head.isJoin then
        // Route to join handling
        onJoinReached(state, JoinReached(state.traversalId, head.id, branchId))
      else if head.isFork then
        // Route to fork handling
        val stateWithBranchAdvanced = ...  // Preserve parent context
        onForkReached(stateWithBranchAdvanced, ForkReached(state.traversalId, head.id))
      else
        // Standard node - dispatch or schedule
        head.deliveryStrategy match
          case Immediate =>
            successResult(state.start(head.id, ...), DispatchNode(...)).pure[F]
          case ScheduleAfter(delay) =>
            scheduleStartOrStartNow(state, head.id, now.plusMillis(delay.toMillis)).pure[F]

    case many =>
      // Multiple successors - must be from Fork
      dag.nodeOf(fromNodeId) match
        case Some(node) if node.isFork =>
          onForkReached(state, ForkReached(state.traversalId, fromNodeId))
        case _ =>
          errorResult(state, InvalidNodeState(...)).pure[F]
```

## Fork/Join Handlers

### ForkReached Handler

```scala
def onForkReached(state: TraversalState, signal: ForkReached): F[TraversalResult] =
  dag.nodeOf(signal.forkNodeId) match
    case Some(forkNode) =>
      forkNode.nodeType match
        case NodeType.Fork(forkId) =>
          // Get outgoing edges for branches
          val outgoingEdges = dag.outgoingEdges(signal.forkNodeId, OnSuccess)

          // Create branch IDs for each edge
          outgoingEdges
            .traverse(edge => BranchId.next[F].map(_ -> edge.to))
            .map(_.toMap)
            .map { branchEntries =>
              // Find parent scope for nesting
              val parentScope = state.branchForNode(signal.forkNodeId).map(_.forkId)

              // Initialize fork and branches
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

              // Start all branches
              val startedBranches = branchEntries.foldLeft(forkedState) {
                case (s, (branchId, nodeId)) =>
                  s.start(nodeId, nodeAddress, None)
                   .startBranch(nodeId, branchId, forkId, nodeAddress, None)
              }

              // Return effect to dispatch all branch entry nodes
              successResult(
                startedBranches,
                DispatchNodes(signal.traversalId, branchEntries.toList, timeout, joinNodeIdOpt)
              )
            }
```

### JoinReached Handler

```scala
def onJoinReached(state: TraversalState, signal: JoinReached): F[TraversalResult] =
  dag.nodeOf(signal.joinNodeId) match
    case Some(joinNode) =>
      joinNode.nodeType match
        case NodeType.Join(forkId, strategy, timeout) =>
          // Verify fork scope exists
          state.forkScopes.get(forkId) match
            case None =>
              errorResult(state, JoinWithoutFork(...)).pure[F]
            case Some(forkScope) =>
              // Mark branch as complete
              val stateAfterBranch = state.completeBranch(...)

              // Get or create pending join
              val pendingJoin = stateAfterBranch.pendingJoins
                .getOrElse(signal.joinNodeId,
                  PendingJoin.create(signal.joinNodeId, forkId, strategy, forkScope.branches, timeout)
                )
                .branchCompleted(signal.branchId)

              // Register arrival and check completion
              val stateWithJoin = stateAfterBranch.registerJoinArrival(...)
              checkJoinCompletion(stateWithJoin, signal.joinNodeId, pendingJoin)
```

### Join Completion Check

```scala
def checkJoinCompletion(
  state: TraversalState,
  joinNodeId: NodeId,
  pendingJoin: PendingJoin
): F[TraversalResult] =

  if pendingJoin.isSatisfied then
    // Join complete - cancel remaining branches for OR join
    val branchesToCancel =
      if pendingJoin.strategy == JoinStrategy.Or
      then pendingJoin.branchesToCancel
      else Set.empty

    val stateWithCancels = branchesToCancel.foldLeft(state) { (s, branchId) =>
      s.cancelBranch(...)
    }

    // Restore parent branch context for nested forks
    val parentBranchContext = forkScope.flatMap(fs =>
      stateWithCancels.branchForNode(fs.forkNodeId)
    )

    // Complete join and clean up
    val joinedState = stateWithCancels.completeJoin(...)

    // Restore parent context for proper nesting
    val stateForTransition = parentBranchContext match
      case Some(parentBranch) =>
        joinedState.copy(nodeToBranch = joinedState.nodeToBranch + (joinNodeId -> parentBranch.branchId))
      case None =>
        joinedState

    // Continue to next node
    transitionToNextNode(stateForTransition, joinNodeId, OnSuccess, branchesToCancel = branchesToCancelWithForkId)

  else if pendingJoin.hasFailed then
    // Join failed - check for failure edge or fail traversal
    dag.outgoingEdges(joinNodeId, OnFailure).headOption match
      case Some(edge) =>
        successResult(state.start(edge.to, ...), DispatchNode(...)).pure[F]
      case None =>
        successResult(state.fail(...), Fail(...)).pure[F]

  else
    // Not complete yet - wait for more branches
    noOpResult(state).pure[F]
```

## Error Handling

The FSM returns errors as values, not exceptions:

```scala
sealed trait TraversalError extends Throwable:
  val traversalId: TraversalId
  def errorMessage: String

// Examples
case class EmptyDag(traversalId: TraversalId) extends TraversalError
case class NodeNotFound(traversalId: TraversalId, nodeId: NodeId) extends TraversalError
case class JoinWithoutFork(traversalId: TraversalId, joinNodeId: NodeId, forkId: ForkId) extends TraversalError
```

All errors are handled through the `Result` type:

```scala
case class Result[S, E, A](state: S, result: Either[E, A])
```

## Idempotency

The FSM is designed to handle duplicate signals gracefully:

```scala
// NodeSuccess is idempotent
if state.isCompleted(node.id) then
  noOpResult(state).pure[F]  // Already done, no-op

// Same for other signals
if state.isStarted(signal.nodeId) then
  noOpResult(state)  // Already started
```

## Testing the FSM

Because the FSM is pure, testing is straightforward:

```scala
test("linear traversal completes") {
  val dag = Dag(...)
  val state = TraversalState.initial(traversalId, nodeAddress, dag)
  val fsm = TraversalFSM.stateless[IO](dag, nodeAddress, Instant.now())

  for
    // Begin
    r1 <- fsm.run(state, TraversalSignal.Begin(traversalId))
    _ = expect(r1.result.isRight)
    _ = expect(r1.result.toOption.get.isInstanceOf[DispatchNode])

    // Node success
    r2 <- fsm.run(r1.state, TraversalSignal.NodeSuccess(traversalId, nodeA))
    _ = expect(r2.result.toOption.get.isInstanceOf[DispatchNode])

    // Final node success -> Complete
    r3 <- fsm.run(r2.state, TraversalSignal.NodeSuccess(traversalId, nodeB))
    _ = expect(r3.result.toOption.get.isInstanceOf[Complete])
  yield success
}
```

## State Machine Diagram

```
                    ┌──────────────────────────────────────────────────┐
                    │                    FSM                           │
                    │                                                  │
   ┌─────────┐      │    ┌───────────────────────────────────────┐    │      ┌──────────┐
   │ Signal  │─────►│    │          Signal Handlers              │    │─────►│  Effect  │
   │ (Input) │      │    │                                       │    │      │ (Output) │
   └─────────┘      │    │  onBegin  │ onNodeSuccess │ onFork    │    │      └──────────┘
                    │    │  onRetry  │ onNodeFailure │ onJoin    │    │
                    │    │  onCancel │ onTimeout     │ ...       │    │
                    │    └───────────────────────────────────────┘    │
                    │                        │                         │
                    │                        ▼                         │
                    │    ┌───────────────────────────────────────┐    │
                    │    │         TraversalState                 │    │
                    │    │  (updated immutably)                   │    │
                    │    └───────────────────────────────────────┘    │
                    │                                                  │
                    └──────────────────────────────────────────────────┘
```
