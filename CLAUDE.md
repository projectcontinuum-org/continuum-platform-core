# Project Continuum — AI Agent Blueprint

> This file is the single source of truth for understanding the full Project Continuum ecosystem.
> Read this FIRST before exploring any code. It eliminates the need to traverse the codebase.

---

## 1. What Is Continuum

A distributed, crash-proof workflow execution platform. Users build data processing workflows as DAGs in a browser-based IDE. Each node processes tabular data (Apache Parquet) stored in S3/MinIO. Execution is durable (Temporal), events stream in real time (Kafka → MQTT → WebSocket), and capabilities scale by deploying independent workers.

---

## 2. Repository Map

The ecosystem spans 5 independent git repositories:

| Repository | GitHub | Role | Tech |
|-----------|--------|------|------|
| **Continuum** (this repo) | `projectcontinuum/Continuum` | Core backend monorepo — API server, worker framework, shared libraries | Kotlin, Spring Boot, Gradle |
| **continuum-workbench** | `projectcontinuum/continuum-workbench` | Frontend — browser-based IDE with workflow editor | React 18, TypeScript, Eclipse Theia, React Flow 11, Turborepo |
| **continuum-feature-base** | `projectcontinuum/continuum-feature-base` | Base analytics nodes (16 nodes) — transforms, REST, scripting, anomaly detection | Kotlin, Spring Boot, Gradle |
| **continuum-feature-ai** | `projectcontinuum/continuum-feature-ai` | AI/ML nodes — LLM fine-tuning with Unsloth + LoRA | Kotlin, Python, Spring Boot, Gradle |
| **continuum-feature-template** | `projectcontinuum/continuum-feature-template` | Template repo — scaffold new feature workers | Kotlin, Spring Boot, Gradle |

---

## 3. Dependency Graph

```
┌─────────────────────────────────────────────────────────────┐
│  Continuum/ (core monorepo — this repo)                      │
│                                                              │
│  continuum-commons ◄─────────────────────────────────┐       │
│    (base classes, data types, Parquet/S3 utils)      │       │
│         ▲          ▲          ▲          ▲            │       │
│         │          │          │          │            │       │
│  continuum-   continuum-  continuum-  continuum-     │       │
│  api-server   message-   worker-     knime-base      │       │
│               bridge     springboot-                 │       │
│    ▲                     starter                     │       │
│    │                        ▲                        │       │
│    │  continuum-avro-schemas│                        │       │
│    │    (shared Kafka msg schemas)                   │       │
│    └────────────┬───────────┘                        │       │
│                 │                                    │       │
└─────────────────┼────────────────────────────────────┘       │
                  │                                            │
    Published to GitHub Packages as Maven artifacts            │
                  │                                            │
    ┌─────────────┼──────────────────────┐                     │
    ▼             ▼                      ▼                     │
┌──────────┐ ┌──────────┐ ┌──────────────────┐                │
│ feature- │ │ feature- │ │ feature-template │                │
│ base     │ │ ai       │ │ (scaffold new    │                │
│ (worker) │ │ (worker) │ │  feature repos)  │                │
│          │ │          │ │                  │                │
│ Depends: │ │ Depends: │ │ Depends:         │                │
│ commons  │ │ commons  │ │ commons          │                │
│ avro-    │ │ starter  │ │ starter          │                │
│ schemas  │ │          │ │                  │                │
│ starter  │ │          │ │                  │                │
└──────────┘ └──────────┘ └──────────────────┘

continuum-workbench (frontend) ──REST/WebSocket──► continuum-api-server
                               ──MQTT/WS──────► continuum-message-bridge
```

---

## 4. Module Reference — Continuum (Core Monorepo)

**Build:** Gradle Kotlin DSL, `settings.gradle.kts` at root
**Key versions:** Kotlin 2.1.0, Spring Boot 3.4.0, JDK 21, Temporal 1.28.0, AWS SDK 2.30.7

