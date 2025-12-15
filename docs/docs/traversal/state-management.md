---
sidebar_position: 6
---

# State Management & Event Sourcing

TraversalState is the immutable data structure that tracks all execution progress. It uses event sourcing for full auditability and replay capability.

## TraversalState Structure

```scala
final case class TraversalState(
  traversalId: TraversalId,
  active: Set[NodeId],           // Currently executing nodes
  completed: Set[NodeId],        // Successfully finished nodes
  failed: Set[NodeId],           // Failed nodes
  retries: Map[NodeId, RetryAttempt],
  vectorClock: VectorClock,      // Causal ordering
  history: Vector[StateEvent],   // Event log
  remainingNodes: RemainingNodes,

  // Fork/Join specific
  forkScopes: Map[ForkId, ForkScope],
  branchStates: Map[BranchId, BranchState],
  pendingJoins: Map[NodeId, PendingJoin],
  nodeToBranch: Map[NodeId, BranchId],

  // Timeout tracking
  traversalTimeoutId: Option[String],
  stateVersion: StateVersion
)
```

## State Transitions

All state changes are pure functions that return new state:

```scala
// Starting a node
def start(
  node: NodeId,
  actor: NodeAddress,
  foreignVectorClock: Option[VectorClock]
): TraversalState =
  val vc = advanceClock(actor, foreignVectorClock)
  val event = TraversalStarted(node, actor, vc)
  copy(active = active + node).record(event)
```

### Linear Transitions

| Method | Description | State Changes |
|--------|-------------|---------------|
| `start` | Begin node execution | `active += node` |
| `schedule` | Schedule for later | Records event only |
| `resume` | Resume scheduled node | `active += node` |
| `successNode` | Mark node successful | `active -= node`, `completed += node` |
| `failNode` | Mark node failed | `active -= node`, `failed += node` |
| `retryNode` | Increment retry count | `retries.updated(node, ...)` |
| `complete` | Complete traversal | `active = Set.empty`, `remainingNodes = 0` |
| `fail` | Fail traversal | Records failure event |
| `cancel` | Cancel traversal | `active = Set.empty` |

### Fork/Join Transitions

| Method | Description | State Changes |
|--------|-------------|---------------|
| `startFork` | Initialize fork scope | `forkScopes += scope`, `branchStates += ...` |
| `startBranch` | Start a branch | `active += node`, `nodeToBranch += ...` |
| `advanceBranch` | Move branch to next node | Updates `nodeToBranch` |
| `completeBranch` | Mark branch complete | `active -= node`, updates `pendingJoins` |
| `failBranch` | Mark branch failed | `active -= node`, updates `pendingJoins` |
| `cancelBranch` | Cancel a branch | `active -= node` |
| `timeoutBranch` | Branch timeout | Similar to fail |
| `registerJoinArrival` | Branch reaches join | `pendingJoins += ...` |
| `completeJoin` | Complete join | Cleans up fork scope, branches |
| `timeoutJoin` | Join timeout | `failed += joinNode` |

## Event Sourcing

Every state change produces an event that's appended to the history:

```scala
sealed trait StateEvent:
  def node: NodeId
  def actor: NodeAddress
  def vectorClock: VectorClock
```

### Event Types

```scala
// Linear Events
case class TraversalStarted(node, actor, vectorClock)
case class TraversalScheduled(node, actor, scheduledAt, vectorClock)
case class TraversalResumed(node, actor, vectorClock)
case class NodeTraversalSucceeded(node, actor, vectorClock)
case class NodeTraversalFailed(node, actor, vectorClock, reason)
case class NodeTraversalRetried(node, actor, attempt, vectorClock)
case class TraversalCompleted(node, actor, vectorClock)
case class TraversalFailed(node, actor, vectorClock, reason)
case class TraversalCanceled(node, actor, vectorClock)

// Fork/Join Events
case class ForkStarted(forkNode, forkId, branches, actor, vectorClock)
case class BranchStarted(node, branchId, forkId, actor, vectorClock)
case class BranchAdvanced(node, branchId, forkId, actor, vectorClock)
case class BranchCompleted(node, branchId, forkId, result, actor, vectorClock)
case class BranchCanceled(node, branchId, forkId, reason, actor, vectorClock)
case class BranchTimedOut(node, branchId, forkId, actor, vectorClock)
case class JoinReached(joinNode, branchId, forkId, actor, vectorClock)
case class JoinCompleted(joinNode, forkId, completedBranches, actor, vectorClock)
case class JoinTimedOut(joinNode, forkId, pendingBranches, actor, vectorClock)

// Timeout Events
case class TraversalTimeoutScheduled(node, timeoutId, deadline, actor, vectorClock)
case class TraversalTimedOut(node, activeNodes, activeBranches, actor, vectorClock)
```

