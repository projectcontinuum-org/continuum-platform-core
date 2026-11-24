# Continuum platform HTTP endpoint inventory

Source of truth for the OpenAPI annotations of every platform HTTP service. Each section was written from the
controller, service, validators, exception handlers and models it touches, plus the callers found in the sibling repos
(continuum-workbench, continuum-feature-knime scheduler, continuum-orchestration-service, worker starter,
continuum-system-tests). Behavior that could not be determined from the code is marked **TODO**.

Conventions for the spec itself are in [CONVENTIONS.md](CONVENTIONS.md).

## Cross-cutting facts

- **Identity.** Continuum has no built-in authentication. `x-continuum-user-id` (owner) and `x-continuum-user-groups`
  (comma-separated groups, admin endpoints only) are set by the user-supplied reverse proxy from the authenticated
  identity; the proxy must strip client-sent values. When `x-continuum-user-id` is missing the caller is `anonymous`
  (local docker-compose). The workbench and both embedded UIs never send it themselves; server-side callers (KNIME
  scheduler, worker starter) and the system tests do.
- **How clients reach the services.** The workbench uses the prefixes `/api-server` and `/credentials-manager`
  (Traefik `stripPrefix` in Kubernetes, nginx in docker setups; `ApiConfig.ts`). Embedded UIs derive their base from
  whatever precedes `/ui`.
- **Error bodies before this change.**
  - api-server: `ProblemDetail` (`application/problem+json`) for `InvalidRequestException` (400),
    `WorkflowValidationException` (400, with `errors`/`warnings`) and `ResourceNotFoundException` (404). Other 404/400
    responses built with `ResponseEntity.notFound()/badRequest()` had **empty bodies**. Spring MVC errors used Boot's
    default `/error` JSON.
  - credentials-server and cluster-manager: `{"error": "<message>"}` maps. Clients read `.error`.
- **Error bodies now.** Every error of all three services is a `ProblemDetail`, including the ones Spring raises before
  a controller method is chosen: 404 for an unknown path, 405, 406 (unsupported `Accept`; every operation declares the
  media type it produces) and 415. For that the `@RestControllerAdvice` of each service is not scoped by
  `basePackages`: Spring only applies a scoped advice when a handler method was resolved.
- **Run status.** `workflow_runs.status` is a free string: `PENDING` (inserted by the api-server at start), then the
  value the orchestration service publishes and the message-bridge upserts: `STARTED`, `RUNNING`, `FINISHED`, `FAILED`,
  `CANCELLED`, `TERMINATED` (`ContinuumWorkflow.kt`, `WorkflowStatusReporter.publishUpdate`).
- **Node status** (`ContinuumWorkflowModel.NodeStatus`): ACTIVE, CONFIGURED, SCHEDULED, BUSY, SUCCESS, FAILED,
  CANCELLED, WARNING, PRE_PROCESSING, POST_PROCESSING, SKIPPED. **Edge status**: DELIVERED, BROKEN (null = source not
  finished). **Port data status**: SUCCESS, FAILED, WARNING.
- **Node outputs.** A node's outputs are `Map<portId, PortData>`. A produced port is
  `{status: SUCCESS, contentType: "application/parquet", tableSpec: [{name, contentType}...], data: "{remote}<runId>/<nodeId>/output.<port>.parquet"}`.
  A non-retriable node failure is the port `$error`: `{status: FAILED, contentType: "text/plain", tableSpec: [], data: "<message>"}`.
  A node that set flow variables also has the system key `$variable`: `{status: SUCCESS, contentType: "application/json", tableSpec: [], data: {<name>: <value>, ...}}`,
  holding only the variables that node set (not the inherited ones). It is absent when the node set none. Every key
  starting with `$` is a system key, not a port: clients listing ports must skip them.
  Parquet lives in S3/MinIO at `<bucket-base-path>/<runId>/<nodeId>/output.<port>.parquet`.
- **MQTT.** Every run update goes Kafka → message-bridge → MQTT topic `continuum/workflow/execution/{workflowId}/update`,
  and the same event is upserted into `workflow_runs` (status, progress, `data.workflowSnapshot`, `data.nodeToOutputMap`).

---

## continuum-api-server

Port 8080. Swagger UI `/swagger-ui/index.html` (`/` redirects there). MCP server (Spring AI, streamable HTTP) at `/mcp`
is not part of the OpenAPI spec.

