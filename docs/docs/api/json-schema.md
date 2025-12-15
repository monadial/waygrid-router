---
sidebar_position: 1
---

# JSON Schema Reference

Complete JSON schema definitions for all traversal-related data structures.

## DAG Schema

### Dag

The top-level workflow definition.

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["hash", "entryPoints", "repeatPolicy", "nodes", "edges"],
  "properties": {
    "hash": {
      "type": "string",
      "description": "Content-based identifier for the DAG"
    },
    "entryPoints": {
      "type": "array",
      "items": { "type": "string" },
      "minItems": 1,
      "description": "Valid starting node IDs"
    },
    "repeatPolicy": {
      "$ref": "#/definitions/RepeatPolicy"
    },
    "nodes": {
      "type": "object",
      "additionalProperties": { "$ref": "#/definitions/Node" }
    },
    "edges": {
      "type": "array",
      "items": { "$ref": "#/definitions/Edge" }
    },
    "timeout": {
      "type": "string",
      "pattern": "^\\d+[smhd]$",
      "description": "Global traversal timeout (e.g., '30m', '1h')"
    }
  }
}
```

### Node

A processing step in the DAG.

```json
{
  "type": "object",
  "required": ["id", "retryPolicy", "deliveryStrategy", "address"],
  "properties": {
    "id": {
      "type": "string",
      "description": "Unique node identifier"
    },
    "label": {
      "type": "string",
      "description": "Human-readable label"
    },
    "retryPolicy": {
      "$ref": "#/definitions/RetryPolicy"
    },
    "deliveryStrategy": {
      "$ref": "#/definitions/DeliveryStrategy"
    },
    "address": {
      "type": "string",
      "pattern": "^waygrid://",
      "description": "Service address URI"
    },
    "nodeType": {
      "$ref": "#/definitions/NodeType",
      "default": { "type": "Standard" }
    }
  }
}
```

### Edge

Connection between nodes.

```json
{
  "type": "object",
  "required": ["from", "to", "guard"],
  "properties": {
    "from": {
      "type": "string",
      "description": "Source node ID"
    },
    "to": {
      "type": "string",
      "description": "Target node ID"
    },
    "guard": {
      "$ref": "#/definitions/EdgeGuard"
    }
  }
}
```

## Type Definitions

### NodeType

```json
{
  "oneOf": [
    {
      "type": "object",
      "required": ["type"],
      "properties": {
        "type": { "const": "Standard" }
      }
    },
    {
      "type": "object",
      "required": ["type", "forkId"],
      "properties": {
        "type": { "const": "Fork" },
        "forkId": {
          "type": "string",
          "pattern": "^[0-9A-HJKMNP-TV-Z]{26}$",
          "description": "ULID linking Fork to Join"
        }
      }
    },
    {
      "type": "object",
      "required": ["type", "forkId", "strategy"],
      "properties": {
        "type": { "const": "Join" },
        "forkId": {
          "type": "string",
          "pattern": "^[0-9A-HJKMNP-TV-Z]{26}$"
        },
        "strategy": { "$ref": "#/definitions/JoinStrategy" },
        "timeout": {
          "type": "string",
          "pattern": "^\\d+[smhd]$"
        }
      }
    }
  ]
}
```

### JoinStrategy

```json
{
  "oneOf": [
    {
      "type": "object",
      "required": ["type"],
      "properties": {
        "type": { "const": "And" }
      },
      "description": "Wait for ALL branches"
    },
    {
      "type": "object",
      "required": ["type"],
      "properties": {
        "type": { "const": "Or" }
      },
      "description": "Continue on ANY branch"
    },
    {
      "type": "object",
      "required": ["type", "n"],
      "properties": {
        "type": { "const": "Quorum" },
        "n": {
          "type": "integer",
          "minimum": 1,
          "description": "Number of branches required"
        }
      }
    }
  ]
}
```

### EdgeGuard

```json
{
  "oneOf": [
    { "const": "OnSuccess" },
    { "const": "OnFailure" },
    { "const": "OnTimeout" },
    { "const": "OnAny" },
    { "const": "Always" },
    {
      "type": "object",
      "required": ["type", "condition"],
      "properties": {
        "type": { "const": "Conditional" },
        "condition": { "$ref": "#/definitions/Condition" }
      }
    }
  ]
}
```

### Condition

```json
{
  "type": "object",
  "required": ["field", "operator", "value"],
  "properties": {
    "field": {
      "type": "string",
      "description": "JSON path to field in output"
    },
    "operator": {
      "enum": ["equals", "notEquals", "contains", "greaterThan", "lessThan"]
    },
    "value": {
      "description": "Value to compare against"
    }
  }
}
```

### RetryPolicy

```json
{
  "oneOf": [
    {
      "type": "object",
      "required": ["type"],
      "properties": {
        "type": { "const": "NoRetry" }
      }
    },
    {
      "type": "object",
      "required": ["type", "delay", "maxRetries"],
      "properties": {
        "type": { "const": "Linear" },
        "delay": {
          "type": "string",
          "pattern": "^\\d+[smh]$",
          "description": "Fixed delay between retries"
        },
        "maxRetries": {
          "type": "integer",
          "minimum": 1
        }
      }
    },
    {
      "type": "object",
      "required": ["type", "baseDelay", "maxRetries"],
      "properties": {
        "type": { "const": "Exponential" },
        "baseDelay": {
          "type": "string",
          "pattern": "^\\d+[smh]$",
          "description": "Initial delay, doubles each retry"
        },
        "maxRetries": {
          "type": "integer",
          "minimum": 1
        }
      }
    }
  ]
}
```

### DeliveryStrategy

```json
{
  "oneOf": [
    {
      "type": "object",
      "required": ["type"],
      "properties": {
        "type": { "const": "Immediate" }
      }
    },
    {
      "type": "object",
      "required": ["type", "delay"],
      "properties": {
        "type": { "const": "ScheduleAfter" },
        "delay": {
          "type": "string",
          "pattern": "^\\d+[smhd]$"
        }
      }
    },
    {
      "type": "object",
      "required": ["type", "time"],
      "properties": {
        "type": { "const": "ScheduleAt" },
        "time": {
          "type": "string",
          "format": "date-time"
        }
      }
    }
  ]
}
```

### RepeatPolicy

```json
{
  "oneOf": [
    {
      "type": "object",
      "required": ["type"],
      "properties": {
        "type": { "const": "NoRepeat" }
      }
    },
    {
      "type": "object",
      "required": ["type", "every"],
      "properties": {
        "type": { "const": "Indefinitely" },
        "every": {
          "type": "string",
          "pattern": "^\\d+[smhd]$"
        }
      }
    },
    {
      "type": "object",
      "required": ["type", "every", "times"],
      "properties": {
        "type": { "const": "Times" },
        "every": { "type": "string" },
        "times": { "type": "integer", "minimum": 1 }
      }
    },
    {
      "type": "object",
      "required": ["type", "every", "until"],
      "properties": {
        "type": { "const": "Until" },
        "every": { "type": "string" },
        "until": { "type": "string", "format": "date-time" }
      }
    }
  ]
}
```

## Signal Schemas

### TraversalSignal

```json
{
  "oneOf": [
    {
      "type": "object",
      "required": ["type", "traversalId"],
      "properties": {
        "type": { "const": "Begin" },
        "traversalId": { "type": "string" },
        "entryNodeId": { "type": "string" }
      }
    },
    {
      "type": "object",
      "required": ["type", "traversalId", "nodeId"],
      "properties": {
        "type": { "const": "Resume" },
        "traversalId": { "type": "string" },
        "nodeId": { "type": "string" }
      }
    },
    {
      "type": "object",
      "required": ["type", "traversalId"],
      "properties": {
        "type": { "const": "Cancel" },
        "traversalId": { "type": "string" }
      }
    },
    {
      "type": "object",
      "required": ["type", "traversalId", "nodeId"],
      "properties": {
        "type": { "const": "NodeSuccess" },
        "traversalId": { "type": "string" },
        "nodeId": { "type": "string" },
        "output": { "type": "object" }
      }
    },
    {
      "type": "object",
      "required": ["type", "traversalId", "nodeId"],
      "properties": {
        "type": { "const": "NodeFailure" },
        "traversalId": { "type": "string" },
        "nodeId": { "type": "string" },
        "reason": { "type": "string" }
      }
    },
    {
      "type": "object",
      "required": ["type", "traversalId", "forkNodeId"],
      "properties": {
        "type": { "const": "ForkReached" },
        "traversalId": { "type": "string" },
        "forkNodeId": { "type": "string" }
      }
    },
    {
      "type": "object",
      "required": ["type", "traversalId", "joinNodeId", "branchId"],
      "properties": {
        "type": { "const": "JoinReached" },
        "traversalId": { "type": "string" },
        "joinNodeId": { "type": "string" },
        "branchId": { "type": "string" }
      }
    },
    {
      "type": "object",
      "required": ["type", "traversalId", "branchId", "forkId", "result"],
      "properties": {
        "type": { "const": "BranchComplete" },
        "traversalId": { "type": "string" },
        "branchId": { "type": "string" },
        "forkId": { "type": "string" },
        "result": { "$ref": "#/definitions/BranchResult" }
      }
    },
    {
      "type": "object",
      "required": ["type", "traversalId"],
      "properties": {
        "type": { "const": "TraversalTimeout" },
        "traversalId": { "type": "string" }
      }
    }
  ]
}
```

### BranchResult

```json
{
  "oneOf": [
    {
      "type": "object",
      "required": ["type"],
      "properties": {
        "type": { "const": "Success" },
        "output": { "type": "object" }
      }
    },
    {
      "type": "object",
      "required": ["type", "reason"],
      "properties": {
        "type": { "const": "Failure" },
        "reason": { "type": "string" }
      }
    },
    {
      "type": "object",
      "required": ["type"],
      "properties": {
        "type": { "const": "Timeout" }
      }
    }
  ]
}
```

## Effect Schemas

### TraversalEffect

```json
{
  "oneOf": [
    {
      "type": "object",
      "required": ["type", "traversalId", "nodeId"],
      "properties": {
        "type": { "const": "DispatchNode" },
        "traversalId": { "type": "string" },
        "nodeId": { "type": "string" },
        "branchesToCancel": {
          "type": "array",
          "items": {
            "type": "array",
            "items": [{ "type": "string" }, { "type": "string" }]
          }
        },
        "scheduleTraversalTimeout": {
          "type": "array",
          "items": [{ "type": "string" }, { "type": "string", "format": "date-time" }]
        }
      }
    },
    {
      "type": "object",
      "required": ["type", "traversalId", "nodes"],
      "properties": {
        "type": { "const": "DispatchNodes" },
        "traversalId": { "type": "string" },
        "nodes": {
          "type": "array",
          "items": {
            "type": "array",
            "items": [{ "type": "string" }, { "type": "string" }]
          }
        },
        "joinTimeout": { "type": "string", "format": "date-time" },
        "joinNodeId": { "type": "string" }
      }
    },
    {
      "type": "object",
      "required": ["type", "traversalId", "scheduledAt", "nodeId"],
      "properties": {
        "type": { "const": "Schedule" },
        "traversalId": { "type": "string" },
        "scheduledAt": { "type": "string", "format": "date-time" },
        "nodeId": { "type": "string" }
      }
    },
    {
      "type": "object",
      "required": ["type", "traversalId"],
      "properties": {
        "type": { "const": "Complete" },
        "traversalId": { "type": "string" },
        "cancelTimeoutId": { "type": "string" }
      }
    },
    {
      "type": "object",
      "required": ["type", "traversalId"],
      "properties": {
        "type": { "const": "Fail" },
        "traversalId": { "type": "string" },
        "cancelTimeoutId": { "type": "string" }
      }
    },
    {
      "type": "object",
      "required": ["type", "traversalId"],
      "properties": {
        "type": { "const": "NoOp" },
        "traversalId": { "type": "string" }
      }
    }
  ]
}
```

## State Schema

### TraversalState

```json
{
  "type": "object",
  "required": ["traversalId", "active", "completed", "failed", "retries", "remainingNodes"],
  "properties": {
    "traversalId": { "type": "string" },
    "active": {
      "type": "array",
      "items": { "type": "string" },
      "description": "Currently executing nodes"
    },
    "completed": {
      "type": "array",
      "items": { "type": "string" }
    },
    "failed": {
      "type": "array",
      "items": { "type": "string" }
    },
    "retries": {
      "type": "object",
      "additionalProperties": { "type": "integer" }
    },
    "remainingNodes": { "type": "integer" },
    "forkScopes": {
      "type": "object",
      "additionalProperties": { "$ref": "#/definitions/ForkScope" }
    },
    "branchStates": {
      "type": "object",
      "additionalProperties": { "$ref": "#/definitions/BranchState" }
    },
    "pendingJoins": {
      "type": "object",
      "additionalProperties": { "$ref": "#/definitions/PendingJoin" }
    },
    "nodeToBranch": {
      "type": "object",
      "additionalProperties": { "type": "string" }
    },
    "traversalTimeoutId": { "type": "string" },
    "stateVersion": { "type": "integer" },
    "history": {
      "type": "array",
      "items": { "$ref": "#/definitions/StateEvent" }
    }
  }
}
```

### ForkScope

```json
{
  "type": "object",
  "required": ["forkId", "forkNodeId", "branches", "startedAt"],
  "properties": {
    "forkId": { "type": "string" },
    "forkNodeId": { "type": "string" },
    "branches": {
      "type": "array",
      "items": { "type": "string" }
    },
    "parentScope": { "type": "string" },
    "startedAt": { "type": "string", "format": "date-time" },
    "timeout": { "type": "string", "format": "date-time" }
  }
}
```

### BranchState

```json
{
  "type": "object",
  "required": ["branchId", "forkId", "entryNode", "status"],
  "properties": {
    "branchId": { "type": "string" },
    "forkId": { "type": "string" },
    "entryNode": { "type": "string" },
    "currentNode": { "type": "string" },
    "status": {
      "enum": ["active", "completed", "failed", "canceled", "timedOut"]
    },
    "startedAt": { "type": "string", "format": "date-time" },
    "completedAt": { "type": "string", "format": "date-time" }
  }
}
```

### PendingJoin

```json
{
  "type": "object",
  "required": ["joinNodeId", "forkId", "strategy", "totalBranches", "completedBranches", "failedBranches", "canceledBranches"],
  "properties": {
    "joinNodeId": { "type": "string" },
    "forkId": { "type": "string" },
    "strategy": { "$ref": "#/definitions/JoinStrategy" },
    "totalBranches": {
      "type": "array",
      "items": { "type": "string" }
    },
    "completedBranches": {
      "type": "array",
      "items": { "type": "string" }
    },
    "failedBranches": {
      "type": "array",
      "items": { "type": "string" }
    },
    "canceledBranches": {
      "type": "array",
      "items": { "type": "string" }
    },
    "timeout": { "type": "string", "format": "date-time" }
  }
}
```

## Duration Format

Durations are specified as strings with format: `<number><unit>`

| Unit | Description | Example |
|------|-------------|---------|
| `s` | Seconds | `30s` |
| `m` | Minutes | `5m` |
| `h` | Hours | `1h` |
| `d` | Days | `1d` |

Examples:
- `"500ms"` - 500 milliseconds
- `"5s"` - 5 seconds
- `"30m"` - 30 minutes
- `"1h"` - 1 hour
- `"7d"` - 7 days
