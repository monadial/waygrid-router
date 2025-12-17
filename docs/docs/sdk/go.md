---
sidebar_position: 9
---

# Go SDK

The official Go SDK for Waygrid provides an idiomatic, type-safe API for building traversal specifications. It follows Go conventions with functional options, interfaces, and explicit error handling.

## Installation

```bash
go get github.com/waygrid/waygrid-sdk-go
```

**Requirements:**
- Go 1.21+

## Quick Start

```go
package main

import (
    "fmt"
    "log"
    "time"

    "github.com/waygrid/waygrid-sdk-go/waygrid"
)

func main() {
    // Define a simple workflow
    spec := waygrid.SingleSpec(
        waygrid.StandardNode("waygrid://processor/openai").
            Label("AI Processing").
            WithRetry(waygrid.ExponentialRetry(time.Second, 3)).
            OnSuccess(
                waygrid.StandardNode("waygrid://destination/webhook").
                    Label("Deliver Result"),
            ),
        waygrid.NoRepeat(),
    )

    // Convert to JSON
    json, err := spec.ToJSON()
    if err != nil {
        log.Fatal(err)
    }
    fmt.Println(string(json))
}
```

### With HTTP Client

```go
package main

import (
    "context"
    "fmt"
    "log"
    "os"

    "github.com/waygrid/waygrid-sdk-go/waygrid"
)

func main() {
    client := waygrid.NewClient(
        "https://api.waygrid.io",
        os.Getenv("WAYGRID_API_KEY"),
    )

    spec := waygrid.SingleSpec(
        waygrid.StandardNode("waygrid://processor/openai").
            OnSuccess(waygrid.StandardNode("waygrid://destination/webhook")),
        waygrid.NoRepeat(),
    )

    result, err := client.Submit(context.Background(), spec)
    if err != nil {
        log.Fatal(err)
    }
    fmt.Printf("Traversal ID: %s\n", result.TraversalID)
}
```

## Core API

### Spec

The root container for a traversal specification:

```go
import "github.com/waygrid/waygrid-sdk-go/waygrid"

// Single entry point (most common)
spec := waygrid.SingleSpec(myNode, waygrid.NoRepeat())

// Multiple entry points (fan-in from multiple origins)
spec := waygrid.MultipleSpec(
    []waygrid.Node{node1, node2, node3},
    waygrid.NoRepeat(),
)
```

### Node Types

#### Standard Node

Linear processing step with optional continuations:

```go
import (
    "time"
    "github.com/waygrid/waygrid-sdk-go/waygrid"
)

// Minimal
node := waygrid.StandardNode("waygrid://processor/openai")

// Full configuration
node := waygrid.StandardNode("waygrid://processor/openai").
    Label("AI Content Analysis").
    WithRetry(waygrid.ExponentialRetry(time.Second, 5)).
    WithDelivery(waygrid.ScheduleAfter(5 * time.Minute)).
    OnSuccess(successHandler).
    OnFailure(failureHandler).
    OnConditions(
        waygrid.When(
            waygrid.JSONEquals("/sentiment", "negative"),
            escalationNode,
        ),
        waygrid.When(
            waygrid.JSONEquals("/sentiment", "positive"),
            archiveNode,
        ),
    )
```

#### Fork Node

Fan-out to parallel branches:

```go
fork := waygrid.ForkNode("waygrid://system/waystation").
    JoinID("enrich-parallel").
    Label("Parallel Enrichment").
    Branches(map[string]waygrid.Node{
        "sentiment": waygrid.StandardNode("waygrid://processor/sentiment"),
        "translate": waygrid.StandardNode("waygrid://processor/translate"),
        "summarize": waygrid.StandardNode("waygrid://processor/openai"),
    })
```

#### Join Node

Fan-in from parallel branches:

```go
join := waygrid.JoinNode("waygrid://system/waystation").
    JoinID("enrich-parallel").  // Must match Fork's JoinID
    Strategy(waygrid.JoinAll).  // Wait for all
    Timeout(30 * time.Second).
    OnSuccess(aggregateNode).
    OnFailure(errorHandler).
    OnTimeout(timeoutHandler)
```

### Retry Policies

