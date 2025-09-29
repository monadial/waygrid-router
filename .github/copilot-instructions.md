# Global instructions for the entire repository
You are contributing to Waygrid — a distributed, event-driven message router written in Scala 3
using the final tagless pattern with Cats Effect, FS2, and http4s.
The project uses Kafka, FSM-based actors (inspired by suprnation/cats-actors), and a strict domain-driven design (DDD) architecture.

Core principles:
- All side effects are wrapped in `F[_]` (final tagless).
- Prefer immutable state, pure functions, and type-safe models.
- All domain models are defined with opaque types or tagged value classes.
- Events are first-class values: typed, versioned, and serialized with Circe.
- FSMs define actor lifecycle stages (Startup, Running, Stopping, Stopped).
- Use modular architecture per bounded context: `origin-*`, `destination-*`, `common-*`.

Guidelines for suggestions:
- Prefer returning `Resource[F, _]`, `Stream[F, _]` when dealing with lifecycle management.
- Use `given` for typeclass instances and favor extension methods via `syntax`.
- Use `Deferred`, `Ref`, or `SignallingRef` for state and coordination.
- Use Monocle optics for working with nested domain models.
- Logging is abstracted via a custom `Logger[F]`, implemented using Odin.
- Kafka is accessed via FS2 Kafka (`ConsumerResource`, `ProducerRecords`, etc.).

File/Folder hints:
- `common-application`: shared logic (actors, event codecs, FSM modeling, logging, etc.)
- `common-domain`: domain model with opaque types and value classes.
- `origin-*` and `destination-*`: integration boundaries (HTTP, Webhook, Kafka, WebSocket).
- `event-router`: message routing logic across topology nodes.
- Prefer `Algebra`, `Model`, `Interpreter` naming conventions in each module.

Naming conventions:
- Event types should follow `<DomainAction>Was<EventType>`, e.g., `NodeJoinRequested`, `MessageRoutingWasRequested`.
- Actor names end with `Actor`, e.g., `SupervisorActor`, `TopologyClientActor`.
- FSM states should be sealed traits/enums, e.g., `sealed trait State`.

Example patterns:
- Use `FSM[F, State, Input, Output]` from `common-application.model.fsm` to model actor transitions.
- Prefer composition using `flatMap`, `parMapN`, `Stream.eval`, etc.
- Avoid `unsafeRunSync` or blocking calls.

Copilot should suggest idiomatic, effect-safe Scala 3 code using Cats Effect, FS2, and final tagless style.
Avoid Java-style code or mutable collections.
