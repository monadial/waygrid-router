---
sidebar_position: 5
---

# Python SDK

The official Python SDK for Waygrid provides a Pythonic, type-safe API for building traversal specifications. It requires Python 3.10+ and leverages modern Python features like dataclasses, type hints, and async/await.

## Installation

Install via pip:

```bash
pip install waygrid-sdk
```

Or with Poetry:

```bash
poetry add waygrid-sdk
```

**Requirements:**
- Python 3.10 or higher
- `httpx` for async HTTP (optional)
- `pydantic` for validation (optional)

## Quick Start

```python
from waygrid import Spec, Node, RetryPolicy, RepeatPolicy
from datetime import timedelta

# Define a simple workflow
spec = Spec.single(
    entry_point=Node.standard("waygrid://processor/openai")
        .label("AI Processing")
        .with_retry(RetryPolicy.exponential(timedelta(seconds=1), max_retries=3))
        .on_success(
            Node.standard("waygrid://destination/webhook")
                .label("Deliver Result")
        ),
    repeat_policy=RepeatPolicy.NO_REPEAT
)

# Convert to JSON
json_str = spec.to_json()

# Or use the client
import asyncio
from waygrid import WaygridClient
import os

async def main():
    async with WaygridClient(
        base_url="https://api.waygrid.io",
        api_key=os.environ["WAYGRID_API_KEY"]
    ) as client:
        result = await client.submit(spec)
        print(f"Traversal ID: {result.traversal_id}")

asyncio.run(main())
```

## Core API

### Spec

The root container for a traversal specification:

```python
from waygrid import Spec, RepeatPolicy

# Single entry point (most common)
spec = Spec.single(
    entry_point=my_node,
    repeat_policy=RepeatPolicy.NO_REPEAT
)

# Multiple entry points (fan-in from multiple origins)
spec = Spec.multiple(
    entry_points=[node1, node2, node3],
    repeat_policy=RepeatPolicy.NO_REPEAT
)
```

### Node Types

#### Standard Node

Linear processing step with optional continuations:

```python
from waygrid import Node, RetryPolicy, DeliveryStrategy, Condition
from datetime import timedelta

# Minimal
node = Node.standard("waygrid://processor/openai")

# Full configuration
node = (
    Node.standard("waygrid://processor/openai")
    .label("AI Content Analysis")
    .with_retry(RetryPolicy.exponential(timedelta(seconds=1), max_retries=5))
    .with_delivery(DeliveryStrategy.schedule_after(timedelta(minutes=5)))
    .on_success(success_handler)
    .on_failure(failure_handler)
    .on_conditions([
        Condition.when(
            Condition.json_equals("/sentiment", "negative"),
            escalation_node
        ),
        Condition.when(
            Condition.json_equals("/sentiment", "positive"),
            archive_node
        ),
    ])
)
```

#### Fork Node

Fan-out to parallel branches:

```python
fork = (
    Node.fork("waygrid://system/waystation")
    .join_id("enrich-parallel")
    .label("Parallel Enrichment")
    .branches({
        "sentiment": Node.standard("waygrid://processor/sentiment"),
        "translate": Node.standard("waygrid://processor/translate"),
        "summarize": Node.standard("waygrid://processor/openai"),
    })
)
```

#### Join Node

Fan-in from parallel branches:

```python
from waygrid import JoinStrategy

join = (
    Node.join("waygrid://system/waystation")
    .join_id("enrich-parallel")  # Must match Fork's join_id
    .strategy(JoinStrategy.AND)  # Wait for all
    .timeout(timedelta(seconds=30))
    .on_success(aggregate_node)
    .on_failure(error_handler)
    .on_timeout(timeout_handler)
)
```

### Retry Policies

```python
from waygrid import RetryPolicy
from datetime import timedelta

# No retries
RetryPolicy.NONE

# Linear backoff: 5s, 5s, 5s...
RetryPolicy.linear(timedelta(seconds=5), max_retries=3)

# Exponential backoff: 1s, 2s, 4s, 8s...
RetryPolicy.exponential(timedelta(seconds=1), max_retries=5)

# Bounded exponential: exponential with cap
RetryPolicy.bounded_exponential(
    base=timedelta(seconds=1),
    cap=timedelta(minutes=1),
    max_retries=10
)

# Fibonacci backoff: 1s, 1s, 2s, 3s, 5s...
RetryPolicy.fibonacci(timedelta(seconds=1), max_retries=5)

# Full jitter (randomized)
RetryPolicy.full_jitter(timedelta(seconds=1), max_retries=5)

# Decorrelated jitter (AWS-style)
RetryPolicy.decorrelated_jitter(timedelta(seconds=1), max_retries=5)
```