```go
import (
    "time"
    "github.com/waygrid/waygrid-sdk-go/waygrid"
)

// No retries
waygrid.NoRetry()

// Linear backoff: 5s, 5s, 5s...
waygrid.LinearRetry(5*time.Second, 3)

// Exponential backoff: 1s, 2s, 4s, 8s...
waygrid.ExponentialRetry(time.Second, 5)

// Bounded exponential: exponential with cap
waygrid.BoundedExponentialRetry(time.Second, time.Minute, 10)

// Fibonacci backoff: 1s, 1s, 2s, 3s, 5s...
waygrid.FibonacciRetry(time.Second, 5)

// Full jitter (randomized)
waygrid.FullJitterRetry(time.Second, 5)

// Decorrelated jitter (AWS-style)
waygrid.DecorrelatedJitterRetry(time.Second, 5)
```

### Delivery Strategies

```go
import (
    "time"
    "github.com/waygrid/waygrid-sdk-go/waygrid"
)

// Execute immediately (default)
waygrid.Immediate()

// Schedule after delay
waygrid.ScheduleAfter(5 * time.Minute)

// Schedule at specific time
waygrid.ScheduleAt(time.Date(2024, 12, 25, 0, 0, 0, 0, time.UTC))
```

### Repeat Policies

```go
import (
    "time"
    "github.com/waygrid/waygrid-sdk-go/waygrid"
)

// One-shot execution
waygrid.NoRepeat()

// Repeat forever
waygrid.RepeatIndefinitely(time.Hour)

// Repeat N times
waygrid.RepeatTimes(time.Hour, 10)

// Repeat until timestamp
waygrid.RepeatUntil(time.Hour, someTime)
```

### Conditions

For conditional routing based on node output:

```go
import "github.com/waygrid/waygrid-sdk-go/waygrid"

// Always matches
waygrid.Always()

// Check if JSON pointer exists
waygrid.JSONExists("/result/data")

// Check JSON value equality
waygrid.JSONEquals("/status", "approved")
waygrid.JSONEquals("/count", 0)
waygrid.JSONEquals("/valid", true)

// Logical operators
waygrid.Not(waygrid.JSONExists("/error"))

waygrid.And(
    waygrid.JSONExists("/result"),
    waygrid.JSONEquals("/result/valid", true),
)

waygrid.Or(
    waygrid.JSONEquals("/status", "approved"),
    waygrid.JSONEquals("/status", "auto_approved"),
)
```

## Advanced Patterns

### Error Handling Pipeline

```go
workflow := waygrid.StandardNode("waygrid://processor/risky-operation").
    WithRetry(waygrid.ExponentialRetry(time.Second, 3)).
    OnSuccess(
        waygrid.StandardNode("waygrid://destination/webhook").
            Label("Success Notification"),
    ).
    OnFailure(
        waygrid.StandardNode("waygrid://processor/error-classifier").
            Label("Classify Error").
            OnConditions(
                waygrid.When(
                    waygrid.JSONEquals("/errorType", "retryable"),
                    waygrid.StandardNode("waygrid://system/scheduler").
                        WithDelivery(waygrid.ScheduleAfter(time.Hour)).
                        Label("Retry Later"),
                ),
                waygrid.When(
                    waygrid.JSONEquals("/errorType", "fatal"),
                    waygrid.StandardNode("waygrid://destination/webhook").
                        Label("Alert On-Call"),
                ),
            ),
    )
```

### Parallel Processing with Quorum

```go
// Call 3 providers, continue when 2 respond
spec := waygrid.SingleSpec(
    waygrid.ForkNode("waygrid://system/waystation").
        JoinID("multi-provider").
        Branches(map[string]waygrid.Node{
            "provider-a": waygrid.StandardNode("waygrid://destination/webhook").
                Label("Provider A"),
            "provider-b": waygrid.StandardNode("waygrid://destination/webhook").
                Label("Provider B"),
            "provider-c": waygrid.StandardNode("waygrid://destination/webhook").
                Label("Provider C"),
        }),
    waygrid.NoRepeat(),
)

// The Join node with Quorum strategy
join := waygrid.JoinNode("waygrid://system/waystation").
    JoinID("multi-provider").
    Strategy(waygrid.JoinQuorum(2)).  // 2 of 3 required
    Timeout(30 * time.Second).
    OnSuccess(aggregateResults).
    OnTimeout(handlePartialResults)
```

## Gin Integration

