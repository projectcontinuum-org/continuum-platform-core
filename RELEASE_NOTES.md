# continuum-platform-core 0.0.23 (proposed)

Changes on `CONTNM-161` since [0.0.22](https://github.com/projectcontinuum-org/continuum-platform-core/releases/tag/release%2F0.0.22) (`release/0.0.22`).

This release is a security and multi-tenancy hardening release. Every workflow, run, schedule and node-output endpoint now checks that the caller owns what it touches, and the places that build shell commands, SQL, Kubernetes manifests, URLs and HTTP headers from user input were fixed so a value can no longer change the meaning of the command. It also removes the `continuum-cloud-gateway` and `continuum-knime-scheduler` modules. `platformVersion` is bumped to `0.0.23`.

## Highlights

- **Tenant isolation on every workflow endpoint.** `/status`, `/cancel`, `/terminate`, `/active`, `/list`, `/count`, `/tree` and node output are scoped to the `x-continuum-user-id` caller. Another tenant's workflow or an unknown id is a 404 in both cases, so ids cannot be probed.
- **Injection fixes** in the node-output preview (SQL), the Temporal visibility queries, the workbench Kubernetes manifests, the credential URL, and the KNIME node launcher (shell).
- **A workflow can no longer stick in `RUNNING` when it is cancelled right after it starts.**
- **`continuum-cloud-gateway` and `continuum-knime-scheduler` are removed** from this repository.

## What's Changed

- CONTNM-161: Security fixes (injection hardening in the api-server, cluster-manager, worker starter and commons)
- CONTNM-161: Scope more APIs to ownership (`/cancel`, `/terminate`, `/active`, node output)
- CONTNM-161: Bump the platform version to 0.0.23
- CONTNM-161: Store the Temporal run id in `workflow_runs`
- CONTNM-161: Check the workflow model before sending a status update (cancel-right-after-start fix)
- CONTNM-161: Create an index on `workflow_runs` by owner
- CONTNM-161: Remove `continuum-cloud-gateway` from the repository
- CONTNM-161: Remove `continuum-knime-scheduler` (it lives in `continuum-feature-knime`)

## Details

### continuum-api-server: ownership

- Every endpoint of `WorkflowController` reads the owner from the `x-continuum-user-id` header (default `anonymous`, so a local docker-compose setup needs no auth layer). Ownership comes from `workflow_runs.owned_by`.
- `/{id}/cancel`, `/{id}/terminate` and `/{id}/status` return **404** for a workflow the caller does not own, and for an unknown id. The check runs **before** any Temporal signal is sent. Cancel and terminate of an unknown id used to be a silent 204.
- `/list`, `/count`, `/tree` and `/active` are scoped to the caller's own runs. A caller with no runs gets an empty result and Temporal is not queried.
- Cancelling or terminating a workflow that has already finished is also a 404: Temporal has no running execution to signal, and the service passes that not-found on instead of answering 204.
- New `ResourceNotFoundException`, mapped to a 404 `ProblemDetail` by `ApiExceptionHandler`.
- A run with no `workflow_runs` row is not visible to any caller. That covers runs created before that table existed, and a scheduled run whose first update has not reached the message bridge yet.

### continuum-api-server: node output

- `GET /api/v1/data/{runId}/nodes/{nodeId}/outputs/{outputId}`: the first path segment is the Temporal **run id**, the first segment of a stored output path (`{remote}<runId>/<nodeId>/output.<port>.parquet`), not the workflow id that `POST /api/v1/workflow` returns. The URL shape is unchanged; the parameter is renamed.
- The caller must own the run (looked up by `workflow_runs.run_id`) before the output is located or read. An unowned and a nonexistent run give the same 404 body.
- `runId`, `nodeId` and `outputId` must match `^[A-Za-z0-9._-]{1,255}$`, and must not be `.` or contain `..`. `page` must be at least 0 and `pageSize` must be in `1..1000`. A violation is a **400** `application/problem+json` (`InvalidRequestException`).
- The Parquet path, `LIMIT` and `OFFSET` are bound parameters (`read_parquet(?)`), never interpolated into SQL. The DuckDB connection has `httpfs` loaded, so an injected path could previously reach the network. The resolved cache file must also stay under the cache directory, and statements and result sets are closed.

### continuum-api-server: Temporal visibility queries

- New `TemporalVisibilityQuery`. Values are escaped, and a client `query` on `/list`, `/count` and `/tree` is AND-combined with the server's own owner filter as `(<owner filter>) AND (<client query>)`, so it can only narrow the caller's own runs.
- A client query is rejected (400) when it has unbalanced parentheses, an unterminated quote, `;`, `--`, `/*`, `#`, a backslash or a control character. `/count` allows a trailing `GROUP BY ExecutionStatus`.
- The owner filter is `WorkflowId IN (...)` in chunks of 500, with counts and groups merged across chunks.

### continuum-api-server: node properties

- `POST /api/v1/workflow` and `POST /api/v1/workflow/schedule` check every node's properties against its `propertiesSchema` before anything is stored or sent to Temporal. An invalid workflow is a **400** `ProblemDetail` (`InvalidRequestException`) whose detail names each invalid node and its violations. Before this change the check was an `assert`, and did nothing.

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
- `ProcessNodeModel.run`, `KnimeNodeModel.run` and now also `TriggerNodeModel.run` (which did not validate before) validate before `execute()`. A failure is a `NodeRuntimeException` with `isRetriable = false`, so the worker records the node as failed (`$error` output) and Temporal does not retry it.
- `WorkflowUpdate` has a new nullable field `runId` (default `null`, so events from an older orchestration service still deserialize).
- `KnimeNodeModel` starts KNIME with `ProcessBuilder(buildKnimeCommand(...))`, an argument list with no `bash -c`, so a node id or path can no longer be run as a command. `resolveWorkflowDir` rejects a node id that resolves to the KNIME workflow root or outside it. The string-joined `prepareWorkflowInputs` is `@Deprecated` and kept for binary compatibility; use `prepareWorkflowInputOptions`.

### continuum-orchestration-service

- `WorkflowStatusReporter` publishes the Temporal run id (`Workflow.getInfo().runId`) on every event.
- **Cancel-right-after-start fix.** The api-server signals `notifyCancelling` straight after starting a workflow, sometimes while it is still resolving task queues. `publishUpdate` then threw a `NullPointerException` (`currentRunningWorkflow()!!`), which fails the workflow *task*. Temporal retries that task forever, so the run stayed `RUNNING` with no status. It now skips the update when the workflow model is not stored yet; the pinned terminal status is carried by the next update.

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

- `CredentialFetcherService` builds the credential URL with `UriComponentsBuilder.pathSegment(...)`, so a name containing `/`, `?`, `#`, `%` or whitespace stays one path segment. A blank, `.` or `..` name is refused and no request is sent.

### Removed modules

- **`continuum-cloud-gateway`** is removed (module, IntelliJ run config, release matrix entry, manual build checkbox and docs). It only read `workbench_instances`, which the cluster-manager owns, so there is nothing to migrate. Workbenches are reached through their own Ingress, gated by `GET /api/v1/workbench/authorize`.
- **`continuum-knime-scheduler`** is removed. It now lives in `continuum-feature-knime` (`scheduler/`). The `Content-Disposition` fix made here (the download filename is stripped of path components and control characters before it goes in the header) was ported there.

### Tests

- About 110 new tests (api-server 53, cluster-manager 43, commons 5, message bridge 3, worker starter 3, orchestration 2): ownership and 404s for every endpoint, hostile-input cases for node output, visibility queries, workbench templates, credentials and the KNIME launcher, a Temporal test-environment test for the cancel-right-after-start race, and the first tests of the message bridge.

## Upgrade notes

- **Node output URL.** Clients must address `/api/v1/data/...` by the Temporal run id taken from the stored output path. The workbench already does. Anything that passed the workflow id there now gets a 404.
- **Ownership is enforced.** A client that cancels, terminates or lists workflows must send the same `x-continuum-user-id` it started them with. Unknown ids and other users' ids are now 404 where they were 204 or readable before. Behind a proxy that does not inject the header, every caller is `anonymous`.
- **Workbench image allow-list.** Create and update requests naming an image outside `projectcontinuum/continuum-workbench` are now rejected with 400. Set `WORKBENCH_ALLOWED_IMAGE_PREFIXES` to allow other repositories (an empty value allows any valid image). With the Helm chart, set `continuum.clusterManager.workbench.allowedImagePrefixes` (needs the matching `continuum-charts` change).
- **Database.** The api-server and message bridge add `workflow_runs.run_id` and two indexes on start, and backfill `run_id` for existing runs. No manual step. Rows that cannot be backfilled have no `run_id`, so their node output is not readable until a new event records one.
- **Events.** Anything that deserializes `WorkflowUpdate` strictly must accept the new optional `runId`.
- **`KnimeNodeModel` subclasses.** Prefer `prepareWorkflowInputOptions`; the string version is deprecated.
- **`continuum-cloud-gateway` image.** No new version is published. The `/workbench/<name>/open` proxy path no longer exists in this repository.
- **Not part of this release.** The Jackson dependency advisories are deferred to the Spring Boot upgrade.
- **Node properties are now validated for real.** Workflows whose saved properties do not match a node's `propertiesSchema` used to run anyway. They are now refused at start or schedule creation (400), and on the worker the node fails without retries. The rules are the workbench's (draft 7), so a node the dialog shows as valid passes. Properties written by hand or by another client can fail: for example, a number sent as a string (`"10"`) to an `integer` field, which `ClaudeAIAgentNodeModel` used to accept. None of the saved example workflows do this.
  - A node schema written for draft 4 that uses boolean `exclusiveMinimum`/`exclusiveMaximum` must declare `"$schema": "http://json-schema.org/draft-04/schema#"`, or switch to numeric values. No current node does.

**Full Changelog**: https://github.com/projectcontinuum-org/continuum-platform-core/compare/release/0.0.22...CONTNM-161
