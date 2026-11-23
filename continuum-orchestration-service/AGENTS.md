## Purpose

Temporal workflow implementation. Executes workflow DAGs — resolves task queues, dispatches node activities in parallel, tracks completion/failure, streams status events to Kafka. This is a standalone Spring Boot service, not a library.

## Ownership

Core platform team. Container image: `projectcontinuum/continuum-orchestration-service`.

## Local Contracts

- `ContinuumWorkflow` (`@WorkflowImpl` on `WORKFLOW_TASK_QUEUE`) — main orchestration loop. Does not execute node logic; only dispatches activities and aggregates results.
- `InitializeActivity` (`@ActivityImpl` on `ACTIVITY_TASK_QUEUE_INITIALIZE`) — resolves node → task queue mapping by calling `POST /api/v1/node-explorer/nodes/task-queues` on the API server. Retries until all node queues are resolved (workers may start after the workflow).
- DAG execution: `NodeReadiness` (pure, no mutation) decides per loop iteration. `getReadyNodes()` returns untouched nodes whose every incoming edge delivered its source port. `getNodesToSkip()` returns untouched nodes that can never run: an unconnected declared input, or a broken incoming edge (source `FAILED`/`SKIPPED`, or finished without producing the port), applied transitively. `toleratesBrokenEdge()` is the extension point for branch conditions. `Async.function {}` starts ready nodes concurrently. `Promise.anyOf()` races all pending promises — the loop continues until no nodes remain.
- Node status updates: `setNodeAnimationAndStatus()` marks nodes `SCHEDULED` (on dispatch), `SUCCESS`, `FAILED` or `SKIPPED` and animates/de-animates incoming edges; the worker's activity signal moves `SCHEDULED` → `BUSY`. `setEdgeStatus()` sets `edge.data.status` on every edge whose source has finished: `DELIVERED`, or `BROKEN` when it will never carry data (the workbench draws a broken wire). `NodeReadiness` uses the same commons `EdgeStatus`, with `null` meaning pending. Both mutate the in-memory workflow model used for Kafka events. Skipped nodes count as done in the progress percentage and are never in `nodeErrorsMap`.
- `sendUpdateEvent()` skips publishing during Temporal replay (`WorkflowUnsafe.isReplaying()`).
- `WorkflowStatusReporter.publishUpdate` is also called from signal handlers (`notifyCancelling`, `updateNodeProgressSignal`), which can arrive before `start()` has stored the workflow model: the API server sends `notifyCancelling` right after the start, while task queues are still being resolved. With no model there is nothing to report, so it returns without publishing. Never use `!!` on the model there: a throw fails the workflow TASK, Temporal retries it forever, and the run stays `RUNNING` with no status. `notifyCancelling` pins the terminal status first, so the next real update carries it.
- Temporal search attributes: `Continuum:ExecutionStatus` (Int) and `Continuum:WorkflowFileName` (Keyword) — must be created in Temporal before use (handled by `temporal-search-attributes-init` Docker service).
- Activity options: `startToCloseTimeout = 60 days`, exponential backoff with `maxAttempts = 500`, `heartbeatTimeout = 5 minutes` per node activity stub.
- Config: `continuum.core.orchestration.api-server-base-url`

## Work Guidance

- README: every change here updates the repository root `README.md` (module table, How It Works) in the same change (root `AGENTS.md`, "User Preferences").
- Temporal workflow code must be deterministic — no `Date.now()`, `Math.random()`, or I/O. Use `Workflow.getLogger()`, not a static logger. Side effects go in activities.
- `nodeIdToActivityMap` holds per-node activity stubs, each routed to the correct task queue. Do not share a single stub across different queues.
- `nodeErrorsMap` uses the `$error` output port as the sentinel. Any output map containing `"$error"` key means the node failed.
- Workflow is cancelled via `CanceledFailure` (Temporal cancellation or termination). All SCHEDULED and BUSY nodes are marked CANCELLED, and the event is published.
- Test fixtures in `src/test/resources/test-workflows/` that name a real node model (e.g. `SplitNodeModel`) must carry properties valid against that node's registered schema, not just the copy in the file. `WorkflowPropertiesValidationTest` checks this and covers the invalid-property cases: one attempt, no retry, downstream nodes do not run. To enable that check in other tests, set `FakeContinuumNodeActivity.registeredSchemas`.

## Verification

```bash
./gradlew :continuum-orchestration-service:test
```
