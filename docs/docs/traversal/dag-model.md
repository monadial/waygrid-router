---
sidebar_position: 2
---

# DAG Model

A DAG (Directed Acyclic Graph) defines the structure of a workflow in Waygrid Router. It specifies nodes (processing steps), edges (transitions), and policies governing execution.

## DAG Structure

```scala
final case class Dag(
  hash: DagHash,                      // Content-based identifier
  entryPoints: NonEmptyList[NodeId],  // Valid starting nodes
  repeatPolicy: RepeatPolicy,         // Repetition behavior
  nodes: Map[NodeId, Node],           // All nodes
  edges: List[Edge],                  // All edges
  timeout: Option[FiniteDuration]     // Global timeout
)
```

## JSON Schema

### Complete DAG Example

```json
{
  "hash": "order-processing-v2",
  "entryPoints": ["receive-order"],
  "repeatPolicy": { "type": "NoRepeat" },
  "timeout": "30m",
  "nodes": {
    "receive-order": {
      "id": "receive-order",
      "label": "Receive Order",
      "retryPolicy": { "type": "NoRetry" },
      "deliveryStrategy": { "type": "Immediate" },
      "address": "waygrid://origin/http",
      "nodeType": { "type": "Standard" }
    },
    "validate": {
      "id": "validate",
      "label": "Validate Order",
      "retryPolicy": {
        "type": "Linear",
        "delay": "500ms",
        "maxRetries": 3
      },
      "deliveryStrategy": { "type": "Immediate" },
      "address": "waygrid://processor/validator",
      "nodeType": { "type": "Standard" }
    },
    "parallel-start": {
      "id": "parallel-start",
      "label": "Start Parallel Processing",
      "retryPolicy": { "type": "NoRetry" },
      "deliveryStrategy": { "type": "Immediate" },
      "address": "waygrid://system/waystation",
      "nodeType": {
        "type": "Fork",
        "forkId": "01HQXYZ123456789ABCDEFGH"
      }
    },
    "check-inventory": {
      "id": "check-inventory",
      "label": "Check Inventory",
      "retryPolicy": {
        "type": "Exponential",
        "baseDelay": "1s",
        "maxRetries": 5
      },
      "deliveryStrategy": { "type": "Immediate" },
      "address": "waygrid://processor/inventory",
      "nodeType": { "type": "Standard" }
    },
    "process-payment": {
      "id": "process-payment",
      "label": "Process Payment",
      "retryPolicy": {
        "type": "Exponential",
        "baseDelay": "2s",
        "maxRetries": 3
      },
      "deliveryStrategy": { "type": "Immediate" },
      "address": "waygrid://processor/payment",
      "nodeType": { "type": "Standard" }
    },
    "parallel-end": {
      "id": "parallel-end",
      "label": "Wait for All",
      "retryPolicy": { "type": "NoRetry" },
      "deliveryStrategy": { "type": "Immediate" },
      "address": "waygrid://system/waystation",
      "nodeType": {
        "type": "Join",
        "forkId": "01HQXYZ123456789ABCDEFGH",
        "strategy": { "type": "And" },
        "timeout": "5m"
      }
    },
    "fulfill": {
      "id": "fulfill",
      "label": "Fulfill Order",
      "retryPolicy": { "type": "NoRetry" },
      "deliveryStrategy": { "type": "Immediate" },
      "address": "waygrid://destination/fulfillment",
      "nodeType": { "type": "Standard" }
    },
    "notify-failure": {
      "id": "notify-failure",
      "label": "Send Failure Notification",
      "retryPolicy": { "type": "NoRetry" },
      "deliveryStrategy": { "type": "Immediate" },
      "address": "waygrid://destination/webhook",
      "nodeType": { "type": "Standard" }
    }
  },
  "edges": [
    { "from": "receive-order", "to": "validate", "guard": "OnSuccess" },
    { "from": "validate", "to": "parallel-start", "guard": "OnSuccess" },
    { "from": "validate", "to": "notify-failure", "guard": "OnFailure" },
    { "from": "parallel-start", "to": "check-inventory", "guard": "OnSuccess" },
    { "from": "parallel-start", "to": "process-payment", "guard": "OnSuccess" },
    { "from": "check-inventory", "to": "parallel-end", "guard": "OnSuccess" },
    { "from": "process-payment", "to": "parallel-end", "guard": "OnSuccess" },
    { "from": "parallel-end", "to": "fulfill", "guard": "OnSuccess" },
    { "from": "parallel-end", "to": "notify-failure", "guard": "OnFailure" }
  ]
}
```

## Node Definition

### Node Structure

```scala
final case class Node(
  id: NodeId,                       // Unique identifier
  label: Option[String],            // Human-readable name
  retryPolicy: RetryPolicy,         // Failure handling
  deliveryStrategy: DeliveryStrategy, // Timing control
  address: ServiceAddress,          // Processing endpoint
  nodeType: NodeType                // Standard/Fork/Join
)
```

### Node JSON Examples

