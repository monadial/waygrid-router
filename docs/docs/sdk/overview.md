---
sidebar_position: 1
---

# SDK Overview

Waygrid provides official SDKs to help you integrate event-driven workflows into your applications. These SDKs provide type-safe, idiomatic APIs for building traversal specifications.

## Available SDKs

| SDK | Language | Package Manager | Min Version | Status |
|-----|----------|-----------------|-------------|--------|
| [waygrid-sdk-scala](./scala) | Scala 3 | Maven Central / sbt | Scala 3.3+ | **Available** |
| [waygrid-sdk-php](./php) | PHP | Packagist / Composer | PHP 8.1+ | **Available** |
| [waygrid-sdk-csharp](./csharp) | C# | NuGet | .NET 8+ | **Available** |
| [waygrid-sdk-python](./python) | Python | PyPI / pip | Python 3.10+ | **Available** |
| [waygrid-sdk-rust](./rust) | Rust | crates.io / Cargo | Rust 1.75+ | **Available** |
| [waygrid-sdk-cpp](./cpp) | C++ | vcpkg / Conan / CMake | C++20 | **Available** |
| [waygrid-sdk-swift](./swift) | Swift | Swift Package Manager | Swift 5.9+ | **Available** |
| [waygrid-sdk-go](./go) | Go | Go modules | Go 1.21+ | **Available** |

## Architecture

All SDKs share a common JSON format defined by the [Spec JSON Schema](/docs/api/spec-schema):

```
┌─────────────────────────────────────────────────────────┐
│                  Your Application                        │
├─────────────────────────────────────────────────────────┤
│  Scala SDK      │      PHP SDK      │     Raw JSON      │
│  (Type-safe)    │    (Fluent API)   │  (Schema-valid)   │
└────────┬────────┴─────────┬─────────┴─────────┬─────────┘
         │                  │                   │
         v                  v                   v
┌─────────────────────────────────────────────────────────┐
│                   JSON Spec Format                       │
│            (Validated against JSON Schema)               │
└───────────────────────────┬─────────────────────────────┘
                            │
                            v
┌─────────────────────────────────────────────────────────┐
│                    Waygrid API                           │
│               POST /v1/traversals                        │
└─────────────────────────────────────────────────────────┘
```

## Quick Comparison

All SDKs follow the same conceptual model. Here's the same workflow in each language:

### Scala

```scala
val spec = Spec.single(
  Node.standard("waygrid://processor/openai")
    .withRetry(RetryPolicy.exponential(1.second, maxRetries = 3))
    .onSuccess(Node.standard("waygrid://destination/webhook")),
  RepeatPolicy.NoRepeat
)
```

### PHP

```php
$spec = Spec::single(
    Node::standard('waygrid://processor/openai')
        ->withRetry(RetryPolicy::exponential('1s', maxRetries: 3))
        ->onSuccess(Node::standard('waygrid://destination/webhook'))
);
```

### C#

```csharp
var spec = Spec.Single(
    Node.Standard("waygrid://processor/openai")
        .WithRetry(RetryPolicy.Exponential(TimeSpan.FromSeconds(1), maxRetries: 3))
        .OnSuccess(Node.Standard("waygrid://destination/webhook")),
    RepeatPolicy.NoRepeat
);
```

### Python

```python
spec = Spec.single(
    Node.standard("waygrid://processor/openai")
        .with_retry(RetryPolicy.exponential(timedelta(seconds=1), max_retries=3))
        .on_success(Node.standard("waygrid://destination/webhook")),
    repeat_policy=RepeatPolicy.NO_REPEAT
)
```

### Rust

```rust
let spec = Spec::single(
    Node::standard("waygrid://processor/openai")
        .with_retry(RetryPolicy::exponential(Duration::from_secs(1), 3))
        .on_success(Node::standard("waygrid://destination/webhook")),
    RepeatPolicy::NoRepeat,
);
```

### C++

```cpp
auto spec = Spec::single(
    Node::standard("waygrid://processor/openai")
        .with_retry(RetryPolicy::exponential(1s, 3))
        .on_success(Node::standard("waygrid://destination/webhook")),
    RepeatPolicy::no_repeat()
);
```

