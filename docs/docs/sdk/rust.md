---
sidebar_position: 6
---

# Rust SDK

The official Rust SDK for Waygrid provides a type-safe, zero-cost abstraction API for building traversal specifications. It leverages Rust's ownership system, enums, and traits for compile-time safety.

## Installation

Add to your `Cargo.toml`:

```toml
[dependencies]
waygrid-sdk = "0.1"

# Optional: async client with tokio
waygrid-sdk = { version = "0.1", features = ["client", "tokio"] }
```

**Requirements:**
- Rust 1.75+ (2024 edition)
- `serde` for serialization (included)

## Quick Start

```rust
use waygrid_sdk::{Spec, Node, RetryPolicy, RepeatPolicy};
use std::time::Duration;

fn main() -> Result<(), Box<dyn std::error::Error>> {
    // Define a simple workflow
    let spec = Spec::single(
        Node::standard("waygrid://processor/openai")
            .label("AI Processing")
            .with_retry(RetryPolicy::exponential(Duration::from_secs(1), 3))
            .on_success(
                Node::standard("waygrid://destination/webhook")
                    .label("Deliver Result")
            ),
        RepeatPolicy::NoRepeat,
    );

    // Convert to JSON
    let json = spec.to_json()?;
    println!("{}", json);

    Ok(())
}
```

### With Async Client

```rust
use waygrid_sdk::{Spec, Node, RepeatPolicy, WaygridClient};
use std::env;

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let client = WaygridClient::new(
        "https://api.waygrid.io",
        &env::var("WAYGRID_API_KEY")?,
    );

    let spec = Spec::single(
        Node::standard("waygrid://processor/openai")
            .on_success(Node::standard("waygrid://destination/webhook")),
        RepeatPolicy::NoRepeat,
    );

    let result = client.submit(&spec).await?;
    println!("Traversal ID: {}", result.traversal_id);

    Ok(())
}
```

## Core API

### Spec

The root container for a traversal specification:

```rust
use waygrid_sdk::{Spec, RepeatPolicy};

// Single entry point (most common)
let spec = Spec::single(my_node, RepeatPolicy::NoRepeat);

// Multiple entry points (fan-in from multiple origins)
let spec = Spec::multiple(
    vec![node1, node2, node3],
    RepeatPolicy::NoRepeat,
);
```

### Node Types

#### Standard Node

Linear processing step with optional continuations:

```rust
use waygrid_sdk::{Node, RetryPolicy, DeliveryStrategy, Condition};
use std::time::Duration;

// Minimal
let node = Node::standard("waygrid://processor/openai");

// Full configuration
let node = Node::standard("waygrid://processor/openai")
    .label("AI Content Analysis")
    .with_retry(RetryPolicy::exponential(Duration::from_secs(1), 5))
    .with_delivery(DeliveryStrategy::schedule_after(Duration::from_secs(300)))
    .on_success(success_handler)
    .on_failure(failure_handler)
    .on_conditions(vec![
        Condition::when(
            Condition::json_equals("/sentiment", "negative"),
            escalation_node,
        ),
        Condition::when(
            Condition::json_equals("/sentiment", "positive"),
            archive_node,
        ),
    ]);
```

#### Fork Node

Fan-out to parallel branches:

```rust
use std::collections::HashMap;

let mut branches = HashMap::new();
branches.insert("sentiment".to_string(), Node::standard("waygrid://processor/sentiment"));
branches.insert("translate".to_string(), Node::standard("waygrid://processor/translate"));
branches.insert("summarize".to_string(), Node::standard("waygrid://processor/openai"));

let fork = Node::fork("waygrid://system/waystation")
    .join_id("enrich-parallel")
    .label("Parallel Enrichment")
    .branches(branches);
```

#### Join Node

Fan-in from parallel branches:

```rust
use waygrid_sdk::JoinStrategy;
use std::time::Duration;

let join = Node::join("waygrid://system/waystation")
    .join_id("enrich-parallel")  // Must match Fork's join_id
    .strategy(JoinStrategy::And)  // Wait for all
    .timeout(Duration::from_secs(30))
    .on_success(aggregate_node)
    .on_failure(error_handler)
    .on_timeout(timeout_handler);
```

### Retry Policies

