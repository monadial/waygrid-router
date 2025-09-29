# Repository Guidelines

## Project Structure & Module Organization
- Scala 3 multi‑module sbt project. Root build in `build.sbt`; shared deps in `project/Dependencies.scala`.
- Source: `modules/<component>/<module>/src/main/scala`
- Resources: `modules/<component>/<module>/src/main/resources`
- Tests: `modules/<component>/<module>/src/test/scala`
- Major components: `common`, `origin`, `destination`, `processor`, `system`. Example runnable modules: `origin-http`, `system-topology`, `destination-webhook`.

## Build, Test, and Development Commands
- `sbt compile`: Compile all modules.
- `sbt test`: Run unit tests (Weaver + ScalaCheck).
- `sbt scalafmtAll` / `sbt scalafixAll`: Format and organize imports.
- `sbt <module>/run`: Run a service, e.g. `sbt origin-http/run`.
- `sbt <module>/Docker/publishLocal`: Build a Docker image, e.g. `sbt system-topology/Docker/publishLocal`.
- `docker compose up -d`: Start local infra (Kafka, Redis, OTEL) from `compose.yaml`.

## Coding Style & Naming Conventions
- Scala 3 syntax. Use 2‑space indentation; line width ~120 (see `.scalafmt.conf`).
- Packages: lowercase. Types/objects: PascalCase. Methods/values: camelCase. Test suites end with `*Spec.scala`.
- Imports: organized via Scalafix (OrganizeImports). Prefer explicit, grouped imports; project imports (`com.monadial.*`) first.

## Testing Guidelines
- Frameworks: [Weaver](https://disneystreaming.github.io/weaver-test/) + ScalaCheck.
- Location/naming: under each module’s `src/test/scala`, name suites `*Spec.scala` (e.g., `RouteGraphSpec.scala`).
- Run all: `sbt test`. Run one: `sbt 'testOnly *RouteGraphSpec'`.

## Commit & Pull Request Guidelines
- Commits: concise, imperative subject; include scope when useful.
  - Example: `origin-http: add RoutingResource` or `common: add event codecs`.
- PRs: clear description, affected modules, linked issues, and verification steps. Ensure `sbt scalafmtAll`, `sbt scalafixAll`, and `sbt test` pass. Note config or breaking changes.

## Security & Configuration Tips
- Do not commit secrets. Keep defaults in `application.conf`; override via env vars/JVM opts.
- Local telemetry: OTEL endpoint `http://localhost:4317` (see JVM options in `build.sbt`).
- If changing ports or infra, update `compose.yaml` and module `application.conf` accordingly.