### Delivery Strategies

```python
from waygrid import DeliveryStrategy
from datetime import datetime, timedelta, timezone

# Execute immediately (default)
DeliveryStrategy.IMMEDIATE

# Schedule after delay
DeliveryStrategy.schedule_after(timedelta(minutes=5))

# Schedule at specific time
DeliveryStrategy.schedule_at(datetime(2024, 12, 25, tzinfo=timezone.utc))
```

### Repeat Policies

```python
from waygrid import RepeatPolicy
from datetime import datetime, timedelta, timezone

# One-shot execution
RepeatPolicy.NO_REPEAT

# Repeat forever
RepeatPolicy.indefinitely(timedelta(hours=1))

# Repeat N times
RepeatPolicy.times(every=timedelta(hours=1), times=10)

# Repeat until timestamp
RepeatPolicy.until(
    every=timedelta(hours=1),
    until=datetime(2024, 12, 31, 23, 59, 59, tzinfo=timezone.utc)
)
```

### Conditions

For conditional routing based on node output:

```python
from waygrid import Condition

# Always matches
Condition.ALWAYS

# Check if JSON pointer exists
Condition.json_exists("/result/data")

# Check JSON value equality
Condition.json_equals("/status", "approved")
Condition.json_equals("/count", 0)
Condition.json_equals("/valid", True)

# Logical operators
Condition.not_(Condition.json_exists("/error"))

Condition.and_([
    Condition.json_exists("/result"),
    Condition.json_equals("/result/valid", True),
])

Condition.or_([
    Condition.json_equals("/status", "approved"),
    Condition.json_equals("/status", "auto_approved"),
])
```

## Advanced Patterns

### Error Handling Pipeline

```python
workflow = (
    Node.standard("waygrid://processor/risky-operation")
    .with_retry(RetryPolicy.exponential(timedelta(seconds=1), max_retries=3))
    .on_success(
        Node.standard("waygrid://destination/webhook")
            .label("Success Notification")
    )
    .on_failure(
        Node.standard("waygrid://processor/error-classifier")
            .label("Classify Error")
            .on_conditions([
                Condition.when(
                    Condition.json_equals("/errorType", "retryable"),
                    Node.standard("waygrid://system/scheduler")
                        .with_delivery(DeliveryStrategy.schedule_after(timedelta(hours=1)))
                        .label("Retry Later")
                ),
                Condition.when(
                    Condition.json_equals("/errorType", "fatal"),
                    Node.standard("waygrid://destination/webhook")
                        .label("Alert On-Call")
                ),
            ])
    )
)
```

### Parallel Processing with Quorum

```python
# Call 3 providers, continue when 2 respond
spec = Spec.single(
    entry_point=Node.fork("waygrid://system/waystation")
        .join_id("multi-provider")
        .branches({
            "provider-a": Node.standard("waygrid://destination/webhook")
                .label("Provider A"),
            "provider-b": Node.standard("waygrid://destination/webhook")
                .label("Provider B"),
            "provider-c": Node.standard("waygrid://destination/webhook")
                .label("Provider C"),
        }),
    repeat_policy=RepeatPolicy.NO_REPEAT
)

# The Join node with Quorum strategy
join = (
    Node.join("waygrid://system/waystation")
    .join_id("multi-provider")
    .strategy(JoinStrategy.quorum(2))  # 2 of 3 required
    .timeout(timedelta(seconds=30))
    .on_success(aggregate_results)
    .on_timeout(handle_partial_results)
)
```

## FastAPI Integration

