## Purpose

Spring Boot auto-starter consumed by every feature worker. Handles the full node-execution lifecycle: S3 download → node `execute()` → S3 upload → progress reporting → feature registration. Feature workers depend on this; they do not re-implement any of this logic.

## Ownership

Core platform team. Published to Maven Central as `org.projectcontinuum.core:continuum-worker-springboot-starter`.

## Local Contracts

- `ContinuumNodeActivity` — Temporal `@ActivityImpl` on `ACTIVITY_TASK_QUEUE` (overridable via `continuum.core.worker.node-task-queue`). Discovers all `@ContinuumNode` beans at `@PostConstruct`, validates the annotation is present, then creates prototype instances on demand via `ApplicationContext.getBean()`. Processes `ProcessNodeModel` (with inputs) and `TriggerNodeModel` (no inputs) paths.
- S3 file naming: inputs as `{runId}/{nodeId}/input.{portId}.parquet`, outputs as `{runId}/{nodeId}/output.{portId}.parquet`. S3 key is prefixed with `cacheBucketBasePath`.
- Progress reporting is rate-limited (`continuum.core.worker.progress-report-rate-limit-ms`, default 5000 ms). Always reports 0%, 100%, and stage changes regardless of rate limit. Progress signals are sent to the parent Temporal workflow via `IContinuumWorkflow.updateNodeProgressSignal()`.
- Background heartbeat scheduler (`continuum.core.worker.heartbeat-interval-ms`, default 60000 ms) keeps long-running activities alive in Temporal even when the node doesn't call `report()`.
- `NodeRuntimeException(isRetriable = false)` causes immediate non-retryable failure. Other exceptions allow Temporal's retry mechanism.
- `CredentialResolver` — scans `propertiesUISchema` for controls where `options.format == "credential"`, fetches from credentials server via `GET {credentialsServerBaseUrl}/api/v1/credentials/{name}` with `x-continuum-user-id` header, passes result as `ExecutionContext.credentials`.
- `FeatureRegistrationPublisher` — publishes node metadata to Kafka at startup so the API server can discover the worker's task queue and node list.

## Work Guidance

- Config properties consumed by workers:
  - `continuum.core.worker.storage.bucket-name`
  - `continuum.core.worker.storage.bucket-base-path`
  - `continuum.core.worker.cache-storage-path`
  - `continuum.core.worker.node-task-queue` (defaults to `ACTIVITY_TASK_QUEUE`)
  - `continuum.core.worker.progress-report-rate-limit-ms`
  - `continuum.core.worker.heartbeat-interval-ms`
  - `continuum.core.worker.credentials-server-base-url` (default `http://localhost:8083`)
- The `@ActivityImpl` task queue annotation uses a Spring property expression — the constant in `TaskQueues.ACTIVITY_TASK_QUEUE` is the default, but each worker can override it to deploy on a different queue.

## Verification

```bash
./gradlew :continuum-worker-springboot-starter:test
```
