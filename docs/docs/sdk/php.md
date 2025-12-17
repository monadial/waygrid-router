---
sidebar_position: 3
---

# PHP SDK

The official PHP SDK for Waygrid provides a fluent, type-safe API for building traversal specifications. It requires PHP 8.1+ and leverages modern PHP features like enums, readonly properties, and named arguments.

## Installation

Install via Composer:

```bash
composer require waygrid/sdk
```

**Requirements:**
- PHP 8.1 or higher
- `ext-json` extension
- PSR-18 HTTP client (optional, for API calls)

## Quick Start

```php
<?php

use Waygrid\Sdk\Spec;
use Waygrid\Sdk\Node;
use Waygrid\Sdk\RetryPolicy;
use Waygrid\Sdk\RepeatPolicy;

// Define a simple workflow
$spec = Spec::single(
    entryPoint: Node::standard('waygrid://processor/openai')
        ->label('AI Processing')
        ->withRetry(RetryPolicy::exponential('1s', maxRetries: 3))
        ->onSuccess(
            Node::standard('waygrid://destination/webhook')
                ->label('Deliver Result')
        )
);

// Convert to JSON
$json = $spec->toJson();

// Or use the client
use Waygrid\Sdk\Client\WaygridClient;

$client = new WaygridClient(
    baseUrl: 'https://api.waygrid.io',
    apiKey: getenv('WAYGRID_API_KEY')
);

$result = $client->submit($spec);
echo "Traversal ID: {$result->traversalId}";
```

## Core API

### Spec

The root container for a traversal specification:

```php
use Waygrid\Sdk\Spec;
use Waygrid\Sdk\RepeatPolicy;

// Single entry point (most common)
$spec = Spec::single(
    entryPoint: $myNode,
    repeatPolicy: RepeatPolicy::NoRepeat
);

// Multiple entry points (fan-in from multiple origins)
$spec = Spec::multiple(
    entryPoints: [$node1, $node2, $node3],
    repeatPolicy: RepeatPolicy::NoRepeat
);
```

### Node Types

#### Standard Node

Linear processing step with optional continuations:

```php
use Waygrid\Sdk\Node;
use Waygrid\Sdk\RetryPolicy;
use Waygrid\Sdk\DeliveryStrategy;
use Waygrid\Sdk\Condition;

// Minimal
$node = Node::standard('waygrid://processor/openai');

// Full configuration
$node = Node::standard('waygrid://processor/openai')
    ->label('AI Content Analysis')
    ->withRetry(RetryPolicy::exponential('1s', maxRetries: 5))
    ->withDelivery(DeliveryStrategy::scheduleAfter('5m'))
    ->onSuccess($successHandler)
    ->onFailure($failureHandler)
    ->onConditions([
        Condition::when(
            Condition::jsonEquals('/sentiment', 'negative'),
            $escalationNode
        ),
        Condition::when(
            Condition::jsonEquals('/sentiment', 'positive'),
            $archiveNode
        ),
    ]);
```

#### Fork Node

Fan-out to parallel branches:

```php
$fork = Node::fork('waygrid://system/waystation')
    ->joinId('enrich-parallel')
    ->label('Parallel Enrichment')
    ->branches([
        'sentiment' => Node::standard('waygrid://processor/sentiment'),
        'translate' => Node::standard('waygrid://processor/translate'),
        'summarize' => Node::standard('waygrid://processor/openai'),
    ]);
```

#### Join Node

Fan-in from parallel branches:

```php
use Waygrid\Sdk\JoinStrategy;

$join = Node::join('waygrid://system/waystation')
    ->joinId('enrich-parallel')  // Must match Fork's joinId
    ->strategy(JoinStrategy::And)  // Wait for all
    ->timeout('30s')
    ->onSuccess($aggregateNode)
    ->onFailure($errorHandler)
    ->onTimeout($timeoutHandler);
```

### Retry Policies

