---
sidebar_position: 5
---

# Signals & Effects Reference

This page provides a complete reference for all signals (inputs) and effects (outputs) in the TraversalFSM.

## Signals Overview

Signals are events that drive state transitions in the FSM. They represent external happenings that the traversal system must respond to.

```scala
sealed trait TraversalSignal:
  def traversalId: TraversalId
```

## Linear Traversal Signals

### Begin

Start a new traversal from the DAG's entry point.

```scala
case class Begin(
  traversalId: TraversalId,
  entryNodeId: Option[NodeId] = None  // Override default entry
) extends TraversalSignal
```

**Preconditions:**
- DAG must have nodes
- Traversal must not already be in progress

**Resulting Effects:**
- `DispatchNode` - Start first node immediately
- `Schedule` - Schedule delayed start

**Example:**
```json
{
  "type": "Begin",
  "traversalId": "01HQABC123456789ABCDEFGH",
  "entryNodeId": null
}
```

### Resume

Resume execution of a scheduled node when its timer fires.

```scala
case class Resume(
  traversalId: TraversalId,
  nodeId: NodeId
) extends TraversalSignal
```

**Preconditions:**
- Node must be scheduled (not started)
- Node must exist in DAG

**Resulting Effects:**
- `DispatchNode` - Start the scheduled node

### Cancel

Cancel the entire traversal.

```scala
case class Cancel(
  traversalId: TraversalId
) extends TraversalSignal
```

**Effects:**
- `Cancel` - Stop all processing

### Retry

Retry a failed node (from retry timer).

```scala
case class Retry(
  traversalId: TraversalId,
  nodeId: NodeId
) extends TraversalSignal
```

**Preconditions:**
- Node must be in failed state
- Retry policy must allow more attempts

### NodeStart

Acknowledgment that a node has begun processing.

```scala
case class NodeStart(
  traversalId: TraversalId,
  nodeId: NodeId
) extends TraversalSignal
```

**Note:** Primarily for logging/tracking. FSM handles idempotently.

### NodeSuccess

A node has completed successfully.

```scala
case class NodeSuccess(
  traversalId: TraversalId,
  nodeId: NodeId,
  output: Option[Json] = None  // Output for conditional routing
) extends TraversalSignal
```

**Resulting Effects:**
- `DispatchNode` - Start next node
- `DispatchNodes` - Fan-out from fork
- `Complete` - Traversal finished
- `NoOp` - Already processed (idempotent)

**Example:**
```json
{
  "type": "NodeSuccess",
  "traversalId": "01HQABC123456789ABCDEFGH",
  "nodeId": "process-order",
  "output": {
    "orderId": "ORD-123",
    "status": "validated"
  }
}
```

### NodeFailure

A node has failed.

```scala
case class NodeFailure(
  traversalId: TraversalId,
  nodeId: NodeId,
  reason: Option[String] = None
) extends TraversalSignal
```

**Resulting Effects:**
- `ScheduleRetry` - Retry after delay
- `DispatchNode` - Follow OnFailure edge
- `Fail` - Traversal failed (no retries left, no failure edge)

### Completed

External completion signal (rarely used, prefer NodeSuccess).

```scala
case class Completed(
  traversalId: TraversalId
) extends TraversalSignal
```

### Failed

External failure signal.

```scala
case class Failed(
  traversalId: TraversalId,
  reason: Option[String] = None
) extends TraversalSignal
```

## Fork/Join Signals

### ForkReached

A Fork node has been reached, initiating parallel branches.

```scala
case class ForkReached(
  traversalId: TraversalId,
  forkNodeId: NodeId
) extends TraversalSignal
```

**Effects:**
- `DispatchNodes` - Fan-out to all branch entry nodes

### BranchComplete

A branch within a fork has completed.

```scala
case class BranchComplete(
  traversalId: TraversalId,
  branchId: BranchId,
  forkId: ForkId,
  result: BranchResult
) extends TraversalSignal
```

Where `BranchResult` is:
```scala
enum BranchResult:
  case Success(output: Option[Json])
  case Failure(reason: String)
  case Timeout
```

### JoinReached

A branch has reached a Join node.

```scala
case class JoinReached(
  traversalId: TraversalId,
  joinNodeId: NodeId,
  branchId: BranchId
) extends TraversalSignal
```

**Effects (when join satisfied):**
- `DispatchNode` - Continue after join
- `CancelBranches` - Cancel remaining (OR join)

### Timeout

Timeout for a branch or join.

```scala
case class Timeout(
  traversalId: TraversalId,
  nodeId: NodeId,
  branchId: Option[BranchId]  // None = join timeout
) extends TraversalSignal
```

### CancelBranches

Request to cancel specific branches.

```scala
case class CancelBranches(
  traversalId: TraversalId,
  forkId: ForkId,
  branchIds: Set[BranchId],
  reason: String
) extends TraversalSignal
```

### TraversalTimeout

The entire traversal has exceeded its deadline.

```scala
case class TraversalTimeout(
  traversalId: TraversalId
) extends TraversalSignal
```

**Effects:**
- `Fail` - Terminate the traversal

---

## Effects Overview

Effects describe actions the executor should perform. They are the outputs of the FSM.

```scala
sealed trait TraversalEffect:
  val traversalId: TraversalId
```

## Dispatch Effects

### DispatchNode

Execute a single node.

```scala
case class DispatchNode(
  traversalId: TraversalId,
  nodeId: NodeId,
  branchesToCancel: Set[(BranchId, ForkId)] = Set.empty,
  scheduleTraversalTimeout: Option[(String, Instant)] = None
) extends TraversalEffect
```

