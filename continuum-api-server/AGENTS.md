## Purpose

REST API server — owns workflow persistence, node registry, execution triggers, and node output data retrieval. Also serves as the source of truth for node-to-task-queue mapping queried by the orchestration service at workflow start.

## Ownership

Core platform team. Container image: `projectcontinuum/continuum-api-server`. Runs on port 8080.

## Local Contracts

- Node registry: workers register their nodes via Kafka (handled by `continuum-message-bridge`). The API server exposes `GET/POST /api/v1/node-explorer/nodes/task-queues` so the orchestration service can resolve which worker owns which node.
- `WorkflowController` — CRUD for workflow definitions (stored as JSONB in PostgreSQL).
- `WorkflowRunController` / `WorkflowRunService` — execution lifecycle. Starting a run creates a Temporal workflow via the SDK client.
- `NodeOutputController` / `NodeOutputService` — DuckDB reads Parquet from MinIO/S3 for in-browser data preview. DuckDB 1.2.2 is included as a JDBC driver.
- `NodeExplorerController` — serves the node palette tree used by the frontend, backed by `RegisteredNodeRepository`.
- Two persistence layers: JPA (`WorkflowRunEntity`, `WorkflowRunSummaryEntity`) for workflow runs; JDBC (`RegisteredNodeRepository`) for node registry.
- RSQL filter support on workflow run queries via `io.github.perplexhub:rsql-jpa-spring-boot-starter`.
- `TreeHelper` — builds the hierarchical node explorer tree from flat registered node records.
- Config defaults: PostgreSQL on `localhost:35432`, MinIO on `localhost:39000`, Temporal on `localhost:7233`.

## Work Guidance

- DDL is managed via `src/main/resources/schema.sql` (applied by `spring.sql.init.mode = always`). JPA `ddl-auto` is `none`.
- Swagger UI available at `/swagger-ui.html` when running locally.
- The `x-continuum-user-id` HTTP header is the owner identity propagated through the system. Ensure it is forwarded when making internal service calls.

## Verification

```bash
./gradlew :continuum-api-server:test
```