```rust
use waygrid_sdk::RetryPolicy;
use std::time::Duration;

// No retries
RetryPolicy::None

// Linear backoff: 5s, 5s, 5s...
RetryPolicy::linear(Duration::from_secs(5), 3)

// Exponential backoff: 1s, 2s, 4s, 8s...
RetryPolicy::exponential(Duration::from_secs(1), 5)

// Bounded exponential: exponential with cap
RetryPolicy::bounded_exponential(
    Duration::from_secs(1),  // base
    Duration::from_secs(60), // cap
    10,                       // max_retries
)

// Fibonacci backoff: 1s, 1s, 2s, 3s, 5s...
RetryPolicy::fibonacci(Duration::from_secs(1), 5)

// Full jitter (randomized)
RetryPolicy::full_jitter(Duration::from_secs(1), 5)

// Decorrelated jitter (AWS-style)
RetryPolicy::decorrelated_jitter(Duration::from_secs(1), 5)
```

### Delivery Strategies

```rust
use waygrid_sdk::DeliveryStrategy;
use std::time::Duration;
use chrono::{DateTime, Utc};

// Execute immediately (default)
DeliveryStrategy::Immediate

// Schedule after delay
DeliveryStrategy::schedule_after(Duration::from_secs(300))

// Schedule at specific time
DeliveryStrategy::schedule_at(DateTime::parse_from_rfc3339("2024-12-25T00:00:00Z")?.with_timezone(&Utc))
```

### Repeat Policies

```rust
use waygrid_sdk::RepeatPolicy;
use std::time::Duration;
use chrono::{DateTime, Utc};

// One-shot execution
RepeatPolicy::NoRepeat

// Repeat forever
RepeatPolicy::indefinitely(Duration::from_secs(3600))

// Repeat N times
RepeatPolicy::times(Duration::from_secs(3600), 10)

// Repeat until timestamp
RepeatPolicy::until(
    Duration::from_secs(3600),
    DateTime::parse_from_rfc3339("2024-12-31T23:59:59Z")?.with_timezone(&Utc),
)
```

### Conditions

For conditional routing based on node output:

```rust
use waygrid_sdk::Condition;
use serde_json::json;

// Always matches
Condition::Always

// Check if JSON pointer exists
Condition::json_exists("/result/data")

// Check JSON value equality
Condition::json_equals("/status", json!("approved"))
Condition::json_equals("/count", json!(0))
Condition::json_equals("/valid", json!(true))

// Logical operators
Condition::not(Condition::json_exists("/error"))

Condition::and(vec![
    Condition::json_exists("/result"),
    Condition::json_equals("/result/valid", json!(true)),
])

Condition::or(vec![
    Condition::json_equals("/status", json!("approved")),
    Condition::json_equals("/status", json!("auto_approved")),
])
```

## Advanced Patterns

### Error Handling Pipeline

```rust
let workflow = Node::standard("waygrid://processor/risky-operation")
    .with_retry(RetryPolicy::exponential(Duration::from_secs(1), 3))
    .on_success(
        Node::standard("waygrid://destination/webhook")
            .label("Success Notification")
    )
    .on_failure(
        Node::standard("waygrid://processor/error-classifier")
            .label("Classify Error")
            .on_conditions(vec![
                Condition::when(
                    Condition::json_equals("/errorType", json!("retryable")),
                    Node::standard("waygrid://system/scheduler")
                        .with_delivery(DeliveryStrategy::schedule_after(Duration::from_secs(3600)))
                        .label("Retry Later"),
                ),
                Condition::when(
                    Condition::json_equals("/errorType", json!("fatal")),
                    Node::standard("waygrid://destination/webhook")
                        .label("Alert On-Call"),
                ),
            ])
    );
```

### Builder Pattern with Type State

```rust
use waygrid_sdk::builder::{SpecBuilder, Incomplete, Complete};

// Type-safe builder that won't compile without required fields
let spec: Spec = SpecBuilder::new()
    .entry_point(my_node)       // Required
    .repeat_policy(RepeatPolicy::NoRepeat)  // Required
    .build();  // Only available when Complete

// This won't compile:
// let spec = SpecBuilder::new().build();  // Error: missing entry_point
```

## Actix-web Integration

