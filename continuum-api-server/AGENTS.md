## Purpose

REST API server — owns workflow persistence, node registry, execution triggers, and node output data retrieval. Also serves as the source of truth for node-to-task-queue mapping queried by the orchestration service at workflow start.

## Ownership

Core platform team. Container image: `projectcontinuum/continuum-api-server`. Runs on port 8080.

## Local Contracts

- Node registry: workers register their nodes via Kafka (handled by `continuum-message-bridge`). The API server exposes `GET/POST /api/v1/node-explorer/nodes/task-queues` so the orchestration service can resolve which worker owns which node.
- `WorkflowController` — CRUD for workflow definitions (stored as JSONB in PostgreSQL).
- `service.WorkflowPropertiesValidator.requireValidProperties` validates every node's `properties` against the `propertiesSchema` **registered** for its `data.nodeModel`. The `propertiesSchema` in the request body is client-controlled and is never used: a client could send `{}`. A node whose `nodeModel` is not registered is rejected. `WorkflowService.startWorkflow` and `WorkflowScheduleService.createSchedule` call it first, before any DB row or Temporal call. It throws `InvalidRequestException` (400), naming each invalid node. Any new endpoint that accepts a workflow to run must call it.
- Node registry cache: `NodeTreeEntryRepository.findByNodeId` is `@Cacheable("REGISTERED_NODES")`. That is the read behind `NodeExplorerService.getNodeMetadata` and `getDocumentation`, so property validation, the node documentation endpoints and the MCP tools all share it. Spring's cache abstraction is enabled in `config.CacheConfig` (`@EnableCaching`); Spring Boot backs it with Caffeine.
  - Configured by `spring.cache.caffeine.spec` in `application.yml`: env `CONTINUUM_NODE_REGISTRY_CACHE_SPEC`, default `maximumSize=10000,expireAfterWrite=30s`. The spec applies to every cache in `spring.cache.cache-names`, so a new cache with different needs gets its own `CacheManagerCustomizer` or spec.
  - The registry is written by `continuum-message-bridge`, another process, so nothing here can evict. A re-registered node takes effect when its entry expires; keep the expiry short.
  - Misses are never cached (`unless = "#result == null"`). A node is found as soon as its worker registers, and lookups of made-up ids (any client can send one) leave nothing in the cache. Keep it that way. The cost is that a repeatedly requested unknown id reaches the database each time; that is a single indexed `LIMIT 1` query.
  - Read failures are not cached.
  - Cached entities are shared between callers. `NodeTreeEntryEntity` is an immutable data class, and `getNodeMetadata` parses a fresh `NodeData` on every call. Keep it that way: never cache a mutable object.
  - Only `findByNodeId` is cached. The tree, search and `findByNodeIdIn` (task queues) read the database every time.