| Module | Group | Depends On | Purpose |
|--------|-------|-----------|---------|
| `continuum-commons` | `org.projectcontinuum.core` | — | Base classes: `ProcessNodeModel`, `ContinuumWorkflowModel`, `NodePort`, `NodeInputReader`, `NodeOutputWriter`, Parquet/S3 utilities |
| `continuum-avro-schemas` | `org.projectcontinuum.core` | — | Avro schemas for Kafka workflow execution messages |
| `continuum-worker-springboot-starter` | `org.projectcontinuum.core` | commons, avro-schemas | Spring Boot starter that auto-registers nodes with Temporal, handles download/upload/progress lifecycle |
| `continuum-api-server` | `org.projectcontinuum.core` | commons, avro-schemas | REST API — workflow CRUD, node registry, execution triggers. Includes DuckDB 1.2.2 |
| `continuum-message-bridge` | `org.projectcontinuum.core` | commons | Kafka consumer → MQTT publisher bridge for real-time browser updates |
| `continuum-knime-base` | `org.projectcontinuum.knime` | commons | KNIME node compatibility layer (experimental) |
| `workers/continuum-base-worker` | `org.projectcontinuum.app.worker.base` | starter, continuum-base, continuum-feature-ai | Legacy monorepo worker (features extracted to separate repos) |

**Key paths:**
```
Continuum/
├── settings.gradle.kts              # Module includes
├── gradle.properties                # repoName=roushan65/Continuum
├── docker/docker-compose.yml        # Full infra stack
├── .run/                            # IntelliJ run configs (ApiServer, MessageBridge)
├── continuum-commons/src/main/kotlin/com/continuum/
│   ├── model/ContinuumWorkflowModel.kt   # Core data model
│   └── node/ProcessNodeModel.kt          # Base class ALL nodes extend
├── continuum-api-server/src/main/kotlin/com/continuum/api/
├── continuum-message-bridge/src/main/kotlin/com/continuum/bridge/
├── continuum-worker-springboot-starter/src/main/kotlin/com/continuum/worker/
├── continuum-avro-schemas/src/main/avro/
├── continuum-knime-base/src/main/kotlin/com/continuum/knime/
├── docs/gifs/                       # Demo GIFs for README
└── continuum-node-builder.prompt.md # AI prompt for generating nodes
```

---

## 5. Module Reference — continuum-workbench (Frontend)

**Build:** Yarn 1.22 + Turborepo, `package.json` at root
**Key versions:** React 18, TypeScript 5, Eclipse Theia (latest), React Flow 11, Node >= 20

| Workspace | Package Name | Purpose |
|-----------|-------------|---------|
| `continuum-core/` | `@continuum/core` | Shared React library — models, types, React Flow node components, JSONForms integration |
| `workflow-editor-extension/` | `@continuum/workflow-editor-extension` | Eclipse Theia extension — workflow canvas widget, node explorer panel, execution viewer |
| `continuum-workbench/` | `@continuum/workbench` | Full Theia IDE application (browser target, port 3002) |
| `continuum-workbench-thin/` | `@continuum/workbench-thin` | Lightweight Theia application |

**Key scripts:** `yarn build`, `yarn dev`, `yarn start:workbench` (port 3002), `yarn start:workbench-thin`

---

## 6. Module Reference — Feature Repos

All feature repos follow the same pattern:

```
continuum-feature-<name>/
├── features/
│   └── continuum-feature-<module>/       # Node implementations (Spring auto-configured)
│       ├── build.gradle.kts              # Depends on continuum-commons (GitHub Packages)
│       └── src/main/kotlin/.../node/     # Node classes extending ProcessNodeModel
├── worker/
│   ├── build.gradle.kts                  # Depends on continuum-worker-springboot-starter + feature modules
│   └── src/main/kotlin/.../App.kt        # Spring Boot entry point
├── docker/docker-compose.yml             # Full local infra (same services as monorepo)
├── settings.gradle.kts                   # Multi-module project
├── gradle.properties                     # GitHub repo names for publishing
└── .github/workflows/build.yml           # CI: build → publish → containerize (Jib)
```

### continuum-feature-base

