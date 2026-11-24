# continuum-platform-core 0.0.23 (proposed)

Changes on `main` (CONTNM-161 through CONTNM-175) since [0.0.22](https://github.com/projectcontinuum-org/continuum-platform-core/releases/tag/release%2F0.0.22) (`release/0.0.22`).

This release is a security and multi-tenancy hardening release. Every workflow, run, schedule and node-output endpoint now checks that the caller owns what it touches, and the places that build shell commands, SQL, Kubernetes manifests, URLs and HTTP headers from user input were fixed so a value can no longer change the meaning of the command. It also removes the `continuum-cloud-gateway` and `continuum-knime-scheduler` modules. `platformVersion` is bumped to `0.0.23`.

## Highlights

- **Tenant isolation on every workflow endpoint.** `/cancel`, `/terminate`, run history and node output are scoped to the `x-continuum-user-id` caller. Another tenant's workflow or an unknown id is a 404 in both cases, so ids cannot be probed.
- **Injection fixes** in the node-output preview (SQL), the workbench Kubernetes manifests, the credential URL, and the KNIME node launcher (shell).
- **Workflow starts and schedule creation now reject unregistered node types with HTTP 400.** Clients must wait for the worker to register its nodes before starting workflows or creating schedules that use them.
- **A workflow can no longer stick in `RUNNING` when it is cancelled right after it starts.**
- **`continuum-cloud-gateway` and `continuum-knime-scheduler` are removed** from this repository.
- **First-class OpenAPI docs** for the api-server, the cluster-manager and the credentials server: typed schemas, real status codes, examples, stable `operationId`s, committed specs in `docs/api/` guarded by CI. Docs are **off in the Helm chart** (`CONTINUUM_API_DOCS_ENABLED=false`).
- **Stale api-server endpoints removed.** Eight endpoints no workbench or platform service calls are gone: `GET /api/v1/workflow/active`, `/workflow/{id}/status`, `/workflow/list`, `/workflow/count`, `/workflow/tree`, `GET /api/v1/workflow-runs/count`, `GET /api/v1/workflow/schedule/filter` and `GET /api/v1/node-explorer/nodes/documentation?nodeId=`. See "Removed endpoints" below.
- **One error contract:** every api-server, cluster-manager and credentials-server error is an RFC 9457 `application/problem+json` body (the cluster-manager and credentials server keep a deprecated `error` property).
- **Flow variables.** A node can set small key/value flow variables (`executionContext.setFlowVariable(...)`), and every node downstream reads them (`executionContext.getFlowVariable<T>(...)`). They travel under a new reserved `$variable` key next to `$error`, so `PortData` and the node model are unchanged. The worker also exports **default variables for every node** (`restNode_3.executionStatus`, `.durationMs`, `.outputCount`, ...), so every workflow now carries flow variables. Node ids and port ids starting with `$` are now reserved and rejected. **Every worker and the orchestration service must be 0.0.23** (see Upgrade notes).

## What's Changed

- CONTNM-161: Security fixes (injection hardening in the api-server, cluster-manager, worker starter and commons)
- CONTNM-161: Scope more APIs to ownership (`/cancel`, `/terminate`, `/active`, node output)
- CONTNM-161: Bump the platform version to 0.0.23
- CONTNM-161: Store the Temporal run id in `workflow_runs`
- CONTNM-161: Check the workflow model before sending a status update (cancel-right-after-start fix)
- CONTNM-161: Create an index on `workflow_runs` by owner
- CONTNM-161: Remove `continuum-cloud-gateway` from the repository
- CONTNM-161: Remove `continuum-knime-scheduler` (it lives in `continuum-feature-knime`)
- CONTNM-169: Explicit `SKIPPED` state for nodes that can never run, and a side-effect-free readiness check
- CONTNM-170: New `SCHEDULED` node status; the worker reports `BUSY` when the activity starts (**breaks workers older than 0.0.23**, see Upgrade notes)
- CONTNM-175: springdoc 3.0.3 (the Spring Boot 4 line), managed in `gradle/libs.versions.toml`
- CONTNM-175: API docs switchable by `CONTINUUM_API_DOCS_ENABLED` (off in the Helm chart), forwarded headers honoured
- CONTNM-175: Admin and ForwardAuth endpoints hidden from the public spec (`public` and `internal` groups)
- CONTNM-175: ProblemDetail errors everywhere, documented status codes
- CONTNM-175: Typed DTOs and schemas for runs, run counts, port data and node outputs
- CONTNM-175: OpenAPI metadata, tags, operationIds, descriptions and examples for every public operation
- CONTNM-175: OpenAPI drift tests, committed specs and the `OpenAPI Spec Check` CI workflow
- CONTNM-175: Client errors that were 500 are now 400/404; schedule create is 201, a duplicate workbench is 409; suspended-workbench fixes
- Flow variables through the `$variable` system key (commons, worker starter, orchestration service); `$` is a reserved prefix for node and port ids
- Default flow variables exported for every node: execution status, error message, start/end timestamps, duration, and input/output row counts per port

## Details

### continuum-api-server: ownership

- Every endpoint of `WorkflowController` reads the owner from the `x-continuum-user-id` header (default `anonymous`, so a local docker-compose setup needs no auth layer). Ownership comes from `workflow_runs.owned_by`.
- `/{id}/cancel` and `/{id}/terminate` return **404** for a workflow the caller does not own, and for an unknown id. The check runs **before** any Temporal signal is sent. Cancel and terminate of an unknown id used to be a silent 204.
- Cancelling or terminating a workflow that has already finished is also a 404: Temporal has no running execution to signal, and the service passes that not-found on instead of answering 204.
- New `ResourceNotFoundException`, mapped to a 404 `ProblemDetail` by `ApiExceptionHandler`.
- A run with no `workflow_runs` row is not visible to any caller. That covers runs created before that table existed, and a scheduled run whose first update has not reached the message bridge yet.

### continuum-api-server: node output

- `GET /api/v1/data/{runId}/nodes/{nodeId}/outputs/{outputId}`: the first path segment is the Temporal **run id**, the first segment of a stored output path (`{remote}<runId>/<nodeId>/output.<port>.parquet`), not the workflow id that `POST /api/v1/workflow` returns. The URL shape is unchanged; the parameter is renamed.
- The caller must own the run (looked up by `workflow_runs.run_id`) before the output is located or read. An unowned and a nonexistent run give the same 404 body.
- `runId`, `nodeId` and `outputId` must match `^[A-Za-z0-9._-]{1,255}$`, and must not be `.` or contain `..`. `page` must be at least 0 and `pageSize` must be in `1..1000`. A violation is a **400** `application/problem+json` (`InvalidRequestException`).
- The Parquet path, `LIMIT` and `OFFSET` are bound parameters (`read_parquet(?)`), never interpolated into SQL. The DuckDB connection has `httpfs` loaded, so an injected path could previously reach the network. The resolved cache file must also stay under the cache directory, and statements and result sets are closed.

### continuum-api-server: node properties

- `POST /api/v1/workflow` and `POST /api/v1/workflow/schedule` validate graph structure and node properties against the registered node definitions, never the `propertiesSchema` sent in the request body, so a client cannot skip checks by sending `{}`. This happens before anything is stored or sent to Temporal. An invalid workflow, or a node whose `nodeModel` is not registered, is a **400** `ProblemDetail`; requests for nodes whose workers have not registered yet are rejected rather than deferred until execution. Before this change the property check was an `assert`, and did nothing.
- Node registry lookups by node id are cached for a short time with Spring's cache abstraction (`@Cacheable("REGISTERED_NODES")` on `NodeTreeEntryRepository.findByNodeId`), backed by Caffeine. This speeds up property validation, the node documentation endpoints and the MCP tools. The setting is `spring.cache.caffeine.spec`, env `CONTINUUM_NODE_REGISTRY_CACHE_SPEC`, default `maximumSize=10000,expireAfterWrite=30s`. A worker that re-registers a node with a new schema or documentation is picked up within the expiry. Lookups that find no node are not cached, so a node is found as soon as its worker registers.

### continuum-api-server: run id and database

- New column `workflow_runs.run_id`, written when the workflow starts and by the message bridge on every event (kept if a later event has none). `schema.sql` adds the column and an index on it, and **backfills existing rows** from the `{remote}<runId>/` prefix of their stored output paths.
- New index `ix_workflow_runs_owned_by_created_at (owned_by, created_at DESC)`. Every endpoint now filters by owner and the list endpoints sort newest first.
- `WorkflowService.startWorkflow` inserts the `PENDING` row first, starts the workflow, then records the run id with a targeted `updateRunId` query (never a second `save()`, which could overwrite newer status written by the bridge). If the start fails, the orphan `PENDING` row is deleted. If recording the run id fails, the start still succeeds.

### continuum-commons

- **Node property validation runs in production.** `ValidationHelper.validateJsonWithSchema` used Kotlin `assert`, which does nothing unless the JVM runs with `-ea`, so properties were never checked. It now throws `PropertiesValidationException`, which carries every `SchemaViolation`.
- **The backend now uses the same JSON Schema rules as the workbench.** JSONForms validates node properties with Ajv 8 (draft 7 by default, with `ajv-formats`). The backend used draft 4. It now defaults to draft 7 and checks `format`, so a value the node dialog accepts is accepted at run time, and the other way round. A schema can still pick its own dialect with `$schema`.
  - Draft 4 has no `const`, so a `oneOf` of `const`s (used by `ClaudeAIAgentNodeModel`) failed for every value. It now works. Draft 7 also adds `if`/`then`/`else`, `contains`, `propertyNames` and numeric `exclusiveMinimum`/`exclusiveMaximum`.
  - A schema that cannot be compiled (a dangling `$ref`, an invalid regex, a draft-4 boolean `exclusiveMinimum`) is reported as one violation with keyword `$schema`, so it is a 400 or a non-retriable node error. Before, it threw.
  - Checked against Ajv with JSONForms' own `createAjv`: the defaults of all 58 feature nodes, every node in the 44 saved `.cwf` and fixture workflows, and 17 edge cases. All 252 cases get the same verdict from both validators. Under draft 4, 14 differed.
- `ProcessNodeModel.run`, `KnimeNodeModel.run` and now also `TriggerNodeModel.run` (which did not validate before) validate before `execute()`, against the node class's own schema (`metadata.propertiesSchema`), not the copy carried in the workflow. A failure is a `NodeRuntimeException` with `isRetriable = false`, so the worker records the node as failed (`$error` output) and Temporal does not retry it.
- `WorkflowUpdate` has a new nullable field `runId` (default `null`, so events from an older orchestration service still deserialize).
- **Removed from the public API:** `model.NodeRepoTreeItem` (only used by the removed worker endpoint) and `ExecutionStatus.fromHistoryEvents` (only used by the removed `/workflow/tree`). The `ExecutionStatus` enum and its values are unchanged.
- **Flow variables.**
  - `IContinuumNodeActivity.NodeOutputSystemPort` has a new key `VARIABLE` (`$variable`), plus `SYSTEM_PORT_PREFIX` (`$`) and `isSystemPort(id)`. Every id starting with `$` is reserved for system keys.
  - `ExecutionContext` has a new constructor parameter `inputFlowVariables: Map<String, Any>` (default empty): the variables inherited from the node's ancestors. Read them with `getFlowVariable(name)`, or converted with `getFlowVariable<T>(name)` / `getFlowVariable(name, type)`, which throws `IllegalArgumentException` when the value cannot be converted. Set the node's own variables with `setFlowVariable(name, value)` / `setFlowVariables(map)`. Values are never null.
  - `inputFlowVariables` is an unmodifiable copy of the map passed in (`Collections.unmodifiableMap(LinkedHashMap(...))`): a node cannot change its inherited variables, even by casting to `MutableMap` (that throws `UnsupportedOperationException`), and later changes to the caller's map are not seen.
  - `ExecutionContext` is no longer a `data class`, so its constructor can store that unmodifiable copy. It keeps every member the data class had, with the same meaning: `copy(...)` (now also with `inputFlowVariables`; the 0.0.22 two-argument `copy` stays in the bytecode, hidden, so binaries built against 0.0.22 still link), `equals`/`hashCode` over `ownerId`, the credential fetcher and `inputFlowVariables`, `toString` and `component1()`. A copy goes through the constructor, so its inherited variables are copied and unmodifiable too. Like the data class copy, it does not carry the output variables set so far, and those are not part of equality.
  - New `FlowVariables.valuesEqual(first, second)`: JSON-value equality that is numeric-aware (`5` and `5L`, `1.0` and `1`, `BigDecimal("2.5")` and `2.50` are equal) and compares maps by keys and values in any order and lists in order. A string is never equal to a number.
  - `ExecutionContext.EMPTY` returns a new instance on every access, so flow variables set through it can never leak from one node into another.
  - New `utils.DefaultFlowVariables` (the default variable names, `prefix(nodeModel, nodeId)` and `build(...)`) and `NodeOutputWriter.getRowCount(portId)` / `OutputPortWriter.getRowCount()`.
  - New `utils.FlowVariables`: `extract(ports)`, `toPortData(variables)` (`PortData` with `contentType = "application/json"` and the map as `data`) and `withoutSystemPorts(ports)`.
  - `TriggerNodeModel` has a new `run(node, nodeOutputWriter, executionContext)` and an overridable `execute(properties, nodeOutputWriter, executionContext)`, so trigger nodes can set initial flow variables and fetch credentials. The two-argument `execute` is now `open` with an empty default, not `abstract`. Existing overrides compile unchanged.
  - `NodeOutputWriter.createOutputPortWriter` refuses a port id starting with `$` (non-retriable `NodeRuntimeException`).
  - `WorkflowValidator` reports an error for a node key starting with `$` (e.g. `$error`, `$variable`), for an edge `fromPort`/`toPort` starting with `$`, and for a registered or (in `RUN` mode) submitted node definition that declares such a port. Existing message texts are unchanged.
  - `PortData` schema mentions `$variable`, and `PortData.data` is now documented as `type: [string, object]` (it was `string`, which was wrong for `$variable`, whose `data` is a JSON object). The serialized JSON is unchanged; generated clients see `data` as a string-or-object union.
- `KnimeNodeModel` starts KNIME with `ProcessBuilder(buildKnimeCommand(...))`, an argument list with no `bash -c`, so a node id or path can no longer be run as a command. `resolveWorkflowDir` rejects a node id that resolves to the KNIME workflow root or outside it. The string-joined `prepareWorkflowInputs` is `@Deprecated` and kept for binary compatibility; use `prepareWorkflowInputOptions`.

### continuum-orchestration-service

- `WorkflowStatusReporter` publishes the Temporal run id (`Workflow.getInfo().runId`) on every event.
- **Cancel-right-after-start fix.** The api-server signals `notifyCancelling` straight after starting a workflow, sometimes while it is still resolving task queues. `publishUpdate` then threw a `NullPointerException` (`currentRunningWorkflow()!!`), which fails the workflow *task*. Temporal retries that task forever, so the run stayed `RUNNING` with no status. It now skips the update when the workflow model is not stored yet; the pinned terminal status is carried by the next update.
- **Nodes that can never run are marked `SKIPPED`.** A node is skipped when it has an unconnected declared input, or an incoming edge that will never carry data: its source is `FAILED` or `SKIPPED`, or finished without producing the connected port. Skipping spreads to all descendants, in workflow node order. Before, these nodes kept status `null`, so the UI could not tell them from pending nodes. Skipped nodes are not errors: they are not in `nodeErrors`, and they do not fail the workflow by themselves. A workflow with a failed node still ends `FAILED`, and independent branches still run. Skipped nodes count as done in `progressPercentage`, so a finished run reaches 100%.
- **Edge status.** Every edge whose source has finished gets `data.status` (new `ContinuumWorkflowModel.EdgeData` / `EdgeStatus`): `DELIVERED` when the source produced the connected port, `BROKEN` when it never will (the source failed or was skipped, or finished without producing the port). No status means the source has not finished. The workbench draws a `BROKEN` edge as a red dashed wire with a cross at the midpoint. The workbench's `NodeStatus` and `EdgeStatus` enums (`@continuum/core`) mirror the Kotlin enums value for value.
- **Flow variables are collected for every node.** `WorkflowDagExecutor.getNodeInputs` walks back through all of a node's ancestors (`FlowVariableMerger`) and merges what each one set: a node's own value overrides what it inherited, so the nearest ancestor wins along a chain. Where branches join, the parent on the lower input port (by the node's declared input order, then `targetHandle`, then source id) wins a key. The same value is merged silently, compared as JSON values (`FlowVariables.valuesEqual`), so a number that comes back from JSON as a different type (`5` vs `5L`, `1.0` vs `1`) is not a false conflict; a different one is logged as a warning with the key, the node and both parent ids. The result goes to the activity under `inputs["$variable"]`, only when it is not empty. `nodeToOutputsMap` (and so the Kafka/MQTT events and `workflow_runs.data.nodeToOutputMap`) holds each node's own `$variable` only. The accumulated input is not size-capped by the orchestrator; Temporal's payload limit is the backstop.
- **New node status `SCHEDULED`.** When the orchestrator dispatches a node it now marks it `SCHEDULED`, not `BUSY`. The worker sends a `BUSY` signal (`ContinuumNodeActivitySignal.nodeStatus`) as soon as the activity starts, before inputs are downloaded. Only `SCHEDULED` → `BUSY` is applied, so a late or retried signal cannot overwrite a finished node. On cancel or failure, `SCHEDULED` nodes are marked `CANCELLED`/`FAILED` like `BUSY` ones.

### continuum-message-bridge

- The `workflow_runs` upsert writes `run_id`, keeps an existing value if a later event has none (`COALESCE`), and the bridge's `schema.sql` adds the column if it starts before the api-server.

### continuum-cluster-manager

- New `WorkbenchInputValidator`. Every value interpolated into a template is checked before rendering and a bad value is a **400** with `{"error": ...}`:
  - the five resource quantities must parse as Kubernetes quantities (at most 32 characters);
  - `storageClassName` and the namespace must be DNS-1123 names;
  - the image must be a valid reference (at most 512 characters);
  - the `x-continuum-user-id` header must be 1 to 256 characters with no control characters, quotes or backslashes.
- **Image allow-list.** A create or update request may only name an image under `WORKBENCH_ALLOWED_IMAGE_PREFIXES` (property `continuum.core.cluster-manager.workbench.allowed-image-prefixes`), which **defaults to the workbench image repository** (`WORKBENCH_IMAGE_REPOSITORY`, `projectcontinuum/continuum-workbench`). An explicitly empty value allows any valid image. The allow-list is not applied to images already stored for existing workbenches, so those keep resuming.
- The templates quote `image`, `imagePullPolicy`, the quantities and the storage class with `?json_string`. After rendering and applying overlays, `applyYaml` verifies the result is exactly the one expected resource (kind, name `wb-{id}-{suffix}`, namespace and labels) and refuses to apply anything else, so a value containing a newline and `---` can no longer inject a second resource.
- Overlay variants must match `^[A-Za-z0-9][A-Za-z0-9._-]{0,62}$` (no `--`), and when overlays are enabled must be in the known list. A path or an unknown name is a 400, not a silent fallback to the base manifest. An overlay file must resolve inside the overlay directory.
- Resume, update and rollback re-validate the stored values, so a tampered row is refused.

### continuum-worker-springboot-starter

- **Removed: the worker's `GET /api/v1/node-repo` endpoint** (`NodeRepositoryController`). Nothing called it; the workbench and every platform service read the node registry through the api-server. To check that a worker's nodes are registered, use the api-server's node explorer, e.g. `GET /api/v1/node-explorer/search?query=<node title>` (the create-new-nodes guide in `continuum-docs` says so).
- **Flow variables.** `inputs["$variable"]` is handed to the node as `ExecutionContext.inputFlowVariables` and is never downloaded from S3 (every `$` key is stripped before inputs are prepared). After the node runs, `outputs["$variable"]` holds the variables the node set itself plus the run's default variables (below). Inherited variables are not passed back; the orchestrator collects them from the ancestors.
  - **Default flow variables, always exported** by `ContinuumNodeActivity`, for successful and failed nodes alike, named `<prefix>.<variable>`. `<prefix>` is the node type in camelCase plus `_` and the node's instance id, e.g. `restNode_3` for node `3` of type `...RestNodeModel` (`DefaultFlowVariables.prefix`). The instance id keeps names unique, so two nodes of the same type never override each other or conflict where branches meet.

    | Variable | Type | Present |
    |---|---|---|
    | `executionStatus` | String | always: `SUCCESS` or `FAILED` |
    | `errorMessage` | String | only when the node failed (the `$error` message) |
    | `startTimestamp` | Long, epoch ms | always: when the activity started, before inputs are downloaded |
    | `endTimestamp` | Long, epoch ms | always: when the activity finished, after outputs are uploaded |
    | `durationMs` | Long | always: `endTimestamp - startTimestamp` |
    | `inputCount` | `{portId: rows}` | process nodes, once their inputs were downloaded (from the Parquet footers) |
    | `outputCount` | `{portId: rows}` | successful nodes: rows written per output port (`NodeOutputWriter.getRowCount`) |

    A failed node therefore returns `$error` **and** `$variable`; downstream nodes are skipped, so its variables are only seen in the run (events, `nodeToOutputMap`, the workbench's Flow Variables tab). A retried activity reports the last attempt's timing. The defaults are not counted against `max-size-bytes`, and they replace a node-set variable of the same name. There is no `statusCode` default: a node that calls an external system already reports that result in its own output or variables.
  - New property `continuum.core.worker.flow-variables.max-size-bytes` (default `65536`): the maximum serialized JSON size of the variables one node sets. Larger, or not serializable to JSON, fails the node with a non-retriable `$error`. Existing `application.yml` files need no change.
  - Trigger nodes now get the same `ExecutionContext` as process nodes (owner, credentials, flow variables).
  - Startup fails with `IllegalStateException` when a node declares an input or output port id starting with `$` (in `inputPorts`/`outputPorts` or `metadata.inputs`/`metadata.outputs`). A node that writes an `output.$<id>.parquet` file directly fails non-retriably.
- `CredentialFetcherService` builds the credential URL with `UriComponentsBuilder.pathSegment(...)`, so a name containing `/`, `?`, `#`, `%` or whitespace stays one path segment. A blank, `.` or `..` name is refused and no request is sent.

### OpenAPI documentation (CONTNM-175)

Applies to `continuum-api-server`, `continuum-cluster-manager` and `continuum-credentials-server` unless noted. Endpoint behavior is unchanged except for the error bodies listed here and the fixes in "Status code and behavior fixes" below; the source for every documented status and field is [`docs/api/ENDPOINT_INVENTORY.md`](../docs/api/ENDPOINT_INVENTORY.md).

- **springdoc 3.0.3** replaces 2.8.6 (the Spring Boot 3 line) in all three services, from the new version catalog `gradle/libs.versions.toml`. springdoc still reads models with Jackson 2, so `com.fasterxml.jackson.module:jackson-module-kotlin` is on the api-server and credentials-server runtime classpath for Kotlin nullability (non-null = `required`). No Jackson version changed.
- **New settings** (all three services; also in the Helm chart):
  - `CONTINUUM_API_DOCS_ENABLED` (default `true`): `/v3/api-docs/**` and `/swagger-ui/**`. **The Helm chart sets `false`**; enable with `--set continuum.apiDocs.enabled=true` where only trusted users reach the services. With docs off, the api-server's `/` no longer redirects to Swagger UI (404).
  - `CONTINUUM_API_DOCS_INTERNAL_ENABLED` (default `false`): serves the `internal` group at `/v3/api-docs/internal` (api-server `/api/v1/admin/**`, cluster-manager `/api/v1/workbench/authorize`). These endpoints are never in `/v3/api-docs` or `/v3/api-docs/public`.
  - `CONTINUUM_API_DOCS_SERVER_URL` (default empty): a fixed server URL for the spec (absolute, or `/`). Empty means it is derived from the request.
- **Forwarded headers.** `X-Forwarded-Prefix`, `-Host` and `-Proto` are now honoured (`server.forward-headers-strategy: framework` in the api-server and credentials-server; an equivalent filter in the cluster-manager that skips `/api/v1/workbench/authorize`, which reads `X-Forwarded-Host` itself). Redirects and Swagger UI keep the proxy prefix (`/api-server`, `/credentials-manager`). The served spec's server URL is absolute (including the proxy prefix) instead of `../`; the committed specs in `docs/api/` use the relative `/`. Make sure the proxy in front of the services is the only source of these headers.
- **Spec groups.** `/v3/api-docs/public` is the public API, which Swagger UI opens first. The ungrouped `/v3/api-docs` has the same public paths.
- **Identity headers** are no longer listed as optional parameters with default `anonymous`. They are described once as security schemes `continuumUserId` (`x-continuum-user-id`) and `continuumUserGroups` (`x-continuum-user-groups`), set by the trusted proxy. The node explorer and the workbench tag and variant lists are documented as not user-scoped. The server behaves as before (the header is still optional, default `anonymous`).
- **`build` keeps the specs current.** In the api-server, cluster-manager and credentials-server, `check` (so `./gradlew build`) depends on the new `generateOpenApiSpec` task (`gradle/openapi-spec.gradle.kts`), which rewrites `docs/api/<module>.openapi.yaml` from the code before `OpenApiSpecTest` runs. A build no longer fails on a stale spec; it leaves a diff in `docs/api/` to review and commit, and the `OpenAPI Spec Check` workflow still fails on that diff. Run one module's with `./gradlew :<module>:generateOpenApiSpec`.
- **Committed specs**: `docs/api/continuum-api-server.openapi.yaml`, `docs/api/continuum-cluster-manager.openapi.yaml` and `docs/api/continuum-credentials-server.openapi.yaml`. Each service's `OpenApiSpecTest` fails when the generated spec differs from the committed one. Regenerate with `./gradlew :<module>:generateOpenApiSpec`. CI (`.github/workflows/openapi-spec-check.yml`) also lints them with Redocly (`redocly.yaml`).
- **operationIds** (client method names) are now stable and descriptive, for example `startWorkflow`, `listWorkflowRuns`, `getNodeOutputPage`, `createWorkbench`. Before, they were the Kotlin method names (`findAll`, `count_1`, ...), so clients generated from the old spec get new method names.
- **Schema names** are unique: `ScheduleValidationProblem` (was `Problem`), `WorkflowNode`/`WorkflowEdge` (was `Node`/`Edge`), `TableCell` (was `CellDto`), and others.
- **`WorkflowRunData.nodeToOutputMap`** is documented as exactly `node id → port id → PortData`. The spec used to add the Kotlin `Map` accessors (`empty`, `entries`, `keys`, `size`, `values`) as properties next to it, which made generated TypeScript clients fail to compile. Responses are unchanged.

#### continuum-api-server: error bodies (client-visible)

Status codes are unchanged except for the fixes listed under "Status code and behavior fixes". Bodies that were empty or Boot's default error JSON are now `application/problem+json`:

- **404** with a `ProblemDetail` body (was an empty body): `POST /api/v1/workflow/{id}/cancel` and `/terminate` (`detail` "Workflow not found"), `GET /api/v1/workflow-runs/{id}` ("Workflow run not found"), `GET /api/v1/workflow/schedule/{id}` and the schedule pause, unpause, trigger and delete ("Schedule not found"; the message no longer contains the owner), the node documentation endpoints ("No documentation for this node").
- **400** with a `ProblemDetail` body (was an empty body): `POST /api/v1/workflow/schedule` when Temporal rejects the schedule (invalid cron or time zone; gRPC `INVALID_ARGUMENT`). Any other Temporal failure on a schedule operation (unreachable, timeout) is a 500, never a 400.
- Spring MVC errors (missing parameter, unparsable path or query value, malformed JSON, unsupported method or media type, unknown path) are a `ProblemDetail` instead of Boot's default `{timestamp, status, error, path}`.
- An unexpected error is a 500 `ProblemDetail` with the generic `detail` "Internal server error" (was Boot's default error body).
- The workbench reads `detail` first, so it shows these messages unchanged.

#### continuum-api-server: response schemas

JSON field names are unchanged; only the documentation changed:

- `GET /api/v1/workflow-runs` items are the `WorkflowRunSummary` DTO and `GET /api/v1/workflow-runs/{id}` is `WorkflowRun` (no longer JPA entities). Same fields. `WorkflowRun.runId` is now documented.
- `data.nodeToOutputMap` is documented as node id → port id → `PortData`; run and node statuses as enums. `TableCell.value` is base64 (`format: byte`).
- Kotlin-defaulted fields are optional in the spec (e.g. `ContinuumWorkflowModel.active`, `nodes`, `edges`; `NodeData.properties`, `propertiesSchema`, `propertiesUISchema`).
- `continuum-commons` has `compileOnly` swagger annotations on the API models. No new runtime dependency for consumers of the library.

#### continuum-cluster-manager: error bodies (client-visible)

- Every error is a `ProblemDetail` (`application/problem+json`) instead of `{"error": "..."}` (`application/json`). Messages are unchanged; status codes too, except a duplicate workbench (400 → 409, see "Status code and behavior fixes").
- The `error` property is kept with the same text as `detail` and is **deprecated**, so the management UI and clients that read `.error` keep working. Read `detail` in new code; `error` will be removed in a later release.
- Spring MVC errors (malformed body, bad parameter, unknown path, unsupported method, `Accept` or content type) also get the `error` property. Their status codes are unchanged; their bodies were Boot's default `{timestamp, status, error, path}` before.

#### continuum-credentials-server: error bodies (client-visible)

- Every error is a `ProblemDetail` (`application/problem+json`) instead of `{"error": "..."}` (`application/json`). Status codes and messages are unchanged: 404 unknown credential or type, 409 duplicate name or type version and concurrent change, 400 validation and schema mismatch, 500 encryption ("Failed to process credential data") and anything unexpected ("Internal server error").
- The `error` property is kept with the same text as `detail` and is **deprecated**. The embedded UI and the workbench's credential picker read `error`, so they keep working.
- Bean-validation failures keep the joined `"field: message; ..."` text in `detail` and `error` and add `fieldErrors` (`[{field, message}]`).
- Spring MVC errors (malformed body, unknown path, unsupported method, `Accept` or content type) are a `ProblemDetail` with `error` too. A create with a missing `name` or a non-JSON body is now a 400 ("Failed to read request"); it was a 500.
- The spec documents the real statuses (201 on create, 204 on delete, 404, 409), marks the identity header as a security scheme, and documents that credential types are not user-scoped and that every read updates `lastAccessedAt`.
- The schemas are named `Credential`, `CredentialType`, `CredentialCreateRequest`, `CredentialUpdateRequest` and `CredentialTypeCreateRequest`. Kotlin-defaulted fields (`description`, and `schema`, `uiSchema`, `version` of a type) are optional.

### Status code and behavior fixes (CONTNM-175, client-visible)

Requests that failed with a 500 because of a client mistake now get the right status, and two status codes follow
HTTP semantics. All are `application/problem+json`.

| Service | Request | Before | Now |
|---------|---------|--------|-----|
| api-server | `filter` (or `sort`) that is not valid RSQL, names an unknown field or has a value of the wrong type, on `GET /api/v1/workflow-runs` and `/distinct-workflows` | 500 | 400 `Invalid filter` |
| api-server | `GET /api/v1/data/{runId}/nodes/{nodeId}/outputs/{outputId}` for an owned run without that stored output | 500 | 404 `Node output not found` |
| api-server | `POST /api/v1/workflow` with a `name` that is blank or not a valid URI path (e.g. `My Workflow.cwf`) | 500 | 400 `Invalid workflow`; `/validate` reports it as an error |
| api-server | `POST /api/v1/workflow/schedule` with a workflow `name` that is not a valid URI path (e.g. a KNIME schedule named `Nightly run`) | 201, then every run listing of the owner failed with 500 | 400 `Invalid workflow`, like a start |
| api-server | schedule get, pause, unpause, trigger, delete for a schedule that exists in the database but no longer in Temporal | 500 | 404 `Schedule not found` |
| api-server | `POST /api/v1/workflow/schedule` success | 200 | **201** |
| cluster-manager | `POST /api/v1/workbench` with a name the caller already uses | 400 | **409** (same `error` text) |
| cluster-manager | `POST /api/v1/workbench` with a partial `resources` object | 500 | accepted: omitted fields take their defaults |
| cluster-manager | `POST /api/v1/workbench` with a missing `instanceName` or a non-JSON body | 500 | 400 |
| credentials-server | `POST /api/v1/credentials` with a missing `name` or a non-JSON body | 500 | 400 |

- **cluster-manager: suspended workbenches.** `GET /api/v1/workbench/{name}` no longer turns a SUSPENDED workbench into
  UNKNOWN (after which it could not be resumed). `PUT /api/v1/workbench/{name}` on a SUSPENDED workbench now only stores
  the change and keeps it suspended; before, it recreated its Deployment, Service and Ingress while the record stayed
  SUSPENDED. Resume applies the stored change.
- **cluster-manager: partial updates.** In `PUT /api/v1/workbench/{name}`, `resources` is now `WorkbenchResourcesUpdate`:
  every field is optional and an omitted one keeps its current value (before, a partial object failed, and a full one
  had to repeat every value).
- **cluster-manager: Kotlin JSON.** The HTTP JSON mapper (Jackson 3) now has the Kotlin module; the `KotlinModule`
  bean was the Jackson 2 one, which Spring Boot 4 never registered. That is what made partial bodies fail.
- **api-server: unreadable run rows.** Runs of schedules created before the name check may have stored a
  `workflow_uri` that `java.net.URI` rejects. They are now read with the illegal characters percent-encoded
  (`Nightly%20run`), instead of failing the owner's `GET /api/v1/workflow-runs` with a 500.
- **api-server: admin endpoint stays undiscoverable.** A caller in no admin group gets exactly the unknown-path 404 for
  any method, `Accept` or content type on `/api/v1/admin/**` (before: 405 for GET and 406 for an unsupported `Accept`
  told it apart). Its 404 body is now the unknown-path one (`detail` "No static resource ..."), not "Not found".
- **api-server: node output cache.** A failed download no longer leaves an empty cache file that later requests would
  try to read.
- **Workbench** (`continuum-workbench`, separate change): the Workflow Runs tree reads the paging numbers from the
  page's nested `page` object (`src/common/RunsPage.ts`). It read a top-level `totalPages`, which the server never
  sends, so "Load more" never appeared for workflows with more runs than one page.
- **Clients to check:** anything that treats only `200` as a successful schedule create, or a `400` as "workbench
  already exists". The KNIME scheduler must percent-encode (or otherwise sanitize) the schedule name it sends as the
  workflow `name`: a name with spaces is now rejected with 400. The workbench and the KNIME scheduler accept any 2xx; the management UI shows the error text for any
  status. `continuum-system-tests` expects the new codes and checks the fixed cases (they were known gaps).

### Removed endpoints (continuum-api-server, client-visible)

These endpoints had no caller left in the workbench, the orchestration service, the KNIME scheduler or the jsonforms playground. Their requests now get the unknown-path 404 `ProblemDetail`. Use the replacement instead:

| Removed | operationId | Use instead |
|---------|-------------|-------------|
| `GET /api/v1/workflow/active` | `listActiveWorkflowIds` | `GET /api/v1/workflow-runs?filter=status==RUNNING` |
| `GET /api/v1/workflow/{workflowId}/status` | `getWorkflowExecutionStatus` | `GET /api/v1/workflow-runs/{workflowId}` (`status`) |
| `GET /api/v1/workflow/list` | `listWorkflowExecutions` | `GET /api/v1/workflow-runs` |
| `GET /api/v1/workflow/count` | `countWorkflowExecutions` | `page.totalElements` of `GET /api/v1/workflow-runs?size=1` |
| `GET /api/v1/workflow/tree` | `getWorkflowExecutionTree` | `GET /api/v1/workflow-runs/distinct-workflows` and `GET /api/v1/workflow-runs` |
| `GET /api/v1/workflow-runs/count` | `countWorkflowRuns` | `page.totalElements` of `GET /api/v1/workflow-runs?size=1&filter=...` |
| `GET /api/v1/workflow/schedule/filter` | `filterWorkflowSchedules` | `GET /api/v1/workflow/schedule` (optionally `?name=`) |
| `GET /api/v1/node-explorer/nodes/documentation?nodeId=` | `getNodeDocumentationByQuery` | `GET /api/v1/node-explorer/nodes/{nodeId}/documentation` |

- Schemas removed from the spec: `WorkflowExecutionStatus`, `WorkflowExecutionCount`, `WorkflowExecutionCountGroup`, `WorkflowTreeItem`, `WorkflowTreeExecution`, `WorkflowRunCount`.
- With them go `TemporalVisibilityQuery` (no endpoint queries Temporal visibility any more), `TreeHelper` and `WorkflowRunRepository.findWorkflowIdsByOwnedBy`. The remaining endpoints behave as before, with one wording change: a bare Temporal gRPC `INVALID_ARGUMENT` is still a 400, but its `detail` is now `Rejected by Temporal: <reason>` instead of `Invalid visibility query: <reason>` (that text described the removed query endpoints).
- **`continuum-system-tests` must be updated together with this release**: its `main` still polls `/status` for a run's final state and checks `/list`, `/active`, `/tree`, `/count`, `/workflow-runs/count`, `/schedule/filter` and the query-parameter documentation. The matching change moves it to `GET /api/v1/workflow-runs/{workflowId}` (a successful run ends `FINISHED`) and drops those checks; run the suites against this release only with it.

### Removed modules

- **`continuum-cloud-gateway`** is removed (module, IntelliJ run config, release matrix entry, manual build checkbox and docs). It only read `workbench_instances`, which the cluster-manager owns, so there is nothing to migrate. Workbenches are reached through their own Ingress, gated by `GET /api/v1/workbench/authorize`.
- **`continuum-knime-scheduler`** is removed. It now lives in `continuum-feature-knime` (`scheduler/`). The `Content-Disposition` fix made here (the download filename is stripped of path components and control characters before it goes in the header) was ported there.

### Tests

- Default flow variables: commons 4 (`DefaultFlowVariablesTest`, `NodeOutputWriter.getRowCount`), worker starter 3 (exported on success and on failure, per-port output counts, not counted against the cap), orchestration 3 (`FakeContinuumNodeActivity.emitDefaultFlowVariables` mirrors the worker: defaults of every ancestor add up down a chain and are stored once per node, a shared ancestor's defaults reach a fan-in node once, a failed node stores its defaults with `$error` and its descendants never run).
- Flow variables: commons 21 (`FlowVariablesTest` including numeric-aware `valuesEqual`, `ExecutionContextTest` including the unmodifiable inherited map, reserved ids in `WorkflowValidatorTest` and `NodeOutputWriterTest`, `TriggerNodeModel` context overload), worker starter 12 (`ContinuumNodeActivityFlowVariablesTest` through a Temporal test workflow, `ContinuumNodeActivityReservedPortTest`), orchestration 16 (`FlowVariableMergerTest`, including no false conflict for `5`/`5L` and `1.0`/`1`, chain, override, grandparent pass-through, fan-in and no-variables workflows in `ContinuumWorkflowTest`).
- About 110 new tests (api-server 53, cluster-manager 43, commons 5, message bridge 3, worker starter 3, orchestration 2): ownership and 404s for every endpoint, hostile-input cases for node output, workbench templates, credentials and the KNIME launcher, a Temporal test-environment test for the cancel-right-after-start race, and the first tests of the message bridge.

## Upgrade notes

- **Breaking: every feature worker and the orchestration service must be 0.0.23.** Every node now exports default flow variables, so every workflow carries `$variable` from its first node on. Upgrade **every** feature worker (`continuum-feature-base`, `-ai`, `-cheminformatics`, `-knime`, and your own) to `continuumPlatformVersion=0.0.23`, and the orchestration service (Helm: `continuum-platform` with `appVersion` 0.0.23). A worker older than 0.0.23 treats `inputs["$variable"]` as a data port and tries to download it from S3, so every node that has an upstream node fails; an older orchestration service never forwards the variables. There is no mixed-version mode. `ExecutionContext(...)` and `TriggerNodeModel` stay source compatible, so rebuilding a worker needs no code change (the rebuild is required by the `SCHEDULED` change below anyway).
- **Breaking: workers built against platform 0.0.22 or older fail every node.** The orchestrator passes the node to the activity with `status = SCHEDULED`. An older `continuum-commons` does not have that enum value, so the worker cannot read the activity input and the node fails after its retries. Rebuild every feature worker (`continuum-feature-base`, `-ai`, `-cheminformatics`, `-knime`, and any of your own) against `continuumPlatformVersion=0.0.23`, and **deploy the workers before the orchestration service**. A 0.0.23 worker also runs against an older orchestrator; that orchestrator just ignores its `BUSY` signal.
- **Node statuses.** Clients that read node status must accept `SCHEDULED` and `SKIPPED`. Runs recorded before this release have `null`, not `SKIPPED`, for nodes that never ran, and no edge `data`; treat `null` and `SKIPPED` the same when showing old runs. The workbench shows both from its CONTNM-170 change.
- **Edges.** `ContinuumWorkflowModel.Edge` has a new optional `data` field. Anything that deserializes workflow models or `WorkflowUpdate` events strictly must accept it.
- **Node output URL.** Clients must address `/api/v1/data/...` by the Temporal run id taken from the stored output path. The workbench already does. Anything that passed the workflow id there now gets a 404.
- **Ownership is enforced.** A client that cancels, terminates or lists workflows must send the same `x-continuum-user-id` it started them with. Unknown ids and other users' ids are now 404 where they were 204 or readable before. Behind a proxy that does not inject the header, every caller is `anonymous`.
- **Workbench image allow-list.** Create and update requests naming an image outside `projectcontinuum/continuum-workbench` are now rejected with 400. Set `WORKBENCH_ALLOWED_IMAGE_PREFIXES` to allow other repositories (an empty value allows any valid image). With the Helm chart, set `continuum.clusterManager.workbench.allowedImagePrefixes` (needs the matching `continuum-charts` change).
- **Database.** The api-server and message bridge add `workflow_runs.run_id` and two indexes on start, and backfill `run_id` for existing runs. No manual step. Rows that cannot be backfilled have no `run_id`, so their node output is not readable until a new event records one.
- **Events.** Anything that deserializes `WorkflowUpdate` strictly must accept the new optional `runId`.
- **Removed library API.** Code built against `continuum-commons` that uses `NodeRepoTreeItem` or `ExecutionStatus.fromHistoryEvents`, or that calls a worker's `/api/v1/node-repo`, must change; none of the Continuum repositories does.
- **`KnimeNodeModel` subclasses.** Prefer `prepareWorkflowInputOptions`; the string version is deprecated.
- **`continuum-cloud-gateway` image.** No new version is published. The `/workbench/<name>/open` proxy path no longer exists in this repository.
- **Not part of this release.** The Jackson dependency advisories are deferred to the Spring Boot upgrade.
- **API docs are off in Kubernetes.** With the matching `continuum-charts` change, `/v3/api-docs` and `/swagger-ui` answer 404. Set `continuum.apiDocs.enabled=true` to get them back where that is acceptable.
- **cluster-manager and credentials-server error bodies** are `application/problem+json` (still with `error`). A client that checks `Content-Type: application/json` on errors must accept `application/problem+json`.
- **Regenerate API clients** from `docs/api/*.openapi.yaml`: operationIds and schema names changed (see above).
- **Status codes:** schedule create is 201 and a duplicate workbench is 409 (see "Status code and behavior fixes").
- **Proxies must own `X-Forwarded-*`.** The services now honour them, so the reverse proxy must set them and strip client-sent values, like the identity headers.
- **Node properties are now validated for real.** Workflows whose saved properties do not match a node's `propertiesSchema` used to run anyway. They are now refused at start or schedule creation (400), and on the worker the node fails without retries. The rules are the workbench's (draft 7), so a node the dialog shows as valid passes. Properties written by hand or by another client can fail: for example, a number sent as a string (`"10"`) to an `integer` field, which `ClaudeAIAgentNodeModel` used to accept. None of the saved example workflows do this.
  - A node schema written for draft 4 that uses boolean `exclusiveMinimum`/`exclusiveMaximum` must declare `"$schema": "http://json-schema.org/draft-04/schema#"`, or switch to numeric values. No current node does.
- **Reserved `$` prefix.** Node ids, port ids and edge handles starting with `$` are now rejected: `POST /api/v1/workflow`, `/workflow/validate` and `/workflow/schedule` report them as validation errors (400 on start and schedule create), and a worker refuses to start if one of its nodes declares such a port. No Continuum node or saved workflow uses one.
- **Bigger run events.** Every node's `nodeToOutputMap` entry (in Kafka/MQTT events and `workflow_runs.data`) now has a `$variable` entry with about eight default variables, and every activity input carries the accumulated defaults of all its ancestors. Expect roughly 0.3 KB per node upstream; very large workflows should watch Temporal's 2 MB payload limit.
- **Viewers of node outputs.** Every node now has an extra `$variable` entry in `nodeToOutputMap`, whose `data` is a JSON object, not a Parquet path. Clients that list output ports must skip keys starting with `$`, as they should for `$error`. The workbench does from 0.0.16 (CONTNM-171): the Node Outputs dialog no longer lists `$` keys as ports, and shows every flow variable, set or inherited, on a new Flow Variables tab. Use workbench 0.0.16 or newer with this release; an older workbench lists `$variable` as a port and fails to load it as a table.
- **Wait for worker registration before starting workflows or creating schedules.** If a workflow references a `nodeModel` that is not yet in the API server's node registry, `POST /api/v1/workflow` and `POST /api/v1/workflow/schedule` now return HTTP 400 instead of accepting it and letting it fail later. Retry after the worker registers its nodes.
- **Schedules created before this release are not re-checked when they fire.** The 400 only applies when a schedule is created. A schedule already in `workflow_schedules` with invalid properties keeps firing: Temporal starts the run, and the invalid node then fails on the worker (non-retriable, no retries), so every run of it fails.
  - To find them, use the new admin endpoint `POST /api/v1/admin/schedules/validate`. It checks every stored schedule against the current node registry and returns the ones that will fail: schedule id, name, owner, node, and the violation. Run it after upgrading, once the workers are up. It is on demand only and does not run at start-up.
  - Only admins can call it. Set `CONTINUUM_ADMIN_GROUPS` on the api-server container to a comma-separated list of admin groups, for example `platform-admins,ops`. A caller is an admin when the comma-separated `x-continuum-user-groups` header contains one of them; matching is exact and case-sensitive. With no admin groups set, the endpoint does not exist (404). A caller in no admin group also gets a 404.
  - **The auth layer in front of the api-server must set `x-continuum-user-groups`** from the authenticated identity and strip any value sent by the client, just as it does for `x-continuum-user-id`. Otherwise anyone can send the header and claim an admin group. Do not set `CONTINUUM_ADMIN_GROUPS` until that is in place.
    ```bash
    # as sent by the auth layer for an admin user
    curl -X POST -H "x-continuum-user-groups: platform-admins" http://<api-server>/api/v1/admin/schedules/validate
    ```
  - It only reports. Nothing is paused, changed or deleted. Fix each reported schedule (delete it and create it again with valid properties), or pause it with `POST /api/v1/workflow/schedule/{id}/pause`.
  - The response lists `invalid` schedules (every run fails), `unregisteredOnly` schedules, and `failedToCheck` ids. `unregisteredOnly` means the only problem is a node type that is not registered: its worker may not be up yet, or the node type was removed. `failedToCheck` means the registry could not be read; call again.

**Full Changelog**: https://github.com/projectcontinuum-org/continuum-platform-core/compare/release/0.0.22...main
