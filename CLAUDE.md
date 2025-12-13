IMPORTANT: When applicable, prefer using jetbrains-index MCP tools for code navigation and refactoring.

# Claude Code Guidelines for Waygrid Router

This document provides specific guidance for Claude Code when working with the Waygrid Router codebase.

## Project Overview

Waygrid is an event-driven graph routing system built with Scala 3, focusing on modular architecture with support for multiple event origins, processors, and destinations.

### Key Technologies
- **Language**: Scala 3.7.0
- **Build Tool**: sbt
- **Testing**: Weaver + ScalaCheck
- **Infrastructure**: Kafka (event streaming), Redis (caching), PostgreSQL (persistence)
- **Observability**: OpenTelemetry (OTLP) + Grafana
- **Effect System**: Cats Effect
- **HTTP**: http4s
- **JSON**: Circe

## Module Architecture

### Component Structure
```
modules/
├── common/
│   ├── common-domain/          # Core domain models, types, algebras
│   └── common-application/     # Application logic, interpreters, Kafka integration
├── origin/                     # Event ingestion
│   ├── origin-http/           # REST API ingress
│   ├── origin-grpc/           # gRPC ingress
│   └── origin-kafka/          # Kafka consumer ingress
├── destination/               # Event egress
│   ├── destination-webhook/   # HTTP webhook delivery
│   ├── destination-websocket/ # WebSocket streaming
│   └── destination-blackhole/ # No-op destination for testing
├── processor/                 # Event transformation
│   └── processor-openai/      # OpenAI integration
└── system/                    # System services
    ├── system-topology/       # Network topology management
    ├── system-waystation/     # Routing waypoints
    ├── system-scheduler/      # Job scheduling
    ├── system-iam/           # Identity & access management
    ├── system-history/       # Event history
    ├── system-dag-registry/  # DAG storage
    ├── system-secure-store/  # Secrets management
    ├── system-billing/       # Usage tracking & billing
    ├── system-kms/          # Key management
    └── system-k8s-operator/ # Kubernetes operator
```

### Key Abstractions

#### Event Flow
1. **EventSource** - Receives events from origins (HTTP, Kafka, gRPC)
2. **EventRouter** - Routes events through DAG using compile-time macros
3. **EventSink** - Delivers events to destinations

## Domain Models

Waygrid uses a rich domain model with type-safe value objects, located in `modules/common/common-domain` and `modules/common/common-application`.

### Core Domain Models (`common-domain`)

#### Node & Service (`model/node`)
Represents service nodes in the Waygrid network:

- **NodeId** - ULID-based unique node identifier
- **NodeComponent** - Component type: Origin, Processor, Destination, System
- **NodeService** - Service name within component
- **NodeDescriptor** - Component + Service identifier (e.g., `origin.http`)
- **NodeAddress** - Full node URI: `waygrid://<component>/<service>?region=<region>&clusterId=<id>&nodeId=<id>`
- **ServiceAddress** - Service-level URI: `waygrid://<component>/<service>`
- **NodeClusterId** - Cluster identifier for node groups
- **NodeRegion** - Geographic region (e.g., `us-west-1`)

**Example NodeDescriptor Factory Methods**:
```scala
NodeDescriptor.Origin(NodeService("http"))      // origin.http
NodeDescriptor.System(NodeService("topology"))  // system.topology
NodeDescriptor.Destination(NodeService("webhook")) // destination.webhook
```

#### Routing (`model/routing`)
Defines event routing behavior:

- **RouteId** - ULID for route identification
- **TraversalId** - ULID tracking event traversal through DAG
- **RouteSalt** - Salt for deterministic routing
- **RetryPolicy** - Retry strategies:
  - `NoRetry`
  - `Linear(delay, maxRetries)`
  - `Exponential(baseDelay, maxRetries)`
- **RepeatPolicy** - Repeat strategies:
  - `NoRepeat`
  - `Indefinitely(every)`
  - `Times(every, times)`
  - `Until(every, until)`
- **DeliveryStrategy**:
  - `Immediate`
  - `ScheduleAfter(delay)`

