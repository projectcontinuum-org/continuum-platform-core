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
./gradlew :continuum-cloud-gateway:bootRun
./gradlew :continuum-knime-scheduler:bootRun
```

IntelliJ run configs in `.run/` cover: ApiServer, MessageBridge, OrchestrationService, CredentialServer, Gateway.

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
| `continuum-cloud-gateway` | HTTP/WebSocket reverse proxy routing to per-user workbench pods | — |
| `continuum-knime-base` | KNIME compatibility layer (experimental) | — |
| `continuum-knime-scheduler` | Object-store CRUD proxy for KNIME `.knwf` workflow files + Postgres bookkeeping | 8085 |
| `landing-page` | Static marketing landing page | — |

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

#### Cluster manager and cloud gateway

`continuum-cluster-manager` manages per-user `continuum-workbench` Kubernetes deployments (PVC + Deployment + Service) via Fabric8 client and FreeMarker templates (`pvc.ftl`, `deployment.ftl`, `service.ftl`). Service names follow `wb-{instanceId}-svc.{namespace}.svc.cluster.local:8080`.

`continuum-cloud-gateway` is an HTTP + WebSocket reverse proxy that resolves the workbench service endpoint from PostgreSQL and forwards requests, stripping the `/workbench/{instanceName}/open` prefix. It proxies to the cluster-local service URL.

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
| Cloud Gateway (container) | 38084 |

PostgreSQL uses a single `continuum` database with multiple schemas. All services default to `continuum_owner` / `continuum-test-password`.

#### Publishing

Libraries (`continuum-commons`, `continuum-avro-schemas`, `continuum-worker-springboot-starter`, `continuum-gradle-plugin`) publish to **Maven Central** via JReleaser and Sonatype. Group: `org.projectcontinuum.core`. Requires `MAVEN_REPO_USERNAME`, `MAVEN_REPO_PASSWORD`, GPG secrets.

Container images build with Jib (no Docker daemon) and push to **Docker Hub** at `docker.io/projectcontinuum/{module-name}:{version}`. Requires `DOCKER_REPO_USERNAME`, `DOCKER_REPO_PASSWORD`.

CI is fully manual-trigger via `.github/workflows/build-module.yml` — select the module, whether to publish to Maven and/or Docker Hub.

## Child DOX Index

- [continuum-commons/AGENTS.md](continuum-commons/AGENTS.md) — base node model, Parquet I/O, shared protocol types; library depended on by every module and every feature repo
- [continuum-worker-springboot-starter/AGENTS.md](continuum-worker-springboot-starter/AGENTS.md) — Spring Boot starter that wraps all workers: S3 I/O, Temporal activity, progress reporting, credential resolution
- [continuum-orchestration-service/AGENTS.md](continuum-orchestration-service/AGENTS.md) — Temporal workflow + InitializeActivity; DAG parallel execution engine
- [continuum-api-server/AGENTS.md](continuum-api-server/AGENTS.md) — REST API: workflow CRUD, node registry, execution trigger, DuckDB node output preview
- [continuum-credentials-server/AGENTS.md](continuum-credentials-server/AGENTS.md) — AES-GCM encrypted credential store with embedded React UI
- [continuum-cluster-manager/AGENTS.md](continuum-cluster-manager/AGENTS.md) — Kubernetes lifecycle manager for per-user workbench pods
- [continuum-cloud-gateway/AGENTS.md](continuum-cloud-gateway/AGENTS.md) — HTTP + WebSocket reverse proxy routing to per-user workbench pods
- [continuum-gradle-plugin/AGENTS.md](continuum-gradle-plugin/AGENTS.md) — convention plugins for feature modules and workers; @ContinuumNode annotation validator
- [continuum-knime-scheduler/AGENTS.md](continuum-knime-scheduler/AGENTS.md) — object-store CRUD proxy for KNIME `.knwf` workflow files, with per-user Postgres bookkeeping

Modules without child AGENTS.md (minimal, self-contained):
- `continuum-avro-schemas` — Avro schema definitions only; no logic
- `continuum-message-bridge` — Kafka consumer → MQTT bridge; 10 files, all config/handler/entity
- `continuum-knime-base` — experimental KNIME compatibility layer; 2 files
- `landing-page` — static marketing page