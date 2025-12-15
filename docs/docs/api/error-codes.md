---
sidebar_position: 2
---

# Error Codes Reference

Complete reference for all TraversalError types that can occur during DAG traversal.

## Error Structure

All traversal errors extend the base trait:

```scala
sealed trait TraversalError extends Throwable:
  val traversalId: TraversalId
  def errorMessage: String
  override def getMessage: String =
    s"${getClass.getSimpleName}(traversalId=$traversalId): $errorMessage"
```

**JSON Format:**
```json
{
  "type": "NodeNotFound",
  "traversalId": "01HQABC123456789ABCDEFGH",
  "message": "NodeNotFound(traversalId=01HQABC123456789ABCDEFGH): node process not found in DAG",
  "details": {
    "nodeId": "process"
  }
}
```

## Linear Traversal Errors

### UnsupportedSignal

The signal is not valid in the current state.

| Property | Value |
|----------|-------|
| **Code** | `UNSUPPORTED_SIGNAL` |
| **HTTP Status** | 400 Bad Request |
| **Recoverable** | No |

**Example:**
```json
{
  "type": "UnsupportedSignal",
  "traversalId": "01HQABC123456789ABCDEFGH",
  "message": "signal not supported in current state"
}
```

**Causes:**
- Sending `NodeSuccess` for a node that isn't started
- Sending `Begin` on an already-running traversal

### EmptyDag

The DAG has no nodes to traverse.

| Property | Value |
|----------|-------|
| **Code** | `EMPTY_DAG` |
| **HTTP Status** | 400 Bad Request |
| **Recoverable** | No |

**Example:**
```json
{
  "type": "EmptyDag",
  "traversalId": "01HQABC123456789ABCDEFGH",
  "message": "DAG has no nodes to traverse"
}
```

### AlreadyInProgress

A traversal is already running.

| Property | Value |
|----------|-------|
| **Code** | `ALREADY_IN_PROGRESS` |
| **HTTP Status** | 409 Conflict |
| **Recoverable** | No |

**Example:**
```json
{
  "type": "AlreadyInProgress",
  "traversalId": "01HQABC123456789ABCDEFGH",
  "message": "traversal already in progress"
}
```

### MissingEntryNode

The DAG's entry node couldn't be found.

| Property | Value |
|----------|-------|
| **Code** | `MISSING_ENTRY_NODE` |
| **HTTP Status** | 400 Bad Request |
| **Recoverable** | No |

**Example:**
```json
{
  "type": "MissingEntryNode",
  "traversalId": "01HQABC123456789ABCDEFGH",
  "message": "DAG entry node not found"
}
```

### NodeNotFound

A referenced node doesn't exist in the DAG.

| Property | Value |
|----------|-------|
| **Code** | `NODE_NOT_FOUND` |
| **HTTP Status** | 404 Not Found |
| **Recoverable** | No |

**Fields:**
- `nodeId` - The missing node ID

**Example:**
```json
{
  "type": "NodeNotFound",
  "traversalId": "01HQABC123456789ABCDEFGH",
  "nodeId": "missing-node",
  "message": "node missing-node not found in DAG"
}
```

### InvalidNodeState

The node is not in the expected state for the operation.

| Property | Value |
|----------|-------|
| **Code** | `INVALID_NODE_STATE` |
| **HTTP Status** | 409 Conflict |
| **Recoverable** | No |

**Fields:**
- `nodeId` - The node
- `expected` - Expected state
- `actual` - Actual state

**Example:**
```json
{
  "type": "InvalidNodeState",
  "traversalId": "01HQABC123456789ABCDEFGH",
  "nodeId": "process",
  "expected": "started",
  "actual": "not started",
  "message": "node process in invalid state: expected=started, actual=not started"
}
```

### NoActiveNode

No node is currently being processed.

| Property | Value |
|----------|-------|
| **Code** | `NO_ACTIVE_NODE` |
| **HTTP Status** | 400 Bad Request |
| **Recoverable** | No |

**Example:**
```json
{
  "type": "NoActiveNode",
  "traversalId": "01HQABC123456789ABCDEFGH",
  "message": "no active node being processed"
}
```

### TraversalAlreadyComplete

The traversal has already finished successfully.

| Property | Value |
|----------|-------|
| **Code** | `TRAVERSAL_ALREADY_COMPLETE` |
| **HTTP Status** | 409 Conflict |
| **Recoverable** | No |

**Example:**
```json
{
  "type": "TraversalAlreadyComplete",
  "traversalId": "01HQABC123456789ABCDEFGH",
  "message": "traversal already complete"
}
```

### TraversalAlreadyFailed

The traversal has already failed.

