## Purpose

Temporal workflow implementation. Executes workflow DAGs — resolves task queues, dispatches node activities in parallel, tracks completion/failure, streams status events to Kafka. This is a standalone Spring Boot service, not a library.

## Ownership

Core platform team. Container image: `projectcontinuum/continuum-orchestration-service`.

## Local Contracts

- `ContinuumWorkflow` (`@WorkflowImpl` on `WORKFLOW_TASK_QUEUE`) — main orchestration loop. Does not execute node logic; only dispatches activities and aggregates results.
- `InitializeActivity` (`@ActivityImpl` on `ACTIVITY_TASK_QUEUE_INITIALIZE`) — resolves node → task queue mapping by calling `POST /api/v1/node-explorer/nodes/task-queues` on the API server. Retries until all node queues are resolved (workers may start after the workflow).
- DAG execution: `getNextNodesToExecute()` finds nodes whose parents have all produced output on all connected ports. `Async.function {}` starts them concurrently. `Promise.anyOf()` races all pending promises — the loop continues until no nodes remain.
- Node status updates: `setNodeAnimationAndStatus()` marks nodes `BUSY`/`SUCCESS`/`FAILED` and animates/de-animates incoming edges. This mutates the in-memory workflow model used for Kafka events.
- `sendUpdateEvent()` skips publishing during Temporal replay (`WorkflowUnsafe.isReplaying()`).
- Temporal search attributes: `Continuum:ExecutionStatus` (Int) and `Continuum:WorkflowFileName` (Keyword) — must be created in Temporal before use (handled by `temporal-search-attributes-init` Docker service).
- Activity options: `startToCloseTimeout = 60 days`, exponential backoff with `maxAttempts = 500`, `heartbeatTimeout = 5 minutes` per node activity stub.
- Config: `continuum.core.orchestration.api-server-base-url`

## Work Guidance

- Temporal workflow code must be deterministic — no `Date.now()`, `Math.random()`, or I/O. Use `Workflow.getLogger()`, not a static logger. Side effects go in activities.
- `nodeIdToActivityMap` holds per-node activity stubs, each routed to the correct task queue. Do not share a single stub across different queues.
- `nodeErrorsMap` uses the `$error` output port as the sentinel. Any output map containing `"$error"` key means the node failed.
- Workflow is cancelled via `CanceledFailure` (Temporal cancellation or termination). All BUSY nodes are marked CANCELLED, and the event is published.

## Verification

```bash
./gradlew :continuum-orchestration-service:test
```
