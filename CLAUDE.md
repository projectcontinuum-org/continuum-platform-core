# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## Commands

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

## Architecture

### Modules

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
| `landing-page` | Static marketing landing page | — |

### Execution flow

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

### Node model pattern

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

### Credentials

Nodes needing external secrets use the credentials system:
- The node's `propertiesUISchema` declares a control with `options.format = "credential"` and `options.credentialLabel = "My Label"`.
- Before `execute()`, `CredentialResolver` scans the UI schema, fetches the named credential from `continuum-credentials-server` (`GET /api/v1/credentials/{name}` with `x-continuum-user-id` header), and passes it via `ExecutionContext`.
- Inside `execute()`: `executionContext.getCredential("My Label")` returns the key-value map.

### Task queues

Three Temporal task queues in `TaskQueues`:
- `WORKFLOW_TASK_QUEUE` — `ContinuumWorkflow` (continuum-orchestration-service)
- `ACTIVITY_TASK_QUEUE` — `ContinuumNodeActivity` (feature workers — name is overridable via `continuum.core.worker.node-task-queue`)
- `ACTIVITY_TASK_QUEUE_INITIALIZE` — `InitializeActivity` (continuum-orchestration-service)

The orchestration service resolves each node's task queue dynamically via `InitializeActivity → POST /api/v1/node-explorer/nodes/task-queues` before dispatching activities. This is how it routes to the correct feature worker.

### Gradle convention plugins

`continuum-gradle-plugin` provides two convention plugins consumed by feature repos:
- `org.projectcontinuum.feature` — Kotlin library module (commons dep, publishing to Maven Central via JReleaser)
- `org.projectcontinuum.worker` — Spring Boot worker app (extends feature plugin, adds Jib for container builds)

The `continuum` DSL extension in each feature `build.gradle.kts` controls versions: `continuumVersion`, `springBootVersion`, `temporalVersion`, `awsSdkVersion`.

### Cluster manager and cloud gateway

`continuum-cluster-manager` manages per-user `continuum-workbench` Kubernetes deployments (PVC + Deployment + Service) via Fabric8 client and FreeMarker templates (`pvc.ftl`, `deployment.ftl`, `service.ftl`). Service names follow `wb-{instanceId}-svc.{namespace}.svc.cluster.local:8080`.

`continuum-cloud-gateway` is an HTTP + WebSocket reverse proxy that resolves the workbench service endpoint from PostgreSQL and forwards requests, stripping the `/workbench/{instanceName}/open` prefix. It proxies to the cluster-local service URL.

### Data format

All inter-node data is Apache Parquet (Snappy compressed, Avro schema `DataRow`). `NodeOutputWriter.OutputPortWriter` writes rows; `NodeInputReader` reads them. Output files are named `output.{portId}.parquet`; input files `input.{portId}.parquet`. Both are staged in a local cache path before upload/after download from MinIO.

### Key versions

| | Version |
|-|---------|
| Kotlin | 2.2.0 |
| Spring Boot | 4.0.6 |
| JDK | 21 (Eclipse Temurin) |
| Temporal BOM | 1.28.0 (orchestration uses 1.35.0) |
| AWS SDK BOM | 2.30.7 |
| Spring Cloud | 2025.1.1 |
| `platformVersion` | 0.0.12 (in `gradle.properties`) |

### Infrastructure ports (docker-compose)

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

### Publishing

Libraries (`continuum-commons`, `continuum-avro-schemas`, `continuum-worker-springboot-starter`, `continuum-gradle-plugin`) publish to **Maven Central** via JReleaser and Sonatype. Group: `org.projectcontinuum.core`. Requires `MAVEN_REPO_USERNAME`, `MAVEN_REPO_PASSWORD`, GPG secrets.

Container images build with Jib (no Docker daemon) and push to **Docker Hub** at `docker.io/projectcontinuum/{module-name}:{version}`. Requires `DOCKER_REPO_USERNAME`, `DOCKER_REPO_PASSWORD`.

CI is fully manual-trigger via `.github/workflows/build-module.yml` — select the module, whether to publish to Maven and/or Docker Hub.
