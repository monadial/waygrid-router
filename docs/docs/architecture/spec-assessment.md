# Spec DSL Assessment

**Files:**
- `modules/common/domain/src/main/scala/.../spec/Spec.scala` (47 lines)
- `modules/common/domain/src/main/scala/.../spec/Node.scala` (180 lines)

**Total Lines:** 227
**Complexity:** Low-Medium
**Quality Score:** 9/10

---

## 1. Executive Summary

The `Spec` DSL provides a **high-level, user-friendly interface** for defining DAG traversals. It abstracts away the complexity of node ID generation, edge management, and validation, allowing users to focus on the routing logic. The design follows functional programming best practices with sealed traits, immutable data, and convenient factory methods.

| Aspect | Score | Notes |
|--------|-------|-------|
| **API Design** | 9/10 | Clean, intuitive factory methods |
| **Type Safety** | 10/10 | Sealed traits, exhaustive pattern matching |
| **Documentation** | 8/10 | Good ScalaDoc, could use more examples |
| **Extensibility** | 9/10 | Easy to add new node types |
| **Usability** | 9/10 | Minimal boilerplate, sensible defaults |

---

## 2. Architecture Overview

### 2.1 Type Hierarchy

```
Spec
 └── entryPoints: NonEmptyList[Node]
 └── repeatPolicy: RepeatPolicy

Node (sealed trait)
 ├── Standard
 │    ├── onSuccess: Option[Node]
 │    ├── onFailure: Option[Node]
 │    └── onConditions: List[ConditionalEdge]
 ├── Fork
 │    ├── branches: Map[String, Node]
 │    └── joinNodeId: String
 └── Join
      ├── joinNodeId: String
      ├── strategy: JoinStrategy
      ├── timeout: Option[FiniteDuration]
      ├── onSuccess: Option[Node]
      ├── onFailure: Option[Node]
      └── onTimeout: Option[Node]
```

### 2.2 Design Principles

| Principle | Implementation |
|-----------|----------------|
| **Immutability** | All case classes, no mutable state |
| **Type Safety** | Sealed trait hierarchy prevents invalid states |
| **Composability** | Nodes reference other nodes, enabling tree building |
| **Sensible Defaults** | Factory methods provide common configurations |
| **Non-Empty Guarantee** | `NonEmptyList[Node]` for entry points |

---

## 3. Spec Analysis

### 3.1 Core Structure

```scala
final case class Spec(
  entryPoints: NonEmptyList[Node],
  repeatPolicy: RepeatPolicy,
)
```

**Strengths:**
- `NonEmptyList` guarantees at least one entry point
- `RepeatPolicy` controls traversal repetition
- Simple, focused data class

### 3.2 Factory Methods

| Method | Purpose | Use Case |
|--------|---------|----------|
| `single(entryPoint, repeatPolicy)` | Single entry point | Most common case |
| `multiple(first, rest*)(repeatPolicy)` | Multiple entry points | Fan-in patterns |

**Example Usage:**
```scala
// Single entry point
Spec.single(
  Node.standard(svc, onSuccess = Some(nextNode)),
  RepeatPolicy.NoRepeat
)

// Multiple entry points (fan-in)
Spec.multiple(
  Node.standard(svcA),
  Node.standard(svcB),
  Node.standard(svcC)
)(RepeatPolicy.NoRepeat)
```

---

## 4. Node Type Analysis

### 4.1 Standard Node

```scala
final case class Standard(
  address: ServiceAddress,
  retryPolicy: RetryPolicy,
  deliveryStrategy: DeliveryStrategy,
  onSuccess: Option[Node],
  onFailure: Option[Node],
  onConditions: List[ConditionalEdge] = Nil,
  label: Option[String] = None
) extends Node
```

**Features:**
- Binary branching: `onSuccess` / `onFailure`
- Conditional routing: `onConditions` for output-based routing
- Optional human-readable label

**Edge Priority:**
1. `onConditions` (evaluated first, first match wins)
2. `onSuccess` / `onFailure` (fallback)

### 4.2 Fork Node

```scala
final case class Fork(
  address: ServiceAddress,
  retryPolicy: RetryPolicy,
  deliveryStrategy: DeliveryStrategy,
  branches: Map[String, Node],
  joinNodeId: String,
  label: Option[String] = None
) extends Node
```

**Features:**
- Named branches for debugging/logging
- `joinNodeId` links to corresponding Join
- Map-based branches enable easy lookup

**Design Decision:** Using `Map[String, Node]` instead of `List` provides:
- Named branches for observability
- Order-independent semantics
- Easy branch identification

### 4.3 Join Node

