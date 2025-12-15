---
sidebar_position: 3
---

# Node Types & Fork/Join Patterns

Waygrid Router supports three node types that enable both linear and parallel execution patterns. This page covers Fork/Join parallelism in depth.

## Node Types Overview

```scala
enum NodeType:
  case Standard                          // Regular processing node
  case Fork(forkId: ForkId)              // Parallel fan-out
  case Join(forkId, strategy, timeout)   // Parallel fan-in
```

### Standard Nodes

Standard nodes are the basic building blocks. They:
- Execute a single task
- Pass control to successors based on edge guards
- Support retry policies

```json
{
  "id": "process-order",
  "label": "Process Order",
  "retryPolicy": { "type": "Linear", "delay": "1s", "maxRetries": 3 },
  "deliveryStrategy": { "type": "Immediate" },
  "address": "waygrid://processor/orders",
  "nodeType": { "type": "Standard" }
}
```

### Fork Nodes

Fork nodes initiate parallel execution. Each outgoing edge creates a separate branch:

```json
{
  "id": "parallel-start",
  "label": "Start Parallel Processing",
  "retryPolicy": { "type": "NoRetry" },
  "deliveryStrategy": { "type": "Immediate" },
  "address": "waygrid://system/waystation",
  "nodeType": {
    "type": "Fork",
    "forkId": "01HQXYZ123456789ABCDEFGH"
  }
}
```

**Key Points:**
- `forkId` is a ULID that links Fork to its corresponding Join
- Fork nodes are pass-through control flow nodes (no actual work)
- Each outgoing `OnSuccess` edge starts a new branch

### Join Nodes

Join nodes synchronize parallel branches:

```json
{
  "id": "wait-all",
  "label": "Synchronize Branches",
  "retryPolicy": { "type": "NoRetry" },
  "deliveryStrategy": { "type": "Immediate" },
  "address": "waygrid://system/waystation",
  "nodeType": {
    "type": "Join",
    "forkId": "01HQXYZ123456789ABCDEFGH",
    "strategy": { "type": "And" },
    "timeout": "5m"
  }
}
```

**Key Points:**
- `forkId` must match the corresponding Fork
- `strategy` determines completion semantics
- `timeout` is optional deadline for branch completion

## Join Strategies

### AND Strategy

Wait for **ALL** branches to complete successfully:

```json
{ "type": "And" }
```

```
        ┌──► B1 (success) ──┐
        │                   │
A ──► Fork ──► B2 (success) ──► Join(And) ──► C
        │                   │
        └──► B3 (success) ──┘
```

**Behavior:**
- Join completes when all branches succeed
- If any branch fails, the join fails
- Useful for workflows where all paths must complete

### OR Strategy

Continue as soon as **ANY** single branch completes:

```json
{ "type": "Or" }
```

```
        ┌──► B1 (success) ────────► Join(Or) ──► C
        │                              ▲
A ──► Fork ──► B2 (still running)  ───┘ (canceled)
        │                              │
        └──► B3 (still running) ───────┘ (canceled)
```

**Behavior:**
- Join completes when first branch succeeds
- Remaining branches are automatically canceled
- Useful for "first responder wins" patterns

### Quorum Strategy

Continue when **N** branches complete:

```json
{ "type": "Quorum", "n": 2 }
```

```
        ┌──► B1 (success) ──┐
        │                   │
A ──► Fork ──► B2 (success) ──► Join(Quorum(2)) ──► C
        │                   │
        └──► B3 (failed) ───┘
```

**Behavior:**
- Join completes when N branches succeed
- Remaining branches are canceled once quorum reached
- Useful for distributed consensus patterns

## Fork/Join Lifecycle

### 1. Fork Initialization

When a Fork node is reached:

```scala
// FSM creates ForkScope and BranchStates
val forkedState = state.startFork(
  forkNodeId,
  forkId,
  branchEntries,      // Map[BranchId, NodeId]
  parentScope,        // For nested forks
  timeout,
  nodeAddress,
  now
)
```

**Events Emitted:**
- `ForkStarted(forkNodeId, forkId, branches)`
- `BranchStarted(nodeId, branchId, forkId)` for each branch

### 2. Branch Execution

Each branch executes independently:

```scala
// Branch advances through nodes
state.advanceBranch(nodeId, branchId, forkId, ...)

// Branch completes or fails
state.completeBranch(...) // or failBranch(...)
```

**Tracking:**
- `nodeToBranch: Map[NodeId, BranchId]` - O(1) lookup of branch context
- `branchStates: Map[BranchId, BranchState]` - Per-branch state

