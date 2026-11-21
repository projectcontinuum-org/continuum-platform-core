# DOX framework

- DOX is highly performant AGENTS.md hierarchy installed here
- Agent must follow DOX instructions across any edits

## Core Contract

- AGENTS.md files are binding work contracts for their subtrees
- Work products, source materials, instructions, records, assets, and durable docs must stay understandable from the nearest applicable AGENTS.md plus every parent AGENTS.md above it

## Read Before Editing

1. Read the root AGENTS.md
2. Identify every file or folder you expect to touch
3. Walk from the repository root to each target path
4. Read every AGENTS.md found along each route
5. If a parent AGENTS.md lists a child AGENTS.md whose scope contains the path, read that child and continue from there
6. Use the nearest AGENTS.md as the local contract and parent docs for repo-wide rules
7. If docs conflict, the closer doc controls local work details, but no child doc may weaken DOX

Do not rely on memory. Re-read the applicable DOX chain in the current session before editing.

## Update After Editing

Every meaningful change requires a DOX pass before the task is done.

Update the closest owning AGENTS.md when a change affects:

- purpose, scope, ownership, or responsibilities
- durable structure, contracts, workflows, or operating rules
- required inputs, outputs, permissions, constraints, side effects, or artifacts
- user preferences about behavior, communication, process, organization, or quality
- AGENTS.md creation, deletion, move, rename, or index contents

Update parent docs when parent-level structure, ownership, workflow, or child index changes. Update child docs when parent changes alter local rules. Remove stale or contradictory text immediately. Small edits that do not change behavior or contracts may leave docs unchanged, but the DOX pass still must happen.

## Hierarchy

- Root AGENTS.md is the DOX rail: project-wide instructions, global preferences, durable workflow rules, and the top-level Child DOX Index
- Child AGENTS.md files own domain-specific instructions and their own Child DOX Index
- Each parent explains what its direct children cover and what stays owned by the parent
- The closer a doc is to the work, the more specific and practical it must be

## Child Doc Shape

- Create a child AGENTS.md when a folder becomes a durable boundary with its own purpose, rules, responsibilities, workflow, materials, or quality standards
- Work Guidance must reflect the current standards of the project or user instructions; if there are no specific standards or instructions yet, leave it empty
- Verification must reflect an existing check; if no verification framework exists yet, leave it empty and update it when one exists

Default section order:
- Purpose
- Ownership
- Local Contracts
- Work Guidance
- Verification
- Child DOX Index

## Style

- Keep docs concise, current, and operational
- Document stable contracts, not diary entries
- Put broad rules in parent docs and concrete details in child docs
- Prefer direct bullets with explicit names
- Do not duplicate rules across many files unless each scope needs a local version
- Delete stale notes instead of explaining history
- Trim obvious statements, repeated rules, misplaced detail, and warnings for risks that no longer exist

## Closeout

1. Re-check changed paths against the DOX chain
2. Update nearest owning docs and any affected parents or children
3. Refresh every affected Child DOX Index
4. Remove stale or contradictory text
5. Run existing verification when relevant
6. Report any docs intentionally left unchanged and why

## User Preferences

When the user requests a durable behavior change, record it here or in the relevant child AGENTS.md

- Kotlin must read descriptively. Use named parameters at call sites (constructors, data classes, functions with 2+ arguments), in tests too, and prefer descriptive names and explanatory locals over dense one-liners
- Every API change ships with a consistent OpenAPI spec, in the same change: an agent never adds or modifies an HTTP endpoint, request/response model or status code without updating its annotations, the endpoint inventory and the committed spec as described in "API Documentation Contract" below

## API Documentation Contract

Every Spring Boot service with `@RestController` endpoints follows [docs/api/CONVENTIONS.md](docs/api/CONVENTIONS.md); a new or changed endpoint is not done until its OpenAPI docs are.

### Rule for AI agents: every API change updates the OpenAPI spec

Applies to any change that adds, removes or modifies an endpoint (path, method, parameter, header, request body, response body, status code, error body), a request/response model or one of its fields, a validation rule a client can hit, or a shared commons model that appears in a payload. Do all of the following in the same change, before reporting the task as done:

