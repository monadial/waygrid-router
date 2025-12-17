---
sidebar_position: 2
---

# Spec JSON Schema

The Spec schema is the **input format** for defining traversals. Unlike the [DAG schema](./json-schema.md) (which is the compiled internal representation), the Spec schema is what integrators write to define their workflow routing.

## Overview

A **Spec** defines a traversal workflow using a graph of nodes. The Waygrid API accepts Spec JSON and compiles it into an optimized DAG with stable node IDs for execution.

```
Spec (your input) → DagCompiler → DAG (internal)
```

## Complete Spec Schema

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "$id": "https://waygrid.com/schemas/spec/v1/spec.json",
  "title": "Waygrid Traversal Spec",
  "description": "High-level specification for defining a DAG traversal workflow",
  "type": "object",
  "required": ["entryPoints", "repeatPolicy"],
  "properties": {
    "entryPoints": {
      "type": "array",
      "minItems": 1,
      "items": { "$ref": "#/definitions/Node" },
      "description": "One or more entry point nodes where traversal begins"
    },
    "repeatPolicy": {
      "$ref": "#/definitions/RepeatPolicy",
      "description": "Policy for repeating the entire traversal"
    }
  },
  "definitions": {
    "Node": {
      "oneOf": [
        { "$ref": "#/definitions/StandardNode" },
        { "$ref": "#/definitions/ForkNode" },
        { "$ref": "#/definitions/JoinNode" }
      ]
    },

    "StandardNode": {
      "type": "object",
      "required": ["type", "address"],
      "additionalProperties": false,
      "properties": {
        "type": { "const": "standard" },
        "address": { "$ref": "#/definitions/ServiceAddress" },
        "retryPolicy": { "$ref": "#/definitions/RetryPolicy" },
        "deliveryStrategy": { "$ref": "#/definitions/DeliveryStrategy" },
        "onSuccess": { "$ref": "#/definitions/Node" },
        "onFailure": { "$ref": "#/definitions/Node" },
        "onConditions": {
          "type": "array",
          "items": { "$ref": "#/definitions/ConditionalEdge" }
        },
        "label": { "type": "string", "maxLength": 64 }
      },
      "default": {
        "retryPolicy": { "type": "None" },
        "deliveryStrategy": { "type": "Immediate" }
      }
    },

    "ForkNode": {
      "type": "object",
      "required": ["type", "address", "branches", "joinNodeId"],
      "additionalProperties": false,
      "properties": {
        "type": { "const": "fork" },
        "address": { "$ref": "#/definitions/ServiceAddress" },
        "retryPolicy": { "$ref": "#/definitions/RetryPolicy" },
        "deliveryStrategy": { "$ref": "#/definitions/DeliveryStrategy" },
        "branches": {
          "type": "object",
          "minProperties": 2,
          "additionalProperties": { "$ref": "#/definitions/Node" },
          "description": "Named branches to execute in parallel (min 2)"
        },
        "joinNodeId": {
          "type": "string",
          "minLength": 1,
          "description": "ID linking this Fork to its corresponding Join"
        },
        "label": { "type": "string", "maxLength": 64 }
      }
    },

    "JoinNode": {
      "type": "object",
      "required": ["type", "address", "joinNodeId", "strategy"],
      "additionalProperties": false,
      "properties": {
        "type": { "const": "join" },
        "address": { "$ref": "#/definitions/ServiceAddress" },
        "retryPolicy": { "$ref": "#/definitions/RetryPolicy" },
        "deliveryStrategy": { "$ref": "#/definitions/DeliveryStrategy" },
        "joinNodeId": {
          "type": "string",
          "description": "Must match the joinNodeId of corresponding Fork"
        },
        "strategy": { "$ref": "#/definitions/JoinStrategy" },
        "timeout": { "$ref": "#/definitions/Duration" },
        "onSuccess": { "$ref": "#/definitions/Node" },
        "onFailure": { "$ref": "#/definitions/Node" },
        "onTimeout": { "$ref": "#/definitions/Node" },
        "label": { "type": "string", "maxLength": 64 }
      }
    },

    "ConditionalEdge": {
      "type": "object",
      "required": ["condition", "to"],
      "properties": {
        "condition": { "$ref": "#/definitions/Condition" },
        "to": { "$ref": "#/definitions/Node" }
      }
    },

    "Condition": {
      "oneOf": [
        {
          "type": "object",
          "required": ["type"],
          "properties": { "type": { "const": "Always" } }
        },
        {
          "type": "object",
          "required": ["type", "pointer"],
          "properties": {
            "type": { "const": "JsonExists" },
            "pointer": {
              "type": "string",
              "pattern": "^(/[^/]*)*$",
              "description": "RFC-6901 JSON Pointer (e.g., /result/status)"
            }
          }
        },
        {
          "type": "object",
          "required": ["type", "pointer", "value"],
          "properties": {
            "type": { "const": "JsonEquals" },
            "pointer": { "type": "string" },
            "value": { "description": "Value to compare against (any JSON type)" }
          }
        },
        {
          "type": "object",
          "required": ["type", "cond"],
          "properties": {
            "type": { "const": "Not" },
            "cond": { "$ref": "#/definitions/Condition" }
          }
        },
        {
          "type": "object",
          "required": ["type", "all"],
          "properties": {
            "type": { "const": "And" },
            "all": {
              "type": "array",
              "items": { "$ref": "#/definitions/Condition" },
              "minItems": 1
            }
          }
        },
        {
          "type": "object",
          "required": ["type", "any"],
          "properties": {
            "type": { "const": "Or" },
            "any": {
              "type": "array",
              "items": { "$ref": "#/definitions/Condition" },
              "minItems": 1
            }
          }
        }
      ]
    },

    "JoinStrategy": {
      "oneOf": [
        {
          "type": "object",
          "required": ["type"],
          "properties": { "type": { "const": "And" } },
          "description": "Wait for ALL branches to complete successfully"
        },
        {
          "type": "object",
          "required": ["type"],
          "properties": { "type": { "const": "Or" } },
          "description": "Continue when ANY branch completes (cancel others)"
        },
        {
          "type": "object",
          "required": ["type", "n"],
          "properties": {
            "type": { "const": "Quorum" },
            "n": { "type": "integer", "minimum": 1 }
          },
          "description": "Continue when N branches complete"
        }
      ]
    },

    "RetryPolicy": {
      "oneOf": [
        {
          "type": "object",
          "required": ["type"],
          "properties": { "type": { "const": "None" } }
        },
        {
          "type": "object",
          "required": ["type", "base", "maxRetries"],
          "properties": {
            "type": { "const": "Linear" },
            "base": { "$ref": "#/definitions/Duration" },
            "maxRetries": { "type": "integer", "minimum": 1, "maximum": 100 }
          }
        },
        {
          "type": "object",
          "required": ["type", "base", "maxRetries"],
          "properties": {
            "type": { "const": "Exponential" },
            "base": { "$ref": "#/definitions/Duration" },
            "maxRetries": { "type": "integer", "minimum": 1, "maximum": 100 }
          }
        },
        {
          "type": "object",
          "required": ["type", "base", "cap", "maxRetries"],
          "properties": {
            "type": { "const": "BoundedExponential" },
            "base": { "$ref": "#/definitions/Duration" },
            "cap": { "$ref": "#/definitions/Duration" },
            "maxRetries": { "type": "integer", "minimum": 1, "maximum": 100 }
          }
        },
        {
          "type": "object",
          "required": ["type", "base", "maxRetries"],
          "properties": {
            "type": { "const": "Fibonacci" },
            "base": { "$ref": "#/definitions/Duration" },
            "maxRetries": { "type": "integer", "minimum": 1, "maximum": 100 }
          }
        },
        {
          "type": "object",
          "required": ["type", "base", "maxRetries"],
          "properties": {
            "type": { "const": "FullJitter" },
            "base": { "$ref": "#/definitions/Duration" },
            "maxRetries": { "type": "integer", "minimum": 1, "maximum": 100 }
          }
        },
        {
          "type": "object",
          "required": ["type", "base", "maxRetries"],
          "properties": {
            "type": { "const": "DecorrelatedJitter" },
            "base": { "$ref": "#/definitions/Duration" },
            "maxRetries": { "type": "integer", "minimum": 1, "maximum": 100 }
          }
        }
      ]
    },

    "DeliveryStrategy": {
      "oneOf": [
        {
          "type": "object",
          "required": ["type"],
          "properties": { "type": { "const": "Immediate" } }
        },
        {
          "type": "object",
          "required": ["type", "delay"],
          "properties": {
            "type": { "const": "ScheduleAfter" },
            "delay": { "$ref": "#/definitions/Duration" }
          }
        },
        {
          "type": "object",
          "required": ["type", "time"],
          "properties": {
            "type": { "const": "ScheduleAt" },
            "time": { "type": "string", "format": "date-time" }
          }
        }
      ]
    },

    "RepeatPolicy": {
      "oneOf": [
        {
          "type": "object",
          "required": ["type"],
          "properties": { "type": { "const": "NoRepeat" } }
        },
        {
          "type": "object",
          "required": ["type", "every"],
          "properties": {
            "type": { "const": "Indefinitely" },
            "every": { "$ref": "#/definitions/Duration" }
          }
        },
        {
          "type": "object",
          "required": ["type", "every", "times"],
          "properties": {
            "type": { "const": "Times" },
            "every": { "$ref": "#/definitions/Duration" },
            "times": { "type": "integer", "minimum": 1 }
          }
        },
        {
          "type": "object",
          "required": ["type", "every", "until"],
          "properties": {
            "type": { "const": "Until" },
            "every": { "$ref": "#/definitions/Duration" },
            "until": { "type": "string", "format": "date-time" }
          }
        }
      ]
    },

    "ServiceAddress": {
      "type": "string",
      "pattern": "^waygrid://[a-z]+/[a-z0-9-]+$",
      "examples": [
        "waygrid://origin/http",
        "waygrid://processor/openai",
        "waygrid://destination/webhook"
      ],
      "description": "Service address in format: waygrid://<component>/<service>"
    },

    "Duration": {
      "type": "string",
      "pattern": "^\\d+(\\.\\d+)?(ms|s|m|h|d)$",
      "examples": ["500ms", "5s", "30m", "1h", "7d"],
      "description": "Duration with unit: ms (milliseconds), s (seconds), m (minutes), h (hours), d (days)"
    }
  }
}
```

## Quick Reference

### Node Types

| Type | Purpose | Required Fields |
|------|---------|-----------------|
| `standard` | Linear processing step | `address` |
| `fork` | Fan-out to parallel branches | `address`, `branches`, `joinNodeId` |
| `join` | Fan-in from parallel branches | `address`, `joinNodeId`, `strategy` |

### Service Address Format

```
waygrid://<component>/<service>
```

**Components:**
- `origin` - Event ingestion services
- `processor` - Event transformation services
- `destination` - Event delivery services
- `system` - Internal system services

**Examples:**
```json
"waygrid://origin/http"
"waygrid://processor/openai"
"waygrid://destination/webhook"
"waygrid://destination/websocket"
```

### Retry Policies

| Policy | Description | Example |
|--------|-------------|---------|
| `None` | No retries | `{"type": "None"}` |
| `Linear` | Fixed delay | `{"type": "Linear", "base": "5s", "maxRetries": 3}` |
| `Exponential` | Doubling delay | `{"type": "Exponential", "base": "1s", "maxRetries": 5}` |
| `BoundedExponential` | Exponential with cap | `{"type": "BoundedExponential", "base": "1s", "cap": "1m", "maxRetries": 10}` |
| `Fibonacci` | Fibonacci sequence | `{"type": "Fibonacci", "base": "1s", "maxRetries": 5}` |
| `FullJitter` | Randomized | `{"type": "FullJitter", "base": "1s", "maxRetries": 5}` |

### Join Strategies

| Strategy | Description |
|----------|-------------|
| `And` | Wait for **ALL** branches to succeed |
| `Or` | Continue on **FIRST** success (cancel others) |
| `Quorum(n)` | Continue when **N** branches succeed |

### Conditions (for conditional routing)

```json
// Always take this edge
{"type": "Always"}

