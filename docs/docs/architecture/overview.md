---
sidebar_position: 1
---

# Architecture Overview

Waygrid is a **distributed, event-driven graph routing system** that enables complex workflow orchestration through directed acyclic graphs (DAGs). Built with Scala 3 and functional programming principles, it provides a scalable, fault-tolerant platform for routing events between services.

## System Vision

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            WAYGRID PLATFORM                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   ┌─────────────┐    ┌─────────────────────────┐    ┌─────────────────┐    │
│   │   ORIGINS   │───▶│    SYSTEM SERVICES      │───▶│  DESTINATIONS   │    │
│   │             │    │                         │    │                 │    │
│   │ • HTTP API  │    │  ┌─────────────────┐   │    │ • Webhooks      │    │
│   │ • Kafka     │    │  │   Waystation    │   │    │ • WebSockets    │    │
│   │ • gRPC      │    │  │  (Orchestrator) │   │    │ • Kafka         │    │
│   └─────────────┘    │  └────────┬────────┘   │    └─────────────────┘    │
│                      │           │            │                           │
│                      │  ┌────────▼────────┐   │    ┌─────────────────┐    │
│                      │  │    Scheduler    │   │    │   PROCESSORS    │    │
│                      │  │ (Time-based)    │   │    │                 │    │
│                      │  └─────────────────┘   │    │ • OpenAI/LLM    │    │
│                      │                        │    │ • Lambda        │    │
│                      │  ┌─────────────────┐   │    │ • Transform     │    │
│                      │  │    Topology     │   │    └─────────────────┘    │
│                      │  │   (Discovery)   │   │                           │
│                      │  └─────────────────┘   │                           │
│                      └────────────────────────┘                           │
│                                                                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                           INFRASTRUCTURE                                     │
│   ┌───────────────┐  ┌───────────────┐  ┌───────────────┐  ┌────────────┐  │
│   │  Kafka Cluster│  │ Redis Cluster │  │  PostgreSQL   │  │ OpenTelemetry│ │
│   │  (Events)     │  │   (State)     │  │ (Persistence) │  │ (Observability)│
│   └───────────────┘  └───────────────┘  └───────────────┘  └────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Key Characteristics

### Event-Driven Architecture

Waygrid is fundamentally event-driven. Every action in the system is triggered by an event, and every action produces events. This enables:

- **Loose coupling** between services
- **Horizontal scalability** through partitioned event streams
- **Fault tolerance** via event replay and persistence
- **Auditability** through complete event history

### Graph-Based Routing

Unlike traditional point-to-point messaging, Waygrid routes events through **Directed Acyclic Graphs (DAGs)**:

```
        ┌──────────────┐
        │   Entry      │
        │   Point      │
        └──────┬───────┘
               │
        ┌──────▼───────┐
        │  Processor   │
        │   (OpenAI)   │
        └──────┬───────┘
               │
       ┌───────┴───────┐
       │               │
┌──────▼──────┐ ┌──────▼──────┐
│  OnSuccess  │ │  OnFailure  │
│  (Webhook)  │ │  (Alert)    │
└─────────────┘ └─────────────┘
```

Benefits:
- **Conditional routing** based on node output
- **Parallel execution** via fork/join patterns
- **Error handling** as first-class routing concern
- **Retry policies** per node

### Functional & Type-Safe

Built with Scala 3 and Cats Effect:

- **Pure functional programming** with referential transparency
- **Type-safe** value objects prevent ID confusion
- **Tagless final** encoding for testable algebras
- **Effect system** for controlled side effects

## Technology Stack

| Layer | Technology | Purpose |
|-------|------------|---------|
| **Language** | Scala 3.7 | Type-safe functional programming |
| **Effect System** | Cats Effect 3 | Async/concurrent programming |
| **HTTP** | http4s | Functional HTTP server/client |
| **Streaming** | fs2 + fs2-kafka | Functional stream processing |
| **JSON** | Circe | Type-safe JSON encoding/decoding |
| **Messaging** | Apache Kafka | Distributed event streaming |
| **Caching** | Redis Cluster | Distributed state management |
| **Persistence** | PostgreSQL | Durable storage |
| **Observability** | OpenTelemetry | Distributed tracing & metrics |
| **Containerization** | Docker | Service packaging |
| **Orchestration** | Kubernetes | Production deployment |

## Module Architecture

Waygrid follows a **modular monorepo** structure with clear separation of concerns:

```
modules/
├── common/                    # Shared foundations
│   ├── domain/               # Pure domain models (no effects)
│   └── application/          # Application infrastructure
│
├── origin/                    # Event ingestion
│   ├── http/                 # REST API
│   ├── kafka/                # Kafka consumer
│   └── grpc/                 # gRPC server
│
├── processor/                 # Event transformation
│   ├── openai/               # LLM integration
│   └── lambda/               # Serverless functions
│
├── destination/               # Event delivery
│   ├── webhook/              # HTTP webhooks
│   ├── websocket/            # WebSocket streaming
│   └── blackhole/            # Testing sink
│
└── system/                    # Platform services
    ├── waystation/           # Traversal orchestration
    ├── scheduler/            # Time-based scheduling
    ├── topology/             # Service discovery
    ├── iam/                  # Identity management
    ├── history/              # Event audit
    └── ...                   # Other system services
```

## Core Abstractions

### 1. Spec → DAG → Traversal

The workflow lifecycle:

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│    Spec     │────▶│     DAG     │────▶│  Traversal  │
│  (User Input)│     │ (Compiled)  │     │ (Execution) │
└─────────────┘     └─────────────┘     └─────────────┘
     JSON              Immutable           Stateful
   definition         structure           execution
```

- **Spec**: High-level workflow definition (what users write)
- **DAG**: Compiled, content-addressed graph with stable node IDs
- **Traversal**: Runtime execution state tracking progress

### 2. Envelope → Transport → Delivery

Message flow:

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│ DomainEnvelope  │────▶│TransportEnvelope│────▶│  Kafka Message  │
│ (Type-safe)     │     │ (Serializable)  │     │   (Wire format) │
└─────────────────┘     └─────────────────┘     └─────────────────┘
    In-process             JSON-encoded           Binary payload
    type safety            with metadata          with headers
```

### 3. Signal → FSM → Effect

State machine pattern:

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│ TraversalSignal │────▶│  TraversalFSM   │────▶│ TraversalEffect │
│    (Input)      │     │    (Pure)       │     │    (Output)     │
└─────────────────┘     └─────────────────┘     └─────────────────┘
   • Begin               Deterministic         • DispatchNode
   • NodeSuccess         state transitions     • Schedule
   • NodeFailure                               • Complete
   • Resume                                    • Fail
```

## Service Communication

All services communicate via **Kafka topics** with a consistent naming scheme:

```
Topic: <component>.<service>
Examples:
  • origin.http         - HTTP origin events
  • system.waystation   - Waystation inbound
  • system.scheduler    - Scheduler tasks
  • destination.webhook - Webhook deliveries
```

### Message Flow Example

```
1. HTTP Origin receives request
   └─▶ Publishes: TraversalRequested → system.waystation

2. Waystation receives traversal request
   └─▶ Publishes: NodeTraversalRequested → processor.openai

3. OpenAI Processor completes
   └─▶ Publishes: NodeTraversalSucceeded → system.waystation

4. Waystation continues traversal
   └─▶ Publishes: NodeTraversalRequested → destination.webhook

5. Webhook Destination delivers
   └─▶ Publishes: NodeTraversalSucceeded → system.waystation

6. Waystation completes
   └─▶ Publishes: TraversalCompleted → (optional: history)
```

## Design Principles

### 1. Separation of Concerns

Each module has a single responsibility:
- **Origins** only ingest events
- **Processors** only transform events
- **Destinations** only deliver events
- **System services** only provide platform capabilities

### 2. Content-Addressed DAGs

DAGs are identified by their content hash:
- Same specification → same hash
- Enables deduplication and caching
- Immutable after compilation

### 3. Idempotent Operations

All operations are designed to be idempotent:
- Safe to retry on failure
- Consistent state after multiple executions
- At-least-once delivery with deduplication

### 4. Observable by Default

Every service exports:
- **Traces** via OpenTelemetry
- **Metrics** via Prometheus format
- **Logs** via structured JSON

## Getting Started

1. **[Core Concepts](./core-concepts)** - Understand the fundamental abstractions
2. **[Components](./components)** - Explore available components
3. **[System Services](./system-services)** - Learn about platform services
4. **[Event Flow](./event-flow)** - See how events traverse the system
5. **[Infrastructure](./infrastructure)** - Set up the required infrastructure
6. **[Deployment](./deployment)** - Deploy to production

## See Also

- [Traversal System](/docs/traversal/overview) - Deep dive into DAG traversal
- [SDK Overview](/docs/sdk/overview) - Client SDKs for integration
- [API Reference](/docs/api/spec-schema) - JSON Schema reference