```php
use Waygrid\Sdk\RetryPolicy;

// No retries
RetryPolicy::None;

// Linear backoff: 5s, 5s, 5s...
RetryPolicy::linear('5s', maxRetries: 3);

// Exponential backoff: 1s, 2s, 4s, 8s...
RetryPolicy::exponential('1s', maxRetries: 5);

// Bounded exponential: exponential with cap
RetryPolicy::boundedExponential(
    base: '1s',
    cap: '1m',
    maxRetries: 10
);

// Fibonacci backoff: 1s, 1s, 2s, 3s, 5s...
RetryPolicy::fibonacci('1s', maxRetries: 5);

// Full jitter (randomized)
RetryPolicy::fullJitter('1s', maxRetries: 5);

// Decorrelated jitter (AWS-style)
RetryPolicy::decorrelatedJitter('1s', maxRetries: 5);
```

### Delivery Strategies

```php
use Waygrid\Sdk\DeliveryStrategy;

// Execute immediately (default)
DeliveryStrategy::Immediate;

// Schedule after delay
DeliveryStrategy::scheduleAfter('5m');

// Schedule at specific time
DeliveryStrategy::scheduleAt(new DateTimeImmutable('2024-12-25T00:00:00Z'));
```

### Repeat Policies

```php
use Waygrid\Sdk\RepeatPolicy;

// One-shot execution
RepeatPolicy::NoRepeat;

// Repeat forever
RepeatPolicy::indefinitely('1h');

// Repeat N times
RepeatPolicy::times(every: '1h', times: 10);

// Repeat until timestamp
RepeatPolicy::until(
    every: '1h',
    until: new DateTimeImmutable('2024-12-31T23:59:59Z')
);
```

### Conditions

For conditional routing based on node output:

```php
use Waygrid\Sdk\Condition;

// Always matches
Condition::Always;

// Check if JSON pointer exists
Condition::jsonExists('/result/data');

// Check JSON value equality
Condition::jsonEquals('/status', 'approved');
Condition::jsonEquals('/count', 0);
Condition::jsonEquals('/valid', true);

// Logical operators
Condition::not(Condition::jsonExists('/error'));

Condition::and([
    Condition::jsonExists('/result'),
    Condition::jsonEquals('/result/valid', true),
]);

Condition::or([
    Condition::jsonEquals('/status', 'approved'),
    Condition::jsonEquals('/status', 'auto_approved'),
]);

// Use in node routing
$node->onConditions([
    Condition::when(
        Condition::jsonEquals('/category', 'urgent'),
        $urgentHandler
    ),
    Condition::when(
        Condition::jsonEquals('/category', 'normal'),
        $normalHandler
    ),
]);
```

## Advanced Patterns

### Error Handling Pipeline

```php
$workflow = Node::standard('waygrid://processor/risky-operation')
    ->withRetry(RetryPolicy::exponential('1s', maxRetries: 3))
    ->onSuccess(
        Node::standard('waygrid://destination/webhook')
            ->label('Success Notification')
    )
    ->onFailure(
        Node::standard('waygrid://processor/error-classifier')
            ->label('Classify Error')
            ->onConditions([
                Condition::when(
                    Condition::jsonEquals('/errorType', 'retryable'),
                    Node::standard('waygrid://system/scheduler')
                        ->withDelivery(DeliveryStrategy::scheduleAfter('1h'))
                        ->label('Retry Later')
                ),
                Condition::when(
                    Condition::jsonEquals('/errorType', 'fatal'),
                    Node::standard('waygrid://destination/webhook')
                        ->label('Alert On-Call')
                ),
            ])
    );
```

### Parallel Processing with Quorum

```php
// Call 3 providers, continue when 2 respond
$spec = Spec::single(
    entryPoint: Node::fork('waygrid://system/waystation')
        ->joinId('multi-provider')
        ->branches([
            'provider-a' => Node::standard('waygrid://destination/webhook')
                ->label('Provider A'),
            'provider-b' => Node::standard('waygrid://destination/webhook')
                ->label('Provider B'),
            'provider-c' => Node::standard('waygrid://destination/webhook')
                ->label('Provider C'),
        ])
);

// The Join node with Quorum strategy
$join = Node::join('waygrid://system/waystation')
    ->joinId('multi-provider')
    ->strategy(JoinStrategy::Quorum(2))  // 2 of 3 required
    ->timeout('30s')
    ->onSuccess($aggregateResults)
    ->onTimeout($handlePartialResults);
```