### POST /api/v1/workflow — start a workflow run

- **Purpose.** Starts one execution of a complete workflow (the `.cwf` content authored in the workbench).
- **Callers.** Workbench `WorkflowService.startWorkflow` (reads `workflowId`; on error reads `detail`, then `message`,
  then `error`), system tests.
- **Identity.** User-scoped: the run is owned by `x-continuum-user-id`.
- **Body.** `ContinuumWorkflowModel` (`id`, `name` required; `active`, `nodes`, `edges` default). `name` becomes the
  run's `workflowUri` and the `Continuum:WorkflowFileName` search attribute, so it must be a valid URI
  (`URI.create`). Node `propertiesSchema`, `inputs`, `outputs` from the body are ignored for validation: the registry
  decides.
- **Validation.** Shared `WorkflowValidator`, `RUN` mode: unique node ids, every `data.nodeModel` registered, edge
  endpoints and ports exist, no self or duplicate edges, one edge per input port, no cycles, properties valid against
  the registered schema. Node ids, edge `sourceHandle`/`targetHandle` and the ports of the submitted or registered
  node definition must not start with `$` (reserved for system keys such as `$error` and `$variable`). All errors are
  collected.
- **200** `StartWorkflowResponse {workflowId, warnings[]}`. Warnings never block (nodes without edges, unconnected
  inputs, embedded definition differs from the registry).
- **400** `ProblemDetail` titled "Invalid workflow" with `errors[]` and `warnings[]` (`WorkflowIssue`:
  `message`, optional `node`, `edge`, `property`). Malformed JSON or missing required fields → 400.
- A `name` that is blank or not a valid URI path is one more validation error (400, and an error on validate).
- **500** Temporal unreachable.
- **Side effects.** Inserts a `workflow_runs` row (`PENDING`, `data.workflowSnapshot` = body) **after** validation,
  starts Temporal workflow `IContinuumWorkflow` on `WORKFLOW_TASK_QUEUE` with a random UUID workflow id and the owner in
  the Temporal header, then records `run_id`. A failed start deletes the row. Then Kafka/MQTT/S3 via the run itself.
- **Idempotency.** Not idempotent: every call starts a new run.

### POST /api/v1/workflow/validate — validate a workflow without running it

- **Purpose.** Dry run of the start checks for the editor. No row, no Temporal call.
- **Callers.** None found yet (planned workbench use); intended for the workbench and AI agents.
- **Identity.** Header accepted and passed on, but not used yet (planned partial run). Treated as user-scoped.
- **Body.** Same as start.
- **200** `WorkflowValidationResponse {valid, nodeCount, edgeCount, errors[], warnings[]}`. An invalid workflow is a
  200 with `valid: false`.
- **400** malformed body only. **Idempotent**, no side effects.

### Removed in platform 0.0.23 (no caller left in the workbench or the platform)

`GET /api/v1/workflow/active`, `/workflow/{workflowId}/status`, `/workflow/list`, `/workflow/count`, `/workflow/tree`,
`GET /api/v1/workflow-runs/count`, `GET /api/v1/workflow/schedule/filter` and
`GET /api/v1/node-explorer/nodes/documentation?nodeId=`. Run state comes from `GET /api/v1/workflow-runs` and
`/workflow-runs/{workflowId}`; schedules from `GET /api/v1/workflow/schedule` (optionally `?name=`) and
`/workflow/schedule/{scheduleId}`; node docs from the path form above.

### POST /api/v1/workflow/{workflowId}/cancel and /terminate — stop a run

- **Callers.** Workbench viewer and runs tree (`?reason=`, only checks `ok`), system tests.
- **Identity.** User-scoped; ownership is checked before any signal.
- **Query.** `reason` optional free text.
- **204** accepted. The service signals `notifyCancelling` / `notifyTerminating` immediately, then after a 5 s grace
  period calls Temporal `cancel()` / `terminate(reason ?: "Terminated via API")` from a timer thread.
- **404** unknown, unowned, or already finished (Temporal not-found on the signal).
- **Idempotency.** Repeating on a running workflow re-signals; on a finished one it is a 404. Final run status
  `CANCELLED` / `TERMINATED` arrives over MQTT and in `workflow_runs`.
- **TODO:** a failure of the delayed hard stop is only logged; the caller already got 204.

