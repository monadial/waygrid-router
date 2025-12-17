---
sidebar_position: 2
---

# Scala SDK

The official Scala SDK for Waygrid provides a type-safe, functional API for building traversal specifications. It leverages Scala 3 features for compile-time safety and integrates seamlessly with Cats Effect for async operations.

## Installation

Add the SDK to your `build.sbt`:

```scala
libraryDependencies += "com.waygrid" %% "waygrid-sdk" % "0.1.0"
```

For Cats Effect integration:

```scala
libraryDependencies ++= Seq(
  "com.waygrid" %% "waygrid-sdk" % "0.1.0",
  "com.waygrid" %% "waygrid-sdk-ce3" % "0.1.0"  // Cats Effect 3 support
)
```

## Quick Start

```scala
import com.waygrid.sdk._
import com.waygrid.sdk.dsl._
import scala.concurrent.duration._

// Define a simple workflow
val spec = Spec.single(
  entryPoint = Node.standard("waygrid://processor/openai")
    .label("AI Processing")
    .withRetry(RetryPolicy.exponential(1.second, maxRetries = 3))
    .onSuccess(
      Node.standard("waygrid://destination/webhook")
        .label("Deliver Result")
    ),
  repeatPolicy = RepeatPolicy.NoRepeat
)

// Convert to JSON
val json: String = spec.toJson

// Or use the client
import com.waygrid.sdk.client._
import cats.effect.IO

val client = WaygridClient[IO]("https://api.waygrid.io", apiKey = "...")
client.submit(spec).unsafeRunSync()
```

## Core API

### Spec

The root container for a traversal specification:

```scala
// Single entry point (most common)
val spec = Spec.single(
  entryPoint = myNode,
  repeatPolicy = RepeatPolicy.NoRepeat
)

// Multiple entry points (fan-in from multiple origins)
val spec = Spec.multiple(node1, node2, node3)(RepeatPolicy.NoRepeat)

// Using NonEmptyList directly
import cats.data.NonEmptyList
val spec = Spec(
  entryPoints = NonEmptyList.of(node1, node2),
  repeatPolicy = RepeatPolicy.Indefinitely(1.hour)
)
```

### Node Types

#### Standard Node

Linear processing step with optional continuations:

```scala
import com.waygrid.sdk.dsl._

// Minimal
val node = Node.standard("waygrid://processor/openai")

// Full configuration
val node = Node.standard("waygrid://processor/openai")
  .label("AI Content Analysis")
  .withRetry(RetryPolicy.exponential(1.second, maxRetries = 5))
  .withDelivery(DeliveryStrategy.scheduleAfter(5.minutes))
  .onSuccess(successHandler)
  .onFailure(failureHandler)
  .onConditions(
    Condition.jsonEquals("/sentiment", "negative") -> escalationNode,
    Condition.jsonEquals("/sentiment", "positive") -> archiveNode
  )
```

#### Fork Node

Fan-out to parallel branches:

```scala
val fork = Node.fork("waygrid://system/waystation")
  .joinId("enrich-parallel")
  .label("Parallel Enrichment")
  .branches(
    "sentiment" -> Node.standard("waygrid://processor/sentiment"),
    "translate" -> Node.standard("waygrid://processor/translate"),
    "summarize" -> Node.standard("waygrid://processor/openai")
  )
```

#### Join Node

Fan-in from parallel branches:

```scala
val join = Node.join("waygrid://system/waystation")
  .joinId("enrich-parallel")  // Must match Fork's joinId
  .strategy(JoinStrategy.And)  // Wait for all
  .timeout(30.seconds)
  .onSuccess(aggregateNode)
  .onFailure(errorHandler)
  .onTimeout(timeoutHandler)
```

### Retry Policies