#### Routing DAG (`model/routing/dag`)
Directed Acyclic Graph for event routing:

- **Dag** - Complete DAG definition
  - `hash: DagHash` - Content-based DAG hash
  - `entry: NodeId` - Entry point node
  - `repeatPolicy: RepeatPolicy` - DAG-level repeat policy
  - `nodes: Map[NodeId, Node]` - All nodes in the DAG
  - `edges: List[Edge]` - All edges connecting nodes
- **Node** - DAG node representing a service
- **Edge** - Connection between nodes
- **EdgeGuard** - Conditional routing:
  - `OnSuccess` - Execute on successful processing
  - `OnFailure` - Execute on processing failure
- **DagHash** - Content-based hash of DAG structure

#### Route Graph (`model/routing`)
Higher-level routing constructs:

- **RouteGraph** - Graph representation of routes
- **RouteNode** - Node in route graph
- **Route** - Complete route definition

#### Events (`model/event`)
Base event model:

- **EventId** - ULID-based event identifier
- **Event** - Base trait for all domain events

#### Messages (`model/message`)
Base message model:

- **Message** - Base trait for all messages in the system

#### Content (`model/content`)
Content handling:

- **Content** - Content wrapper
- **ContentType** - MIME type specification
- **ContentData** - Binary content data

#### Topology (`model/topology`)
Network topology management:

- **Topology** - Network topology representation
- **ContractId** - ULID for service contracts
- **ClusterId** - ULID for cluster identification

#### Commands (`model/command`)
Command patterns:

- **Command** - Base trait for commands

#### Cryptography (`model/cryptography`)
Cryptographic primitives:

- **Hash functions** - Content hashing utilities

#### WRN - Waygrid Resource Name (`model/wrn`)
Resource identification scheme (similar to AWS ARN):

- **Wrn** - Universal resource naming (placeholder for future implementation)

### Application Domain Models (`common-application`)

#### Envelope (`domain/model/envelope`)
Internal event wrapper with metadata:

- **Envelope[M <: Message]** - Type-safe message envelope
  - `id: EnvelopeId` - Unique envelope identifier
  - `address: ServiceAddress` - Destination service
  - `sender: NodeAddress` - Source node address
  - `message: M` - Wrapped message (type-safe)

**Factory Methods**:
```scala
Envelope.toService[F, M](address, message)      // Send to service
Envelope.toWaystation[F, M](message)            // Send to waystation
```

#### TransportEnvelope (`domain/model/transport`)
Wire format for Kafka/network transmission:

- **TransportEnvelope** - Serialization-friendly envelope format
- Used by **KafkaTransportEnvelope** for Kafka message transport

#### Event Streaming (`domain/model/event`)
Application-level events:

- **Event** - Application event model (extends domain Event)

#### Settings (`domain/model/settings`)
Service configuration:

- **NodeSettings** - Node configuration
- **PostgresSettings** - Database configuration
- **HttpServerSettings** - HTTP server configuration
- **RedisSettings** - Redis cluster configuration
- **EventStreamSettings** - Kafka stream configuration

#### FSM (`domain/model/fsm`)
Finite State Machine abstractions:

- **FSM** - State machine implementation

#### Platform (`domain/model`)
Platform-specific abstractions:

- **Platform** - Platform identification and utilities

### Value Objects Foundation

All domain models use type-safe value objects from `common.domain.value`:

#### Primitive Value Wrappers
- **StringValue** - Type-safe string wrapper
- **StringValueRefined** - Refined string with validation
- **IntegerValue** - Type-safe integer wrapper
- **LongValue** - Type-safe long wrapper
- **BytesValue** - Type-safe byte array wrapper
- **InstantValue** - Type-safe timestamp wrapper
- **ULIDValue** - ULID (Universally Unique Lexicographically Sortable ID)
- **URIValue** - Type-safe URI wrapper

#### Features
- **Type Safety** - Each value type prevents mixing incompatible values
- **Validation** - Refined types with compile-time validation
- **Codec Generation** - Automatic JSON/binary codec derivation
- **Show Instances** - Pretty printing for all value types

## Development Workflow