// Check if field exists
{"type": "JsonExists", "pointer": "/result/data"}

// Check field value
{"type": "JsonEquals", "pointer": "/result/status", "value": "approved"}

// Logical NOT
{"type": "Not", "cond": {"type": "JsonExists", "pointer": "/error"}}

// Logical AND
{"type": "And", "all": [
  {"type": "JsonExists", "pointer": "/result"},
  {"type": "JsonEquals", "pointer": "/result/valid", "value": true}
]}

// Logical OR
{"type": "Or", "any": [
  {"type": "JsonEquals", "pointer": "/status", "value": "approved"},
  {"type": "JsonEquals", "pointer": "/status", "value": "auto_approved"}
]}
```

## Examples

### Simple Linear Workflow

```json
{
  "entryPoints": [{
    "type": "standard",
    "address": "waygrid://processor/openai",
    "label": "AI Processing",
    "onSuccess": {
      "type": "standard",
      "address": "waygrid://destination/webhook",
      "label": "Deliver Result"
    }
  }],
  "repeatPolicy": {"type": "NoRepeat"}
}
```

### Workflow with Error Handling

```json
{
  "entryPoints": [{
    "type": "standard",
    "address": "waygrid://processor/openai",
    "retryPolicy": {
      "type": "Exponential",
      "base": "1s",
      "maxRetries": 3
    },
    "onSuccess": {
      "type": "standard",
      "address": "waygrid://destination/webhook"
    },
    "onFailure": {
      "type": "standard",
      "address": "waygrid://destination/webhook",
      "label": "Error Notification"
    }
  }],
  "repeatPolicy": {"type": "NoRepeat"}
}
```

### Parallel Fan-Out / Fan-In

```json
{
  "entryPoints": [{
    "type": "fork",
    "address": "waygrid://system/waystation",
    "joinNodeId": "parallel-processors",
    "branches": {
      "sentiment": {
        "type": "standard",
        "address": "waygrid://processor/sentiment"
      },
      "translation": {
        "type": "standard",
        "address": "waygrid://processor/translate"
      },
      "summary": {
        "type": "standard",
        "address": "waygrid://processor/openai"
      }
    }
  }],
  "repeatPolicy": {"type": "NoRepeat"}
}
```

*(Note: The corresponding Join node is auto-generated by the DAG compiler)*

### Conditional Routing

```json
{
  "entryPoints": [{
    "type": "standard",
    "address": "waygrid://processor/classifier",
    "onConditions": [
      {
        "condition": {
          "type": "JsonEquals",
          "pointer": "/result/category",
          "value": "urgent"
        },
        "to": {
          "type": "standard",
          "address": "waygrid://destination/webhook",
          "label": "Urgent Handler"
        }
      },
      {
        "condition": {
          "type": "JsonEquals",
          "pointer": "/result/category",
          "value": "normal"
        },
        "to": {
          "type": "standard",
          "address": "waygrid://destination/webhook",
          "label": "Normal Queue"
        }
      }
    ],
    "onFailure": {
      "type": "standard",
      "address": "waygrid://destination/blackhole",
      "label": "Discard Failures"
    }
  }],
  "repeatPolicy": {"type": "NoRepeat"}
}
```

### Scheduled Recurring Workflow

```json
{
  "entryPoints": [{
    "type": "standard",
    "address": "waygrid://processor/data-aggregator",
    "deliveryStrategy": {
      "type": "ScheduleAfter",
      "delay": "5m"
    },
    "onSuccess": {
      "type": "standard",
      "address": "waygrid://destination/webhook"
    }
  }],
  "repeatPolicy": {
    "type": "Indefinitely",
    "every": "1h"
  }
}
```

## Validation

Use the JSON Schema above with any JSON Schema validator:

**JavaScript (ajv):**
```javascript
import Ajv from 'ajv';
const ajv = new Ajv();
const validate = ajv.compile(specSchema);
const valid = validate(yourSpec);
```

**PHP (opis/json-schema):**
```php
$validator = new \Opis\JsonSchema\Validator();
$result = $validator->validate($spec, $schema);
```

**Python (jsonschema):**
```python
from jsonschema import validate
validate(instance=spec, schema=spec_schema)
```

## See Also

- [DAG Schema Reference](./json-schema.md) - Internal compiled DAG format
- [Scala SDK Guide](/docs/sdk/scala) - Type-safe Scala SDK
- [PHP SDK Guide](/docs/sdk/php) - PHP SDK for integrators
