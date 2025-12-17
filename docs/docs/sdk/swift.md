---
sidebar_position: 8
---

# Swift SDK

The official Swift SDK for Waygrid provides a type-safe, Swifty API for building traversal specifications. It supports Swift 5.9+ and leverages modern features like async/await, result builders, and property wrappers.

## Installation

### Swift Package Manager

Add to your `Package.swift`:

```swift
dependencies: [
    .package(url: "https://github.com/waygrid/waygrid-sdk-swift.git", from: "0.1.0")
]
```

Or in Xcode: File → Add Packages → Enter the repository URL.

**Requirements:**
- Swift 5.9+
- iOS 15+ / macOS 12+ / tvOS 15+ / watchOS 8+

## Quick Start

```swift
import WaygridSDK

// Define a simple workflow
let spec = Spec.single(
    entryPoint: Node.standard("waygrid://processor/openai")
        .label("AI Processing")
        .withRetry(.exponential(base: .seconds(1), maxRetries: 3))
        .onSuccess(
            Node.standard("waygrid://destination/webhook")
                .label("Deliver Result")
        ),
    repeatPolicy: .noRepeat
)

// Convert to JSON
let json = try spec.toJSON()
print(json)
```

### With Async Client

```swift
import WaygridSDK

@main
struct MyApp {
    static func main() async throws {
        let client = WaygridClient(
            baseURL: URL(string: "https://api.waygrid.io")!,
            apiKey: ProcessInfo.processInfo.environment["WAYGRID_API_KEY"]!
        )

        let spec = Spec.single(
            entryPoint: Node.standard("waygrid://processor/openai")
                .onSuccess(Node.standard("waygrid://destination/webhook")),
            repeatPolicy: .noRepeat
        )

        let result = try await client.submit(spec)
        print("Traversal ID: \(result.traversalId)")
    }
}
```

## Core API

### Spec

The root container for a traversal specification:

```swift
import WaygridSDK

// Single entry point (most common)
let spec = Spec.single(
    entryPoint: myNode,
    repeatPolicy: .noRepeat
)

// Multiple entry points (fan-in from multiple origins)
let spec = Spec.multiple(
    entryPoints: [node1, node2, node3],
    repeatPolicy: .noRepeat
)
```

### Node Types

#### Standard Node

Linear processing step with optional continuations:

```swift
import WaygridSDK

// Minimal
let node = Node.standard("waygrid://processor/openai")

// Full configuration
let node = Node.standard("waygrid://processor/openai")
    .label("AI Content Analysis")
    .withRetry(.exponential(base: .seconds(1), maxRetries: 5))
    .withDelivery(.scheduleAfter(.minutes(5)))
    .onSuccess(successHandler)
    .onFailure(failureHandler)
    .onConditions([
        .when(.jsonEquals("/sentiment", "negative"), then: escalationNode),
        .when(.jsonEquals("/sentiment", "positive"), then: archiveNode)
    ])
```

#### Fork Node

Fan-out to parallel branches:

```swift
let fork = Node.fork("waygrid://system/waystation")
    .joinId("enrich-parallel")
    .label("Parallel Enrichment")
    .branches([
        "sentiment": Node.standard("waygrid://processor/sentiment"),
        "translate": Node.standard("waygrid://processor/translate"),
        "summarize": Node.standard("waygrid://processor/openai")
    ])
```

#### Join Node

Fan-in from parallel branches:

```swift
let join = Node.join("waygrid://system/waystation")
    .joinId("enrich-parallel")  // Must match Fork's joinId
    .strategy(.and)  // Wait for all
    .timeout(.seconds(30))
    .onSuccess(aggregateNode)
    .onFailure(errorHandler)
    .onTimeout(timeoutHandler)
```

### Retry Policies