1. **Inventory first.** Add or update the endpoint's section in [docs/api/ENDPOINT_INVENTORY.md](docs/api/ENDPOINT_INVENTORY.md) from the code (callers, identity, params with constraints, every success and error status with its body, side effects, idempotency). Unknown intent is a `TODO:` there, never a guess in the spec.
2. **Annotate** to [docs/api/CONVENTIONS.md](docs/api/CONVENTIONS.md):
   - `@Tag` on the controller.
   - `@Operation` with a new, unique lowerCamelCase `operationId` (never rename an existing one without a release note), a summary under 80 characters and a description with side effects.
   - `@Parameter` with a description and an example; identity headers stay `@Parameter(hidden = true)` with `@SecurityRequirement` on user-scoped operations.
   - `@RequestBody` with a realistic example.
   - `@ApiResponse` for every real status, including 201/204/404/409 and errors as `ProblemDetail` with an example.
   - `@Schema(description, example)` on every new or changed field. Fields with Kotlin defaults get `requiredMode = NOT_REQUIRED`. Nested types get a unique `@Schema(name)`. Enums and string statuses list their values.
   - Internal endpoints go in the `internal` group only.
3. **Regenerate and review** the committed spec: `./gradlew :<module>:test --tests '*OpenApiSpecTest*' -PupdateOpenApiSpec`, then read the diff of `docs/api/<module>.openapi.yaml` and check that it shows exactly the intended change. A changed commons model can change several specs; regenerate every affected module.
4. **Verify:** `./gradlew :<module>:test` passes (including `OpenApiSpecTest`), and `npx --yes @redocly/cli@2 lint docs/api/*.openapi.yaml --config redocly.yaml` reports no errors and no warnings. Fix findings in the annotations, never by editing the YAML by hand.
5. **Record client-visible changes** (status codes, error bodies, renamed or removed fields, operationIds, schema names) in `release_notes/RELEASE_NOTES_v<platformVersion>.md`, and update `continuum-system-tests` checks that assert them.
6. **A new service** with `@RestController` endpoints gets the full setup from CONVENTIONS.md: an `OpenApiConfig`, the docs flag in `application.yml`, an `OpenApiSpecTest`, a committed spec, and an entry in `.github/workflows/openapi-spec-check.yml`.

If any step cannot be done (for example the lint cannot run), say so in the report and do not claim the API change is complete.

### Reference
- Errors are RFC 9457 `ProblemDetail` (`application/problem+json`). Identity headers are documented as security schemes, never as parameters. Internal endpoints (operator, ForwardAuth) belong to the `internal` spec group only.
- `springdoc` comes from `gradle/libs.versions.toml` and tracks the Spring Boot line (springdoc 3.0.x for Boot 4.0.x); upgrade both together.
- Each documented service (api-server, cluster-manager, credentials-server) has `OpenApiSpecTest`, which fails when the generated spec differs from the committed `docs/api/<module>.openapi.yaml`. CI (`.github/workflows/openapi-spec-check.yml`) repeats that check, fails on an unreviewed spec diff, and lints the specs with Redocly (`redocly.yaml`).

## Project Reference

### Commands

**Build all modules:**
```bash
./gradlew build
```

**Build a single module:**
```bash
./gradlew :continuum-commons:build
./gradlew :continuum-api-server:build
```

**Run tests (all):**
```bash
./gradlew test
```

**Run tests for one module:**
```bash
./gradlew :continuum-orchestration-service:test
./gradlew :continuum-credentials-server:test --tests "org.projectcontinuum.core.credentials.service.CredentialServiceTest"
```

**Run a service locally:**
```bash
./gradlew :continuum-api-server:bootRun
./gradlew :continuum-orchestration-service:bootRun
./gradlew :continuum-message-bridge:bootRun
./gradlew :continuum-credentials-server:bootRun
./gradlew :continuum-cluster-manager:bootRun
```

IntelliJ run configs in `.run/` cover: ApiServer, MessageBridge, OrchestrationService, CredentialServer.