- `WorkflowRunController` / `WorkflowRunService` — execution lifecycle. Starting a run creates a Temporal workflow via the SDK client.
- `NodeOutputController` / `NodeOutputService` — DuckDB reads Parquet from MinIO/S3 for in-browser data preview. DuckDB 1.2.2 is included as a JDBC driver. Outputs are stored under the Temporal **run id** (`{remote}<runId>/<nodeId>/output.<port>.parquet`), which is not the workflow id returned by `POST /api/v1/workflow`. The endpoint is `/api/v1/data/{runId}/nodes/{nodeId}/outputs/{outputId}`; the workbench sends the first segment of the stored path. `NodeOutputRepository` contract:
  - `runId`, `nodeId` and `outputId` must match `^[A-Za-z0-9._-]{1,255}$`, and must not be `.` or contain `..`.
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
- `NodeOutputService.getOutput` checks that the caller owns the run before locating or reading any output, by `workflow_runs.run_id` (`existsByRunIdAndOwnedBy`), never by `workflow_id`. An unowned run and a nonexistent run both throw `ResourceNotFoundException`, which `ApiExceptionHandler` maps to a 404 `ProblemDetail` with the same message, so a tenant cannot probe for other tenants' ids.
- `workflow_runs` indexes (`schema.sql`): every endpoint filters on `owned_by`, and the list endpoints sort newest first, so `ix_workflow_runs_owned_by_created_at (owned_by, created_at DESC)` serves them; `ix_workflow_runs_run_id` serves node output; `ix_workflow_runs_schedule_id` serves schedules. A new owner-scoped query must be served by one of these, or add an index in the same change.
- `workflow_runs.run_id` is the Temporal run id. `WorkflowService.startWorkflow` inserts the PENDING row first (so the owner row exists before any event), then starts the workflow, then records `run_id` from the `WorkflowExecution` that `WorkflowClient.start` returns, with a targeted `updateRunId` query. Never use a second `save()` for this: the message-bridge may already have upserted newer status/outputs, and `save()` would overwrite them. If the start fails, the PENDING row is deleted; if recording the run id fails, the start still succeeds. The id is also carried on `WorkflowUpdate.runId` (set by the orchestration service from `Workflow.getInfo().runId`) and written by the message-bridge upsert, kept via `COALESCE` if a later event has none, which covers schedule-started runs and failures of the api-server write. `schema.sql` adds the column and an index, and backfills older rows from the `{remote}<runId>/` prefix of their stored output paths.
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
- `WorkflowScheduleController` — generic Temporal-`ScheduleClient`-backed cron scheduling for any `ContinuumWorkflowModel` DAG, backed by `workflow_schedules` (JSONB embed, no separate schedule entity per node type). This module has zero KNIME awareness — KNIME-specific scheduling lives entirely in `continuum-knime-scheduler` in the `continuum-feature-knime` repo (`scheduler/`), which calls this controller's `/api/v1/workflow/schedule` API as a plain REST client.
  - Node properties are validated only at `createSchedule`. A schedule's workflow is never revalidated when Temporal fires it; the worker's own check is the only guard at run time.
  - To find schedules that will fail, operators call `POST /api/v1/admin/schedules/validate` (on demand, never at start-up). It runs `service.ScheduleValidationService.validateAll`, which pages through `workflow_schedules` (`PAGE_SIZE` 200, sorted by id) and checks each workflow with `WorkflowPropertiesValidator.findProblems`. It returns a `ScheduleValidationReport` with `invalid`, `unregisteredOnly` and `failedToCheck`.
  - It is report-only: never make it pause, modify or delete schedules, which belong to their owners. One schedule failing to check does not stop the rest.
- `AdminController` (`/api/v1/admin/**`) — operator endpoints that act across all tenants, so they are not owner-scoped.
  - Every endpoint must call `requireAdmin`: at least one entry of the comma-separated `x-continuum-user-groups` header must be in `continuum.core.api-server.admin.groups` (env `CONTINUUM_ADMIN_GROUPS`, also comma-separated). Entries are trimmed, blanks are dropped, and matching is exact and case-sensitive.
  - With no admin groups configured (default) they are disabled. A caller in no admin group gets the same 404 as a disabled endpoint, so they cannot be found by probing.
  - `x-continuum-user-groups` is trusted the same way as `x-continuum-user-id`: the ingress/auth layer must set it from the authenticated identity and strip any client-sent value. If anything can reach the api-server without passing that layer, anyone can claim an admin group.
  - `x-continuum-user-id` is not a group.
  - Any new cross-tenant operation goes here, behind the same check.
- Config defaults: PostgreSQL on `localhost:35432`, MinIO on `localhost:39000`, Temporal on `localhost:7233`.

## Work Guidance

- DDL is managed via `src/main/resources/schema.sql` (applied by `spring.sql.init.mode = always`). JPA `ddl-auto` is `none`.
- Swagger UI available at `/swagger-ui.html` when running locally.
- The `x-continuum-user-id` HTTP header is the owner identity propagated through the system. Ensure it is forwarded when making internal service calls.

## Verification

```bash
./gradlew :continuum-api-server:test
```