### GET /api/v1/workflow-runs — page of run summaries from the database

- **Callers.** Workbench runs tree (`page`, `size`, `filter`; reads `content[]` with `workflowId`, `status`,
  `createdAt`, `updatedAt`, and a flat `totalPages`), system tests (`page.*`).
- **Identity.** User-scoped (`owned_by`).
- **Query.** `filter` RSQL over the summary fields (e.g. `workflowUri=="examples/demo.cwf";updatedAt>=2026-11-01T00:00:00Z`),
  `page` (default 0), `size` (default 20), `sort` (RSQL sort, e.g. `createdAt,desc`; the base order is `createdAt` desc).
- **200** Spring Data page (`VIA_DTO`): `{content: WorkflowRunSummary[], page: {size, number, totalElements, totalPages}}`.
  Summary = `workflowId, workflowType, ownedBy, workflowUri, scheduleId?, progressPercentage, status, createdAt,
  updatedAt` (no `data`, no `runId`).
- **400** `Invalid filter` for unparsable RSQL, an unknown field, or a value of the wrong type (same for `sort`).
- The workbench reads the nested `page` through `readRunsPage` (`continuum-workbench`, `src/common/RunsPage.ts`); it used
  to read a top-level `totalPages`, so "Load more" never appeared.

### GET /api/v1/workflow-runs/{workflowId} — one run with snapshot and outputs

- **Callers.** Workbench viewer (`data.workflowSnapshot`, `data.nodeToOutputMap`, `status`, `createdAt`), system tests.
- **Identity.** User-scoped. **404** unknown or unowned (empty body before this change).
- **200** run with `data: {workflowSnapshot: ContinuumWorkflowModel, nodeToOutputMap: {nodeId: {portId: PortData}}}`,
  plus `runId` (Temporal run id; null until the first event) and the summary fields. `workflowSnapshot` has the live
  node/edge statuses from the last event.

### GET /api/v1/workflow-runs/distinct-workflows — workflow files the caller has run

- **Callers.** Workbench runs tree (`content` strings, `totalPages`), system tests.
- **Query.** `filter` RSQL (as above), `page` (0), `size` (50).
- **200** page of distinct `workflowUri` strings, sorted. With a filter the distinct set is computed in memory over all
  matching rows. **400** for a malformed filter (as above).

### POST /api/v1/workflow/schedule — create a cron schedule

- **Callers.** Workbench schedule dialog (response not read), KNIME scheduler (deserializes the full response), system tests.
- **Identity.** User-scoped.
- **Body.** `CreateWorkflowScheduleRequest {name, cronExpression, timeZone?, continuumWorkflowModel}`. The workbench
  uses `name = "<workflow>/<schedule>.schedule"`. `cronExpression` is a Temporal cron string (5 fields, or `@daily`
  style); `timeZone` an IANA zone (default UTC). The workflow is validated exactly like a start
  (`WorkflowRunValidator`, shared with start and validate), including the workflow `name` check: a name that is not a
  valid URI path (e.g. a KNIME schedule name with spaces) is a 400. Before platform 0.0.23 schedule creation skipped
  that check, so runs of such schedules stored a `workflow_uri` `URI.create` rejects; `UriAttributeConverter` reads
  those rows percent-encoded instead of failing the caller's run listing with a 500.
- **201** `WorkflowScheduleResponse` (was 200 before platform 0.0.23; the KNIME scheduler accepts any 2xx).
- **400** invalid workflow (`ProblemDetail` with `errors`/`warnings`); Temporal rejecting the schedule (gRPC
  `INVALID_ARGUMENT`, e.g. bad cron or zone) was an **empty** 400, now `ProblemDetail`. Malformed body → 400.
- **500** any other Temporal failure (unreachable, timeout). The SDK wraps every schedule failure in
  `ScheduleException`; `ApiExceptionHandler` maps it by its gRPC cause, never as a whole to 400.
- **Side effects.** Creates Temporal schedule (id = new UUID) starting `IContinuumWorkflow` with workflow id = schedule
  id and the owner + schedule id in the header, then inserts `workflow_schedules`. Each fire creates runs whose
  `workflow_runs.schedule_id` is set. Not idempotent. The workflow is not revalidated when the schedule fires.

### GET /api/v1/workflow/schedule — list the caller's schedules