```scala
import com.waygrid.sdk.RetryPolicy
import scala.concurrent.duration._

// No retries
RetryPolicy.None

// Linear backoff: 5s, 5s, 5s...
RetryPolicy.linear(5.seconds, maxRetries = 3)

// Exponential backoff: 1s, 2s, 4s, 8s...
RetryPolicy.exponential(1.second, maxRetries = 5)

// Bounded exponential: exponential with cap
RetryPolicy.boundedExponential(
  base = 1.second,
  cap = 1.minute,
  maxRetries = 10
)

// Fibonacci backoff: 1s, 1s, 2s, 3s, 5s...
RetryPolicy.fibonacci(1.second, maxRetries = 5)

// Full jitter (randomized)
RetryPolicy.fullJitter(1.second, maxRetries = 5)

// Decorrelated jitter (AWS-style)
RetryPolicy.decorrelatedJitter(1.second, maxRetries = 5)
```

### Delivery Strategies

```scala
import com.waygrid.sdk.DeliveryStrategy
import java.time.Instant

// Execute immediately (default)
DeliveryStrategy.Immediate

// Schedule after delay
DeliveryStrategy.scheduleAfter(5.minutes)

// Schedule at specific time
DeliveryStrategy.scheduleAt(Instant.parse("2024-12-25T00:00:00Z"))
```

### Repeat Policies

```scala
import com.waygrid.sdk.RepeatPolicy

// One-shot execution
RepeatPolicy.NoRepeat

// Repeat forever
RepeatPolicy.indefinitely(1.hour)

// Repeat N times
RepeatPolicy.times(every = 1.hour, times = 10)

// Repeat until timestamp
RepeatPolicy.until(
  every = 1.hour,
  until = Instant.parse("2024-12-31T23:59:59Z")
)
```

### Conditions

For conditional routing based on node output:

```scala
import com.waygrid.sdk.Condition
import io.circe.Json

// Always matches
Condition.Always

// Check if JSON pointer exists
Condition.jsonExists("/result/data")

// Check JSON value equality
Condition.jsonEquals("/status", Json.fromString("approved"))
Condition.jsonEquals("/count", Json.fromInt(0))
Condition.jsonEquals("/valid", Json.True)

// Logical operators
Condition.not(Condition.jsonExists("/error"))

Condition.and(
  Condition.jsonExists("/result"),
  Condition.jsonEquals("/result/valid", Json.True)
)

Condition.or(
  Condition.jsonEquals("/status", "approved"),
  Condition.jsonEquals("/status", "auto_approved")
)
```

## Advanced Patterns

### Error Handling Pipeline

```scala
val workflow = Node.standard("waygrid://processor/risky-operation")
  .withRetry(RetryPolicy.exponential(1.second, maxRetries = 3))
  .onSuccess(
    Node.standard("waygrid://destination/webhook")
      .label("Success Notification")
  )
  .onFailure(
    Node.standard("waygrid://processor/error-classifier")
      .label("Classify Error")
      .onConditions(
        Condition.jsonEquals("/errorType", "retryable") ->
          Node.standard("waygrid://system/scheduler")
            .withDelivery(DeliveryStrategy.scheduleAfter(1.hour))
            .label("Retry Later"),
        Condition.jsonEquals("/errorType", "fatal") ->
          Node.standard("waygrid://destination/webhook")
            .label("Alert On-Call")
      )
  )
```

### Parallel Processing with Quorum

```scala
// Call 3 providers, continue when 2 respond
val spec = Spec.single(
  entryPoint = Node.fork("waygrid://system/waystation")
    .joinId("multi-provider")
    .branches(
      "provider-a" -> Node.standard("waygrid://destination/webhook")
        .label("Provider A"),
      "provider-b" -> Node.standard("waygrid://destination/webhook")
        .label("Provider B"),
      "provider-c" -> Node.standard("waygrid://destination/webhook")
        .label("Provider C")
    ),
  repeatPolicy = RepeatPolicy.NoRepeat
)

// The Join node with Quorum strategy
val join = Node.join("waygrid://system/waystation")
  .joinId("multi-provider")
  .strategy(JoinStrategy.Quorum(2))  // 2 of 3 required
  .timeout(30.seconds)
  .onSuccess(aggregateResults)
  .onTimeout(handlePartialResults)
```

### Nested Fork/Join (Diamond Pattern)