**Git LFS:** run `git lfs install && git lfs pull` after cloning. The api-server bundles DuckDB `httpfs` extensions under LFS and refuses to start on pointer files; every CI checkout that builds modules uses `lfs: true`.

**Start infrastructure (required before running any service):**
```bash
cd docker && docker compose up -d
```

**Build and push a container image (Jib — no Docker daemon needed):**
```bash
./gradlew :continuum-api-server:jib
```

**Publish libraries to Maven Central staging:**
```bash
./gradlew :continuum-commons:publish
./gradlew :continuum-worker-springboot-starter:publish
```

---

### Architecture

#### Modules

| Module | Purpose | Port |
|--------|---------|------|
| `continuum-commons` | Library — base node classes, Parquet I/O, shared models | — |
| `continuum-avro-schemas` | Library — Avro schemas for Kafka execution messages | — |
| `continuum-worker-springboot-starter` | Library — Spring Boot auto-starter for building feature workers | — |
| `continuum-gradle-plugin` | Convention plugins — `org.projectcontinuum.feature` / `org.projectcontinuum.worker` | — |
| `continuum-api-server` | REST API — workflow CRUD, node registry, execution trigger | 8080 |
| `continuum-orchestration-service` | Temporal workflow + activity implementation for DAG execution | — |
| `continuum-message-bridge` | Kafka consumer → MQTT publisher bridge for browser real-time updates | 8081 |
| `continuum-credentials-server` | AES-GCM encrypted credential storage (REST + embedded React UI) | 8083 |
| `continuum-cluster-manager` | Kubernetes lifecycle manager for per-user workbench instances | — |
| `continuum-knime-base` | KNIME compatibility layer (experimental) | — |

#### Execution flow

```
Browser (React Flow) → continuum-api-server (REST)
    → Temporal workflow started (continuum-orchestration-service)
    → InitializeActivity: POST /api/v1/node-explorer/nodes/task-queues to api-server
          resolves which task queue (= which worker) handles each node
    → For each DAG node: IContinuumNodeActivity.run() dispatched to the resolved worker task queue
          Worker (feature repo): downloads input Parquet from MinIO → execute() → uploads output Parquet
    → Node sends progress signals → ContinuumWorkflow receives → publishes WorkflowUpdateEvent to Kafka
    → continuum-message-bridge consumes Kafka → publishes to Mosquitto MQTT
    → Browser receives via MQTT over WebSocket
```

Nodes run in parallel where the DAG allows. `Promise.anyOf()` in `ContinuumWorkflow.run()` drives the execution loop — it waits for the first pending node to complete, then schedules newly unblocked nodes.

#### Node model pattern

Every node in a feature worker must:
1. Be annotated with `@ContinuumNode` (not just `@Component`) — enforced at build time by `validateContinuumNodeAnnotations` Gradle task and at startup by `ContinuumNodeActivity`.
2. Extend `ProcessNodeModel` (for data nodes) or `TriggerNodeModel` (for source nodes).
3. Override `inputPorts`, `outputPorts`, and `metadata` (as `ContinuumWorkflowModel.NodeData`).
4. Implement `execute()` — override the base overload for simple nodes, or the `ExecutionContext` overload for nodes that need credentials.

```kotlin
@ContinuumNode
class MyNodeModel : ProcessNodeModel() {
    override val inputPorts = mapOf("input" to ContinuumWorkflowModel.NodePort(...))
    override val outputPorts = mapOf("output" to ContinuumWorkflowModel.NodePort(...))
    override val metadata = ContinuumWorkflowModel.NodeData(...)

    override fun execute(
        properties: Map<String, Any>?,
        inputs: Map<String, NodeInputReader>,
        nodeOutputWriter: NodeOutputWriter,
        nodeProgressCallback: NodeProgressCallback,
        executionContext: ExecutionContext   // only needed for credential access
    ) { ... }
}
```

Node documentation is auto-loaded from `resources/<package>/<ClassName>.doc.md` on `@PostConstruct`.

#### Credentials

