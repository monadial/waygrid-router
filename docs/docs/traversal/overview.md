---
sidebar_position: 1
---

# Traversal System Overview

The Traversal System is the core execution engine of Waygrid Router. It processes events through a Directed Acyclic Graph (DAG) of nodes, managing state transitions, retries, parallel execution, and error handling.

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                     Traversal Execution Flow                        │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│   ┌──────────┐     ┌──────────────┐     ┌───────────────────┐      │
│   │  Signal  │────►│ TraversalFSM │────►│      Effect       │      │
│   │  (Input) │     │   (Pure)     │     │     (Output)      │      │
│   └──────────┘     └──────────────┘     └───────────────────┘      │
│                           │                       │                 │
│                           ▼                       ▼                 │
│                    ┌──────────────┐        ┌───────────┐           │
│                    │TraversalState│        │  Executor │           │
│                    │  (History)   │        │  (Actor)  │           │
│                    └──────────────┘        └───────────┘           │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

## Key Components

### 1. TraversalFSM (Finite State Machine)

The FSM is the **pure, deterministic core** of the traversal system. It:

- Processes `TraversalSignal` inputs
- Produces `TraversalEffect` outputs
- Updates `TraversalState` immutably
- Has **no side effects** - all I/O is described by effects

```scala
object TraversalFSM:
  def stateless[F[+_]: Monad](
    dag: Dag,
    nodeAddress: NodeAddress,
    now: Instant
  ): FSM[F, TraversalState, TraversalSignal, TraversalError, TraversalEffect]
```

**Why Pure?**
- Reproducible testing
- State replay capability
- Deterministic behavior
- Time is passed explicitly for testability

### 2. TraversalState

Tracks all execution progress with full event sourcing:

```scala
final case class TraversalState(
  traversalId: TraversalId,
  active: Set[NodeId],           // Currently executing nodes
  completed: Set[NodeId],        // Successfully finished nodes
  failed: Set[NodeId],           // Failed nodes
  retries: Map[NodeId, RetryAttempt],
  vectorClock: VectorClock,      // Causal ordering
  history: Vector[StateEvent],   // Event log
  forkScopes: Map[ForkId, ForkScope],     // Active forks
  branchStates: Map[BranchId, BranchState], // Branch tracking
  pendingJoins: Map[NodeId, PendingJoin]    // Join synchronization
)
```

### 3. Signals & Effects

**Signals** are inputs that drive state transitions:
- `Begin` - Start traversal
- `NodeSuccess` / `NodeFailure` - Node completion
- `ForkReached` / `JoinReached` - Parallel execution control
- `Cancel` / `Timeout` - Interruption handling

**Effects** are outputs describing actions to take:
- `DispatchNode` - Execute a node
- `DispatchNodes` - Fan-out parallel execution
- `Schedule` / `ScheduleRetry` - Delayed execution
- `Complete` / `Fail` - Terminal states

## Execution Modes

### Linear Traversal

The simplest mode - nodes execute sequentially:

```
A ──OnSuccess──► B ──OnSuccess──► C ──OnSuccess──► D
```

Only one node is active at any time. Each node must complete (or fail with exhausted retries) before the next begins.

### Parallel Traversal (Fork/Join)

Complex workflows with parallel branches:

```
        ┌──► B1 ──┐
        │        │
A ──► Fork ──► B2 ──► Join ──► D
        │        │
        └──► B3 ──┘
```

Multiple branches execute concurrently. The Join node synchronizes based on its strategy (AND, OR, or Quorum).

## State Lifecycle

```
          ┌────────────────┐
          │    Initial     │
          │ (No activity)  │
          └───────┬────────┘
                  │ Begin
                  ▼
          ┌────────────────┐
          │    Active      │◄──────┐
          │ (Processing)   │       │ Retry/Resume
          └───────┬────────┘───────┘
                  │
        ┌─────────┼─────────┐
        ▼         ▼         ▼
   ┌─────────┐ ┌─────────┐ ┌─────────┐
   │Complete │ │ Failed  │ │Canceled │
   └─────────┘ └─────────┘ └─────────┘
```

## Edge Guards

Edge guards control conditional routing:

| Guard | Description |
|-------|-------------|
| `OnSuccess` | Execute when upstream succeeds |
| `OnFailure` | Execute when upstream fails (after retries) |
| `OnTimeout` | Execute when upstream times out |
| `OnAny` | Execute on any result (for OR joins) |
| `Always` | Execute regardless of outcome |
| `Conditional` | Execute when predicate matches output |

## Retry Policies

Built-in resilience through configurable retry strategies:

```json
// No retries - fail immediately
{ "type": "NoRetry" }

// Linear backoff: 1s, 1s, 1s (3 retries)
{ "type": "Linear", "delay": "1s", "maxRetries": 3 }

// Exponential backoff: 1s, 2s, 4s, 8s, 16s (5 retries)
{ "type": "Exponential", "baseDelay": "1s", "maxRetries": 5 }
```

## Delivery Strategies

Control when nodes execute:

```json
// Execute immediately
{ "type": "Immediate" }

// Execute after delay
{ "type": "ScheduleAfter", "delay": "5m" }

// Execute at specific time
{ "type": "ScheduleAt", "time": "2024-01-15T10:00:00Z" }
```

## Event Sourcing

Every state change is recorded as an event:

```scala
sealed trait StateEvent:
  def node: NodeId
  def actor: NodeAddress
  def vectorClock: VectorClock

// Examples:
case class TraversalStarted(...)
case class NodeTraversalSucceeded(...)
case class ForkStarted(...)
case class JoinCompleted(...)
```

Benefits:
- **Audit trail**: Complete history of execution
- **Replay**: Reconstruct state from events
- **Debugging**: Understand exactly what happened
- **Distributed consistency**: Vector clocks track causality

## Next Steps

- [**DAG Model**](./dag-model) - Define workflow graphs
- [**Node Types**](./node-types) - Fork/Join patterns
- [**State Machine**](./state-machine) - FSM deep dive
- [**Signals & Effects**](./signals-effects) - API reference