```scala
val innerFork = Node.fork("waygrid://system/waystation")
  .joinId("inner-parallel")
  .branches(
    "step-a" -> Node.standard("waygrid://processor/a"),
    "step-b" -> Node.standard("waygrid://processor/b")
  )

val outerFork = Node.fork("waygrid://system/waystation")
  .joinId("outer-parallel")
  .branches(
    "branch-1" -> innerFork,  // Nested fork
    "branch-2" -> Node.standard("waygrid://processor/c")
  )
```

## Cats Effect Integration

For applications using Cats Effect:

```scala
import cats.effect._
import com.waygrid.sdk.client._
import com.waygrid.sdk.client.ce3._

object MyApp extends IOApp.Simple:
  def run: IO[Unit] =
    WaygridClient.resource[IO](
      baseUrl = "https://api.waygrid.io",
      apiKey = sys.env("WAYGRID_API_KEY")
    ).use { client =>
      for
        spec <- IO.pure(buildMySpec())
        result <- client.submit(spec)
        _ <- IO.println(s"Traversal submitted: ${result.traversalId}")
      yield ()
    }

  def buildMySpec(): Spec = ???
```

## JSON Serialization

The SDK uses Circe for JSON serialization:

```scala
import com.waygrid.sdk._
import com.waygrid.sdk.json._
import io.circe.syntax._
import io.circe.parser._

// Spec to JSON
val json: String = spec.asJson.spaces2

// JSON to Spec
val parsed: Either[Error, Spec] = decode[Spec](json)
```

## Type-Safe Service Addresses

```scala
import com.waygrid.sdk.address._

// Using string literals (validated at compile time with macro)
val addr = ServiceAddress("waygrid://processor/openai")

// Using builder (runtime validation)
val addr = ServiceAddress.builder
  .component(Component.Processor)
  .service("openai")
  .build()

// Predefined addresses
object Addresses:
  val OpenAI = ServiceAddress.processor("openai")
  val Webhook = ServiceAddress.destination("webhook")
  val WebSocket = ServiceAddress.destination("websocket")
  val Waystation = ServiceAddress.system("waystation")
```

## Testing

The SDK provides test utilities:

```scala
import com.waygrid.sdk.testing._

class MyWorkflowSpec extends AnyFlatSpec:
  "My workflow" should "have valid structure" in {
    val spec = buildMySpec()

    // Validate spec structure
    SpecValidator.validate(spec) shouldBe Valid

    // Check for common issues
    spec.hasUnreachableNodes shouldBe false
    spec.hasOrphanJoins shouldBe false
  }

  it should "serialize round-trip" in {
    val spec = buildMySpec()
    val json = spec.toJson
    val parsed = Spec.fromJson(json)

    parsed shouldBe Right(spec)
  }
```

## Repository Structure

The SDK repository (`waygrid-sdk-scala`) is organized as:

```
waygrid-sdk-scala/
├── build.sbt
├── src/
│   └── main/
│       └── scala/
│           └── com/
│               └── waygrid/
│                   └── sdk/
│                       ├── Spec.scala
│                       ├── Node.scala
│                       ├── RetryPolicy.scala
│                       ├── DeliveryStrategy.scala
│                       ├── RepeatPolicy.scala
│                       ├── Condition.scala
│                       ├── JoinStrategy.scala
│                       ├── address/
│                       │   └── ServiceAddress.scala
│                       ├── dsl/
│                       │   └── package.scala      # DSL helpers
│                       ├── json/
│                       │   └── codecs.scala       # Circe codecs
│                       └── client/
│                           ├── WaygridClient.scala
│                           └── ce3/
│                               └── package.scala  # Cats Effect 3
└── src/
    └── test/
        └── scala/
            └── com/
                └── waygrid/
                    └── sdk/
                        └── SpecSuite.scala
```

## See Also

- [SDK Overview](./overview) - Compare SDKs
- [PHP SDK](./php) - PHP SDK guide
- [Spec JSON Schema](/docs/api/spec-schema) - Raw JSON format