**Fields:**
- `nodeId` - The node to execute
- `branchesToCancel` - Branches to cancel (OR join completion)
- `scheduleTraversalTimeout` - Timeout to schedule (Begin signal)

**Executor Behavior:**
1. Look up node's service address
2. Send work to that service
3. Optionally cancel specified branches
4. Optionally schedule traversal timeout

### DispatchNodes

Execute multiple nodes in parallel (fan-out).

```scala
case class DispatchNodes(
  traversalId: TraversalId,
  nodes: List[(NodeId, BranchId)],
  joinTimeout: Option[Instant] = None,
  joinNodeId: Option[NodeId] = None
) extends TraversalEffect
```

**Fields:**
- `nodes` - List of (node, branch) pairs to dispatch
- `joinTimeout` - Optional deadline for join
- `joinNodeId` - Join node (for timeout scheduling)

**Executor Behavior:**
1. Dispatch all nodes concurrently
2. Track each node's branch context
3. Schedule join timeout if specified

## Scheduling Effects

### Schedule

Schedule a node for future execution.

```scala
case class Schedule(
  traversalId: TraversalId,
  scheduledAt: Instant,
  nodeId: NodeId
) extends TraversalEffect
```

**Executor Behavior:**
1. Set timer for `scheduledAt`
2. Send `Resume` signal when timer fires

### ScheduleRetry

Schedule a retry for a failed node.

```scala
case class ScheduleRetry(
  traversalId: TraversalId,
  scheduledAt: Instant,
  retryAttempt: RetryAttempt,
  nodeId: NodeId
) extends TraversalEffect
```

**Executor Behavior:**
1. Set timer for `scheduledAt`
2. Send `Retry` signal when timer fires
3. Log retry attempt number

### ScheduleTimeout

Schedule a timeout for fork/join.

```scala
case class ScheduleTimeout(
  traversalId: TraversalId,
  nodeId: NodeId,
  branchId: Option[BranchId],
  deadline: Instant
) extends TraversalEffect
```

### ScheduleTraversalTimeout

Schedule traversal-level timeout.

```scala
case class ScheduleTraversalTimeout(
  traversalId: TraversalId,
  deadline: Instant,
  timeoutId: String
) extends TraversalEffect
```

### CancelTraversalTimeout

Cancel a scheduled traversal timeout.

```scala
case class CancelTraversalTimeout(
  traversalId: TraversalId,
  timeoutId: String
) extends TraversalEffect
```

## Terminal Effects

### Complete

Traversal completed successfully.

```scala
case class Complete(
  traversalId: TraversalId,
  cancelTimeoutId: Option[String] = None
) extends TraversalEffect
```

**Executor Behavior:**
1. Mark traversal as complete
2. Cancel timeout if specified
3. Clean up resources

### Fail

Traversal failed.

```scala
case class Fail(
  traversalId: TraversalId,
  cancelTimeoutId: Option[String] = None
) extends TraversalEffect
```

### Cancel

Traversal canceled.

```scala
case class Cancel(
  traversalId: TraversalId,
  cancelTimeoutId: Option[String] = None
) extends TraversalEffect
```

## Fork/Join Effects

### JoinComplete

A join condition was satisfied.

```scala
case class JoinComplete(
  traversalId: TraversalId,
  joinNodeId: NodeId,
  forkId: ForkId,
  completedBranches: Set[BranchId]
) extends TraversalEffect
```

### CancelBranches

Cancel running branches.

```scala
case class CancelBranches(
  traversalId: TraversalId,
  forkId: ForkId,
  branchIds: Set[BranchId],
  reason: String
) extends TraversalEffect
```

**Executor Behavior:**
1. Send cancel signals to running branch executors
2. Clean up branch resources

## No-Op Effect

### NoOp

No action needed (idempotent handling).

```scala
case class NoOp(
  traversalId: TraversalId
) extends TraversalEffect
```

## Persistence Effect

### PersistState

Save state for recovery.

```scala
case class PersistState(
  traversalId: TraversalId,
  state: TraversalState
) extends TraversalEffect
```

---

## Signal → Effect Mapping

| Signal | Possible Effects |
|--------|-----------------|
| `Begin` | `DispatchNode`, `Schedule`, `EmptyDag`, `AlreadyInProgress` |
| `Resume` | `DispatchNode`, `NoOp` |
| `Cancel` | `Cancel` |
| `Retry` | `DispatchNode` |
| `NodeStart` | `NoOp` |
| `NodeSuccess` | `DispatchNode`, `DispatchNodes`, `Complete`, `NoOp` |
| `NodeFailure` | `ScheduleRetry`, `DispatchNode`, `Fail` |
| `ForkReached` | `DispatchNodes` |
| `JoinReached` | `DispatchNode`, `CancelBranches`, `NoOp` |
| `Timeout` | `DispatchNode`, `Fail` |
| `TraversalTimeout` | `Fail`, `NoOp` |

## JSON Serialization

All signals and effects can be serialized to JSON for transport and logging:

```json
// Signal
{
  "type": "NodeSuccess",
  "traversalId": "01HQABC123456789ABCDEFGH",
  "nodeId": "validate",
  "output": { "valid": true }
}

// Effect
{
  "type": "DispatchNode",
  "traversalId": "01HQABC123456789ABCDEFGH",
  "nodeId": "process",
  "branchesToCancel": [],
  "scheduleTraversalTimeout": null
}
```