### Scheduled Data Pipeline

```php
$spec = Spec::single(
    entryPoint: Node::standard('waygrid://processor/data-fetcher')
        ->label('Fetch External Data')
        ->withRetry(RetryPolicy::exponential('5s', maxRetries: 3))
        ->onSuccess(
            Node::fork('waygrid://system/waystation')
                ->joinId('process-data')
                ->branches([
                    'validate' => Node::standard('waygrid://processor/validator'),
                    'enrich' => Node::standard('waygrid://processor/enricher'),
                    'transform' => Node::standard('waygrid://processor/transformer'),
                ])
        )
        ->onFailure(
            Node::standard('waygrid://destination/webhook')
                ->label('Notify Data Team')
        ),
    repeatPolicy: RepeatPolicy::indefinitely('6h')  // Every 6 hours
);
```

## Laravel Integration

### Service Provider

```php
// config/waygrid.php
return [
    'base_url' => env('WAYGRID_BASE_URL', 'https://api.waygrid.io'),
    'api_key' => env('WAYGRID_API_KEY'),
];

// app/Providers/WaygridServiceProvider.php
namespace App\Providers;

use Illuminate\Support\ServiceProvider;
use Waygrid\Sdk\Client\WaygridClient;

class WaygridServiceProvider extends ServiceProvider
{
    public function register(): void
    {
        $this->app->singleton(WaygridClient::class, fn ($app) => new WaygridClient(
            baseUrl: config('waygrid.base_url'),
            apiKey: config('waygrid.api_key'),
        ));
    }
}
```

### Usage in Controllers

```php
namespace App\Http\Controllers;

use Waygrid\Sdk\Client\WaygridClient;
use Waygrid\Sdk\Spec;
use Waygrid\Sdk\Node;

class WorkflowController extends Controller
{
    public function __construct(
        private readonly WaygridClient $waygrid
    ) {}

    public function startProcessing(Request $request)
    {
        $spec = Spec::single(
            entryPoint: Node::standard('waygrid://processor/openai')
                ->onSuccess(Node::standard('waygrid://destination/webhook'))
        );

        $result = $this->waygrid->submit($spec, payload: $request->all());

        return response()->json([
            'traversal_id' => $result->traversalId,
            'status' => 'submitted',
        ]);
    }
}
```

## Symfony Integration

### Service Configuration

```yaml
# config/services.yaml
services:
    Waygrid\Sdk\Client\WaygridClient:
        arguments:
            $baseUrl: '%env(WAYGRID_BASE_URL)%'
            $apiKey: '%env(WAYGRID_API_KEY)%'
```

### Usage in Services

```php
namespace App\Service;

use Waygrid\Sdk\Client\WaygridClient;
use Waygrid\Sdk\Spec;
use Waygrid\Sdk\Node;

class WorkflowService
{
    public function __construct(
        private readonly WaygridClient $waygrid
    ) {}

    public function triggerContentWorkflow(array $content): string
    {
        $spec = Spec::single(
            entryPoint: Node::standard('waygrid://processor/openai')
                ->label('Analyze Content')
                ->onSuccess(Node::standard('waygrid://destination/webhook'))
        );

        $result = $this->waygrid->submit($spec, payload: $content);

        return $result->traversalId;
    }
}
```

## JSON Schema Validation

The SDK includes built-in validation against the JSON Schema:

```php
use Waygrid\Sdk\Validation\SpecValidator;
use Waygrid\Sdk\Validation\ValidationResult;

$validator = new SpecValidator();

// Validate before submitting
$result = $validator->validate($spec);

if (!$result->isValid()) {
    foreach ($result->getErrors() as $error) {
        echo "Error at {$error->path}: {$error->message}\n";
    }
}

// Or validate JSON directly
$jsonSpec = '{"entryPoints": [...], "repeatPolicy": {...}}';
$result = $validator->validateJson($jsonSpec);
```