| Property | Value |
|----------|-------|
| **Code** | `TRAVERSAL_ALREADY_FAILED` |
| **HTTP Status** | 409 Conflict |
| **Recoverable** | No |

**Example:**
```json
{
  "type": "TraversalAlreadyFailed",
  "traversalId": "01HQABC123456789ABCDEFGH",
  "message": "traversal already failed"
}
```

### TraversalCanceled

The traversal was canceled.

| Property | Value |
|----------|-------|
| **Code** | `TRAVERSAL_CANCELED` |
| **HTTP Status** | 409 Conflict |
| **Recoverable** | No |

**Example:**
```json
{
  "type": "TraversalCanceled",
  "traversalId": "01HQABC123456789ABCDEFGH",
  "message": "traversal was canceled"
}
```

### CannotRetry

The node cannot be retried.

| Property | Value |
|----------|-------|
| **Code** | `CANNOT_RETRY` |
| **HTTP Status** | 400 Bad Request |
| **Recoverable** | No |

**Fields:**
- `nodeId` - The node
- `reason` - Why retry is not allowed

**Example:**
```json
{
  "type": "CannotRetry",
  "traversalId": "01HQABC123456789ABCDEFGH",
  "nodeId": "process",
  "reason": "max retries exceeded (3/3)",
  "message": "node process cannot be retried: max retries exceeded (3/3)"
}
```

## Fork/Join Errors

### ForkMismatch

Fork ID doesn't match expected value.

| Property | Value |
|----------|-------|
| **Code** | `FORK_MISMATCH` |
| **HTTP Status** | 400 Bad Request |
| **Recoverable** | No |

**Fields:**
- `expectedForkId` - Expected fork ID
- `actualForkId` - Actual fork ID

**Example:**
```json
{
  "type": "ForkMismatch",
  "traversalId": "01HQABC123456789ABCDEFGH",
  "expectedForkId": "01HQ001FORK00000000000001",
  "actualForkId": "01HQ001FORK00000000000002",
  "message": "fork mismatch: expected=01HQ001FORK00000000000001, actual=01HQ001FORK00000000000002"
}
```

### JoinWithoutFork

A Join node was reached without an active Fork scope.

| Property | Value |
|----------|-------|
| **Code** | `JOIN_WITHOUT_FORK` |
| **HTTP Status** | 400 Bad Request |
| **Recoverable** | No |

**Fields:**
- `joinNodeId` - The join node
- `forkId` - Expected fork ID

**Example:**
```json
{
  "type": "JoinWithoutFork",
  "traversalId": "01HQABC123456789ABCDEFGH",
  "joinNodeId": "join-node",
  "forkId": "01HQ001FORK00000000000001",
  "message": "join node join-node reached without active fork scope 01HQ001FORK00000000000001"
}
```

### BranchNotFound

The specified branch doesn't exist.

| Property | Value |
|----------|-------|
| **Code** | `BRANCH_NOT_FOUND` |
| **HTTP Status** | 404 Not Found |
| **Recoverable** | No |

**Fields:**
- `branchId` - The missing branch ID

**Example:**
```json
{
  "type": "BranchNotFound",
  "traversalId": "01HQABC123456789ABCDEFGH",
  "branchId": "01HQ001BR0000000000000001",
  "message": "branch 01HQ001BR0000000000000001 not found"
}
```

### JoinTimeout

The join timed out waiting for branches.

| Property | Value |
|----------|-------|
| **Code** | `JOIN_TIMEOUT` |
| **HTTP Status** | 504 Gateway Timeout |
| **Recoverable** | Maybe (via OnTimeout edge) |

**Fields:**
- `joinNodeId` - The join node
- `pendingBranches` - Branches that didn't complete

**Example:**
```json
{
  "type": "JoinTimeout",
  "traversalId": "01HQABC123456789ABCDEFGH",
  "joinNodeId": "sync-join",
  "pendingBranches": ["01HQ001BR1", "01HQ001BR2"],
  "message": "join sync-join timed out waiting for branches: 01HQ001BR1, 01HQ001BR2"
}
```

### NestedForkDepthExceeded

Fork nesting limit exceeded.

| Property | Value |
|----------|-------|
| **Code** | `NESTED_FORK_DEPTH_EXCEEDED` |
| **HTTP Status** | 400 Bad Request |
| **Recoverable** | No |

**Fields:**
- `maxDepth` - Maximum allowed depth
- `currentDepth` - Attempted depth

**Example:**
```json
{
  "type": "NestedForkDepthExceeded",
  "traversalId": "01HQABC123456789ABCDEFGH",
  "maxDepth": 5,
  "currentDepth": 6,
  "message": "nested fork depth exceeded: max=5, current=6"
}
```

