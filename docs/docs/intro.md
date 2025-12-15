---
sidebar_position: 1
---

# Introduction to Waygrid Router

Waygrid Router is an event-driven graph routing system that enables sophisticated workflow orchestration through DAG (Directed Acyclic Graph) traversal. Built with Scala 3, it provides a robust foundation for routing events through complex processing pipelines.

## Key Features

- **DAG-based Routing**: Define complex workflows as directed acyclic graphs
- **Fork/Join Parallelism**: Execute branches in parallel with flexible synchronization strategies
- **Event Sourcing**: Full state history with vector clocks for distributed systems
- **Resilient Execution**: Built-in retry policies with exponential backoff
- **Pure Functional**: Deterministic FSM for testing and replay capabilities

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        Waygrid Router                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────┐    ┌──────────────┐    ┌─────────────────────┐    │
│  │ Origins │───►│   Scheduler  │───►│     Waystations     │    │
│  └─────────┘    └──────────────┘    └─────────────────────┘    │
│       │                                        │                │
│       ▼                                        ▼                │
│  ┌─────────┐                           ┌─────────────┐         │
│  │  HTTP   │                           │  TraversalFSM │        │
│  │  gRPC   │                           │  (Pure Logic) │        │
│  │  Kafka  │                           └─────────────┘         │
│  └─────────┘                                   │                │
│                                                ▼                │
│                                         ┌───────────────┐      │
│                                         │  Destinations │      │
│                                         │  (Webhook,WS) │      │
│                                         └───────────────┘      │
└─────────────────────────────────────────────────────────────────┘
```

## Quick Start

### Understanding the Traversal System

The traversal system is the core of Waygrid Router. It processes events through a DAG of nodes, where each node represents a processing step:

1. **DAG Definition**: Create a graph defining your workflow
2. **Traversal Execution**: The FSM processes signals and produces effects
3. **State Management**: Track progress with event sourcing

### Example: Simple Linear Workflow

```json
{
  "hash": "simple-workflow-v1",
  "entryPoints": ["validate"],
  "repeatPolicy": { "type": "NoRepeat" },
  "nodes": {
    "validate": {
      "id": "validate",
      "label": "Validate Input",
      "retryPolicy": { "type": "Linear", "delay": "1s", "maxRetries": 3 },
      "deliveryStrategy": { "type": "Immediate" },
      "address": "waygrid://processor/validator"
    },
    "process": {
      "id": "process",
      "label": "Process Data",
      "retryPolicy": { "type": "NoRetry" },
      "deliveryStrategy": { "type": "Immediate" },
      "address": "waygrid://processor/transformer"
    },
    "notify": {
      "id": "notify",
      "label": "Send Notification",
      "retryPolicy": { "type": "Exponential", "baseDelay": "1s", "maxRetries": 5 },
      "deliveryStrategy": { "type": "Immediate" },
      "address": "waygrid://destination/webhook"
    }
  },
  "edges": [
    { "from": "validate", "to": "process", "guard": "OnSuccess" },
    { "from": "process", "to": "notify", "guard": "OnSuccess" }
  ]
}
```

This workflow:
1. Validates input with 3 linear retries
2. On success, processes the data
3. Finally sends a notification with exponential backoff

## Core Concepts

| Concept | Description |
|---------|-------------|
| **DAG** | Directed Acyclic Graph defining the workflow |
| **Node** | A processing step with retry and delivery policies |
| **Edge** | Connection between nodes with conditional guards |
| **Signal** | External events driving the FSM |
| **Effect** | Actions produced by the FSM for execution |
| **TraversalState** | Immutable state tracking execution progress |

## What's Next?

- [**Traversal System Overview**](/docs/traversal/overview) - Deep dive into the traversal architecture
- [**DAG Model**](/docs/traversal/dag-model) - Understanding the DAG structure
- [**Fork/Join Parallelism**](/docs/traversal/node-types) - Parallel execution patterns
- [**Examples**](/docs/traversal/examples) - Real-world usage patterns