**Settings:** `include(":features:continuum-feature-analytics")`, `include(":worker")`
**Group:** `org.projectcontinuum.base` (feature), `org.projectcontinuum.feature.base` (worker)
**Extra deps beyond commons:** Kafka + Confluent Avro 7.6.1, Temporal SDK, AWS SDK, MQTT Paho 1.2.5, FreeMarker 2.3.32, Kotlin Scripting

**16 nodes** in `features/continuum-feature-analytics/src/main/kotlin/com/continuum/feature/analytics/node/`:

| Node Class | Title | Category |
|-----------|-------|----------|
| `CreateTableNodeModel` | Create Table | Table & Data Structures |
| `ColumnJoinNodeModel` | Column Join Node | Processing |
| `JointNodeModel` | Joint Node | Processing |
| `JoinOnMultipleKeysNodeModel` | Join on Multiple Keys | Join & Merge |
| `PivotColumnsNodeModel` | Pivot Columns | Transform |
| `JsonExploderNodeModel` | JSON Exploder | JSON & Data Parsing |
| `SplitNodeModel` | Column Splitter | Processing |
| `KotlinScriptNodeModel` | Kotlin Script | Transform |
| `DynamicRowFilterNodeModel` | Dynamic Row Filter | Filter & Select |
| `ConditionalSplitterNodeModel` | Conditional Splitter | Flow Control |
| `TimeWindowAggregatorNodeModel` | Time Window Aggregator | Aggregation & Time Series |
| `BatchAccumulatorNodeModel` | Batch Accumulator | Aggregation & Grouping |
| `TextNormalizerNodeModel` | Text Normalizer | String & Text |
| `CryptoHasherNodeModel` | Crypto Hasher | Security & Encryption |
| `AnomalyDetectorZScoreNodeModel` | Anomaly Detector | Analysis & Statistics |
| `RestNodeModel` | REST Client | Integration & API |

### continuum-feature-ai

**Settings:** `include("features:continuum-feature-unsloth")`, `include(":worker")`
**Group:** `org.projectcontinuum.feature.unsloth` (feature), `org.projectcontinuum.feature.ai` (worker)
**Extra deps beyond commons:** Jackson Kotlin

**1 node** in `features/continuum-feature-unsloth/src/main/kotlin/com/continuum/feature/ai/node/`:

| Node Class | Title | Category |
|-----------|-------|----------|
| `UnslothTrainerNodeModel` | LLM Trainer (Unsloth) | Machine Learning, LLM Training |

- Input: Parquet table (`training_data` port) with instruction + response columns
- Output: JSON (`model_info` port) with model path, base model, training config
- Supported models: Phi-4, Mistral 7B, Llama 2/3, Gemma 2, Qwen 2.5, Falcon 7B, any HuggingFace causal LM
- Config groups: Model, Data, Training (epochs, batch, lr, seq length), LoRA (rank, alpha, dropout), Advanced (4-bit quant, seed)
- Python execution via auto-managed venv (`PythonEnvironmentManager.kt`)
- Config: `org.projectcontinuum.feature.ai.unsloth-trainer.venv-path` (default: `~/.continuum/unsloth-env`)

### continuum-feature-template

**Settings:** `include(":features:continuum-feature-example")`, `include(":worker")`
**Group:** `org.projectcontinuum.feature.template`
**Note:** Directory is `continuum-feature-example/` but Kotlin package is `org.projectcontinuum.feature.template`

**1 example node** in `features/continuum-feature-example/src/main/kotlin/com/continuum/feature/template/node/`:

| Node Class | Title |
|-----------|-------|
| `ColumnJoinerNodeModel` | Column Joiner |

---

## 7. How Nodes Work (Pattern Reference)

Every node follows this pattern — understand this and you understand the whole system:

```kotlin
@Component
class MyNodeModel : ProcessNodeModel() {

    // 1. Define ports
    final override val inputPorts = mapOf("input" to ContinuumWorkflowModel.NodePort(...))
    final override val outputPorts = mapOf("output" to ContinuumWorkflowModel.NodePort(...))

    // 2. Define JSON Schema for config
    val propertiesSchema: Map<String, Any> = objectMapper.readValue("""{ ... }""")

    // 3. Define metadata (title, description, icon, ports, schema)
    override val metadata = ContinuumWorkflowModel.NodeData(...)

    // 4. Implement execution
    override fun execute(
        properties: Map<String, Any>?,
        inputs: Map<String, NodeInputReader>,
        nodeOutputWriter: NodeOutputWriter,
        nodeProgressCallback: NodeProgressCallback
    ) {
        // Read input rows → process → write output rows → report progress
    }
}
```

**Auto-discovery:** Each feature module has `AutoConfigure.kt` with `@ComponentScan` + Spring `AutoConfiguration.imports` file. The worker's Spring Boot app auto-discovers all `@Component` nodes.

**Data flow at runtime:**
1. User triggers workflow via API server
2. API server creates Temporal workflow
3. Temporal dispatches node activities to registered workers
4. Worker downloads input Parquet from S3/MinIO
5. Node's `execute()` processes rows
6. Worker uploads output Parquet to S3/MinIO
7. Worker reports progress via Kafka
8. Message bridge forwards to MQTT → browser WebSocket

---

## 8. Infrastructure Services (Docker Compose)

Every repo with a `docker/` directory runs the same infrastructure stack:

| Service | Port(s) | Purpose |
|---------|---------|---------|
| PostgreSQL | 35432 | Temporal persistence |
| Temporal | 7233 | Workflow orchestration |
| Temporal UI | 38081 | Workflow monitoring web UI |
| Kafka (x3 KRaft) | 39092, 39093, 39094 | Event streaming |
| Schema Registry | 38080 | Kafka schema management |
| Kafka UI | 38082 | Topic monitoring web UI |
| MinIO | 39000 (API), 39001 (Console) | S3-compatible object storage |
| Mosquitto | 31883 (TCP), 31884 (WS) | MQTT broker |
| API Server | 8080 | Continuum REST API |
| Message Bridge | 8081 | Kafka → MQTT bridge |

---

## 9. Shared Dependency Versions

| Dependency | Version | Used In |
|-----------|---------|---------|
| Kotlin | 2.1.0 | All Kotlin repos |
| Spring Boot | 3.4.0–3.4.1 | All Kotlin repos |
| JDK | 21 (Eclipse Temurin) | All Kotlin repos |
| Temporal BOM | 1.28.0 | Monorepo + feature workers |
| AWS SDK BOM | 2.30.7 | Monorepo + feature-base |
| Spring Cloud | 2024.0.0 | Monorepo + feature workers |
| Jackson Kotlin | 2.18.2 | All Kotlin repos |
| Confluent Avro | 7.6.1 | Monorepo + feature-base |
| MQTT Paho | 1.2.5 | Message bridge + feature-base |
| Jib (Docker) | 3.4.1 | All workers |
| Node.js | >= 20 | Workbench |
| Yarn | 1.22 | Workbench |
| Turborepo | 1.13.3 | Workbench |
| React | 18.2.0 | Workbench |
| React Flow | 11.10.1 | Workbench |

---

## 10. Conventions

- **GitHub Packages:** All Kotlin artifacts publish to GitHub Packages Maven. Requires `GITHUB_USERNAME` + `GITHUB_TOKEN` env vars.
- **Gradle properties:** `sourceRepoName` = monorepo (for reading deps), `repoName` = current repo (for publishing)
- **Containerization:** Jib plugin (no Docker daemon needed), base image `eclipse-temurin:21-jre`, publishes to GHCR
- **CI/CD:** GitHub Actions — build on push/PR, publish + containerize on tags
- **Node documentation:** Each node has a `.doc.md` file in resources matching its package path, auto-loaded by `ProcessNodeModel`
- **Spring auto-config:** Every feature module registers via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- **Port content type:** All Parquet data ports use `APPLICATION_OCTET_STREAM_VALUE`
- **Config schemas:** JSON Schema for node properties, with optional UI Schema for form layout