### Recording Events

```scala
private def record(event: StateEvent): TraversalState =
  copy(
    history = history :+ event,
    vectorClock = event.vectorClock
  )
```

### Replaying State

State can be reconstructed by replaying events:

```scala
def replay(events: Vector[StateEvent], dag: Dag): TraversalState =
  events.foldLeft(TraversalState.initial(...)): (state, event) =>
    event match
      case TraversalStarted(node, _, _) =>
        state.copy(active = state.active + node, history = state.history :+ event)
      case NodeTraversalSucceeded(node, _, _) =>
        state.copy(
          active = state.active - node,
          completed = state.completed + node,
          history = state.history :+ event
        )
      // ... handle all event types
```

## Vector Clocks

Vector clocks provide causal ordering in distributed systems:

```scala
final case class VectorClock(entries: Map[NodeAddress, Long]):
  def tick(actor: NodeAddress): VectorClock =
    copy(entries = entries.updated(actor, entries.getOrElse(actor, 0L) + 1))

  def merge(other: VectorClock): VectorClock =
    VectorClock(
      (entries.keySet ++ other.entries.keySet).map { k =>
        k -> math.max(entries.getOrElse(k, 0L), other.entries.getOrElse(k, 0L))
      }.toMap
    )
```

### Usage in State

```scala
private def advanceClock(
  actor: NodeAddress,
  foreignVectorClock: Option[VectorClock]
): VectorClock =
  foreignVectorClock
    .fold(vectorClock)(vc => vectorClock.merge(vc))
    .tick(actor)
```

### Benefits

1. **Causality tracking**: Know which events happened before others
2. **Conflict detection**: Identify concurrent modifications
3. **Distributed replay**: Merge histories from multiple actors

## Fork Scope Tracking

```scala
case class ForkScope(
  forkId: ForkId,
  forkNodeId: NodeId,
  branches: Set[BranchId],
  parentScope: Option[ForkId],  // For nested forks
  startedAt: Instant,
  timeout: Option[Instant]
)
```

Tracks active forks and their branches.

## Branch State

```scala
case class BranchState(
  branchId: BranchId,
  forkId: ForkId,
  entryNode: NodeId,
  currentNode: Option[NodeId],
  status: BranchStatus,
  startedAt: Option[Instant],
  completedAt: Option[Instant]
):
  def isActive: Boolean = status == BranchStatus.Active
  def start(node: NodeId): BranchState
  def advanceTo(node: NodeId): BranchState
  def complete(): BranchState
  def fail(reason: String): BranchState
  def cancel: BranchState
  def timeout: BranchState
```

## Pending Join

```scala
case class PendingJoin(
  joinNodeId: NodeId,
  forkId: ForkId,
  strategy: JoinStrategy,
  totalBranches: Set[BranchId],
  completedBranches: Set[BranchId],
  failedBranches: Set[BranchId],
  canceledBranches: Set[BranchId],
  timeout: Option[Instant]
):
  def isSatisfied: Boolean = strategy match
    case JoinStrategy.And =>
      completedBranches == totalBranches
    case JoinStrategy.Or =>
      completedBranches.nonEmpty
    case JoinStrategy.Quorum(n) =>
      completedBranches.size >= n

  def hasFailed: Boolean = strategy match
    case JoinStrategy.And =>
      failedBranches.nonEmpty
    case JoinStrategy.Or =>
      (failedBranches ++ canceledBranches) == totalBranches
    case JoinStrategy.Quorum(n) =>
      totalBranches.size - failedBranches.size - canceledBranches.size < n

  def branchesToCancel: Set[BranchId] =
    totalBranches -- completedBranches -- failedBranches -- canceledBranches
```

