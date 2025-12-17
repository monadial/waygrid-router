---
sidebar_position: 7
---

# C++ SDK

The official C++ SDK for Waygrid provides a modern, type-safe API for building traversal specifications. It requires C++20 and leverages features like concepts, ranges, and structured bindings.

## Installation

### vcpkg

```bash
vcpkg install waygrid-sdk
```

### CMake FetchContent

```cmake
include(FetchContent)
FetchContent_Declare(
    waygrid-sdk
    GIT_REPOSITORY https://github.com/waygrid/waygrid-sdk-cpp.git
    GIT_TAG v0.1.0
)
FetchContent_MakeAvailable(waygrid-sdk)

target_link_libraries(your_target PRIVATE waygrid::sdk)
```

### Conan

```bash
conan install waygrid-sdk/0.1.0@
```

**Requirements:**
- C++20 compatible compiler (GCC 12+, Clang 15+, MSVC 2022+)
- nlohmann/json (included via package manager)
- libcurl (optional, for HTTP client)

## Quick Start

```cpp
#include <waygrid/sdk.hpp>
#include <iostream>

int main() {
    using namespace waygrid;

    // Define a simple workflow
    auto spec = Spec::single(
        Node::standard("waygrid://processor/openai")
            .label("AI Processing")
            .with_retry(RetryPolicy::exponential(std::chrono::seconds{1}, 3))
            .on_success(
                Node::standard("waygrid://destination/webhook")
                    .label("Deliver Result")
            ),
        RepeatPolicy::no_repeat()
    );

    // Convert to JSON
    std::cout << spec.to_json() << std::endl;

    return 0;
}
```

### With HTTP Client

```cpp
#include <waygrid/sdk.hpp>
#include <waygrid/client.hpp>
#include <cstdlib>

int main() {
    using namespace waygrid;

    auto client = WaygridClient{
        "https://api.waygrid.io",
        std::getenv("WAYGRID_API_KEY")
    };

    auto spec = Spec::single(
        Node::standard("waygrid://processor/openai")
            .on_success(Node::standard("waygrid://destination/webhook")),
        RepeatPolicy::no_repeat()
    );

    auto result = client.submit(spec);
    std::cout << "Traversal ID: " << result.traversal_id << std::endl;

    return 0;
}
```

## Core API

### Spec

The root container for a traversal specification:

```cpp
#include <waygrid/sdk.hpp>
using namespace waygrid;

// Single entry point (most common)
auto spec = Spec::single(my_node, RepeatPolicy::no_repeat());

// Multiple entry points (fan-in from multiple origins)
auto spec = Spec::multiple(
    {node1, node2, node3},
    RepeatPolicy::no_repeat()
);
```

### Node Types

#### Standard Node

Linear processing step with optional continuations:

```cpp
using namespace waygrid;
using namespace std::chrono_literals;

// Minimal
auto node = Node::standard("waygrid://processor/openai");

// Full configuration
auto node = Node::standard("waygrid://processor/openai")
    .label("AI Content Analysis")
    .with_retry(RetryPolicy::exponential(1s, 5))
    .with_delivery(DeliveryStrategy::schedule_after(5min))
    .on_success(success_handler)
    .on_failure(failure_handler)
    .on_conditions({
        Condition::when(
            Condition::json_equals("/sentiment", "negative"),
            escalation_node
        ),
        Condition::when(
            Condition::json_equals("/sentiment", "positive"),
            archive_node
        )
    });
```

#### Fork Node

Fan-out to parallel branches:

```cpp
auto fork = Node::fork("waygrid://system/waystation")
    .join_id("enrich-parallel")
    .label("Parallel Enrichment")
    .branches({
        {"sentiment", Node::standard("waygrid://processor/sentiment")},
        {"translate", Node::standard("waygrid://processor/translate")},
        {"summarize", Node::standard("waygrid://processor/openai")}
    });
```

#### Join Node

Fan-in from parallel branches:

```cpp
using namespace std::chrono_literals;

auto join = Node::join("waygrid://system/waystation")
    .join_id("enrich-parallel")  // Must match Fork's join_id
    .strategy(JoinStrategy::all())  // Wait for all
    .timeout(30s)
    .on_success(aggregate_node)
    .on_failure(error_handler)
    .on_timeout(timeout_handler);
```

### Retry Policies

```cpp
using namespace waygrid;
using namespace std::chrono_literals;

// No retries
RetryPolicy::none()

// Linear backoff: 5s, 5s, 5s...
RetryPolicy::linear(5s, 3)

// Exponential backoff: 1s, 2s, 4s, 8s...
RetryPolicy::exponential(1s, 5)

// Bounded exponential: exponential with cap
RetryPolicy::bounded_exponential(1s, 1min, 10)

// Fibonacci backoff: 1s, 1s, 2s, 3s, 5s...
RetryPolicy::fibonacci(1s, 5)

// Full jitter (randomized)
RetryPolicy::full_jitter(1s, 5)

// Decorrelated jitter (AWS-style)
RetryPolicy::decorrelated_jitter(1s, 5)
```

### Delivery Strategies

```cpp
using namespace waygrid;
using namespace std::chrono_literals;
using namespace std::chrono;

// Execute immediately (default)
DeliveryStrategy::immediate()

// Schedule after delay
DeliveryStrategy::schedule_after(5min)

// Schedule at specific time
DeliveryStrategy::schedule_at(
    system_clock::time_point{} + hours{24}  // 24 hours from epoch
)
```

### Repeat Policies