### Local Development Setup
```bash
# Start infrastructure (Kafka, Redis, Grafana)
docker compose up -d

# Compile project
sbt compile

# Run specific service
sbt origin-http/run
sbt system-topology/run

# Run tests
sbt test
sbt 'testOnly *RouteGraphSpec'

# Format code
sbt scalafmtAll scalafixAll
```

### Docker Images
```bash
# Build Docker image for a module
sbt system-topology/Docker/publishLocal

# Images follow pattern: monadial/waygrid-<component>-<service>
# Example: monadial/waygrid-system-topology
```

### Infrastructure Access
- **Kafka Brokers**: localhost:29092, localhost:29094, localhost:29096
- **Redpanda Console**: http://redpanda.router.waygrid.local
- **Redis Cluster**: localhost:6379, localhost:6380, localhost:6381
- **RedisInsight**: http://redisinsight.router.waygrid.local
- **Grafana/OTLP**: http://grafana.router.waygrid.local (OTLP: localhost:4317)

## Code Patterns & Best Practices

### Functional Programming Patterns
- Use `IO` from Cats Effect for side effects
- Leverage `F[_]: Async` for polymorphic effect types
- Prefer tagless final encoding for algebras
- Use `Resource` for resource management

### Event Routing
- Routes are defined using compile-time macros in `EventRouterMacro`
- DAG compilation happens via `DagCompiler`
- Metadata propagation is handled by `MetadataPropagator`

### Testing
- Property-based testing with ScalaCheck
- Weaver test framework for async testing
- Test specs in `src/test/scala` with `*Spec.scala` naming
- Use Weaver's `Expectations` for assertions

### Error Handling
- Use `Either` or effect types for recoverable errors
- Provide detailed error messages
- Log errors with context using Odin logger

## Working with Macros

### EventRouter Macro
Located in `common-application/src/main/scala/.../macro/EventRouterMacro.scala`

- Generates compile-time routing logic based on event types
- Uses Scala 3 metaprogramming (quotes/splices)
- Automatically discovers event handlers

### CirceEventCodecRegistry Macro
- Auto-generates JSON codecs for events
- Scans event types at compile time
- Registers codecs in a type-safe registry

## Configuration

### Application Config
- Base config: `src/main/resources/application.conf`
- Environment overrides via JVM system properties
- Never commit secrets - use environment variables

### OpenTelemetry
JVM options in `build.sbt`:
```scala
"-Dotel.java.global-autoconfigure.enabled=true"
"-Dotel.service.name=${name.value}"
"-Dotel.exporter.otlp.endpoint=http://localhost:4317"
```

## Common Tasks for Claude Code

### Adding a New Event Type
1. Define event case class in appropriate domain module
2. Ensure it extends the base event trait
3. Codec will be auto-generated via macro
4. Add routing logic if needed

### Creating a New Service Module
1. Create module directory: `modules/<component>/<module>`
2. Add to `build.sbt` with dependencies
3. Enable plugins: `BuildInfoPlugin`, `DockerPlugin`, `JavaAppPackaging`
4. Add `Main.scala` extending `WaygridApp` or `SystemWaygridApp`
5. Create `application.conf` for configuration

### Adding Dependencies
Update `project/Dependencies.scala`:
```scala
object Libraries {
  lazy val newLib = "org.example" %% "library" % "version"
}
```

Then reference in `build.sbt`:
```scala
libraryDependencies ++= List(
  Libraries.newLib.value
)
```

### Debugging Event Flow
1. Check Redpanda Console for Kafka messages
2. Use Grafana for traces and metrics
3. Enable detailed logging in `application.conf`
4. Check envelope metadata for routing information

## Git Workflow

### Commit Messages

