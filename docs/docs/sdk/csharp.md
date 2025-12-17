---
sidebar_position: 4
---

# C# SDK

The official C# SDK for Waygrid provides a fluent, type-safe API for building traversal specifications. It targets .NET 8+ and leverages modern C# features like records, required properties, and pattern matching.

## Installation

Install via NuGet:

```bash
dotnet add package Waygrid.Sdk
```

Or via Package Manager:

```powershell
Install-Package Waygrid.Sdk
```

**Requirements:**
- .NET 8.0 or higher
- `System.Text.Json` (included in .NET)

## Quick Start

```csharp
using Waygrid.Sdk;
using Waygrid.Sdk.Nodes;
using Waygrid.Sdk.Policies;

// Define a simple workflow
var spec = Spec.Single(
    entryPoint: Node.Standard("waygrid://processor/openai")
        .WithLabel("AI Processing")
        .WithRetry(RetryPolicy.Exponential(TimeSpan.FromSeconds(1), maxRetries: 3))
        .OnSuccess(
            Node.Standard("waygrid://destination/webhook")
                .WithLabel("Deliver Result")
        ),
    repeatPolicy: RepeatPolicy.NoRepeat
);

// Convert to JSON
string json = spec.ToJson();

// Or use the client
using var client = new WaygridClient(
    baseUrl: "https://api.waygrid.io",
    apiKey: Environment.GetEnvironmentVariable("WAYGRID_API_KEY")!
);

var result = await client.SubmitAsync(spec);
Console.WriteLine($"Traversal ID: {result.TraversalId}");
```

## Core API

### Spec

The root container for a traversal specification:

```csharp
using Waygrid.Sdk;

// Single entry point (most common)
var spec = Spec.Single(
    entryPoint: myNode,
    repeatPolicy: RepeatPolicy.NoRepeat
);

// Multiple entry points (fan-in from multiple origins)
var spec = Spec.Multiple(
    entryPoints: [node1, node2, node3],
    repeatPolicy: RepeatPolicy.NoRepeat
);
```

### Node Types

#### Standard Node

Linear processing step with optional continuations:

```csharp
using Waygrid.Sdk.Nodes;
using Waygrid.Sdk.Policies;
using Waygrid.Sdk.Conditions;

// Minimal
var node = Node.Standard("waygrid://processor/openai");

// Full configuration
var node = Node.Standard("waygrid://processor/openai")
    .WithLabel("AI Content Analysis")
    .WithRetry(RetryPolicy.Exponential(TimeSpan.FromSeconds(1), maxRetries: 5))
    .WithDelivery(DeliveryStrategy.ScheduleAfter(TimeSpan.FromMinutes(5)))
    .OnSuccess(successHandler)
    .OnFailure(failureHandler)
    .OnConditions(
        Condition.When(
            Condition.JsonEquals("/sentiment", "negative"),
            escalationNode
        ),
        Condition.When(
            Condition.JsonEquals("/sentiment", "positive"),
            archiveNode
        )
    );
```

#### Fork Node

Fan-out to parallel branches:

```csharp
var fork = Node.Fork("waygrid://system/waystation")
    .WithJoinId("enrich-parallel")
    .WithLabel("Parallel Enrichment")
    .WithBranches(new Dictionary<string, INode>
    {
        ["sentiment"] = Node.Standard("waygrid://processor/sentiment"),
        ["translate"] = Node.Standard("waygrid://processor/translate"),
        ["summarize"] = Node.Standard("waygrid://processor/openai")
    });
```

#### Join Node

Fan-in from parallel branches:

```csharp
using Waygrid.Sdk.Strategies;

var join = Node.Join("waygrid://system/waystation")
    .WithJoinId("enrich-parallel")  // Must match Fork's JoinId
    .WithStrategy(JoinStrategy.And)  // Wait for all
    .WithTimeout(TimeSpan.FromSeconds(30))
    .OnSuccess(aggregateNode)
    .OnFailure(errorHandler)
    .OnTimeout(timeoutHandler);
```

### Retry Policies