```scala
final case class Join(
  address: ServiceAddress,
  retryPolicy: RetryPolicy,
  deliveryStrategy: DeliveryStrategy,
  joinNodeId: String,
  strategy: JoinStrategy,
  timeout: Option[FiniteDuration],
  onSuccess: Option[Node],
  onFailure: Option[Node],
  onTimeout: Option[Node],
  label: Option[String] = None
) extends Node
```

**Features:**
- Three continuation paths: success, failure, timeout
- Configurable strategy: AND, OR, Quorum
- Optional timeout with dedicated handler

**JoinStrategy Options:**
| Strategy | Behavior |
|----------|----------|
| `And` | All branches must succeed |
| `Or` | First branch wins, others canceled |
| `Quorum(n)` | At least n branches must succeed |

---

## 5. Factory Method Analysis

### 5.1 Convenience Factories

```scala
def standard(
  address: ServiceAddress,
  onSuccess: Option[Node] = None,
  onFailure: Option[Node] = None,
  onConditions: List[ConditionalEdge] = Nil,
  label: Option[String] = None
): Standard
```

**Defaults Applied:**
- `retryPolicy = RetryPolicy.None`
- `deliveryStrategy = DeliveryStrategy.Immediate`

**Benefits:**
- Reduces boilerplate for common cases
- Explicit overrides when needed

### 5.2 ConditionalEdge Helper

```scala
def when(condition: Condition, to: Node): ConditionalEdge =
  ConditionalEdge(condition, to)
```

**Usage:**
```scala
Node.standard(
  svc,
  onConditions = List(
    Node.when(Condition.JsonEquals("/status", "approved"), approvedNode),
    Node.when(Condition.JsonEquals("/status", "rejected"), rejectedNode)
  ),
  onSuccess = Some(fallbackNode) // if no condition matches
)
```

---

## 6. Conditional Routing

### 6.1 ConditionalEdge

```scala
final case class ConditionalEdge(
  condition: Condition,
  to: Node
)
```

### 6.2 Condition DSL (from `Condition.scala`)

```scala
enum Condition:
  case Always
  case JsonExists(pointer: String)
  case JsonEquals(pointer: String, value: Json)
  case Not(condition: Condition)
  case And(conditions: List[Condition])
  case Or(conditions: List[Condition])
```

**JSON Pointer Syntax:** RFC-6901 compliant (e.g., `/a/b/0`)

**Example Conditions:**
```scala
// Simple equality
Condition.JsonEquals("/status", Json.fromString("ok"))

// Existence check
Condition.JsonExists("/user/email")

// Composite
Condition.And(List(
  Condition.JsonExists("/approved"),
  Condition.JsonEquals("/type", Json.fromString("premium"))
))
```

---

## 7. Usage Patterns

### 7.1 Linear DAG

```scala
val nodeC = Node.standard(svcC, label = Some("C"))
val nodeB = Node.standard(svcB, onSuccess = Some(nodeC), label = Some("B"))
val nodeA = Node.standard(svcA, onSuccess = Some(nodeB), label = Some("A"))

Spec.single(nodeA, RepeatPolicy.NoRepeat)
// A → B → C
```

### 7.2 Fork/Join Pattern

```scala
val join = Node.join(svcJoin, "fork1", JoinStrategy.And,
  onSuccess = Some(finalNode))

val branchA = Node.standard(svcA, onSuccess = Some(join))
val branchB = Node.standard(svcB, onSuccess = Some(join))

val fork = Node.fork(svcFork,
  branches = Map("a" -> branchA, "b" -> branchB),
  joinNodeId = "fork1")

Spec.single(fork, RepeatPolicy.NoRepeat)
//     ┌─→ A ─┐
// Fork       Join → Final
//     └─→ B ─┘
```

### 7.3 Conditional Routing

```scala
val approved = Node.standard(svcApproved)
val rejected = Node.standard(svcRejected)
val review = Node.standard(svcReview)

val router = Node.standard(svcProcess,
  onConditions = List(
    Node.when(Condition.JsonEquals("/status", "approved"), approved),
    Node.when(Condition.JsonEquals("/status", "rejected"), rejected)
  ),
  onSuccess = Some(review) // fallback
)

Spec.single(router, RepeatPolicy.NoRepeat)
// Process → [approved] → Approved
//         → [rejected] → Rejected
//         → [else]     → Review
```

### 7.4 Nested Forks