## Node-to-Branch Index

O(1) lookup of branch context for any node:

```scala
nodeToBranch: Map[NodeId, BranchId]

// Used by:
def branchForNode(nodeId: NodeId): Option[BranchState] =
  nodeToBranch.get(nodeId).flatMap(branchStates.get)
```

Updated on:
- `startBranch`: Add entry
- `advanceBranch`: Update entry
- `completeBranch`/`failBranch`/`cancelBranch`: Remove entry
- `completeJoin`: Remove all entries for fork's branches

## State Queries

```scala
extension (state: TraversalState)
  // Progress queries
  def hasProgress: Boolean = completed.nonEmpty || failed.nonEmpty
  def hasActiveWork: Boolean = active.nonEmpty
  def hasFailures: Boolean = failed.nonEmpty
  def isTraversalComplete: Boolean = active.isEmpty && remainingNodes.unwrap <= 0

  // Node queries
  def isStarted(node: NodeId): Boolean = active.contains(node)
  def isCompleted(node: NodeId): Boolean = completed.contains(node)
  def isFailed(node: NodeId): Boolean = failed.contains(node)
  def isFinished(node: NodeId): Boolean = isCompleted(node) || isFailed(node)
  def retryCount(node: NodeId): Int = retries.get(node).map(_.unwrap).getOrElse(0)

  // Fork/Join queries
  def hasActiveForks: Boolean = forkScopes.nonEmpty
  def forkDepth: Int = ...  // Nesting depth
  def isJoinReady(joinNodeId: NodeId): Boolean
  def hasJoinFailed(joinNodeId: NodeId): Boolean
```

## Optimistic Locking

For concurrent storage access:

```scala
stateVersion: StateVersion = StateVersion.Initial

// On load:
val loaded = repository.load(traversalId)

// On save:
repository.save(newState.copy(stateVersion = loaded.stateVersion.increment))

// Repository checks version match, throws ConcurrentModification if not
```

## JSON Representation

```json
{
  "traversalId": "01HQABC123456789ABCDEFGH",
  "active": ["process-data"],
  "completed": ["validate", "load-config"],
  "failed": [],
  "retries": {},
  "remainingNodes": 3,
  "forkScopes": {
    "01HQ001FORK00000000000001": {
      "forkId": "01HQ001FORK00000000000001",
      "forkNodeId": "parallel-start",
      "branches": ["01HQ001BR1", "01HQ001BR2"],
      "parentScope": null,
      "startedAt": "2024-01-15T10:00:00Z",
      "timeout": "2024-01-15T10:10:00Z"
    }
  },
  "branchStates": {
    "01HQ001BR1": {
      "branchId": "01HQ001BR1",
      "forkId": "01HQ001FORK00000000000001",
      "entryNode": "check-inventory",
      "currentNode": "check-inventory",
      "status": "active"
    }
  },
  "pendingJoins": {},
  "nodeToBranch": {
    "check-inventory": "01HQ001BR1",
    "process-payment": "01HQ001BR2"
  },
  "history": [
    {
      "type": "TraversalStarted",
      "node": "validate",
      "actor": "waygrid://system/waystation?nodeId=abc123",
      "vectorClock": { "abc123": 1 }
    }
  ]
}
```

## Best Practices

### 1. Always Use Pure Methods

```scala
// Good - pure method
val newState = state.start(nodeId, actor, None)

// Bad - mutation (not possible, but conceptually)
state.active.add(nodeId)  // Won't compile - Set is immutable
```

### 2. Preserve Event History

Never filter or truncate history during active traversal:

```scala
// Good - keep all events
state.copy(history = state.history :+ newEvent)

// Bad - losing history
state.copy(history = Vector(newEvent))
```

### 3. Check Idempotency

```scala
// FSM already handles this, but in custom code:
if state.isCompleted(nodeId) then
  state  // No change, already done
else
  state.successNode(nodeId, actor, None)
```

### 4. Verify Consistency

```scala
def verifyRemainingConsistency(dag: Dag): Boolean =
  remainingNodes.unwrap == dag.nodes.size - (completed ++ failed).size
```