```csharp
using Waygrid.Sdk.Policies;

// No retries
RetryPolicy.None;

// Linear backoff: 5s, 5s, 5s...
RetryPolicy.Linear(TimeSpan.FromSeconds(5), maxRetries: 3);

// Exponential backoff: 1s, 2s, 4s, 8s...
RetryPolicy.Exponential(TimeSpan.FromSeconds(1), maxRetries: 5);

// Bounded exponential: exponential with cap
RetryPolicy.BoundedExponential(
    baseDelay: TimeSpan.FromSeconds(1),
    cap: TimeSpan.FromMinutes(1),
    maxRetries: 10
);

// Fibonacci backoff: 1s, 1s, 2s, 3s, 5s...
RetryPolicy.Fibonacci(TimeSpan.FromSeconds(1), maxRetries: 5);

// Full jitter (randomized)
RetryPolicy.FullJitter(TimeSpan.FromSeconds(1), maxRetries: 5);

// Decorrelated jitter (AWS-style)
RetryPolicy.DecorrelatedJitter(TimeSpan.FromSeconds(1), maxRetries: 5);
```

### Delivery Strategies

```csharp
using Waygrid.Sdk.Policies;

// Execute immediately (default)
DeliveryStrategy.Immediate;

// Schedule after delay
DeliveryStrategy.ScheduleAfter(TimeSpan.FromMinutes(5));

// Schedule at specific time
DeliveryStrategy.ScheduleAt(DateTimeOffset.Parse("2024-12-25T00:00:00Z"));
```

### Repeat Policies

```csharp
using Waygrid.Sdk.Policies;

// One-shot execution
RepeatPolicy.NoRepeat;

// Repeat forever
RepeatPolicy.Indefinitely(TimeSpan.FromHours(1));

// Repeat N times
RepeatPolicy.Times(every: TimeSpan.FromHours(1), times: 10);

// Repeat until timestamp
RepeatPolicy.Until(
    every: TimeSpan.FromHours(1),
    until: DateTimeOffset.Parse("2024-12-31T23:59:59Z")
);
```

### Conditions

For conditional routing based on node output:

```csharp
using Waygrid.Sdk.Conditions;
using System.Text.Json;

// Always matches
Condition.Always;

// Check if JSON pointer exists
Condition.JsonExists("/result/data");

// Check JSON value equality
Condition.JsonEquals("/status", "approved");
Condition.JsonEquals("/count", 0);
Condition.JsonEquals("/valid", true);

// Logical operators
Condition.Not(Condition.JsonExists("/error"));

Condition.And(
    Condition.JsonExists("/result"),
    Condition.JsonEquals("/result/valid", true)
);

Condition.Or(
    Condition.JsonEquals("/status", "approved"),
    Condition.JsonEquals("/status", "auto_approved")
);
```

## Advanced Patterns

### Error Handling Pipeline

```csharp
var workflow = Node.Standard("waygrid://processor/risky-operation")
    .WithRetry(RetryPolicy.Exponential(TimeSpan.FromSeconds(1), maxRetries: 3))
    .OnSuccess(
        Node.Standard("waygrid://destination/webhook")
            .WithLabel("Success Notification")
    )
    .OnFailure(
        Node.Standard("waygrid://processor/error-classifier")
            .WithLabel("Classify Error")
            .OnConditions(
                Condition.When(
                    Condition.JsonEquals("/errorType", "retryable"),
                    Node.Standard("waygrid://system/scheduler")
                        .WithDelivery(DeliveryStrategy.ScheduleAfter(TimeSpan.FromHours(1)))
                        .WithLabel("Retry Later")
                ),
                Condition.When(
                    Condition.JsonEquals("/errorType", "fatal"),
                    Node.Standard("waygrid://destination/webhook")
                        .WithLabel("Alert On-Call")
                )
            )
    );
```

### Parallel Processing with Quorum

```csharp
// Call 3 providers, continue when 2 respond
var spec = Spec.Single(
    entryPoint: Node.Fork("waygrid://system/waystation")
        .WithJoinId("multi-provider")
        .WithBranches(new Dictionary<string, INode>
        {
            ["provider-a"] = Node.Standard("waygrid://destination/webhook")
                .WithLabel("Provider A"),
            ["provider-b"] = Node.Standard("waygrid://destination/webhook")
                .WithLabel("Provider B"),
            ["provider-c"] = Node.Standard("waygrid://destination/webhook")
                .WithLabel("Provider C")
        }),
    repeatPolicy: RepeatPolicy.NoRepeat
);

// The Join node with Quorum strategy
var join = Node.Join("waygrid://system/waystation")
    .WithJoinId("multi-provider")
    .WithStrategy(JoinStrategy.Quorum(2))  // 2 of 3 required
    .WithTimeout(TimeSpan.FromSeconds(30))
    .OnSuccess(aggregateResults)
    .OnTimeout(handlePartialResults);
```