Nodes needing external secrets fetch them on demand from `execute()`, by name:
- The node's `propertiesUISchema` declares a control with `options.format = "credential"` (and optionally `options.credentialType`) purely so `continuum-workbench` renders a credential picker there — the backend does not read this schema.
- Inside `execute()`, the node reads its own `properties` to find the credential name(s) it configured, then calls `executionContext.getCredential<T>(name)`. This fetches the named credential from `continuum-credentials-server` on demand (`GET /api/v1/credentials/{name}` with `x-continuum-user-id` header, via `CredentialFetcherService` in `continuum-worker-springboot-starter`) and deserializes its key-value data into `T` (Jackson), throwing `CredentialsNotFoundException` if not found.

#### Task queues

Three Temporal task queues in `TaskQueues`:
- `WORKFLOW_TASK_QUEUE` — `ContinuumWorkflow` (continuum-orchestration-service)
- `ACTIVITY_TASK_QUEUE` — `ContinuumNodeActivity` (feature workers — name is overridable via `continuum.core.worker.node-task-queue`)
- `ACTIVITY_TASK_QUEUE_INITIALIZE` — `InitializeActivity` (continuum-orchestration-service)

The orchestration service resolves each node's task queue dynamically via `InitializeActivity → POST /api/v1/node-explorer/nodes/task-queues` before dispatching activities. This is how it routes to the correct feature worker.

#### Gradle convention plugins

`continuum-gradle-plugin` provides two convention plugins consumed by feature repos:
- `org.projectcontinuum.feature` — Kotlin library module (commons dep, publishing to Maven Central via JReleaser)
- `org.projectcontinuum.worker` — Spring Boot worker app (extends feature plugin, adds Jib for container builds)

The `continuum` DSL extension in each feature `build.gradle.kts` controls versions: `continuumVersion`, `springBootVersion`, `temporalVersion`, `awsSdkVersion`.

#### Cluster manager

`continuum-cluster-manager` manages per-user `continuum-workbench` Kubernetes deployments (PVC + Deployment + Service + Ingress) via Fabric8 client and FreeMarker templates (`pvc.ftl`, `deployment.ftl`, `service.ftl`, `ingress.ftl`). Service names follow `wb-{instanceId}-svc.{namespace}.svc.cluster.local:8080`. Access to a workbench is gated by `GET /api/v1/workbench/authorize`, which the ingress layer calls with the caller's `x-continuum-user-id` and the forwarded host.

#### Data format

All inter-node data is Apache Parquet (Snappy compressed, Avro schema `DataRow`). `NodeOutputWriter.OutputPortWriter` writes rows; `NodeInputReader` reads them. Output files are named `output.{portId}.parquet`; input files `input.{portId}.parquet`. Both are staged in a local cache path before upload/after download from MinIO.

#### Key versions

| | Version |
|-|---------|
| Kotlin | 2.2.0 |
| Spring Boot | 4.0.6 |
| JDK | 21 (Eclipse Temurin) |
| Temporal BOM | 1.28.0 (orchestration uses 1.35.0) |
| AWS SDK BOM | 2.30.7 |
| Spring Cloud | 2025.1.1 |
| `platformVersion` | 0.0.12 (in `gradle.properties`) |

#### Infrastructure ports (docker-compose)

| Service | Port |
|---------|------|
| PostgreSQL | 35432 |
| Temporal | 7233 |
| Temporal UI | 38081 |
| Kafka (x3) | 39092–39094 |
| Schema Registry | 38080 |
| Kafka UI | 38082 |
| MinIO API / Console | 39000 / 39001 |
| Mosquitto TCP / WS | 31883 / 31884 |
| Credentials Server (container) | 38083 |

PostgreSQL uses a single `continuum` database with multiple schemas. All services default to `continuum_owner` / `continuum-test-password`.

#### Publishing