```cpp
using namespace waygrid;
using namespace std::chrono_literals;

// One-shot execution
RepeatPolicy::no_repeat()

// Repeat forever
RepeatPolicy::indefinitely(1h)

// Repeat N times
RepeatPolicy::times(1h, 10)

// Repeat until timestamp
RepeatPolicy::until(1h, some_time_point)
```

### Conditions

For conditional routing based on node output:

```cpp
using namespace waygrid;

// Always matches
Condition::always()

// Check if JSON pointer exists
Condition::json_exists("/result/data")

// Check JSON value equality
Condition::json_equals("/status", "approved")
Condition::json_equals("/count", 0)
Condition::json_equals("/valid", true)

// Logical operators
Condition::not_(Condition::json_exists("/error"))

Condition::all({
    Condition::json_exists("/result"),
    Condition::json_equals("/result/valid", true)
})

Condition::any({
    Condition::json_equals("/status", "approved"),
    Condition::json_equals("/status", "auto_approved")
})
```

## Advanced Patterns

### Error Handling Pipeline

```cpp
using namespace std::chrono_literals;

auto workflow = Node::standard("waygrid://processor/risky-operation")
    .with_retry(RetryPolicy::exponential(1s, 3))
    .on_success(
        Node::standard("waygrid://destination/webhook")
            .label("Success Notification")
    )
    .on_failure(
        Node::standard("waygrid://processor/error-classifier")
            .label("Classify Error")
            .on_conditions({
                Condition::when(
                    Condition::json_equals("/errorType", "retryable"),
                    Node::standard("waygrid://system/scheduler")
                        .with_delivery(DeliveryStrategy::schedule_after(1h))
                        .label("Retry Later")
                ),
                Condition::when(
                    Condition::json_equals("/errorType", "fatal"),
                    Node::standard("waygrid://destination/webhook")
                        .label("Alert On-Call")
                )
            })
    );
```

### Parallel Processing with Quorum

```cpp
// Call 3 providers, continue when 2 respond
auto spec = Spec::single(
    Node::fork("waygrid://system/waystation")
        .join_id("multi-provider")
        .branches({
            {"provider-a", Node::standard("waygrid://destination/webhook")
                .label("Provider A")},
            {"provider-b", Node::standard("waygrid://destination/webhook")
                .label("Provider B")},
            {"provider-c", Node::standard("waygrid://destination/webhook")
                .label("Provider C")}
        }),
    RepeatPolicy::no_repeat()
);

// The Join node with Quorum strategy
auto join = Node::join("waygrid://system/waystation")
    .join_id("multi-provider")
    .strategy(JoinStrategy::quorum(2))  // 2 of 3 required
    .timeout(30s)
    .on_success(aggregate_results)
    .on_timeout(handle_partial_results);
```

## Modern C++ Features

### Concepts

```cpp
namespace waygrid {

template<typename T>
concept NodeLike = requires(T t) {
    { t.address() } -> std::convertible_to<std::string_view>;
    { t.to_json() } -> std::convertible_to<nlohmann::json>;
};

template<NodeLike N>
Spec make_simple_spec(N&& node) {
    return Spec::single(std::forward<N>(node), RepeatPolicy::no_repeat());
}

}
```

### std::variant for Sum Types

```cpp
namespace waygrid {

using RetryPolicyVariant = std::variant<
    NoneRetryPolicy,
    LinearRetryPolicy,
    ExponentialRetryPolicy,
    BoundedExponentialRetryPolicy,
    FibonacciRetryPolicy,
    FullJitterRetryPolicy,
    DecorrelatedJitterRetryPolicy
>;

using NodeVariant = std::variant<
    StandardNode,
    ForkNode,
    JoinNode
>;

}
```

### std::expected for Error Handling (C++23)

```cpp
#include <expected>

namespace waygrid {

std::expected<SubmitResult, WaygridError> submit(const Spec& spec) {
    // ...
}

// Usage
auto result = client.submit(spec);
if (result) {
    std::cout << "ID: " << result->traversal_id << std::endl;
} else {
    std::cerr << "Error: " << result.error().message << std::endl;
}

}
```

## CMake Integration

```cmake
cmake_minimum_required(VERSION 3.20)
project(my_waygrid_app)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(waygrid-sdk REQUIRED)

add_executable(my_app main.cpp)
target_link_libraries(my_app PRIVATE waygrid::sdk)
```

## Repository Structure

The SDK repository (`waygrid-sdk-cpp`) is organized as:

```
waygrid-sdk-cpp/
├── CMakeLists.txt
├── vcpkg.json
├── conanfile.py
├── include/
│   └── waygrid/
│       ├── sdk.hpp
│       ├── spec.hpp
│       ├── node/
│       │   ├── node.hpp
│       │   ├── standard.hpp
│       │   ├── fork.hpp
│       │   └── join.hpp
│       ├── policy/
│       │   ├── retry.hpp
│       │   ├── delivery.hpp
│       │   └── repeat.hpp
│       ├── condition/
│       │   └── condition.hpp
│       ├── strategy/
│       │   └── join.hpp
│       └── client/
│           ├── client.hpp
│           └── result.hpp
├── src/
│   ├── spec.cpp
│   ├── node.cpp
│   ├── condition.cpp
│   └── client.cpp
├── examples/
│   ├── simple/
│   ├── parallel/
│   └── conditional/
└── tests/
    ├── spec_test.cpp
    ├── node_test.cpp
    └── serialization_test.cpp
```

## See Also

- [SDK Overview](./overview) - Compare SDKs
- [Spec JSON Schema](/docs/api/spec-schema) - Raw JSON format