### Swift

```swift
let spec = Spec.single(
    entryPoint: Node.standard("waygrid://processor/openai")
        .withRetry(.exponential(base: .seconds(1), maxRetries: 3))
        .onSuccess(Node.standard("waygrid://destination/webhook")),
    repeatPolicy: .noRepeat
)
```

### Go

```go
spec := waygrid.SingleSpec(
    waygrid.StandardNode("waygrid://processor/openai").
        WithRetry(waygrid.ExponentialRetry(time.Second, 3)).
        OnSuccess(waygrid.StandardNode("waygrid://destination/webhook")),
    waygrid.NoRepeat(),
)
```

## Core Concepts

### 1. Spec (Specification)

The top-level container for a traversal workflow. Contains one or more entry points and a repeat policy.

### 2. Nodes

Nodes represent processing steps in your workflow:

| Node Type | Purpose | Use Case |
|-----------|---------|----------|
| **Standard** | Linear processing | API calls, transformations, delivery |
| **Fork** | Fan-out to parallel branches | Parallel processing, A/B testing |
| **Join** | Fan-in from branches | Aggregation, quorum-based decisions |

### 3. Edges

Nodes connect via edges that define control flow:

- **onSuccess** - Next node when processing succeeds
- **onFailure** - Next node when processing fails (after retries)
- **onTimeout** - Next node when a join times out
- **onConditions** - Conditional routing based on output

### 4. Policies

Configure behavior with policies:

- **RetryPolicy** - How to handle transient failures
- **DeliveryStrategy** - When to execute (immediate, scheduled)
- **RepeatPolicy** - Whether to repeat the entire workflow

## Service Addresses

Services are identified by URIs in the format:

```
waygrid://<component>/<service>
```

**Available Components:**

| Component | Description | Example Services |
|-----------|-------------|------------------|
| `origin` | Event ingestion | `http`, `kafka`, `grpc` |
| `processor` | Transformation | `openai`, `transform` |
| `destination` | Event delivery | `webhook`, `websocket`, `blackhole` |
| `system` | Internal services | `waystation`, `scheduler` |

## Error Handling

All SDKs provide consistent error handling:

1. **Validation errors** - Invalid spec structure (caught at build time)
2. **Network errors** - Connection issues to Waygrid API
3. **API errors** - Rejected specs, quota exceeded, etc.

## Best Practices

### 1. Always Set Retry Policies

```php
// Good: Explicit retry handling
Node::standard('waygrid://destination/webhook')
    ->withRetry(RetryPolicy::exponential('1s', maxRetries: 5))
```

### 2. Use Labels for Observability

```scala
Node.standard("waygrid://processor/openai")
  .label("AI Content Analysis")
```

### 3. Handle Failures Explicitly

```php
Node::standard('waygrid://processor/risky-operation')
    ->onSuccess($happyPath)
    ->onFailure($errorHandler)  // Don't let failures disappear
```

### 4. Use Conditions for Complex Routing

```scala
Node.standard("waygrid://processor/classifier")
  .onConditions(
    Condition.jsonEquals("/category", "urgent") -> urgentHandler,
    Condition.jsonEquals("/category", "normal") -> normalHandler
  )
```

## Next Steps

Choose the SDK for your platform:

| Platform | SDK Guide |
|----------|-----------|
| JVM (Scala, Java, Kotlin) | [Scala SDK](./scala) |
| PHP (Laravel, Symfony) | [PHP SDK](./php) |
| .NET (C#, F#) | [C# SDK](./csharp) |
| Python (FastAPI, Django) | [Python SDK](./python) |
| Rust (Actix, Axum) | [Rust SDK](./rust) |
| C++ (Qt, Unreal) | [C++ SDK](./cpp) |
| Apple (iOS, macOS) | [Swift SDK](./swift) |
| Go (Gin, Echo) | [Go SDK](./go) |

**Other Resources:**
- [Spec JSON Schema](/docs/api/spec-schema) - Raw JSON format reference
- [Traversal Overview](/docs/traversal/overview) - How traversals work