```go
package main

import (
    "net/http"
    "os"

    "github.com/gin-gonic/gin"
    "github.com/waygrid/waygrid-sdk-go/waygrid"
)

func main() {
    client := waygrid.NewClient(
        os.Getenv("WAYGRID_BASE_URL"),
        os.Getenv("WAYGRID_API_KEY"),
    )

    r := gin.Default()

    r.POST("/workflow/start", func(c *gin.Context) {
        var payload map[string]interface{}
        if err := c.BindJSON(&payload); err != nil {
            c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
            return
        }

        spec := waygrid.SingleSpec(
            waygrid.StandardNode("waygrid://processor/openai").
                OnSuccess(waygrid.StandardNode("waygrid://destination/webhook")),
            waygrid.NoRepeat(),
        )

        result, err := client.SubmitWithPayload(c.Request.Context(), spec, payload)
        if err != nil {
            c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
            return
        }

        c.JSON(http.StatusOK, gin.H{
            "traversal_id": result.TraversalID,
            "status":       "submitted",
        })
    })

    r.Run(":8080")
}
```

## Echo Integration

```go
package main

import (
    "net/http"
    "os"

    "github.com/labstack/echo/v4"
    "github.com/waygrid/waygrid-sdk-go/waygrid"
)

func main() {
    client := waygrid.NewClient(
        os.Getenv("WAYGRID_BASE_URL"),
        os.Getenv("WAYGRID_API_KEY"),
    )

    e := echo.New()

    e.POST("/workflow/start", func(c echo.Context) error {
        var payload map[string]interface{}
        if err := c.Bind(&payload); err != nil {
            return err
        }

        spec := waygrid.SingleSpec(
            waygrid.StandardNode("waygrid://processor/openai").
                OnSuccess(waygrid.StandardNode("waygrid://destination/webhook")),
            waygrid.NoRepeat(),
        )

        result, err := client.SubmitWithPayload(c.Request().Context(), spec, payload)
        if err != nil {
            return err
        }

        return c.JSON(http.StatusOK, map[string]interface{}{
            "traversal_id": result.TraversalID,
            "status":       "submitted",
        })
    })

    e.Logger.Fatal(e.Start(":8080"))
}
```

## Functional Options Pattern

```go
package waygrid

// NodeOption configures a node
type NodeOption func(*nodeConfig)

// WithLabel sets the node label
func WithLabel(label string) NodeOption {
    return func(c *nodeConfig) {
        c.label = label
    }
}

// WithRetryPolicy sets the retry policy
func WithRetryPolicy(policy RetryPolicy) NodeOption {
    return func(c *nodeConfig) {
        c.retryPolicy = policy
    }
}

// Usage
node := waygrid.NewStandardNode(
    "waygrid://processor/openai",
    waygrid.WithLabel("AI Processing"),
    waygrid.WithRetryPolicy(waygrid.ExponentialRetry(time.Second, 3)),
)
```

## Interface Types

```go
package waygrid

// Node represents any node in the DAG
type Node interface {
    Address() string
    ToJSON() ([]byte, error)
    nodeType() string
}

// RetryPolicy defines retry behavior
type RetryPolicy interface {
    Type() string
    ToJSON() ([]byte, error)
}

// Condition defines routing conditions
type Condition interface {
    Evaluate(output map[string]interface{}) bool
    ToJSON() ([]byte, error)
}

// JoinStrategy defines join completion behavior
type JoinStrategy interface {
    Type() string
    ToJSON() ([]byte, error)
}
```

## Repository Structure

The SDK repository (`waygrid-sdk-go`) is organized as:

```
waygrid-sdk-go/
├── go.mod
├── go.sum
├── waygrid/
│   ├── spec.go
│   ├── node.go
│   ├── standard_node.go
│   ├── fork_node.go
│   ├── join_node.go
│   ├── retry_policy.go
│   ├── delivery_strategy.go
│   ├── repeat_policy.go
│   ├── condition.go
│   ├── join_strategy.go
│   ├── client.go
│   └── result.go
├── examples/
│   ├── simple/
│   │   └── main.go
│   ├── parallel/
│   │   └── main.go
│   └── conditional/
│       └── main.go
└── waygrid_test/
    ├── spec_test.go
    ├── node_test.go
    └── serialization_test.go
```

## See Also

- [SDK Overview](./overview) - Compare SDKs
- [Spec JSON Schema](/docs/api/spec-schema) - Raw JSON format
