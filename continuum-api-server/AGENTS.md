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
- MCP server (Spring AI, streamable HTTP at `/mcp`, no auth, annotation scanning) in package `mcp`. It owns all Continuum-specific agent tooling, so clients (the workbench WorkflowBuilder agent, the Claude Code agent) stay a prompt plus these tools plus their host's generic file tools. They depend on these tool names and argument shapes:
  - `ContinuumNodeMcpTools` — `listContinuumNodeCategories`, `searchContinuumNodes` (compact `McpNodeTreeItem` results, no icons or schemas), `getContinuumNodeMetadata` (full `NodeData`), `getContinuumNodeDocumentation` (markdown text). An unknown id throws, which the client receives as `isError`.
  - `ContinuumWorkflowMcpTools` — both take the compact spec `(name, nodes[{key,nodeId,properties}], edges[{from,fromPort,to,toPort}])`:
    - `validateContinuumWorkflow`, backed by `WorkflowSpecValidator`. Checks against the live registry, collects every problem and returns `{valid, nodeCount, edgeCount, errors[], warnings[]}` as a normal result (never `isError`), each issue naming its `node`, `edge` or `property`. Properties are validated merged over the registry defaults, with `ValidationHelper.findSchemaViolations`.
    - `buildContinuumWorkflow`, backed by `WorkflowSpecBuilder`. Validates first; when valid returns `{valid, fileName, errors, warnings, workflow}` where `workflow` is the complete `.cwf` content as a `ContinuumWorkflowModel`: full registry `NodeData` with spec properties merged over the defaults, `type` `BaseNode`/`BaseEdge`, `selected:false`, edge ids `reactflow__edge-{from}{fromPort}-{to}{toPort}`, layout by longest-path depth (330 x, 160 y), random 64-hex id, `active:false`; `fileName` is the name slug + `.cwf`. When invalid, `workflow` and `fileName` are omitted.
    - An empty `nodes` list is valid (warning "The workflow has no nodes yet.") and builds an empty workflow, so clients can save the file first and rebuild it after each added node.
  - `ContinuumNodeMcpResources` — resource template `continuum-node://{nodeId}/documentation`.
- `WorkflowScheduleController` — generic Temporal-`ScheduleClient`-backed cron scheduling for any `ContinuumWorkflowModel` DAG, backed by `workflow_schedules` (JSONB embed, no separate schedule entity per node type). This module has zero KNIME awareness — KNIME-specific scheduling lives entirely in `continuum-knime-scheduler`, which calls this controller's `/api/v1/workflow/schedule` API as a plain REST client.
- Config defaults: PostgreSQL on `localhost:35432`, MinIO on `localhost:39000`, Temporal on `localhost:7233`.

## Work Guidance

- DDL is managed via `src/main/resources/schema.sql` (applied by `spring.sql.init.mode = always`). JPA `ddl-auto` is `none`.
- Swagger UI available at `/swagger-ui.html` when running locally.
- The `x-continuum-user-id` HTTP header is the owner identity propagated through the system. Ensure it is forwarded when making internal service calls.

## Verification

```bash
./gradlew :continuum-api-server:test
```