### ForkScopeNotFound

The fork scope doesn't exist.

| Property | Value |
|----------|-------|
| **Code** | `FORK_SCOPE_NOT_FOUND` |
| **HTTP Status** | 404 Not Found |
| **Recoverable** | No |

**Fields:**
- `forkId` - The missing fork scope ID

**Example:**
```json
{
  "type": "ForkScopeNotFound",
  "traversalId": "01HQABC123456789ABCDEFGH",
  "forkId": "01HQ001FORK00000000000001",
  "message": "fork scope 01HQ001FORK00000000000001 not found"
}
```

### InvalidBranchState

The branch is not in the expected state.

| Property | Value |
|----------|-------|
| **Code** | `INVALID_BRANCH_STATE` |
| **HTTP Status** | 409 Conflict |
| **Recoverable** | No |

**Fields:**
- `branchId` - The branch
- `expected` - Expected state
- `actual` - Actual state

**Example:**
```json
{
  "type": "InvalidBranchState",
  "traversalId": "01HQABC123456789ABCDEFGH",
  "branchId": "01HQ001BR0000000000000001",
  "expected": "active",
  "actual": "completed",
  "message": "branch 01HQ001BR0000000000000001 in invalid state: expected=active, actual=completed"
}
```

## Storage Errors

### ConcurrentModification

Optimistic locking conflict during storage.

| Property | Value |
|----------|-------|
| **Code** | `CONCURRENT_MODIFICATION` |
| **HTTP Status** | 409 Conflict |
| **Recoverable** | Yes (retry) |

**Fields:**
- `expectedVersion` - Expected state version
- `actualVersion` - Actual version in storage

**Example:**
```json
{
  "type": "ConcurrentModification",
  "traversalId": "01HQABC123456789ABCDEFGH",
  "expectedVersion": 5,
  "actualVersion": 6,
  "message": "concurrent modification: expected version=5, actual=6"
}
```

**Recovery:**
1. Reload state from storage
2. Re-apply operation
3. Retry save

## Error Handling Best Practices

### 1. Use OnFailure Edges

```json
{
  "from": "risky-node",
  "to": "error-handler",
  "guard": "OnFailure"
}
```

### 2. Set Appropriate Retry Policies

```json
{
  "retryPolicy": {
    "type": "Exponential",
    "baseDelay": "1s",
    "maxRetries": 3
  }
}
```

### 3. Configure Join Timeouts

```json
{
  "nodeType": {
    "type": "Join",
    "forkId": "...",
    "strategy": { "type": "And" },
    "timeout": "5m"
  }
}
```

### 4. Use OnTimeout Edges

```json
{
  "from": "join-node",
  "to": "timeout-handler",
  "guard": "OnTimeout"
}
```

### 5. Set Global Traversal Timeout

```json
{
  "hash": "...",
  "entryPoints": ["..."],
  "timeout": "30m"
}
```

## Error Code Summary

| Error | Code | HTTP | Recoverable |
|-------|------|------|-------------|
| UnsupportedSignal | UNSUPPORTED_SIGNAL | 400 | No |
| EmptyDag | EMPTY_DAG | 400 | No |
| AlreadyInProgress | ALREADY_IN_PROGRESS | 409 | No |
| MissingEntryNode | MISSING_ENTRY_NODE | 400 | No |
| NodeNotFound | NODE_NOT_FOUND | 404 | No |
| InvalidNodeState | INVALID_NODE_STATE | 409 | No |
| NoActiveNode | NO_ACTIVE_NODE | 400 | No |
| TraversalAlreadyComplete | TRAVERSAL_ALREADY_COMPLETE | 409 | No |
| TraversalAlreadyFailed | TRAVERSAL_ALREADY_FAILED | 409 | No |
| TraversalCanceled | TRAVERSAL_CANCELED | 409 | No |
| CannotRetry | CANNOT_RETRY | 400 | No |
| ForkMismatch | FORK_MISMATCH | 400 | No |
| JoinWithoutFork | JOIN_WITHOUT_FORK | 400 | No |
| BranchNotFound | BRANCH_NOT_FOUND | 404 | No |
| JoinTimeout | JOIN_TIMEOUT | 504 | Maybe |
| NestedForkDepthExceeded | NESTED_FORK_DEPTH_EXCEEDED | 400 | No |
| ForkScopeNotFound | FORK_SCOPE_NOT_FOUND | 404 | No |
| InvalidBranchState | INVALID_BRANCH_STATE | 409 | No |
| ConcurrentModification | CONCURRENT_MODIFICATION | 409 | Yes |