```swift
import WaygridSDK

// No retries
RetryPolicy.none

// Linear backoff: 5s, 5s, 5s...
RetryPolicy.linear(base: .seconds(5), maxRetries: 3)

// Exponential backoff: 1s, 2s, 4s, 8s...
RetryPolicy.exponential(base: .seconds(1), maxRetries: 5)

// Bounded exponential: exponential with cap
RetryPolicy.boundedExponential(
    base: .seconds(1),
    cap: .minutes(1),
    maxRetries: 10
)

// Fibonacci backoff: 1s, 1s, 2s, 3s, 5s...
RetryPolicy.fibonacci(base: .seconds(1), maxRetries: 5)

// Full jitter (randomized)
RetryPolicy.fullJitter(base: .seconds(1), maxRetries: 5)

// Decorrelated jitter (AWS-style)
RetryPolicy.decorrelatedJitter(base: .seconds(1), maxRetries: 5)
```

### Delivery Strategies

```swift
import WaygridSDK

// Execute immediately (default)
DeliveryStrategy.immediate

// Schedule after delay
DeliveryStrategy.scheduleAfter(.minutes(5))

// Schedule at specific time
DeliveryStrategy.scheduleAt(Date(timeIntervalSince1970: 1735084800))
```

### Repeat Policies

```swift
import WaygridSDK

// One-shot execution
RepeatPolicy.noRepeat

// Repeat forever
RepeatPolicy.indefinitely(every: .hours(1))

// Repeat N times
RepeatPolicy.times(every: .hours(1), times: 10)

// Repeat until timestamp
RepeatPolicy.until(every: .hours(1), until: someDate)
```

### Conditions

For conditional routing based on node output:

```swift
import WaygridSDK

// Always matches
Condition.always

// Check if JSON pointer exists
Condition.jsonExists("/result/data")

// Check JSON value equality
Condition.jsonEquals("/status", "approved")
Condition.jsonEquals("/count", 0)
Condition.jsonEquals("/valid", true)

// Logical operators
Condition.not(.jsonExists("/error"))

Condition.and([
    .jsonExists("/result"),
    .jsonEquals("/result/valid", true)
])

Condition.or([
    .jsonEquals("/status", "approved"),
    .jsonEquals("/status", "auto_approved")
])
```

## Advanced Patterns

### Error Handling Pipeline

```swift
let workflow = Node.standard("waygrid://processor/risky-operation")
    .withRetry(.exponential(base: .seconds(1), maxRetries: 3))
    .onSuccess(
        Node.standard("waygrid://destination/webhook")
            .label("Success Notification")
    )
    .onFailure(
        Node.standard("waygrid://processor/error-classifier")
            .label("Classify Error")
            .onConditions([
                .when(.jsonEquals("/errorType", "retryable"), then:
                    Node.standard("waygrid://system/scheduler")
                        .withDelivery(.scheduleAfter(.hours(1)))
                        .label("Retry Later")
                ),
                .when(.jsonEquals("/errorType", "fatal"), then:
                    Node.standard("waygrid://destination/webhook")
                        .label("Alert On-Call")
                )
            ])
    )
```

### Result Builder DSL

```swift
import WaygridSDK

// Using result builders for declarative syntax
let spec = Spec {
    EntryPoint {
        Node.standard("waygrid://processor/openai")
            .label("Process")
            .onSuccess {
                Node.standard("waygrid://destination/webhook")
            }
    }
    RepeatPolicy.noRepeat
}
```

### Parallel Processing with Quorum

```swift
// Call 3 providers, continue when 2 respond
let spec = Spec.single(
    entryPoint: Node.fork("waygrid://system/waystation")
        .joinId("multi-provider")
        .branches([
            "provider-a": Node.standard("waygrid://destination/webhook")
                .label("Provider A"),
            "provider-b": Node.standard("waygrid://destination/webhook")
                .label("Provider B"),
            "provider-c": Node.standard("waygrid://destination/webhook")
                .label("Provider C")
        ]),
    repeatPolicy: .noRepeat
)

// The Join node with Quorum strategy
let join = Node.join("waygrid://system/waystation")
    .joinId("multi-provider")
    .strategy(.quorum(2))  // 2 of 3 required
    .timeout(.seconds(30))
    .onSuccess(aggregateResults)
    .onTimeout(handlePartialResults)
```

## SwiftUI Integration