## ASP.NET Core Integration

### Service Registration

```csharp
// Program.cs
using Waygrid.Sdk;
using Waygrid.Sdk.Extensions;

var builder = WebApplication.CreateBuilder(args);

// Register Waygrid client
builder.Services.AddWaygrid(options =>
{
    options.BaseUrl = builder.Configuration["Waygrid:BaseUrl"]!;
    options.ApiKey = builder.Configuration["Waygrid:ApiKey"]!;
});

var app = builder.Build();
```

### Usage in Controllers

```csharp
using Microsoft.AspNetCore.Mvc;
using Waygrid.Sdk;
using Waygrid.Sdk.Nodes;

[ApiController]
[Route("api/[controller]")]
public class WorkflowController : ControllerBase
{
    private readonly IWaygridClient _waygrid;

    public WorkflowController(IWaygridClient waygrid)
    {
        _waygrid = waygrid;
    }

    [HttpPost("process")]
    public async Task<IActionResult> StartProcessing([FromBody] ProcessRequest request)
    {
        var spec = Spec.Single(
            entryPoint: Node.Standard("waygrid://processor/openai")
                .OnSuccess(Node.Standard("waygrid://destination/webhook")),
            repeatPolicy: RepeatPolicy.NoRepeat
        );

        var result = await _waygrid.SubmitAsync(spec, payload: request);

        return Ok(new
        {
            TraversalId = result.TraversalId,
            Status = "submitted"
        });
    }
}
```

## Record Types (Immutable Models)

The SDK uses C# records for immutable models:

```csharp
namespace Waygrid.Sdk;

public sealed record Spec(
    IReadOnlyList<INode> EntryPoints,
    RepeatPolicy RepeatPolicy
);

public abstract record RetryPolicy
{
    public sealed record None : RetryPolicy;
    public sealed record Linear(TimeSpan Base, int MaxRetries) : RetryPolicy;
    public sealed record Exponential(TimeSpan Base, int MaxRetries) : RetryPolicy;
    // ...
}

public abstract record JoinStrategy
{
    public sealed record And : JoinStrategy;
    public sealed record Or : JoinStrategy;
    public sealed record Quorum(int N) : JoinStrategy;
}
```

## JSON Serialization

The SDK uses `System.Text.Json` with source generators for AOT compatibility:

```csharp
using System.Text.Json;
using Waygrid.Sdk.Json;

// Spec to JSON
string json = spec.ToJson();
string prettyJson = spec.ToJson(indented: true);

// JSON to Spec
Spec? parsed = Spec.FromJson(json);

// Using custom options
var options = WaygridJsonOptions.Default;
string json = JsonSerializer.Serialize(spec, options);
```

## Repository Structure

The SDK repository (`waygrid-sdk-csharp`) is organized as:

```
waygrid-sdk-csharp/
├── Waygrid.Sdk.sln
├── src/
│   └── Waygrid.Sdk/
│       ├── Waygrid.Sdk.csproj
│       ├── Spec.cs
│       ├── Nodes/
│       │   ├── INode.cs
│       │   ├── StandardNode.cs
│       │   ├── ForkNode.cs
│       │   └── JoinNode.cs
│       ├── Policies/
│       │   ├── RetryPolicy.cs
│       │   ├── DeliveryStrategy.cs
│       │   └── RepeatPolicy.cs
│       ├── Conditions/
│       │   └── Condition.cs
│       ├── Strategies/
│       │   └── JoinStrategy.cs
│       ├── Client/
│       │   ├── IWaygridClient.cs
│       │   ├── WaygridClient.cs
│       │   └── SubmitResult.cs
│       ├── Json/
│       │   ├── WaygridJsonOptions.cs
│       │   └── WaygridJsonContext.cs
│       └── Extensions/
│           └── ServiceCollectionExtensions.cs
└── tests/
    └── Waygrid.Sdk.Tests/
        ├── SpecTests.cs
        ├── NodeTests.cs
        └── SerializationTests.cs
```

## See Also

- [SDK Overview](./overview) - Compare SDKs
- [Spec JSON Schema](/docs/api/spec-schema) - Raw JSON format