This project follows [Conventional Commits](https://www.conventionalcommits.org/) specification.

**Format**: `<type>(<scope>): <description>`

Where:
- **type**: The kind of change (feat, fix, docs, style, refactor, perf, test, build, ci, chore)
- **scope**: The module or component affected (optional but recommended)
- **description**: Brief description in imperative mood

**Common Types**:
- `feat`: A new feature
- `fix`: A bug fix
- `docs`: Documentation only changes
- `style`: Code style changes (formatting, missing semi-colons, etc)
- `refactor`: Code change that neither fixes a bug nor adds a feature
- `perf`: Performance improvements
- `test`: Adding or updating tests
- `build`: Changes to build system or dependencies
- `ci`: CI/CD configuration changes
- `chore`: Other changes that don't modify src or test files

**Scope Examples**:
- `origin-http`, `origin-kafka`, `origin-grpc`
- `destination-webhook`, `destination-websocket`
- `system-topology`, `system-waystation`, `system-iam`
- `common-domain`, `common-application`
- `processor-openai`

**Examples**:
- `feat(origin-http): add webhook signature verification`
- `fix(system-topology): resolve DAG compilation race condition`
- `refactor(common): implement metadata propagation for transport envelopes`
- `test(common-domain): add property tests for routing DAG`
- `docs: update CLAUDE.md with conventional commits`
- `perf(destination-webhook): optimize batch delivery algorithm`
- `build(deps): upgrade cats-effect to 3.6.0`

**Breaking Changes**:
Add `!` after type/scope for breaking changes, and include `BREAKING CHANGE:` in the commit body:
```
feat(common-domain)!: redesign event envelope structure

BREAKING CHANGE: Envelope now requires explicit metadata field
```

### Pull Requests
- Ensure `sbt scalafmtAll scalafixAll test` passes
- Update documentation if adding features
- Include affected modules in PR description
- Link related issues

## Troubleshooting

### Common Issues

**Compilation Errors with Macros**
- Macros run at compile time; check for type mismatches
- Use `sbt clean compile` to rebuild macro-generated code

**Kafka Connection Issues**
- Verify `docker compose up -d` is running
- Check broker endpoints in application.conf
- Ensure no port conflicts (29092, 29094, 29096)

**Test Failures**
- Run `sbt clean test` to ensure clean state
- Check for resource leaks (unclosed streams, connections)
- Verify test isolation (no shared mutable state)

**Docker Build Issues**
- Ensure JDK 23 compatibility (base image: eclipse-temurin:23)
- Check exposed ports (default: 1337)

## Important Files

### Configuration Files
- `build.sbt` - Main build configuration
- `project/Dependencies.scala` - Dependency management
- `.scalafmt.conf` - Code formatting rules
- `.scalafix.conf` - Linting/refactoring rules
- `compose.yaml` - Local infrastructure setup

### Documentation
- `AGENTS.md` - Repository guidelines
- `README.md` - Project overview
- `docs/` - Docusaurus documentation site

## Quick Reference

### sbt Commands
```bash
sbt compile                    # Compile all modules
sbt test                      # Run all tests
sbt <module>/run              # Run specific module
sbt <module>/Docker/publishLocal  # Build Docker image
sbt scalafmtAll               # Format all code
sbt scalafixAll               # Apply scalafix rules
sbt 'testOnly *SpecName'      # Run specific test
sbt clean                     # Clean build artifacts
```

### Module Naming Convention
- Main source: `modules/<component>/<module>/src/main/scala/com/monadial/waygrid/<component>/<module>/`
- Test source: `modules/<component>/<module>/src/test/scala/com/monadial/waygrid/<component>/<module>/`
- Resources: `modules/<component>/<module>/src/main/resources/`

### Package Structure
```
com.monadial.waygrid
├── <component>.<module>       # Module root
├── <component>.<module>.domain.model  # Domain models
├── <component>.<module>.algebra       # Algebras (interfaces)
├── <component>.<module>.interpreter   # Implementations
├── <component>.<module>.http         # HTTP routes
└── <component>.<module>.config       # Configuration
```

## Additional Resources

- Scala 3 Book: https://docs.scala-lang.org/scala3/book/introduction.html
- Cats Effect: https://typelevel.org/cats-effect/
- http4s: https://http4s.org/
- Weaver Test: https://disneystreaming.github.io/weaver-test/
- fs2-kafka: https://fd4s.github.io/fs2-kafka/

---

**Note**: This project uses functional programming patterns extensively. When in doubt, prefer immutability, pure functions, and composable abstractions.