#### Standard Node
```json
{
  "id": "process-data",
  "label": "Process Data",
  "retryPolicy": {
    "type": "Linear",
    "delay": "1s",
    "maxRetries": 3
  },
  "deliveryStrategy": { "type": "Immediate" },
  "address": "waygrid://processor/transformer",
  "nodeType": { "type": "Standard" }
}
```

#### Fork Node
```json
{
  "id": "start-parallel",
  "label": "Fan Out",
  "retryPolicy": { "type": "NoRetry" },
  "deliveryStrategy": { "type": "Immediate" },
  "address": "waygrid://system/waystation",
  "nodeType": {
    "type": "Fork",
    "forkId": "01HQABC123456789ABCDEFGH"
  }
}
```

#### Join Node
```json
{
  "id": "wait-all",
  "label": "Synchronize",
  "retryPolicy": { "type": "NoRetry" },
  "deliveryStrategy": { "type": "Immediate" },
  "address": "waygrid://system/waystation",
  "nodeType": {
    "type": "Join",
    "forkId": "01HQABC123456789ABCDEFGH",
    "strategy": { "type": "And" },
    "timeout": "10m"
  }
}
```

## Edge Definition

### Edge Structure

```scala
final case class Edge(
  from: NodeId,      // Source node
  to: NodeId,        // Target node
  guard: EdgeGuard   // Transition condition
)
```

### Edge Guards

| Guard | JSON | Description |
|-------|------|-------------|
| OnSuccess | `"OnSuccess"` | Traverse when source succeeds |
| OnFailure | `"OnFailure"` | Traverse when source fails |
| OnTimeout | `"OnTimeout"` | Traverse when source times out |
| OnAny | `"OnAny"` | Traverse on any result |
| Always | `"Always"` | Always traverse |
| Conditional | `{"type": "Conditional", "condition": {...}}` | Traverse when condition matches |

### Edge JSON Examples

```json
// Success edge
{ "from": "A", "to": "B", "guard": "OnSuccess" }

// Failure fallback
{ "from": "A", "to": "error-handler", "guard": "OnFailure" }

// Conditional routing based on output
{
  "from": "router",
  "to": "premium-path",
  "guard": {
    "type": "Conditional",
    "condition": {
      "field": "customerType",
      "operator": "equals",
      "value": "premium"
    }
  }
}
```

## Retry Policies

### NoRetry
Fail immediately on error:
```json
{ "type": "NoRetry" }
```

### Linear
Fixed delay between retries:
```json
{
  "type": "Linear",
  "delay": "2s",
  "maxRetries": 5
}
```
Timeline: 2s → 2s → 2s → 2s → 2s → fail

### Exponential
Exponentially increasing delay:
```json
{
  "type": "Exponential",
  "baseDelay": "1s",
  "maxRetries": 4
}
```
Timeline: 1s → 2s → 4s → 8s → fail

## Delivery Strategies

### Immediate
Execute as soon as ready:
```json
{ "type": "Immediate" }
```

### ScheduleAfter
Execute after a delay:
```json
{ "type": "ScheduleAfter", "delay": "5m" }
```

### ScheduleAt
Execute at specific time:
```json
{ "type": "ScheduleAt", "time": "2024-01-15T10:00:00Z" }
```

## Repeat Policies

### NoRepeat
Execute once:
```json
{ "type": "NoRepeat" }
```

### Indefinitely
Repeat forever at interval:
```json
{ "type": "Indefinitely", "every": "1h" }
```

### Times
Repeat N times:
```json
{ "type": "Times", "every": "30m", "times": 5 }
```

### Until
Repeat until deadline:
```json
{ "type": "Until", "every": "15m", "until": "2024-01-15T18:00:00Z" }
```

## Service Addresses

Nodes route to services using Waygrid URIs:

```
waygrid://<component>/<service>?region=<region>&clusterId=<id>
```

### Examples:
```
waygrid://origin/http          # HTTP origin service
waygrid://processor/validator  # Validation processor
waygrid://destination/webhook  # Webhook destination
waygrid://system/waystation    # System waystation
```

## DAG Validation

The system validates DAGs for:

1. **Acyclicity**: No cycles allowed
2. **Reachability**: All nodes reachable from entry
3. **Fork/Join Matching**: Every Fork has corresponding Join
4. **Edge Consistency**: No dangling references

## Linear vs Parallel DAGs

### Detecting Linear DAGs

```scala
def isLinear: Boolean =
  !nodes.values.exists(n => n.isFork || n.isJoin) &&
  edges.groupBy(e => (e.from, e.guard)).forall(_._2.size <= 1)
```

Linear DAGs have:
- No Fork or Join nodes
- At most one outgoing edge per (node, guard) pair
- No conditional guards

### When to Use Each

| Use Linear When | Use Parallel When |
|----------------|-------------------|
| Sequential processing | Independent subtasks |
| Strict ordering needed | Performance critical |
| Simple workflows | Fan-out/fan-in patterns |
| Debugging/testing | Quorum decisions |
