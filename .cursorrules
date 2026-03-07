# Project Continuum — Copilot Instructions

> For the full architecture blueprint, see `AGENTS.md` in this repo.

## What Is This Repo

Core backend monorepo for **Project Continuum** — a distributed, crash-proof workflow execution platform. Users build DAG workflows in a browser IDE. Nodes process Apache Parquet data in S3/MinIO. Execution is durable via Temporal. Events stream in real time via Kafka → MQTT → WebSocket.

**GitHub org:** `projectcontinuum`

## Ecosystem (5 repos)

| Repo | Role |
|------|------|
| **Continuum** (this) | Core backend — API server, worker framework, shared libraries |
| continuum-workbench | Browser IDE — React 18 + Eclipse Theia + React Flow 11 |
| continuum-feature-base | 16 analytics nodes (transforms, REST, scripting, anomaly detection) |
| continuum-feature-ai | AI/ML nodes — LLM fine-tuning with Unsloth + LoRA |
| continuum-feature-template | Template for scaffolding new feature repos |

## Modules In This Repo

| Module | Purpose |
|--------|---------|
| `continuum-commons` | Base classes: `ProcessNodeModel`, `ContinuumWorkflowModel`, Parquet/S3 utils |
| `continuum-worker-springboot-starter` | Spring Boot starter — auto-registers nodes with Temporal |
| `continuum-api-server` | REST API — workflow CRUD, node registry, execution triggers |
| `continuum-message-bridge` | Kafka → MQTT bridge for real-time browser updates |
| `continuum-avro-schemas` | Shared Avro schemas for Kafka messages |
| `continuum-knime-base` | KNIME compatibility (experimental) |

## Dependency Chain

```
continuum-commons (base) ← continuum-worker-springboot-starter ← feature repos (via GitHub Packages)
continuum-commons ← continuum-api-server
continuum-commons ← continuum-message-bridge
```

## Stack

Kotlin 2.1.0, Spring Boot 3.4.0, JDK 21, Temporal 1.28.0, AWS SDK 2.30.7, Kafka, MQTT, Gradle

## Node Pattern

Every workflow node extends `ProcessNodeModel` with: input/output ports, JSON Schema config, metadata, and `execute()` method. Nodes are `@Component` beans, auto-discovered via Spring Boot auto-configuration.

## Key Paths

- `continuum-commons/src/main/kotlin/com/continuum/node/ProcessNodeModel.kt` — Base class all nodes extend
- `continuum-commons/src/main/kotlin/com/continuum/model/ContinuumWorkflowModel.kt` — Core data model
- `continuum-api-server/` — REST API
- `continuum-message-bridge/` — Kafka→MQTT bridge
- `docker/docker-compose.yml` — Full infrastructure stack

## Conventions

- GitHub Packages for Maven artifacts (requires `GITHUB_USERNAME` + `GITHUB_TOKEN`)
- Jib for containerization (no Docker daemon), publishes to GHCR
- GitHub Actions CI/CD — build on push/PR, publish + containerize on tags
- Each node has a `.doc.md` file in resources, auto-loaded by `ProcessNodeModel`
- Spring auto-config via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