## Type Definitions (PHP 8.1 Enums)

The SDK uses PHP 8.1 enums for type safety:

```php
namespace Waygrid\Sdk;

enum JoinStrategy: string
{
    case And = 'And';
    case Or = 'Or';

    public static function Quorum(int $n): QuorumStrategy
    {
        return new QuorumStrategy($n);
    }
}

enum NodeType: string
{
    case Standard = 'standard';
    case Fork = 'fork';
    case Join = 'join';
}
```

## Repository Structure

The SDK repository (`waygrid-sdk-php`) is organized as:

```
waygrid-sdk-php/
├── composer.json
├── src/
│   ├── Spec.php
│   ├── Node/
│   │   ├── Node.php
│   │   ├── StandardNode.php
│   │   ├── ForkNode.php
│   │   └── JoinNode.php
│   ├── Policy/
│   │   ├── RetryPolicy.php
│   │   ├── DeliveryStrategy.php
│   │   └── RepeatPolicy.php
│   ├── Condition/
│   │   └── Condition.php
│   ├── Strategy/
│   │   └── JoinStrategy.php
│   ├── Address/
│   │   └── ServiceAddress.php
│   ├── Client/
│   │   ├── WaygridClient.php
│   │   └── Response/
│   │       └── SubmitResult.php
│   └── Validation/
│       ├── SpecValidator.php
│       └── ValidationResult.php
├── resources/
│   └── schema/
│       └── spec.json           # JSON Schema
└── tests/
    ├── Unit/
    │   ├── SpecTest.php
    │   ├── NodeTest.php
    │   └── ConditionTest.php
    └── Integration/
        └── ClientTest.php
```

## Complete Example

Here's a complete example of a content moderation workflow:

```php
<?php

declare(strict_types=1);

use Waygrid\Sdk\Spec;
use Waygrid\Sdk\Node;
use Waygrid\Sdk\Condition;
use Waygrid\Sdk\RetryPolicy;
use Waygrid\Sdk\JoinStrategy;
use Waygrid\Sdk\RepeatPolicy;

// Content moderation workflow with AI analysis
$spec = Spec::single(
    entryPoint: Node::standard('waygrid://processor/content-classifier')
        ->label('Initial Classification')
        ->withRetry(RetryPolicy::exponential('1s', maxRetries: 3))
        ->onConditions([
            // Safe content - quick path
            Condition::when(
                Condition::jsonEquals('/classification', 'safe'),
                Node::standard('waygrid://destination/webhook')
                    ->label('Approve & Publish')
            ),
            // Needs review - parallel AI analysis
            Condition::when(
                Condition::jsonEquals('/classification', 'needs_review'),
                Node::fork('waygrid://system/waystation')
                    ->joinId('ai-review')
                    ->branches([
                        'toxicity' => Node::standard('waygrid://processor/toxicity-detector'),
                        'sentiment' => Node::standard('waygrid://processor/sentiment-analyzer'),
                        'pii' => Node::standard('waygrid://processor/pii-scanner'),
                    ])
            ),
            // Flagged - immediate action
            Condition::when(
                Condition::jsonEquals('/classification', 'flagged'),
                Node::standard('waygrid://destination/webhook')
                    ->label('Alert Moderation Team')
            ),
        ])
        ->onFailure(
            Node::standard('waygrid://destination/webhook')
                ->label('Classification Failed - Manual Review')
        ),
    repeatPolicy: RepeatPolicy::NoRepeat
);

// Print the JSON for inspection
echo $spec->toJson(pretty: true);
```

## See Also

- [SDK Overview](./overview) - Compare SDKs
- [Scala SDK](./scala) - Scala SDK guide
- [Spec JSON Schema](/docs/api/spec-schema) - Raw JSON format