- **Query.** `name` optional exact match.
- **200** `WorkflowScheduleResponse[]`; each entry calls Temporal `describe()` for `paused` and `nextRunTimes`.
  **500** a schedule row whose Temporal schedule is missing, or Temporal failing.
  **TODO:** one stale row fails the whole list; skipping or reporting it per entry is a product decision.

### GET /api/v1/workflow/schedule/{scheduleId} — get one schedule

- **Path.** UUID (non-UUID → 400). **200** response; **404** unknown or unowned (empty body before), or a row whose
  Temporal schedule no longer exists (gRPC `NOT_FOUND`).

The single-schedule operations below (pause, unpause, trigger, delete) map a missing Temporal schedule to the same 404,
and any other Temporal failure to 500. A delete that fails in Temporal keeps the row.

### POST /api/v1/workflow/schedule/{scheduleId}/pause | /unpause — pause or resume firing

- **Query.** `note` optional, stored by Temporal. **204**; **404** unknown/unowned. Idempotent.

### POST /api/v1/workflow/schedule/{scheduleId}/trigger — run now

- **204**: Temporal starts one run immediately, outside the cron. **404** unknown/unowned. Not idempotent.
  **TODO:** the workflow id equals the schedule id, so triggering while a previous fired run still runs may be
  rejected or skipped by Temporal's overlap policy (default `SKIP`); not handled.

### DELETE /api/v1/workflow/schedule/{scheduleId} — delete a schedule

- **204**: deletes the Temporal schedule, then the row (`workflow_runs.schedule_id` → NULL). Runs already started
  continue. **404** unknown/unowned; a second delete is 404.

### GET /api/v1/data/{runId}/nodes/{nodeId}/outputs/{outputId} — page of a node output table