```python
from fastapi import FastAPI, Depends
from waygrid import WaygridClient, Spec, Node, RepeatPolicy
import os

app = FastAPI()

def get_waygrid_client() -> WaygridClient:
    return WaygridClient(
        base_url=os.environ["WAYGRID_BASE_URL"],
        api_key=os.environ["WAYGRID_API_KEY"]
    )

@app.post("/workflow/start")
async def start_workflow(
    data: dict,
    client: WaygridClient = Depends(get_waygrid_client)
):
    spec = Spec.single(
        entry_point=Node.standard("waygrid://processor/openai")
            .on_success(Node.standard("waygrid://destination/webhook")),
        repeat_policy=RepeatPolicy.NO_REPEAT
    )

    async with client:
        result = await client.submit(spec, payload=data)

    return {"traversal_id": result.traversal_id, "status": "submitted"}
```

## Django Integration

```python
# settings.py
WAYGRID_BASE_URL = os.environ.get("WAYGRID_BASE_URL", "https://api.waygrid.io")
WAYGRID_API_KEY = os.environ.get("WAYGRID_API_KEY")

# services/waygrid_service.py
from django.conf import settings
from waygrid import WaygridClient, Spec, Node, RepeatPolicy

class WaygridService:
    def __init__(self):
        self.client = WaygridClient(
            base_url=settings.WAYGRID_BASE_URL,
            api_key=settings.WAYGRID_API_KEY
        )

    async def submit_workflow(self, payload: dict) -> str:
        spec = Spec.single(
            entry_point=Node.standard("waygrid://processor/openai")
                .on_success(Node.standard("waygrid://destination/webhook")),
            repeat_policy=RepeatPolicy.NO_REPEAT
        )

        async with self.client:
            result = await self.client.submit(spec, payload=payload)

        return result.traversal_id

# views.py
from django.http import JsonResponse
from django.views import View
from .services.waygrid_service import WaygridService
import asyncio

class WorkflowView(View):
    def post(self, request):
        service = WaygridService()
        traversal_id = asyncio.run(service.submit_workflow(request.POST.dict()))
        return JsonResponse({"traversal_id": traversal_id})
```

## Type Hints

The SDK is fully typed with Python type hints:

```python
from waygrid import Spec, Node, RetryPolicy, RepeatPolicy
from waygrid.types import INode, ConditionalEdge

def build_workflow(
    processor_address: str,
    destination_address: str,
    max_retries: int = 3
) -> Spec:
    entry: INode = (
        Node.standard(processor_address)
        .with_retry(RetryPolicy.exponential(timedelta(seconds=1), max_retries=max_retries))
        .on_success(Node.standard(destination_address))
    )
    return Spec.single(entry_point=entry, repeat_policy=RepeatPolicy.NO_REPEAT)
```

## Dataclasses

The SDK uses Python dataclasses for immutable models:

```python
from dataclasses import dataclass
from typing import List, Optional
from datetime import timedelta

@dataclass(frozen=True)
class Spec:
    entry_points: List[INode]
    repeat_policy: RepeatPolicy

@dataclass(frozen=True)
class LinearRetryPolicy:
    base: timedelta
    max_retries: int

@dataclass(frozen=True)
class ExponentialRetryPolicy:
    base: timedelta
    max_retries: int
```

## Repository Structure

The SDK repository (`waygrid-sdk-python`) is organized as:

```
waygrid-sdk-python/
├── pyproject.toml
├── src/
│   └── waygrid/
│       ├── __init__.py
│       ├── spec.py
│       ├── nodes/
│       │   ├── __init__.py
│       │   ├── base.py
│       │   ├── standard.py
│       │   ├── fork.py
│       │   └── join.py
│       ├── policies/
│       │   ├── __init__.py
│       │   ├── retry.py
│       │   ├── delivery.py
│       │   └── repeat.py
│       ├── conditions/
│       │   ├── __init__.py
│       │   └── condition.py
│       ├── strategies/
│       │   ├── __init__.py
│       │   └── join.py
│       ├── client/
│       │   ├── __init__.py
│       │   ├── client.py
│       │   └── result.py
│       └── types/
│           └── __init__.py
└── tests/
    ├── test_spec.py
    ├── test_nodes.py
    └── test_serialization.py
```

## See Also

- [SDK Overview](./overview) - Compare SDKs
- [Spec JSON Schema](/docs/api/spec-schema) - Raw JSON format