```scala
val innerJoin = Node.join(svc, "inner", JoinStrategy.And)
val innerBranch1 = Node.standard(svc, onSuccess = Some(innerJoin))
val innerBranch2 = Node.standard(svc, onSuccess = Some(innerJoin))
val innerFork = Node.fork(svc,
  Map("1" -> innerBranch1, "2" -> innerBranch2), "inner")

val outerJoin = Node.join(svc, "outer", JoinStrategy.And)
val branchWithNestedFork = Node.standard(svc, onSuccess = Some(innerFork))
// innerJoin.onSuccess = Some(outerJoin)

val outerFork = Node.fork(svc,
  Map("nested" -> branchWithNestedFork, "simple" -> simpleNode), "outer")
```

---

## 8. API Ergonomics

### 8.1 Strengths

| Feature | Benefit |
|---------|---------|
| Optional continuations | Terminal nodes are simple: `None` |
| Factory methods | Reduce boilerplate |
| Named parameters | Self-documenting code |
| Default values | Common cases need minimal code |

### 8.2 Potential Improvements

**1. Builder Pattern Alternative**
```scala
// Current: nested options
Node.standard(svc,
  onSuccess = Some(Node.standard(svc2,
    onSuccess = Some(Node.standard(svc3)))))

// Potential: builder with `then`
Node.standard(svc)
  .then(Node.standard(svc2))
  .then(Node.standard(svc3))
```

**2. Type-Safe Fork/Join Linking**
```scala
// Current: string-based linking (error-prone)
Fork(joinNodeId = "fork1")
Join(joinNodeId = "fork1")

// Potential: compile-time linking
val link = ForkJoinLink()
Fork(link = link)
Join(link = link)
```

---

## 9. Comparison with Alternatives

### 9.1 vs. Raw DAG Construction

| Aspect | Spec DSL | Raw Dag |
|--------|----------|---------|
| Boilerplate | Low | High |
| ID Management | Automatic | Manual |
| Validation | At compile time | Runtime |
| Flexibility | Constrained | Full |

### 9.2 vs. String-Based DSL

| Aspect | Spec DSL | String DSL |
|--------|----------|------------|
| Type Safety | Full | None |
| IDE Support | Autocomplete | None |
| Refactoring | Safe | Error-prone |
| Learning Curve | Medium | Low |

---

## 10. Test Coverage

### 10.1 Spec Tests (`SpecSuite.scala`)

| Test | Coverage |
|------|----------|
| Single entry compilation | ✅ |
| Multiple entry compilation | ✅ |
| Fork/Join compilation | ✅ |
| Conditional edges | ✅ |

### 10.2 Compiler Integration

All Spec patterns are tested via `DagCompilerSuite`:
- Linear DAGs
- Fork/Join patterns
- Nested forks
- Conditional routing
- Retry policies

---

## 11. Documentation Quality

### 11.1 Current State

```scala
/**
 * High-level specification for a DAG traversal.
 *
 * A Spec defines the structure of a routing DAG through a graph of Node instances.
 * The Spec is compiled into a Dag by the DagCompiler, which generates stable
 * node IDs and hashes for deduplication and caching.
 *
 * @param entryPoints Non-empty list of entry point nodes (supports multiple origins)
 * @param repeatPolicy Policy for repeating the entire traversal
 */
```

### 11.2 Recommendations

1. Add usage examples in ScalaDoc
2. Document edge priority rules
3. Add common patterns section

---

## 12. Recommendations

### 12.1 Short-Term

1. **Add validation helper**
```scala
def validate: Either[String, Spec] =
  // Check fork/join pairing
  // Check for cycles
  // Verify addresses are valid
```

2. **Add chain helper for linear DAGs**
```scala
def chain(nodes: Node*): Spec =
  nodes.reduceRight((a, b) =>
    a.copy(onSuccess = Some(b))
  ).let(Spec.single(_, RepeatPolicy.NoRepeat))
```

### 12.2 Medium-Term

1. **Visual DAG builder integration**
   - JSON import/export
   - YAML configuration support

2. **Type-safe fork/join linking**
   - Compile-time verification
   - Better error messages

### 12.3 Long-Term

1. **DAG composition operators**
   - Combine Specs
   - Sub-graph extraction
   - Template instantiation

---

## 13. Conclusion

The `Spec` DSL is a **well-designed, user-friendly interface** for defining DAG traversals. Its sealed trait hierarchy provides excellent type safety, while factory methods reduce boilerplate. The support for conditional routing, fork/join patterns, and nested structures enables complex routing scenarios.

**Key Strengths:**
- Clean, functional API
- Type-safe node hierarchy
- Sensible defaults
- Support for complex patterns

**Minor Improvements:**
- Builder pattern for chaining
- Type-safe fork/join linking
- More documentation examples

**Overall Assessment:** Production-ready DSL that balances simplicity with power.
