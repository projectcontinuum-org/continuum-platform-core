## Purpose

REST API server — owns workflow persistence, node registry, execution triggers, and node output data retrieval. Also serves as the source of truth for node-to-task-queue mapping queried by the orchestration service at workflow start.

## Ownership

Core platform team. Container image: `projectcontinuum/continuum-api-server`. Runs on port 8080.

## Local Contracts

- Node registry: workers register their nodes via Kafka (handled by `continuum-message-bridge`). The API server exposes `GET/POST /api/v1/node-explorer/nodes/task-queues` so the orchestration service can resolve which worker owns which node.
- `WorkflowController` — CRUD for workflow definitions (stored as JSONB in PostgreSQL).
- `WorkflowRunController` / `WorkflowRunService` — execution lifecycle. Starting a run creates a Temporal workflow via the SDK client.
- `NodeOutputController` / `NodeOutputService` — DuckDB reads Parquet from MinIO/S3 for in-browser data preview. DuckDB 1.2.2 is included as a JDBC driver. `NodeOutputRepository` contract:
  - `workflowId`, `nodeId` and `outputId` must match `^[A-Za-z0-9._-]{1,255}$`, and must not be `.` or contain `..`.
  - `page` must be `>= 0`. `pageSize` must be in `1..NodeOutputRepository.MAX_PAGE_SIZE` (1000).
  - The resolved cache file must canonically stay under `cacheDirectoryBasePath`.
  - Any violation throws `InvalidRequestException`. `ApiExceptionHandler` maps it to HTTP 400 `ProblemDetail`.
  - The parquet path and LIMIT/OFFSET are always bound parameters (`read_parquet(?) ... LIMIT ? OFFSET ?`). Never interpolate them into SQL; httpfs is loaded, so an injected path could reach the network.
- Temporal visibility queries: build them with `utils.TemporalVisibilityQuery`. Never use raw string interpolation.
  - `quote()` escapes values.
  - `scope()` AND-combines a client query as `(<server filter>) AND (<client query>)`, after `validateClientQuery` rejects unbalanced parens, unterminated quotes, `;`, comments, backslashes and control characters (400).
- `WorkflowController` is owner-scoped end to end by the `x-continuum-user-id` header (default `anonymous`, so local docker-compose needs no auth layer). Ownership comes from `workflow_runs.owned_by`:
  - `/list`, `/count`, `/tree` and `/active` use the server filter `WorkflowId IN (...)`, in chunks of 500. A caller with no runs gets an empty result, and Temporal is not queried.
  - `/{workflowId}/status`, `/{workflowId}/cancel` and `/{workflowId}/terminate` return 404 for a workflow the caller does not own, and for an unknown id. The check (`existsByWorkflowIdAndOwnedBy`) runs before any Temporal signal is sent. Never signal a workflow before checking ownership.
  - The optional `query` parameter is still accepted, but only narrows the caller's own runs.
  - Any new endpoint that takes a workflow id must check ownership the same way.
- `NodeOutputService.getOutput` checks that the caller owns the run before locating or reading any output. An unowned run and a nonexistent run both throw `ResourceNotFoundException`, which `ApiExceptionHandler` maps to a 404 `ProblemDetail` with the same message, so a tenant cannot probe for other tenants' ids.
- Runs with no `workflow_runs` row (created before that table existed, or a scheduled run whose first snapshot has not reached the message bridge yet) are not visible to any caller, so they cannot be cancelled, terminated or read through the API until a row exists.
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
