---
sidebar_position: 7
---

# Examples & Patterns

This page provides complete, working examples of common traversal patterns.

## Basic Patterns

### Simple Sequential Workflow

A straightforward A → B → C workflow:

```json
{
  "hash": "sequential-v1",
  "entryPoints": ["step-a"],
  "repeatPolicy": { "type": "NoRepeat" },
  "nodes": {
    "step-a": {
      "id": "step-a",
      "label": "Step A",
      "retryPolicy": { "type": "NoRetry" },
      "deliveryStrategy": { "type": "Immediate" },
      "address": "waygrid://processor/step-a",
      "nodeType": { "type": "Standard" }
    },
    "step-b": {
      "id": "step-b",
      "label": "Step B",
      "retryPolicy": { "type": "NoRetry" },
      "deliveryStrategy": { "type": "Immediate" },
      "address": "waygrid://processor/step-b",
      "nodeType": { "type": "Standard" }
    },
    "step-c": {
      "id": "step-c",
      "label": "Step C",
      "retryPolicy": { "type": "NoRetry" },
      "deliveryStrategy": { "type": "Immediate" },
      "address": "waygrid://processor/step-c",
      "nodeType": { "type": "Standard" }
    }
  },
  "edges": [
    { "from": "step-a", "to": "step-b", "guard": "OnSuccess" },
    { "from": "step-b", "to": "step-c", "guard": "OnSuccess" }
  ]
}
```

### With Error Handling

Add fallback paths for failures:

```json
{
  "hash": "with-error-handling-v1",
  "entryPoints": ["process"],
  "repeatPolicy": { "type": "NoRepeat" },
  "nodes": {
    "process": {
      "id": "process",
      "label": "Process Data",
      "retryPolicy": {
        "type": "Exponential",
        "baseDelay": "1s",
        "maxRetries": 3
      },
      "deliveryStrategy": { "type": "Immediate" },
      "address": "waygrid://processor/main",
      "nodeType": { "type": "Standard" }
    },
    "success-handler": {
      "id": "success-handler",
      "label": "Handle Success",
      "retryPolicy": { "type": "NoRetry" },
      "deliveryStrategy": { "type": "Immediate" },
      "address": "waygrid://destination/success",
      "nodeType": { "type": "Standard" }
    },
    "error-handler": {
      "id": "error-handler",
      "label": "Handle Error",
      "retryPolicy": { "type": "NoRetry" },
      "deliveryStrategy": { "type": "Immediate" },
      "address": "waygrid://destination/error-alert",
      "nodeType": { "type": "Standard" }
    }
  },
  "edges": [
    { "from": "process", "to": "success-handler", "guard": "OnSuccess" },
    { "from": "process", "to": "error-handler", "guard": "OnFailure" }
  ]
}
```

### Scheduled Execution

Delayed start with scheduled delivery:

```json
{
  "hash": "scheduled-workflow-v1",
  "entryPoints": ["scheduled-task"],
  "repeatPolicy": { "type": "NoRepeat" },
  "nodes": {
    "scheduled-task": {
      "id": "scheduled-task",
      "label": "Run After Delay",
      "retryPolicy": { "type": "NoRetry" },
      "deliveryStrategy": {
        "type": "ScheduleAfter",
        "delay": "5m"
      },
      "address": "waygrid://processor/delayed-task",
      "nodeType": { "type": "Standard" }
    },
    "follow-up": {
      "id": "follow-up",
      "label": "Follow-up Task",
      "retryPolicy": { "type": "NoRetry" },
      "deliveryStrategy": { "type": "Immediate" },
      "address": "waygrid://processor/follow-up",
      "nodeType": { "type": "Standard" }
    }
  },
  "edges": [
    { "from": "scheduled-task", "to": "follow-up", "guard": "OnSuccess" }
  ]
}
```

## Fork/Join Patterns

### Parallel Notifications

Send notifications through multiple channels simultaneously:

```json
{
  "hash": "parallel-notify-v1",
  "entryPoints": ["start"],
  "repeatPolicy": { "type": "NoRepeat" },
  "nodes": {
    "start": {
      "id": "start",
      "label": "Receive Event",
      "retryPolicy": { "type": "NoRetry" },
      "deliveryStrategy": { "type": "Immediate" },
      "address": "waygrid://origin/http",
      "nodeType": { "type": "Standard" }
    },
    "fork-notifications": {
      "id": "fork-notifications",
      "label": "Fan Out Notifications",
      "retryPolicy": { "type": "NoRetry" },
      "deliveryStrategy": { "type": "Immediate" },
      "address": "waygrid://system/waystation",
      "nodeType": {
        "type": "Fork",
        "forkId": "01HQ001NOTIFY00000000001"
      }
    },
    "send-email": {
      "id": "send-email",
      "label": "Send Email",
      "retryPolicy": {
        "type": "Exponential",
        "baseDelay": "2s",
        "maxRetries": 3
      },
      "deliveryStrategy": { "type": "Immediate" },
      "address": "waygrid://destination/email",
      "nodeType": { "type": "Standard" }
    },
    "send-sms": {
      "id": "send-sms",
      "label": "Send SMS",
      "retryPolicy": {
        "type": "Linear",
        "delay": "5s",
        "maxRetries": 2
      },
      "deliveryStrategy": { "type": "Immediate" },
      "address": "waygrid://destination/sms",
      "nodeType": { "type": "Standard" }
    },
    "send-push": {
      "id": "send-push",
      "label": "Send Push Notification",
      "retryPolicy": { "type": "NoRetry" },
      "deliveryStrategy": { "type": "Immediate" },
      "address": "waygrid://destination/push",
      "nodeType": { "type": "Standard" }
    },
    "join-notifications": {
      "id": "join-notifications",
      "label": "Wait for All",
      "retryPolicy": { "type": "NoRetry" },
      "deliveryStrategy": { "type": "Immediate" },
      "address": "waygrid://system/waystation",
      "nodeType": {
        "type": "Join",
        "forkId": "01HQ001NOTIFY00000000001",
        "strategy": { "type": "And" },
        "timeout": "2m"
      }
    },
    "complete": {
      "id": "complete",
      "label": "Mark Complete",
      "retryPolicy": { "type": "NoRetry" },
      "deliveryStrategy": { "type": "Immediate" },
      "address": "waygrid://destination/webhook",
      "nodeType": { "type": "Standard" }
    }
  },
  "edges": [
    { "from": "start", "to": "fork-notifications", "guard": "OnSuccess" },
    { "from": "fork-notifications", "to": "send-email", "guard": "OnSuccess" },
    { "from": "fork-notifications", "to": "send-sms", "guard": "OnSuccess" },
    { "from": "fork-notifications", "to": "send-push", "guard": "OnSuccess" },
    { "from": "send-email", "to": "join-notifications", "guard": "OnSuccess" },
    { "from": "send-sms", "to": "join-notifications", "guard": "OnSuccess" },
    { "from": "send-push", "to": "join-notifications", "guard": "OnSuccess" },
    { "from": "join-notifications", "to": "complete", "guard": "OnSuccess" }
  ]
}
```

### First Responder (OR Join)

Use the first successful result:

```json
{
  "hash": "first-responder-v1",
  "entryPoints": ["request"],
  "repeatPolicy": { "type": "NoRepeat" },
  "nodes": {
    "request": {
      "id": "request",
      "label": "Receive Request",
      "retryPolicy": { "type": "NoRetry" },
      "deliveryStrategy": { "type": "Immediate" },
      "address": "waygrid://origin/http",
      "nodeType": { "type": "Standard" }
    },
    "fork-providers": {
      "id": "fork-providers",
      "label": "Query Providers",
      "retryPolicy": { "type": "NoRetry" },
      "deliveryStrategy": { "type": "Immediate" },
      "address": "waygrid://system/waystation",
      "nodeType": {
        "type": "Fork",
        "forkId": "01HQ001PROVIDERS000000001"
      }
    },
    "provider-a": {
      "id": "provider-a",
      "label": "Query Provider A",
      "retryPolicy": { "type": "NoRetry" },
      "deliveryStrategy": { "type": "Immediate" },
      "address": "waygrid://processor/provider-a",
      "nodeType": { "type": "Standard" }
    },
    "provider-b": {
      "id": "provider-b",
      "label": "Query Provider B",
      "retryPolicy": { "type": "NoRetry" },
      "deliveryStrategy": { "type": "Immediate" },
      "address": "waygrid://processor/provider-b",
      "nodeType": { "type": "Standard" }
    },
    "provider-c": {
      "id": "provider-c",
      "label": "Query Provider C",
      "retryPolicy": { "type": "NoRetry" },
      "deliveryStrategy": { "type": "Immediate" },
      "address": "waygrid://processor/provider-c",
      "nodeType": { "type": "Standard" }
    },
    "join-first": {
      "id": "join-first",
      "label": "Use First Result",
      "retryPolicy": { "type": "NoRetry" },
      "deliveryStrategy": { "type": "Immediate" },
      "address": "waygrid://system/waystation",
      "nodeType": {
        "type": "Join",
        "forkId": "01HQ001PROVIDERS000000001",
        "strategy": { "type": "Or" },
        "timeout": "10s"
      }
    },
    "respond": {
      "id": "respond",
      "label": "Send Response",
      "retryPolicy": { "type": "NoRetry" },
      "deliveryStrategy": { "type": "Immediate" },
      "address": "waygrid://destination/http-response",
      "nodeType": { "type": "Standard" }
    }
  },
  "edges": [
    { "from": "request", "to": "fork-providers", "guard": "OnSuccess" },
    { "from": "fork-providers", "to": "provider-a", "guard": "OnSuccess" },
    { "from": "fork-providers", "to": "provider-b", "guard": "OnSuccess" },
    { "from": "fork-providers", "to": "provider-c", "guard": "OnSuccess" },
    { "from": "provider-a", "to": "join-first", "guard": "OnSuccess" },
    { "from": "provider-b", "to": "join-first", "guard": "OnSuccess" },
    { "from": "provider-c", "to": "join-first", "guard": "OnSuccess" },
    { "from": "join-first", "to": "respond", "guard": "OnSuccess" }
  ]
}
```

### Quorum Decision

Require 2-of-3 consensus:

```json
{
  "hash": "quorum-decision-v1",
  "entryPoints": ["start"],
  "repeatPolicy": { "type": "NoRepeat" },
  "nodes": {
    "start": {
      "id": "start",
      "label": "Start Vote",
      "retryPolicy": { "type": "NoRetry" },
      "deliveryStrategy": { "type": "Immediate" },
      "address": "waygrid://origin/http",
      "nodeType": { "type": "Standard" }
    },
    "fork-voters": {
      "id": "fork-voters",
      "label": "Fan Out to Voters",
      "retryPolicy": { "type": "NoRetry" },
      "deliveryStrategy": { "type": "Immediate" },
      "address": "waygrid://system/waystation",
      "nodeType": {
        "type": "Fork",
        "forkId": "01HQ001VOTERS0000000001"
      }
    },
    "voter-1": {
      "id": "voter-1",
      "label": "Voter 1",
      "retryPolicy": { "type": "NoRetry" },
      "deliveryStrategy": { "type": "Immediate" },
      "address": "waygrid://processor/voter-1",
      "nodeType": { "type": "Standard" }
    },
    "voter-2": {
      "id": "voter-2",
      "label": "Voter 2",
      "retryPolicy": { "type": "NoRetry" },
      "deliveryStrategy": { "type": "Immediate" },
      "address": "waygrid://processor/voter-2",
      "nodeType": { "type": "Standard" }
    },
    "voter-3": {
      "id": "voter-3",
      "label": "Voter 3",
      "retryPolicy": { "type": "NoRetry" },
      "deliveryStrategy": { "type": "Immediate" },
      "address": "waygrid://processor/voter-3",
      "nodeType": { "type": "Standard" }
    },
    "quorum-join": {
      "id": "quorum-join",
      "label": "Reach Quorum (2/3)",
      "retryPolicy": { "type": "NoRetry" },
      "deliveryStrategy": { "type": "Immediate" },
      "address": "waygrid://system/waystation",
      "nodeType": {
        "type": "Join",
        "forkId": "01HQ001VOTERS0000000001",
        "strategy": { "type": "Quorum", "n": 2 },
        "timeout": "30s"
      }
    },
    "apply-decision": {
      "id": "apply-decision",
      "label": "Apply Decision",
      "retryPolicy": { "type": "NoRetry" },
      "deliveryStrategy": { "type": "Immediate" },
      "address": "waygrid://processor/apply",
      "nodeType": { "type": "Standard" }
    }
  },
  "edges": [
    { "from": "start", "to": "fork-voters", "guard": "OnSuccess" },
    { "from": "fork-voters", "to": "voter-1", "guard": "OnSuccess" },
    { "from": "fork-voters", "to": "voter-2", "guard": "OnSuccess" },
    { "from": "fork-voters", "to": "voter-3", "guard": "OnSuccess" },
    { "from": "voter-1", "to": "quorum-join", "guard": "OnSuccess" },
    { "from": "voter-2", "to": "quorum-join", "guard": "OnSuccess" },
    { "from": "voter-3", "to": "quorum-join", "guard": "OnSuccess" },
    { "from": "quorum-join", "to": "apply-decision", "guard": "OnSuccess" }
  ]
}
```