```rust
use actix_web::{web, App, HttpServer, HttpResponse};
use waygrid_sdk::{WaygridClient, Spec, Node, RepeatPolicy};
use std::sync::Arc;

struct AppState {
    waygrid: Arc<WaygridClient>,
}

async fn start_workflow(
    data: web::Json<serde_json::Value>,
    state: web::Data<AppState>,
) -> HttpResponse {
    let spec = Spec::single(
        Node::standard("waygrid://processor/openai")
            .on_success(Node::standard("waygrid://destination/webhook")),
        RepeatPolicy::NoRepeat,
    );

    match state.waygrid.submit(&spec).await {
        Ok(result) => HttpResponse::Ok().json(serde_json::json!({
            "traversal_id": result.traversal_id,
            "status": "submitted"
        })),
        Err(e) => HttpResponse::InternalServerError().body(e.to_string()),
    }
}

#[actix_web::main]
async fn main() -> std::io::Result<()> {
    let client = WaygridClient::new(
        "https://api.waygrid.io",
        &std::env::var("WAYGRID_API_KEY").expect("WAYGRID_API_KEY required"),
    );

    let state = web::Data::new(AppState {
        waygrid: Arc::new(client),
    });

    HttpServer::new(move || {
        App::new()
            .app_data(state.clone())
            .route("/workflow", web::post().to(start_workflow))
    })
    .bind("127.0.0.1:8080")?
    .run()
    .await
}
```

## Axum Integration

```rust
use axum::{routing::post, Router, Json, Extension};
use waygrid_sdk::{WaygridClient, Spec, Node, RepeatPolicy};
use std::sync::Arc;

async fn start_workflow(
    Extension(client): Extension<Arc<WaygridClient>>,
    Json(payload): Json<serde_json::Value>,
) -> Json<serde_json::Value> {
    let spec = Spec::single(
        Node::standard("waygrid://processor/openai")
            .on_success(Node::standard("waygrid://destination/webhook")),
        RepeatPolicy::NoRepeat,
    );

    match client.submit(&spec).await {
        Ok(result) => Json(serde_json::json!({
            "traversal_id": result.traversal_id,
            "status": "submitted"
        })),
        Err(e) => Json(serde_json::json!({
            "error": e.to_string()
        })),
    }
}

#[tokio::main]
async fn main() {
    let client = Arc::new(WaygridClient::new(
        "https://api.waygrid.io",
        &std::env::var("WAYGRID_API_KEY").expect("WAYGRID_API_KEY required"),
    ));

    let app = Router::new()
        .route("/workflow", post(start_workflow))
        .layer(Extension(client));

    axum::Server::bind(&"0.0.0.0:3000".parse().unwrap())
        .serve(app.into_make_service())
        .await
        .unwrap();
}
```

## Enum Types

The SDK uses Rust enums for type safety:

```rust
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type")]
pub enum RetryPolicy {
    None,
    Linear { base: Duration, max_retries: u32 },
    Exponential { base: Duration, max_retries: u32 },
    BoundedExponential { base: Duration, cap: Duration, max_retries: u32 },
    Fibonacci { base: Duration, max_retries: u32 },
    FullJitter { base: Duration, max_retries: u32 },
    DecorrelatedJitter { base: Duration, max_retries: u32 },
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type")]
pub enum JoinStrategy {
    And,
    Or,
    Quorum { n: u32 },
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type")]
pub enum Node {
    Standard(StandardNode),
    Fork(ForkNode),
    Join(JoinNode),
}
```

## Repository Structure

The SDK repository (`waygrid-sdk-rust`) is organized as:

```
waygrid-sdk-rust/
├── Cargo.toml
├── src/
│   ├── lib.rs
│   ├── spec.rs
│   ├── node/
│   │   ├── mod.rs
│   │   ├── standard.rs
│   │   ├── fork.rs
│   │   └── join.rs
│   ├── policy/
│   │   ├── mod.rs
│   │   ├── retry.rs
│   │   ├── delivery.rs
│   │   └── repeat.rs
│   ├── condition/
│   │   ├── mod.rs
│   │   └── condition.rs
│   ├── strategy/
│   │   ├── mod.rs
│   │   └── join.rs
│   ├── client/
│   │   ├── mod.rs
│   │   ├── client.rs
│   │   └── result.rs
│   └── builder/
│       └── mod.rs
├── examples/
│   ├── simple.rs
│   ├── parallel.rs
│   └── conditional.rs
└── tests/
    ├── spec_tests.rs
    ├── node_tests.rs
    └── serialization_tests.rs
```

## Feature Flags

```toml
[dependencies]
waygrid-sdk = { version = "0.1", features = ["client", "tokio"] }

# Available features:
# - client: HTTP client for Waygrid API
# - tokio: Tokio runtime support
# - async-std: async-std runtime support
# - blocking: Synchronous blocking client
```

## See Also

- [SDK Overview](./overview) - Compare SDKs
- [Spec JSON Schema](/docs/api/spec-schema) - Raw JSON format