- **Callers.** Workbench table view (`page`, `pageSize`; reads `data[]` cells and `totalElements`; 404 = "not
  available"), system tests.
- **Identity.** User-scoped by `workflow_runs.run_id`. Unowned and unknown runs are the same 404 `ProblemDetail`
  ("Workflow run not found").
- **Path.** `runId` = Temporal run id (first segment of `{remote}<runId>/...`), **not** the workflow id. `runId`,
  `nodeId`, `outputId` must match `^[A-Za-z0-9._-]{1,255}$`, not `.`, no `..` (400).
- **Query.** `page` ≥ 0 (default 0), `pageSize` 1..1000 (default 10); violations 400.
- **200** `Page {data: Cell[][], currentPage, currentPageSize, totalPages, totalElements, hasNext, hasPrevious}`; each
  row is a list of `{name, contentType, value}` with `value` base64 of the raw cell bytes (`DataCell.value` bytes).
- **Side effects.** Downloads the Parquet file from S3 into the local cache directory on first access (never evicted
  here), then reads it with DuckDB. Read-only for clients.
- **404** also when the run has no stored output for this node and port (`NoSuchKey` or an S3 404); the partial cache
  file is deleted so a later request downloads again. Other storage failures stay 500.

### GET /api/v1/node-explorer/children — one level of the node palette

- **Callers.** Workbench node explorer, jsonforms playground, system tests.
- **Identity.** Not user-scoped: every caller sees the same registry.
- **Query.** `parentId` = category path (`Analytics/Text`), empty = root. Unknown path → empty list.
- **200** `NodeExplorerTreeItem[]`: categories (`type: CATEGORY`, `hasChildren: true`, `id` = path) first, then nodes
  (`type: NODE`, `id` = node id = fully qualified class name, `nodeInfo` = registered `NodeData`).

### GET /api/v1/node-explorer/search — search registered nodes

- **Query.** `query` required (400 when missing; blank → `[]`). Case-insensitive substring of name, description,
  documentation or node id.
- **200** tree of matches with their category ancestors (`children` filled). Not user-scoped.
- **TODO:** `%` and `_` in `query` act as SQL LIKE wildcards (bound parameter, so no injection).

### GET /api/v1/node-explorer/nodes/{nodeId}/documentation — node docs

- **Callers.** Workbench `NodeExplorerService` (node docs tab), system tests.
- **200** `text/markdown` documentation registered by the worker. **404** unknown node or no documentation (empty
  body before). Not user-scoped. Cached 30 s.

### POST /api/v1/node-explorer/nodes/task-queues — resolve node ids to worker task queues

- **Callers.** Orchestration `InitializeActivity` before dispatching nodes (no user header), system tests.
- **Body.** JSON array of node ids. **200** `{nodeId: taskQueue}`; unknown ids are absent (the orchestrator retries).
  Not user-scoped. Read-only, idempotent.
- **Internal?** Called by the orchestration service, but the workbench and tests use it too and it reveals nothing
  user-specific; kept public.

### POST /api/v1/admin/schedules/validate — check every stored schedule (internal)

- **Callers.** Operators only, through the proxy. Never in the public spec.
- **Identity.** Not owner-scoped (all tenants). Requires one entry of `x-continuum-user-groups` to be in
  `CONTINUUM_ADMIN_GROUPS`; otherwise, and when no admin groups are configured, **404** exactly like an unknown path.
- **Undiscoverable.** For a non-admin the response equals an unknown path's for every method and `Accept`/`Content-Type`:
  `requireAdmin` throws Spring's `NoResourceFoundException` (same body as an unknown path), the mapping declares no
  `produces`/`consumes` (those would answer 406/415 before the check), and a catch-all mapping turns other methods
  into the 404 too. An admin gets the real 405 (`Allow: POST`). Its schemas are pruned from the ungrouped
  `/v3/api-docs`.
- **200** `ScheduleValidationReport {checked, valid, invalid[], unregisteredOnly[], failedToCheck[]}`; results carry
  `scheduleId, name, ownedBy, problems[{nodeId, nodeModel, kind: INVALID_PROPERTIES|UNREGISTERED_NODE, detail}]`.
- **Side effects.** None (report only). Pages through `workflow_schedules` 200 at a time.

---

## continuum-cluster-manager

Port 8080. Embedded React UI at `/ui/` (`/` redirects there).

### GET /api/v1/workbench/tags — available workbench image tags

- **Callers.** Embedded UI. **Identity.** Not user-scoped.
- **200** `DockerHubTag[] {name, lastUpdated (ISO-8601 from Docker Hub, nullable), fullSize (bytes, nullable)}` for
  `WORKBENCH_IMAGE_REPOSITORY`, newest first, up to 100. Cached 5 min; a stale cache is served if Docker Hub fails.
- **502** `{"error": "Failed to fetch data from external service"}` when Docker Hub fails and there is no cache.
  **TODO:** the service wraps `RestClientException` in `RuntimeException`, which today lands in the generic handler
  (500) unless Spring unwraps it; system tests accept 502. Verify.

### GET /api/v1/workbench/variants — overlay variants

- **200** sorted variant names (`<variant>--<resource>.yaml` files in `OVERLAY_PATH`), empty when overlays are
  disabled. Not user-scoped.

### GET /api/v1/workbench/authorize — Traefik ForwardAuth check (internal)

- **Callers.** Traefik `workbench-forward-auth` middleware only. Never in the public spec.
- **Headers.** `x-continuum-user-id` and `X-Forwarded-Host` both **required** (400 when missing).
- **200** the caller owns a RUNNING workbench whose stored `ingressUrl` is `https://<X-Forwarded-Host>`; **403**
  otherwise. Empty bodies. Lookup by ingress URL is cached.
- **Constraint.** `server.forward-headers-strategy: framework` would remove `X-Forwarded-Host` before the controller:
  this path must be excluded from the forwarded-header filter.
- **Any `Accept`.** ForwardAuth forwards the browser's headers (a page load sends `Accept: text/html`), so the mapping
  is `produces = */*`; the class-level `application/json` would make every check a 406.

### POST /api/v1/workbench — create a workbench

- **Callers.** Embedded UI, system tests. **Identity.** User-scoped; `x-continuum-user-id` must be 1–255 characters
  without control characters, quotes or backslashes (400).
- **Body.** `WorkbenchCreateRequest {instanceName, resources?, image?, variant?}`. `resources` fields are Kubernetes
  quantities (`500m`, `2`, `512Mi`, `5Gi`; regex + Fabric8 parse, ≤ 32 chars) with defaults cpuRequest 500m, cpuLimit
  2, memoryRequest 512Mi, memoryLimit 1Gi, storageSize 5Gi; `storageClassName` DNS-1123 subdomain or null. `image`
  must be a valid reference starting with an allowed prefix (`WORKBENCH_ALLOWED_IMAGE_PREFIXES`); default
  `projectcontinuum/continuum-workbench:0.0.5` (**TODO:** this hard-coded default ignores `WORKBENCH_DEFAULT_IMAGE`).
  `variant` must be a listed variant when overlays are enabled.
- **201** `WorkbenchResponse` with status RUNNING.
- **400** validation failure, a missing `instanceName`, or a body that is not JSON.
- **409** an active (RUNNING/SUSPENDED) workbench with the same name already exists ("already exists"; was 400 before
  platform 0.0.23).
- **500** Kubernetes failure (`Kubernetes operation failed: ...`), rendered-manifest verification failure.
- **Side effects.** Creates PVC, Deployment, Service, Ingress (`wb-<instanceId>-*`) in the workbench namespace, rolls
  them back on failure, then inserts the row. AUDIT log lines. Not idempotent.
- A partial `resources` object is accepted: omitted fields take their defaults (the HTTP JSON mapper has the Jackson 3
  Kotlin module).

### GET /api/v1/workbench — list the caller's workbenches

- **200** `WorkbenchResponse[]` (not DELETED/TERMINATING). Status as stored (not refreshed from Kubernetes).

### GET /api/v1/workbench/{instanceName} — workbench status

- **200** `WorkbenchResponse`; the status is refreshed from the Deployment (RUNNING when ≥ 1 ready replica, PENDING
  when not ready, UNKNOWN when the Deployment is missing) and saved if it changed. **404** unknown for this user.
- A SUSPENDED workbench is not refreshed (it has no Deployment on purpose) and stays SUSPENDED, so it can be resumed.

### GET /api/v1/workbench/{instanceName}/liveness — is the pod ready?

- **200** `{ready, status}`: `ready` true only for RUNNING with ≥ 1 ready replica; read-only. **404** unknown.

### PUT /api/v1/workbench/{instanceName}/suspend — stop the pod, keep the volume

- **200** `WorkbenchResponse` (SUSPENDED). Deletes Deployment, Service, Ingress; keeps the PVC. **400** already
  suspended. **404** unknown. **500** Kubernetes failure.

### PUT /api/v1/workbench/{instanceName}/resume — restart a suspended workbench

- **200** (RUNNING, new `ingressUrl`). Re-creates Deployment, Service, Ingress from the stored row. **400** not
  suspended. **404**. **500** (partial resources removed again).

### PUT /api/v1/workbench/{instanceName} — change image or resources

- **Body.** `WorkbenchUpdateRequest {resources?, image?}`; only given fields change, including inside `resources`
  (`WorkbenchResourcesUpdate`: every field optional, an omitted one keeps its current value).
- **200** updated response. **400** invalid image/quantities. **404**. **500** Kubernetes failure (old manifests are
  re-applied). A SUSPENDED workbench only stores the change (no Kubernetes call) and stays suspended; resume applies it.

### DELETE /api/v1/workbench/{instanceName} — delete a workbench and its volume

- **204**. Deletes Deployment, Service, PVC, Ingress by label (data on the PVC is lost), then marks the row DELETED.
  **404** unknown (a second delete is 404). **500** Kubernetes failure (row unchanged).

---

## continuum-credentials-server

Port 8080 (38083 in docker-compose). Embedded React UI at `/ui/` (`/` redirects there). Reached by the workbench under
the `/credentials-manager` prefix. Credential `data` is a flat `string → string` map, encrypted per value with AES-GCM
(master key `CREDENTIALS_ENCRYPTION_MASTER_KEY`) before it is stored, and returned **decrypted** by every read.

Shared facts:
- **Identity.** `/api/v1/credentials/**` is user-scoped by `x-continuum-user-id` (default `anonymous`). Another
  user's credential and an unknown name are the same 404. `/api/v1/credential-types/**` is **not** user-scoped.
- **Reads have a side effect:** every credential returned by a GET (list, by type, by name) gets
  `lastAccessedAt = now` written back, one row update per credential. Reads are therefore not side-effect free.
  **TODO:** list endpoints update every row on each call; probably meant only for `GET /{name}` (the worker read).
- **Data validation.** On create (and on update with `data`), `data` is validated against the credential type's
  `schema` with JSON Schema **draft 4** (networknt). An empty schema `{}` accepts anything.
  **TODO:** draft 4 here vs draft 7 for node properties (`continuum-commons`).
- **Errors before this change:** `{"error": "<message>"}` (`application/json`). Bean-validation errors join all
  field messages: `"name: Name is required; data: Data must not be empty"`. `error` is always a string.
- **Status mapping** (`GlobalExceptionHandler`): `CredentialNotFoundException` and `CredentialTypeNotFoundException`
  → 404; `CredentialAlreadyExistsException` → 409; `CredentialDataValidationException`, `IllegalArgumentException`,
  bean validation, missing header → 400; `OptimisticLockingFailureException` → 409 ("Resource was modified
  concurrently, please retry"); `EncryptionException` → 500 ("Failed to process credential data"); anything else
  → 500 ("Internal server error").
- A missing required field (e.g. `name`) or a non-JSON body is a 400 "Failed to read request": Jackson fails before
  bean validation runs, and the `ResponseEntityExceptionHandler` base maps it (it was a 500 from the catch-all handler).

### POST /api/v1/credentials — create a credential

- **Callers.** Embedded UI. Workers never write.
- **Body.** `CredentialCreateRequest {name, type, typeVersion, data, description?}`: `name` 1-255 chars of
  `[a-zA-Z0-9_-]`, unique per user; `type`, `typeVersion` ≤ 50 chars, must name an existing credential type version;
  `data` non-empty `string → string` map valid against the type's schema; `description` ≤ 1000 chars.
- **201** `CredentialResponse` (with decrypted `data`, `lastAccessedAt` null).
- **400** validation failure, a missing field or a non-JSON body; **404** unknown type or version; **409** name already
  used by this user.
- **Side effects.** Inserts one `credentials` row with encrypted data. Not idempotent (a repeat is 409).

### GET /api/v1/credentials — list the caller's credentials

- **Callers.** Workbench credential picker (reads `name`, `description`), embedded UI.
- **200** `CredentialResponse[]` including decrypted `data`. Updates `lastAccessedAt` of each (see above).

### GET /api/v1/credentials/type/{type} and /type/{type}/{version} — list by type

- **Callers.** Workbench picker (`/type/{type}`, for `options.credentialType`), KNIME scheduler (`GENERIC`).
- **200** array (empty when none; an unknown type is not a 404). Updates `lastAccessedAt`.

### GET /api/v1/credentials/{name} — get one credential

- **Callers.** Worker starter `CredentialFetcherService` from a node's `execute()` (reads `data`), embedded UI.
- **200** with decrypted `data`; **404** unknown for this user. Updates `lastAccessedAt`.

### PUT /api/v1/credentials/{name} — update a credential

- **Body.** `CredentialUpdateRequest`, every field optional; only given fields change. `name` renames (409 if the
  new name is taken); `type`/`typeVersion`/`data` are rechecked against the (new) type's schema; `data` replaces the
  whole map. A null `description` keeps the old one (it cannot be cleared). **TODO:** clearing `description`.
- **200** updated credential; **400**, **404** (credential or type), **409** (name taken, concurrent change).
- **Side effects.** Updates the row, `updatedBy`/`updatedAt`. Idempotent for the same body.

### DELETE /api/v1/credentials/{name} — delete a credential

- **204**; **404** unknown (a second delete is 404). Nodes that reference the name fail on their next run.

### POST /api/v1/credential-types — register a credential type version

- **Callers.** Operators, system tests. Not user-scoped: any caller can add types (**TODO:** restrict like the
  api-server admin endpoints?).
- **Body.** `CredentialTypeCreateRequest {type, schema?, uiSchema?, version?}`: `type` 1-50 chars `[a-zA-Z0-9_-]`;
  `schema` JSON Schema (draft 4) of the `data` map, default `{}`; `uiSchema` JSONForms UI schema for the UI, default
  `{}`; `version` ≤ 50 chars, default `1.0.0`.
- **201** `CredentialTypeResponse`. **400** validation; **409** type + version exists, or the same type with an
  identical schema and uiSchema exists under another version (the id is a UUIDv5 of type + schema + uiSchema).
- **Side effects.** Inserts a `credential_types` row. Types cannot be changed or deleted (no endpoint; DELETE is 405).

### GET /api/v1/credential-types — list every type version

- **200** `CredentialTypeResponse[]`, including the built-in `GENERIC` 1.0.0 (any string values) seeded at start-up.

### GET /api/v1/credential-types/{type} — versions of one type

- **200** non-empty array; **404** unknown type.

### GET /api/v1/credential-types/{type}/{version} — one type version

- **200**; **404** unknown type or version.