## Real-World Scenarios

### E-Commerce Order Processing

Complete order workflow with parallel inventory/payment:

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
    "validate-order": {
      "id": "validate-order",
      "label": "Validate Order",
      "retryPolicy": {
        "type": "Linear",
        "delay": "500ms",
        "maxRetries": 3
      },
      "deliveryStrategy": { "type": "Immediate" },
      "address": "waygrid://processor/order-validator",
      "nodeType": { "type": "Standard" }
    },
    "fork-processing": {
      "id": "fork-processing",
      "label": "Start Parallel Processing",
      "retryPolicy": { "type": "NoRetry" },
      "deliveryStrategy": { "type": "Immediate" },
      "address": "waygrid://system/waystation",
      "nodeType": {
        "type": "Fork",
        "forkId": "01HQ001ORDERPROC00000001"
      }
    },
    "reserve-inventory": {
      "id": "reserve-inventory",
      "label": "Reserve Inventory",
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
    "join-processing": {
      "id": "join-processing",
      "label": "Wait for Inventory & Payment",
      "retryPolicy": { "type": "NoRetry" },
      "deliveryStrategy": { "type": "Immediate" },
      "address": "waygrid://system/waystation",
      "nodeType": {
        "type": "Join",
        "forkId": "01HQ001ORDERPROC00000001",
        "strategy": { "type": "And" },
        "timeout": "5m"
      }
    },
    "create-shipment": {
      "id": "create-shipment",
      "label": "Create Shipment",
      "retryPolicy": {
        "type": "Linear",
        "delay": "3s",
        "maxRetries": 3
      },
      "deliveryStrategy": { "type": "Immediate" },
      "address": "waygrid://processor/shipping",
      "nodeType": { "type": "Standard" }
    },
    "send-confirmation": {
      "id": "send-confirmation",
      "label": "Send Order Confirmation",
      "retryPolicy": {
        "type": "Exponential",
        "baseDelay": "1s",
        "maxRetries": 5
      },
      "deliveryStrategy": { "type": "Immediate" },
      "address": "waygrid://destination/email",
      "nodeType": { "type": "Standard" }
    },
    "handle-validation-error": {
      "id": "handle-validation-error",
      "label": "Handle Validation Error",
      "retryPolicy": { "type": "NoRetry" },
      "deliveryStrategy": { "type": "Immediate" },
      "address": "waygrid://destination/error-webhook",
      "nodeType": { "type": "Standard" }
    },
    "handle-processing-error": {
      "id": "handle-processing-error",
      "label": "Handle Processing Error",
      "retryPolicy": { "type": "NoRetry" },
      "deliveryStrategy": { "type": "Immediate" },
      "address": "waygrid://processor/rollback",
      "nodeType": { "type": "Standard" }
    }
  },
  "edges": [
    { "from": "receive-order", "to": "validate-order", "guard": "OnSuccess" },
    { "from": "validate-order", "to": "fork-processing", "guard": "OnSuccess" },
    { "from": "validate-order", "to": "handle-validation-error", "guard": "OnFailure" },
    { "from": "fork-processing", "to": "reserve-inventory", "guard": "OnSuccess" },
    { "from": "fork-processing", "to": "process-payment", "guard": "OnSuccess" },
    { "from": "reserve-inventory", "to": "join-processing", "guard": "OnSuccess" },
    { "from": "process-payment", "to": "join-processing", "guard": "OnSuccess" },
    { "from": "join-processing", "to": "create-shipment", "guard": "OnSuccess" },
    { "from": "join-processing", "to": "handle-processing-error", "guard": "OnFailure" },
    { "from": "create-shipment", "to": "send-confirmation", "guard": "OnSuccess" }
  ]
}
```

### Data Pipeline with Timeout

ETL workflow with global timeout:

```json
{
  "hash": "etl-pipeline-v1",
  "entryPoints": ["extract"],
  "repeatPolicy": { "type": "NoRepeat" },
  "timeout": "1h",
  "nodes": {
    "extract": {
      "id": "extract",
      "label": "Extract Data",
      "retryPolicy": {
        "type": "Exponential",
        "baseDelay": "5s",
        "maxRetries": 3
      },
      "deliveryStrategy": { "type": "Immediate" },
      "address": "waygrid://processor/extract",
      "nodeType": { "type": "Standard" }
    },
    "transform": {
      "id": "transform",
      "label": "Transform Data",
      "retryPolicy": {
        "type": "Linear",
        "delay": "10s",
        "maxRetries": 2
      },
      "deliveryStrategy": { "type": "Immediate" },
      "address": "waygrid://processor/transform",
      "nodeType": { "type": "Standard" }
    },
    "load": {
      "id": "load",
      "label": "Load Data",
      "retryPolicy": {
        "type": "Exponential",
        "baseDelay": "5s",
        "maxRetries": 5
      },
      "deliveryStrategy": { "type": "Immediate" },
      "address": "waygrid://processor/load",
      "nodeType": { "type": "Standard" }
    },
    "notify-success": {
      "id": "notify-success",
      "label": "Notify Success",
      "retryPolicy": { "type": "NoRetry" },
      "deliveryStrategy": { "type": "Immediate" },
      "address": "waygrid://destination/slack",
      "nodeType": { "type": "Standard" }
    },
    "notify-failure": {
      "id": "notify-failure",
      "label": "Notify Failure",
      "retryPolicy": { "type": "NoRetry" },
      "deliveryStrategy": { "type": "Immediate" },
      "address": "waygrid://destination/pagerduty",
      "nodeType": { "type": "Standard" }
    }
  },
  "edges": [
    { "from": "extract", "to": "transform", "guard": "OnSuccess" },
    { "from": "extract", "to": "notify-failure", "guard": "OnFailure" },
    { "from": "transform", "to": "load", "guard": "OnSuccess" },
    { "from": "transform", "to": "notify-failure", "guard": "OnFailure" },
    { "from": "load", "to": "notify-success", "guard": "OnSuccess" },
    { "from": "load", "to": "notify-failure", "guard": "OnFailure" }
  ]
}
```

## Pattern Summary

| Pattern | Join Strategy | Use Case |
|---------|---------------|----------|
| **Parallel All** | `And` | All paths must complete |
| **First Wins** | `Or` | Use fastest responder |
| **Quorum** | `Quorum(n)` | Distributed consensus |
| **Scatter-Gather** | `And` | Collect all results |
| **Race** | `Or` | Timeout-sensitive queries |
