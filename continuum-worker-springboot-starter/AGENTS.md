## Purpose

Spring Boot auto-starter consumed by every feature worker. Handles the full node-execution lifecycle: S3 download → node `execute()` → S3 upload → progress reporting → feature registration. Feature workers depend on this; they do not re-implement any of this logic.

## Ownership

Core platform team. Published to Maven Central as `org.projectcontinuum.core:continuum-worker-springboot-starter`.

## Local Contracts

- `ContinuumNodeActivity` — Temporal `@ActivityImpl` on `ACTIVITY_TASK_QUEUE` (overridable via `continuum.core.worker.node-task-queue`). Discovers all `@ContinuumNode` beans at `@PostConstruct`, validates the annotation is present and that no declared port id (`inputPorts`, `outputPorts`, `metadata.inputs`, `metadata.outputs`) starts with `$` (`IllegalStateException`), then creates prototype instances on demand via `ApplicationContext.getBean()`. Processes `ProcessNodeModel` (with inputs) and `TriggerNodeModel` (no inputs) paths; both get the same `ExecutionContext` (owner, credentials, flow variables).
- Flow variables:
  - `inputs["$variable"]` becomes `ExecutionContext.inputFlowVariables`. Every `$` key is stripped before `prepareNodeInputs`, so a system key is never downloaded from S3.
  - `outputs["$variable"]` carries only the variables the node set itself (`getOutputFlowVariables()`), and is omitted when it set none. Never merge the inherited variables back in: the orchestrator collects them from the ancestors.
  - The emitted map is checked against `continuum.core.worker.flow-variables.max-size-bytes` (default 65536, serialized as JSON with the Temporal payload mapper) before outputs are uploaded. Over the cap, or not serializable, is a non-retriable `NodeRuntimeException`.
  - An `output.$<id>.parquet` file is refused (non-retriable).
  - Error outputs still carry only `$error`.
- S3 file naming: inputs as `{runId}/{nodeId}/input.{portId}.parquet`, outputs as `{runId}/{nodeId}/output.{portId}.parquet`. S3 key is prefixed with `cacheBucketBasePath`.
- Progress reporting is rate-limited (`continuum.core.worker.progress-report-rate-limit-ms`, default 5000 ms). Always reports 0%, 100%, and stage changes regardless of rate limit. Progress signals are sent to the parent Temporal workflow via `IContinuumWorkflow.updateNodeProgressSignal()`.
- Background heartbeat scheduler (`continuum.core.worker.heartbeat-interval-ms`, default 60000 ms) keeps long-running activities alive in Temporal even when the node doesn't call `report()`.
- Cancellation: when a heartbeat (background or from `report()`) throws `ActivityCompletionException`, the activity thread is interrupted and the activity rethrows that exception, even if `execute()` swallowed the interrupt or returned normally. Nodes running subprocesses must stop them on `InterruptedException` or on an exception from `report()`. The interrupt is cleared before the worker thread is reused.
- `NodeRuntimeException(isRetriable = false)` causes immediate non-retryable failure: the activity returns an `$error` output with status `FAILED`. Invalid node properties take this path, on the first attempt (the check is in the commons base classes, see `ContinuumNodeActivityValidationTest`). Other exceptions allow Temporal's retry mechanism.
- `CredentialFetcherService` — fetches a named credential on demand via `GET {credentialsServerBaseUrl}/api/v1/credentials/{name}` with `x-continuum-user-id` header. The URL is built with `UriComponentsBuilder.pathSegment(...)` and sent as a `URI`. This encodes `{name}` as a single path segment, so `/`, `?`, `#`, `%` and whitespace in a name cannot change the path or query. Blank, `.` and `..` names are refused (`fetch` returns null and sends no request). `ContinuumNodeActivity` wraps it in a `CredentialFetcher` on `ExecutionContext`, so nodes call `executionContext.getCredential(name)` from `execute()` by whatever name they parsed from their own `properties` — the framework does not scan `propertiesUISchema` to find credentials.
- `FeatureRegistrationPublisher` — publishes node metadata to Kafka at startup so the API server can discover the worker's task queue and node list.

## Work Guidance

- README: every change here updates the repository root `README.md` (module table, worker configuration) in the same change (root `AGENTS.md`, "User Preferences").
- Config properties consumed by workers:
  - `continuum.core.worker.storage.bucket-name`
  - `continuum.core.worker.storage.bucket-base-path`
  - `continuum.core.worker.cache-storage-path`
  - `continuum.core.worker.node-task-queue` (defaults to `ACTIVITY_TASK_QUEUE`)
  - `continuum.core.worker.progress-report-rate-limit-ms`
  - `continuum.core.worker.heartbeat-interval-ms`
  - `continuum.core.worker.credentials-server-base-url` (default `http://localhost:8083`)
  - `continuum.core.worker.flow-variables.max-size-bytes` (default `65536`)
- Activity tests that need the worker's progress signals delivered (every successful node signals 100%) run the activity under a small host workflow in `TestWorkflowEnvironment` (see `ContinuumNodeActivityFlowVariablesTest`). A bare `TestActivityEnvironment` cannot deliver signals, so only failure paths work there.
- The `@ActivityImpl` task queue annotation uses a Spring property expression — the constant in `TaskQueues.ACTIVITY_TASK_QUEUE` is the default, but each worker can override it to deploy on a different queue.

## Verification

```bash
./gradlew :continuum-worker-springboot-starter:test
```