```swift
import SwiftUI
import WaygridSDK

struct WorkflowView: View {
    @StateObject private var viewModel = WorkflowViewModel()

    var body: some View {
        VStack {
            Button("Start Workflow") {
                Task {
                    await viewModel.startWorkflow()
                }
            }

            if let traversalId = viewModel.traversalId {
                Text("Traversal: \(traversalId)")
            }

            if let error = viewModel.error {
                Text("Error: \(error.localizedDescription)")
                    .foregroundColor(.red)
            }
        }
    }
}

@MainActor
class WorkflowViewModel: ObservableObject {
    @Published var traversalId: String?
    @Published var error: Error?

    private let client = WaygridClient(
        baseURL: URL(string: "https://api.waygrid.io")!,
        apiKey: Config.waygridApiKey
    )

    func startWorkflow() async {
        let spec = Spec.single(
            entryPoint: Node.standard("waygrid://processor/openai")
                .onSuccess(Node.standard("waygrid://destination/webhook")),
            repeatPolicy: .noRepeat
        )

        do {
            let result = try await client.submit(spec)
            self.traversalId = result.traversalId
        } catch {
            self.error = error
        }
    }
}
```

## Vapor Integration (Server-Side Swift)

```swift
import Vapor
import WaygridSDK

func configure(_ app: Application) throws {
    // Register Waygrid client
    app.waygrid.client = WaygridClient(
        baseURL: URL(string: Environment.get("WAYGRID_BASE_URL")!)!,
        apiKey: Environment.get("WAYGRID_API_KEY")!
    )
}

// Routes
func routes(_ app: Application) throws {
    app.post("workflow", "start") { req async throws -> Response in
        let spec = Spec.single(
            entryPoint: Node.standard("waygrid://processor/openai")
                .onSuccess(Node.standard("waygrid://destination/webhook")),
            repeatPolicy: .noRepeat
        )

        let result = try await req.waygrid.submit(spec)

        return Response(
            status: .ok,
            body: .init(string: """
                {"traversal_id": "\(result.traversalId)", "status": "submitted"}
                """)
        )
    }
}
```

## Enum Types

The SDK uses Swift enums for type safety:

```swift
public enum RetryPolicy: Codable, Sendable {
    case none
    case linear(base: Duration, maxRetries: Int)
    case exponential(base: Duration, maxRetries: Int)
    case boundedExponential(base: Duration, cap: Duration, maxRetries: Int)
    case fibonacci(base: Duration, maxRetries: Int)
    case fullJitter(base: Duration, maxRetries: Int)
    case decorrelatedJitter(base: Duration, maxRetries: Int)
}

public enum JoinStrategy: Codable, Sendable {
    case and
    case or
    case quorum(Int)
}

public enum Node: Codable, Sendable {
    case standard(StandardNode)
    case fork(ForkNode)
    case join(JoinNode)
}
```

## Repository Structure

The SDK repository (`waygrid-sdk-swift`) is organized as:

```
waygrid-sdk-swift/
├── Package.swift
├── Sources/
│   └── WaygridSDK/
│       ├── Spec.swift
│       ├── Node/
│       │   ├── Node.swift
│       │   ├── StandardNode.swift
│       │   ├── ForkNode.swift
│       │   └── JoinNode.swift
│       ├── Policy/
│       │   ├── RetryPolicy.swift
│       │   ├── DeliveryStrategy.swift
│       │   └── RepeatPolicy.swift
│       ├── Condition/
│       │   └── Condition.swift
│       ├── Strategy/
│       │   └── JoinStrategy.swift
│       ├── Client/
│       │   ├── WaygridClient.swift
│       │   └── SubmitResult.swift
│       └── DSL/
│           └── SpecBuilder.swift
├── Tests/
│   └── WaygridSDKTests/
│       ├── SpecTests.swift
│       ├── NodeTests.swift
│       └── SerializationTests.swift
└── Examples/
    ├── iOSExample/
    └── VaporExample/
```

## See Also

- [SDK Overview](./overview) - Compare SDKs
- [Spec JSON Schema](/docs/api/spec-schema) - Raw JSON format