### 3. Join Synchronization

When a branch reaches the Join:

```scala
// Register arrival at join
state.registerJoinArrival(joinNodeId, branchId, forkId, pendingJoin)

// Check if join condition is satisfied
pendingJoin.isSatisfied  // Based on strategy
```

### 4. Join Completion

```scala
// Clean up fork scope and continue
state.completeJoin(joinNodeId, forkId, completedBranches)
```

**Cleanup:**
- ForkScope removed
- BranchStates removed
- nodeToBranch entries cleared

## Nested Forks

Waygrid Router supports nested fork/join patterns:

```
                    ┌──► C1 ──┐
        ┌──► B1 ──► Fork2 ──► C2 ──► Join2 ──► B1' ──┐
        │           └──► C3 ──┘                      │
A ──► Fork1 ──► B2 ───────────────────────────────────► Join1 ──► D
        │                                             │
        └──► B3 ──────────────────────────────────────┘
```

### Scope Tracking

```scala
case class ForkScope(
  forkId: ForkId,
  forkNodeId: NodeId,
  branches: Set[BranchId],
  parentScope: Option[ForkId],  // Links to outer fork
  startedAt: Instant,
  timeout: Option[Instant]
)
```

### Parent Context Restoration

When inner join completes, the FSM restores parent branch context:

```scala
// Before completing inner join, save parent context
val parentBranchContext = forkScope.flatMap(fs =>
  state.branchForNode(fs.forkNodeId)
)

// After join completes, restore for outer branch tracking
val stateForTransition = parentBranchContext match
  case Some(parentBranch) =>
    joinedState.copy(nodeToBranch = joinedState.nodeToBranch +
      (joinNodeId -> parentBranch.branchId))
  case None =>
    joinedState
```

## JSON Examples

### Simple Fork/Join

```json
{
  "hash": "parallel-notification-v1",
  "entryPoints": ["start"],
  "repeatPolicy": { "type": "NoRepeat" },
  "nodes": {
    "start": {
      "id": "start",
      "nodeType": { "type": "Standard" },
      "address": "waygrid://origin/http"
    },
    "fork": {
      "id": "fork",
      "nodeType": {
        "type": "Fork",
        "forkId": "01HQ001FORK00000000000001"
      },
      "address": "waygrid://system/waystation"
    },
    "email": {
      "id": "email",
      "label": "Send Email",
      "nodeType": { "type": "Standard" },
      "retryPolicy": { "type": "Exponential", "baseDelay": "1s", "maxRetries": 3 },
      "address": "waygrid://destination/email"
    },
    "sms": {
      "id": "sms",
      "label": "Send SMS",
      "nodeType": { "type": "Standard" },
      "retryPolicy": { "type": "Linear", "delay": "2s", "maxRetries": 2 },
      "address": "waygrid://destination/sms"
    },
    "push": {
      "id": "push",
      "label": "Send Push",
      "nodeType": { "type": "Standard" },
      "retryPolicy": { "type": "NoRetry" },
      "address": "waygrid://destination/push"
    },
    "join": {
      "id": "join",
      "nodeType": {
        "type": "Join",
        "forkId": "01HQ001FORK00000000000001",
        "strategy": { "type": "Or" },
        "timeout": "30s"
      },
      "address": "waygrid://system/waystation"
    },
    "complete": {
      "id": "complete",
      "nodeType": { "type": "Standard" },
      "address": "waygrid://destination/webhook"
    }
  },
  "edges": [
    { "from": "start", "to": "fork", "guard": "OnSuccess" },
    { "from": "fork", "to": "email", "guard": "OnSuccess" },
    { "from": "fork", "to": "sms", "guard": "OnSuccess" },
    { "from": "fork", "to": "push", "guard": "OnSuccess" },
    { "from": "email", "to": "join", "guard": "OnSuccess" },
    { "from": "sms", "to": "join", "guard": "OnSuccess" },
    { "from": "push", "to": "join", "guard": "OnSuccess" },
    { "from": "join", "to": "complete", "guard": "OnSuccess" }
  ]
}
```

This workflow sends notifications through 3 channels simultaneously, continuing as soon as any one succeeds (OR strategy).

### Nested Fork/Join