Libraries (`continuum-commons`, `continuum-avro-schemas`, `continuum-worker-springboot-starter`, `continuum-gradle-plugin`) publish to **Maven Central** via JReleaser and Sonatype. Group: `org.projectcontinuum.core`. Requires `MAVEN_REPO_USERNAME`, `MAVEN_REPO_PASSWORD`, GPG secrets. Before `jreleaserDeploy`, CI runs `.github/scripts/check-maven-central.js` (an `actions/github-script` module: `require(...)({core, glob})`), which fails the job (with a step-summary table) if any staged artifact version already exists on Maven Central; bump `platformVersion` to republish. On a failed deploy CI prints `<module>/build/jreleaser/trace.log` (the console only says "Unexpected error") and uploads it as the `build-diagnostics-<module>` artifact.

Container images build with Jib (no Docker daemon) and push to **Docker Hub** at `docker.io/projectcontinuum/{module-name}:{version}`. Requires `DOCKER_REPO_USERNAME`, `DOCKER_REPO_PASSWORD`. CI builds each image natively on amd64 and arm64 runners (`-Djib.from.platforms=linux/<arch>`) and merges the per-arch digests from each module's `build/jib-image.json` into one multi-arch tag (`.github/workflows/generic-module-builder.yml`). Bundled native binaries (e.g. `continuum-api-server` DuckDB extensions) need a `linux_arm64` build as well.

CI runs `.github/workflows/generic-module-builder.yml`, triggered manually via `.github/workflows/build-module.yml` (tick one or more modules, or "All modules", plus whether to publish to Maven and/or Docker Hub; each ticked module runs as its own matrix leg) and on every published GitHub release via `.github/workflows/on-release.yml` (all libraries and services).

Release notes live in `release_notes/`, one file per version: `RELEASE_NOTES_v<platformVersion>.md` (same layout as `continuum-workbench`). Add changes to the file of the version being prepared; start a new file when `platformVersion` is bumped.

PRs that touch the documented services, `continuum-commons`, `docs/api/` or the shared build files run `.github/workflows/openapi-spec-check.yml`: the `OpenApiSpecTest` drift tests plus a regenerate-and-`git diff` check of `docs/api/*.openapi.yaml`, and a Redocly lint of the committed specs.

PRs that touch any `continuum-*` module or shared build files (`settings.gradle.kts`, root `build.gradle.kts`, `gradle.properties`, `gradle/`) run `.github/workflows/pr-maven-central-check.yml`. All modules share `platformVersion`, so the workflow stages the Maven-published modules locally and runs the same `check-maven-central.js` with `MAVEN_CENTRAL_CHECK_MODE=warn`: an already-published version is a warning annotation and a step-summary note, never a failure. A PR does not need to bump `platformVersion`; bump it when preparing a release, since the release-time run of the script (fail mode) rejects a published version.

## Child DOX Index

- [continuum-commons/AGENTS.md](continuum-commons/AGENTS.md) — base node model, Parquet I/O, shared protocol types; library depended on by every module and every feature repo
- [continuum-worker-springboot-starter/AGENTS.md](continuum-worker-springboot-starter/AGENTS.md) — Spring Boot starter that wraps all workers: S3 I/O, Temporal activity, progress reporting, credential resolution
- [continuum-orchestration-service/AGENTS.md](continuum-orchestration-service/AGENTS.md) — Temporal workflow + InitializeActivity; DAG parallel execution engine
- [continuum-api-server/AGENTS.md](continuum-api-server/AGENTS.md) — REST API: workflow CRUD, node registry, execution trigger, DuckDB node output preview
- [continuum-credentials-server/AGENTS.md](continuum-credentials-server/AGENTS.md) — AES-GCM encrypted credential store with embedded React UI
- [continuum-cluster-manager/AGENTS.md](continuum-cluster-manager/AGENTS.md) — Kubernetes lifecycle manager for per-user workbench pods
- [continuum-gradle-plugin/AGENTS.md](continuum-gradle-plugin/AGENTS.md) — convention plugins for feature modules and workers; @ContinuumNode annotation validator

Modules without child AGENTS.md (minimal, self-contained):
- `continuum-avro-schemas` — Avro schema definitions only; no logic
- `continuum-message-bridge` — Kafka consumer → MQTT bridge; 10 files, all config/handler/entity
- `continuum-knime-base` — experimental KNIME compatibility layer; 2 files