```json
{
  "hash": "nested-processing-v1",
  "entryPoints": ["entry"],
  "repeatPolicy": { "type": "NoRepeat" },
  "nodes": {
    "entry": {
      "id": "entry",
      "nodeType": { "type": "Standard" },
      "address": "waygrid://origin/http"
    },
    "outer-fork": {
      "id": "outer-fork",
      "nodeType": {
        "type": "Fork",
        "forkId": "01HQ001OUTERFORK00000001"
      },
      "address": "waygrid://system/waystation"
    },
    "branch-a": {
      "id": "branch-a",
      "nodeType": { "type": "Standard" },
      "address": "waygrid://processor/a"
    },
    "inner-fork": {
      "id": "inner-fork",
      "nodeType": {
        "type": "Fork",
        "forkId": "01HQ001INNERFORK00000001"
      },
      "address": "waygrid://system/waystation"
    },
    "inner-a": {
      "id": "inner-a",
      "nodeType": { "type": "Standard" },
      "address": "waygrid://processor/inner-a"
    },
    "inner-b": {
      "id": "inner-b",
      "nodeType": { "type": "Standard" },
      "address": "waygrid://processor/inner-b"
    },
    "inner-join": {
      "id": "inner-join",
      "nodeType": {
        "type": "Join",
        "forkId": "01HQ001INNERFORK00000001",
        "strategy": { "type": "And" }
      },
      "address": "waygrid://system/waystation"
    },
    "after-inner": {
      "id": "after-inner",
      "nodeType": { "type": "Standard" },
      "address": "waygrid://processor/after-inner"
    },
    "outer-join": {
      "id": "outer-join",
      "nodeType": {
        "type": "Join",
        "forkId": "01HQ001OUTERFORK00000001",
        "strategy": { "type": "And" }
      },
      "address": "waygrid://system/waystation"
    },
    "final": {
      "id": "final",
      "nodeType": { "type": "Standard" },
      "address": "waygrid://destination/webhook"
    }
  },
  "edges": [
    { "from": "entry", "to": "outer-fork", "guard": "OnSuccess" },
    { "from": "outer-fork", "to": "branch-a", "guard": "OnSuccess" },
    { "from": "outer-fork", "to": "inner-fork", "guard": "OnSuccess" },
    { "from": "inner-fork", "to": "inner-a", "guard": "OnSuccess" },
    { "from": "inner-fork", "to": "inner-b", "guard": "OnSuccess" },
    { "from": "inner-a", "to": "inner-join", "guard": "OnSuccess" },
    { "from": "inner-b", "to": "inner-join", "guard": "OnSuccess" },
    { "from": "inner-join", "to": "after-inner", "guard": "OnSuccess" },
    { "from": "branch-a", "to": "outer-join", "guard": "OnSuccess" },
    { "from": "after-inner", "to": "outer-join", "guard": "OnSuccess" },
    { "from": "outer-join", "to": "final", "guard": "OnSuccess" }
  ]
}
```

## Error Handling in Fork/Join

### Branch Failure

When a branch fails:

```scala
state.failBranch(node, branchId, forkId, reason, ...)
```

Depending on join strategy:
- **AND**: Join fails immediately
- **OR**: Join waits for another branch
- **Quorum**: Join recalculates if quorum still achievable

### Join Timeout

If configured, joins can timeout:

```json
{
  "type": "Join",
  "forkId": "...",
  "strategy": { "type": "And" },
  "timeout": "5m"
}
```

On timeout:
1. `Timeout` signal sent
2. Remaining branches can be canceled
3. `OnTimeout` edge followed (if exists)

### OnFailure Edges from Join

```json
{ "from": "join", "to": "error-handler", "guard": "OnFailure" }
```

If the join fails (AND with failed branch, Quorum not reached), the workflow can continue via failure edge.

## Best Practices

### 1. Match Fork/Join IDs

Always ensure Fork and Join have matching `forkId`:

```json
// Fork
{ "type": "Fork", "forkId": "01HQABC123456789ABCDEFGH" }

// Join - MUST match!
{ "type": "Join", "forkId": "01HQABC123456789ABCDEFGH", ... }
```

### 2. Set Appropriate Timeouts

For long-running branches, set join timeouts:

```json
{
  "type": "Join",
  "forkId": "...",
  "strategy": { "type": "And" },
  "timeout": "10m"  // Fail if branches take too long
}
```

### 3. Use OR for "Fast Path"

When you want the fastest result:

```json
{ "type": "Or" }  // First success wins
```

### 4. Use Quorum for Reliability

For distributed consensus (e.g., 2-of-3 agreement):

```json
{ "type": "Quorum", "n": 2 }
```

### 5. Handle Branch Failures

Add failure edges for graceful degradation:

```json
{ "from": "join", "to": "fallback", "guard": "OnFailure" }
```
