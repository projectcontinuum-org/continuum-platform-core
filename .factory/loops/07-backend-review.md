# Backend review: loops

## Round 1

Date 2026-11-25. Repo `continuum-platform-core`, branch `CONTNM-180`, uncommitted against `main` (31 tracked files,
about 110 untracked files). Inputs: `03-backend-dev.md` rounds 1-5, `02-plan.md` round 1 plus "Round 2 — full CEL",
`01-requirements.md` Round 3 (D-23..D-38). DOX chain read: root `AGENTS.md`, `continuum-orchestration-service/AGENTS.md`,
`continuum-commons/AGENTS.md`, `continuum-api-server/AGENTS.md`, `continuum-loop-condition/AGENTS.md`.

### What I verified myself

| Check | Result |
|---|---|
| Six-module run `./gradlew --offline :continuum-commons:test :continuum-loop-condition:test :continuum-orchestration-service:test :continuum-api-server:test :continuum-worker-springboot-starter:test :continuum-message-bridge:test --continue` | PASS: 184 / 230 / 153 / 250 / 40 / 3, 0 failures, 0 errors (matches round 5) |
| `runtimeClasspath` of commons, worker-starter, message-bridge | protobuf-java 3.25.5, protobuf-java-util 3.25.5, guava 32.0.1-jre, no `dev.cel` (same as main). knime-base: not re-checked (developer report only) |
| orchestration-service, api-server | protobuf-java 4.36.2, protobuf-java-util 4.36.2 through a constraint (`(c)`), guava 33.7.2-jre. Only `continuum-loop-condition`, orchestration and api-server build files name cel |
| `git diff main -- continuum-commons/build.gradle.kts` | empty |
| Factory-ID grep (`\b(BE\|FE\|AC\|D\|PR\|SCOPE\|P\|Q\|V\|W)-[0-9]+\b`) over modified and untracked files | 0 hits |
| `@McpTool` count in api-server | 6 |
| OpenAPI diff vs main | exactly `NodeData.collapsed`, `WorkflowNode.parentNode` and the `WorkflowNode.type` description |
| `NodeStatus` enum | unchanged |
| Loop-free path | `WorkflowLoops.NONE` and `LoopReadinessGate.NONE` give the same calls as main (one skip pass per `advanceLoops`, the same ready set, the same dispatch instance, the same progress). No new Temporal command for a loop-free run, so no `Workflow.getVersion` is needed |
| `WorkflowValidator` split | read against `git show main:...WorkflowValidator.kt`: node, edge, port and cycle checks, connection warnings, and the order of every message and warning are the same for loop-free input. The round-2 AUTHORING minLength dedupe is the only change, already accepted and release-noted |
| Probes (throw-away jshell scripts in `mktemp -d`, run against the module test classpaths; nothing written to the repo) | see the findings below |

### Findings

#### BR-1 — BLOCKER — BE-14 / BE-12 — the node after a loop loses the carried variables
- **Where:** `continuum-platform-core/continuum-orchestration-service/src/main/kotlin/org/projectcontinuum/core/orchestration/workflow/loop/LoopRunner.kt:55-56` (`carriedByLoopId()` keeps only `ITERATING` loops), used at `.../workflow/WorkflowDagExecutor.kt:189`.
- **What is wrong:** an outside node after the loop inherits through its member parents (`available(member) = inherited(member) + own(member)`). The member's `inherited` contains the carried set only while the loop is `ITERATING`. When `write` becomes ready the loop is already `FINISHED`, so `FlowVariableMerger` rebuilds the entry member's inheritance without the carried set. The stale outside value then comes back.
- **Reproduced:** `seed`(page=1) → L[`fetch`] → `write`. `fetch` sets `{token: t1, page: 2, hasMore: true}`, then `{hasMore: true}`, then `{hasMore: false}`.
  - `fetch` received `{page=1}`, then `{page=2, token=t1, hasMore=true}` twice. That is correct.
  - `write` received `{page=1, hasMore=false}`. It should have received `{page=2, token=t1, hasMore=false}`. `token` is lost, and `page` falls back to the outside value 1.
  - The loop's display `$variable` is `{token=t1, page=2, hasMore=true}`. The workbench "last-iteration view" (shared case file, FE-4) therefore shows variables that differ from what the run passed.
- **Why it matters:** this breaks D-14 / AC-21 ("a node after the loop inherits the last iteration's variables"). It also breaks the shared `loop-carry-cases.json` contract: the case file computes `write` with the carried set applied, and the pure merger passes that, but the runner wiring does not. The paging example breaks whenever the entry member does not set every carried name again each iteration (cursor, token, accumulators).
- **Test gap:** `ContinuumWorkflowLoopTest` › "carried variables reach the next iteration's entry member…" checks `fetch` and the condition, never `write`. The paging test checks only `hasMore`, which the entry member sets itself.
- **Fix:** keep the carried set of a loop that finished after at least one iteration (`carriedIntoIteration` of the last iteration) in `carriedByLoopId()`. Leave out zero-iteration, SKIPPED and FAILED loops, and states dropped by an outer reset. Then add an end-to-end assertion on the node after the loop with a variable that only the first iteration sets.

#### BR-2 — MAJOR — BE-14 / BE-16 — a loop whose members never run spins to `maxIterations` inside one workflow task, and the history guard cannot see it
- **Where:** `.../workflow/loop/LoopRunner.kt:58-66, 114-154`, `.../workflow/loop/LoopIterationProbe.kt:38-54`, `.../workflow/loop/LoopHistoryGuard.kt:14,37`.
- **What is wrong:** when no member is dispatched in an iteration (an empty loop, which the validator only warns about, or members SKIPPED by an unconnected input), `isIterationOver` is true at once. `advance()` then runs every iteration back to back in the same workflow task, one `sideEffect` marker each. `Workflow.getInfo().historyLength` changes only between workflow tasks, so the guard never fires inside that task.
- **Reproduced:**
  - Empty loop, condition always true, `maxIterations` 10,000, `stop-with-warning`: 10,033 events, 10,000 markers, in 6 workflow tasks.
  - Nested: outer loop with 6 iterations around an empty inner loop with 10,000. The workflow task completion exceeded the gRPC limit (`RESOURCE_EXHAUSTED: received message larger than max (10082004 vs. 4194304)`). The server **terminated** the run: no FAILED status is published and no guard message is written.
- **Why it matters:** with no member run, neither the carried set nor the outside variables can change, so the condition gives the same outcome every time and the spin achieves nothing. It costs a large burst of history and can kill the run outright.
- **Fix:** when an iteration ends with no node inside having produced outputs or errors, apply `onLimit` at once, or end the loop with a WARNING ("no node inside the loop ran in iteration k") instead of starting the next iteration. The result is the same as spinning to the limit. Add a test for an empty loop and for an all-skipped loop.

#### BR-3 — MAJOR — BE-15 — a validator-accepted condition can block the workflow thread for more than 1 s and wedge the run in a workflow-task retry loop
- **Where:** `continuum-platform-core/continuum-loop-condition/src/main/kotlin/org/projectcontinuum/core/loopcondition/LoopConditionSizeGuard.kt:16-36`, `CelLoopConditionEvaluator.kt:55-57`, caps in `continuum-commons/.../validation/loop/LoopConditionLimits.kt`.
- **What is wrong:** the size guard caps each value and the iteration budget caps comprehension steps. Neither bounds the work of one unmetered function call, nor that work repeated by a comprehension.
- **Reproduced:**
  - `sets.contains(lists.range(10000), lists.range(10000).reverse())` takes 133 ms, inside every cap.
  - `lists.range(20).all(i, sets.contains(lists.range(10000), lists.range(10000).reverse())) && vars["x"] == 1` is `Valid` and takes 2.1 s per evaluation.
  - Run in a loop through `LoopRunHarness` with the real evaluator, it gives `PotentialDeadlockException: [TMPRL1101] … didn't yield control for over a second` on every attempt. The side effect is not recorded, so each retry evaluates again and the run stays RUNNING.
  - A realistic `sets.intersects(vars["page"], vars["seen"])` over two 10,000-element flow-variable lists takes 319 ms.
- **Why it matters:** D-28 / AC-43 say a condition must not stall a workflow task. The hostile list in `CelLoopConditionEvaluatorRobustnessTest` misses the "comprehension × quadratic function" shape. Any workflow author can wedge a run.
- **Fix:** the evaluation runs inside `Workflow.sideEffect` and its outcome is recorded, so a wall-clock budget there is replay-safe. Check `System.nanoTime()` in `LoopConditionSizeGuard.callback` against a budget well under the detector (for example 200 ms → `LIMIT_EXCEEDED` "the condition ran longer than … ms"). Lowering `MAX_COLLECTION_SIZE` for the `sets.*` arguments helps too. Add the probe condition to the hostile list.

#### BR-4 — MINOR — BE-16 / BE-15 — the loop-start check is not recorded, so "a cel-java change cannot break a replay" is only partly true
- **Where:** `.../workflow/loop/LoopRunner.kt:91-99` (`startCheck.check` and `evaluator().compile` run on the workflow thread outside any side effect), and the claims at `.../workflow/loop/LoopConditionCheck.kt:10-11` and `continuum-orchestration-service/AGENTS.md` (Condition check bullet).
- **Scenario:** a run starts on build A, where the condition is valid, and is replayed on build B, where the upgraded cel-java rejects it, or the reverse. On replay the loop fails at start instead of dispatching members, which is a non-determinism error.
- **Fix:** record the start verdict, or the whole `LoopStartCheck.Result`, with `Workflow.sideEffect` too. Otherwise narrow the documented claim to "condition outcomes".

#### BR-5 — MINOR — house rule 5 — positional arguments at a changed call site
- **Where:** `.../workflow/WorkflowDagExecutor.kt:84`: `getNodeInputs(continuumWorkflow, node, nodeToOutputsMap, loops)`. The call was edited in this change, and the new argument is positional.
- **Fix:** use named arguments.

#### BR-6 — MINOR — stale wording from the subset rounds
- **Where:**
  - `continuum-orchestration-service/build.gradle.kts:52`: "the cel-java parser (and later the evaluator)". The evaluator exists now and there is no parser.
  - `gradle/libs.versions.toml:8`: "loop condition parser and evaluator".
  - `release_notes/RELEASE_NOTES_v0.0.23.md:127`: the orchestration line is still titled "Loops groundwork".
- **Fix:** reword them for the validator and evaluator, and merge the release-note line into "Loop execution".

#### BR-7 — NIT — `CelLiteralChecks` ignores the injected environment
- **Where:** `.../loopcondition/CelLiteralChecks.kt:15` uses `LoopCelEnvironment.shared`, while `CelLoopConditionValidator` takes `environment` as a constructor parameter.
- **Fix:** pass the environment through.

#### BR-8 — NIT — repeated validation on every nested-loop restart
- **Where:** `LoopRunner.tryStart` validates the condition twice (start check, then `compile`, which validates again). This happens every time an outer iteration restarts the inner loop, up to 10,000 × 2 parse and check passes on the workflow thread.
- **Fix:** cache the compiled condition per loop id and text for the run, or let the start check reuse `compile`'s result.

### Checked and fine (no finding)
- **Determinism:**
  - every collection that orders decisions is insertion-ordered (`LinkedHashMap` or `LinkedHashSet`, `PriorityQueue` by `nodes` index);
  - `vars` is a `TreeMap`;
  - `NumberFormat` uses `Locale.ROOT`;
  - the history guard reads `historyLength` / `historySize`, which are replay-stable;
  - the condition outcome goes through `Workflow.sideEffect`, one marker per check;
  - `RecordedConditionOutcome` round-trips through the Jackson and Kotlin converter;
  - replay with a different evaluator is tested.
- **Continue and fail paths:** limit `fail` and `stop-with-warning`, member failure, a nested loop failure that propagates as "node 'I' failed", a broken input that skips the loop, `before` with zero iterations, and non-boolean under both strategies (strategy objects, open/closed).
- **Cancellation:** a BUSY loop node is marked CANCELLED by `markInFlightNodesAs`.
- **CEL:**
  - The validator and the evaluator are built on the same `LoopCelEnvironment.shared`, with the extension set and versions pinned and `bindings` registered at compile time only, which is correct for a macro library.
  - Probes confirmed the cost guards on intermediate values: bytes doubling ends with `LIMIT_EXCEEDED`, and so do a 200,000-character string variable and a 20,000-element list variable.
  - Regex cap 1,000. `@result` and `#unused` are excluded by `ReboundNameRule`.
  - `StackOverflowError` is caught in both the validator and the evaluator.
  - Null values in `vars` work: `vars["x"] == null` is true, and a null `dyn` result is NonBoolean `null`.
- **Registration retry:** policy delays 0/5/10/20/40 s; one daemon thread; `schedule` is skipped after shutdown; `@PreDestroy` calls `shutdownNow`; the sender never throws; the ERROR log names the node and the attempt count.
- **api-server:** `WorkflowRunValidator` and `WorkflowSpecValidator` inject `LoopConditionValidators.default`. The MCP descriptions carry the CEL text, and the tool parameters are unchanged.
- **OpenAPI:** the inventory is updated; `CONVENTIONS.md` needs no change; release notes 0.0.23 (unreleased; the latest tag is `release/0.0.22`) have Highlights, Details and Upgrade notes.
- **Docs:** READMEs (root, new module) and the AGENTS.md chain are updated.
- **House rule 12:** the new units are small: `LoopRunner` 230 lines with 5 dependencies, longest function about 31 lines. `WorkflowValidator` is 73 lines. The executor edit is thin.

### Verdict

**CHANGES REQUESTED**: 1 BLOCKER (BR-1), 2 MAJOR (BR-2, BR-3), 3 MINOR (BR-4..BR-6), 2 NIT (BR-7, BR-8).

## Round 2

Date 2026-11-25. Re-review of `03-backend-dev.md` "Round 6 — fix round 1" in `continuum-platform-core`, branch
`CONTNM-180` (uncommitted). Scope: BR-1, BR-2, BR-3 and F-1 (`05-backend-system-tests.md`). Read in full:
`LoopRunState`, `LoopRunner`, `LoopIterationProbe`, `LoopStatusPublisher`, `LoopCarriedVariables`, `LoopConditionCheck`,
`ContinuumWorkflow`, `LoopConditionTimeBudget`, `EvaluationLimitExceeded`, `LoopConditionSizeGuard`,
`CelLoopConditionEvaluator`, `CelEvaluationFailures`, `LoopConditionLimits`, and the new tests. DOX chain re-read: root,
orchestration-service and loop-condition `AGENTS.md`.

User decisions are not re-raised: an all-skipped loop is SKIPPED, an empty loop is WARNING, and the budget is 200 ms.

### What I verified myself

| Check | Result |
|---|---|
| `./gradlew --offline :continuum-commons:test :continuum-loop-condition:test :continuum-orchestration-service:test :continuum-api-server:test --continue --rerun-tasks` | PASS: 184 / 234 / 162 / 250, 0 failures, 0 errors, 0 skipped. Worker-starter 40 and message-bridge 3 pass too (up to date, unchanged). Matches the developer's numbers |
| BR-1 probe re-run (`seed`(page=1) → L[`fetch`] → `write`) | `write` inherited `{page=2, hasMore=false, token=t1}` (was `{page=1, hasMore=false}`). Loop `$variable` `{hasMore=true, page=2, token=t1}`, the same set `fetch` got in iteration 3 |
| BR-2 probe re-run, loop around an unfed member, 10,000, always true | 33 events, 0 markers. L SKIPPED "No node inside the loop could run in iteration 1" (was 10,033 events and 10,000 markers) |
| BR-2 nested shape (outer 6 iterations with a member, empty inner loop at 10,000) | 99 events, 6 markers, both loops WARNING, `write` SUCCESS, run FINISHED. Before the fix this ended in `RESOURCE_EXHAUSTED` and a terminated run |
| New probe: an all-skipped inner loop inside an outer loop with a member (outer 3 iterations) | 56 events, 3 markers. `a` ran 3 times, inner SKIPPED in each outer iteration, the outer loop ended WARNING at its limit. The node depending on the skipped member and the node after are SKIPPED, run FINISHED. No spin |
| BR-3 probes re-run | The 20× `sets.contains` macro: LIMIT_EXCEEDED after 263 ms (was 2.1 s). 5× in `all`: 262 ms. `sets.intersects` over two 10,000-element string variables: LIMIT_EXCEEDED after 288 ms. Chains of 4 to 30 `&&` / `\|\|` calls: all stop at 200 to 300 ms |
| F-1 replay claim | Temporal 1.35.0 sources, `WorkflowStateMachines.validateCommand`: `COMMAND_TYPE_UPSERT_WORKFLOW_SEARCH_ATTRIBUTES` sits in the no-attribute-check group, so only the event type is matched. Probe on a loop-free run where `seed → a` and `a` fails: I took the recorded history, rewrote the final upsert from `WORKFLOW_EXECUTION_FAILED` (3) to the old `COMPLETED` (1), and replayed it with `WorkflowReplayer`. It replayed OK, and so did the original. The claim holds |
| F-1 loop-free behaviour | Same command sequence (STARTED upsert, DAG, one final upsert). Only the upserted value and the Kafka status string change, and only when `nodeErrorsMap` is non-empty. FAILED is published exactly when `start()` then throws `WorkflowExecutionFailed`. A pinned CANCELLED/TERMINATED still overrides it as before |
| F-1 consumers | The message bridge stores the string as is (`WorkflowExecutionSnapshotToDbSyncHandler`). No api-server code branches on it. `Continuum:ExecutionStatus` is read nowhere in main code. The workbench already treats `FAILED` as terminal with an error icon (`WorkflowViewer.tsx:34,91`, `WorkflowRunsTreeWidget.tsx:17`) |
| OpenAPI | The spec diff vs main adds exactly the run `status` description (`WorkflowRunSummary`, `WorkflowRun`) to the round-1 fields. Enum, example and operationIds are unchanged. The inventory's cross-cutting run-status fact is updated. `CONVENTIONS.md` needs no change |
| Release notes 0.0.23 (unreleased: no 0.0.23 tag; `platformVersion=0.0.23`) | Orchestration bullet for failed runs, the evaluator time-budget text, the loop execution empty and all-skipped semantics, the carried-variable text (10 cases), the api-server description line, and the Upgrade note "Failed runs are `FAILED` in the run record" |
| Case file pin | `shasum -a 256 loop-carry-cases.json` = `f419100f…79a66`, the same as `CASES_SHA256` |
| House rule 13 (factory-ID grep over every modified and untracked file) | 0 hits |
| House rule 12 | `LoopRunner` 266 lines, 5 constructor dependencies, longest function `tryStart` about 35 lines. The new behaviour sits in `LoopIterationProbe` (two pure questions), `LoopStatusPublisher.skipped`, `LoopRunState.passesCarriedVariablesOn`, `LoopConditionTimeBudget` and `CombinedEvaluationListener`. `publishCompletion` is 8 lines. Fine |

### Status of the round-1 findings

- **BR-1 (BLOCKER) — RESOLVED.**
  - The rule: `passesCarriedVariablesOn` is true for ITERATING, and for FINISHED with SUCCESS or WARNING after `iteration > 0`. It leaves out zero-iteration, FAILED and SKIPPED loops. A nested loop's set is still dropped with its state by `dropNestedLoopStates`.
  - My probe now gives the expected `write` input.
  - The new end-to-end test asserts `write` and the display `$variable` against the last iteration's `fetch` input. The new case `node-after-finished-loop-gets-last-iteration-carried-set` is in the shared file.
  - The outer loop's carried set is not polluted: `LoopCarriedVariables.afterIteration` folds only members' own variables, and the condition variables add only the outer loop's own set.
- **BR-2 (MAJOR) — RESOLVED.**
  - `hasNodeToRun` is checked after the start check and before `compile`. An empty loop, or one holding only empty loops, gets WARNING with no side effect.
  - `anyNodeRanInIteration` is checked after the failed-member check. All skipped gives SKIPPED with no condition check, and the gate's `failedOrSkippedLoopIds` skips the nodes after the loop.
  - Only iteration 1 can be all-skipped. The entry members' readiness depends only on the outside edges, which do not change between iterations, so the shortcut can never hide a later iteration.
  - All four reproductions end at once (see the table).
- **BR-3 (MAJOR) — RESOLVED for the reported shape (a macro repeating an expensive call).** The budget stops it at about 260 ms, the outcome is recorded, there is no `WORKFLOW_TASK_FAILED`, and the history replays (the new replay test). The remaining part, one uninterruptible call, is the new BR-9 below. The developer's note "with 200 ms the worst case stays well under 1 s" does not hold.
- **F-1 — RESOLVED.** The code and the replay claim are both verified (see the table).
- **BR-4..BR-8 — unchanged, still open** (MINOR/NIT; not touched in this round, as stated).

### New findings

#### BR-9 — MAJOR — BR-3 follow-up / AC-43 — one valid `sets.*` call over flow variables within the worker cap blocks the workflow thread for about 0.8 to 1.3 s, past the budget; under CPU pressure this is the same retry wedge as BR-3
- **Where:**
  - `continuum-platform-core/continuum-loop-condition/src/main/kotlin/org/projectcontinuum/core/loopcondition/LoopConditionTimeBudget.kt:33-38`: the clock is read only when cel-java calls back after a value.
  - `CelLoopConditionEvaluator.kt:61-62`.
  - Wrong claims:
    - `continuum-commons/.../validation/loop/LoopConditionLimits.kt:38-45` ("well under Temporal's one-second deadlock threshold even when the last function call before the check runs a few hundred milliseconds");
    - `release_notes/RELEASE_NOTES_v0.0.23.md:113` ("well before Temporal's one-second deadlock detector");
    - `continuum-loop-condition/README.md:42`.
- **What is wrong:**
  - The budget cannot stop a function call that is already running. `sets.equivalent`, `sets.contains` and `sets.intersects` are O(n·m) with CEL equality.
  - The arguments are capped only at 10,000 elements (`MAX_COLLECTION_SIZE`). The worker caps each node's variables at 64 KiB, but two members can each set one list.
- **Reproduced:** throw-away jshell scripts in `mktemp -d` against the module classpaths; no repo writes.
  - `sets.equivalent(vars["a"], vars["b"])`, `a` and `b` = 9,000 one-element lists (61,897 JSON bytes each, under the 64 KiB worker cap): 1,314 ms for one evaluation. Nested two deep, 7,402 elements (65,515 bytes): 1,192 ms. 10,000 maps `{id, v}` with `sets.contains(m, m)`: 1,020 ms. Every result is LIMIT_EXCEEDED, but only after the call returned.
  - In a real Temporal test workflow (`LoopRunHarness`, real evaluator, two members each setting one list, timed inside the side effect), on an idle 18-core M5 Pro:
    - one evaluation took 750 to 935 ms;
    - with an `&&` prefix call it took 930 ms;
    - the run ended cleanly (loop FAILED).
  - The same workflow with CPU-bound threads equal to the core count: 995 ms, then `PotentialDeadlockException [TMPRL1101] … didn't yield control for over a second`. One `WORKFLOW_TASK_FAILED`; the retry got through.
  - With 3× oversubscription, every attempt took 2.6 to 3.0 s and failed with TMPRL1101 (8 in the log). The side effect is never recorded, so every retry evaluates again. The run did not finish within the harness's 30 s wait: the BR-3 wedge (run stays RUNNING).
  - At n = 3,000 the same call takes 119 ms and at n = 2,000 72 ms. The cost is quadratic in the argument size.
- **Why it matters:**
  - D-28 / AC-43 say a condition must never stall a workflow task.
  - The orchestrator's chart requests 500m CPU (limit 2), and the detector measures wall-clock time. A loop over two "seen ids" lists, a common paging pattern, comes within about 10 % of the detector's threshold on a fast idle machine. On a throttled pod or a slower vCPU it passes the threshold, and then it wedges the run on every retry.
  - The docs and the KDoc promise that this cannot happen.
- **Suggested fix (one of):**
  - (a) Run the evaluation inside the side effect on a dedicated daemon executor and wait with `Future.get(budget + margin)`. On timeout, record LIMIT_EXCEEDED and abandon the call; the listener stops it at its next value. The workflow thread then never blocks for longer than about 250 ms, whatever one call costs. The result is still recorded, so it stays replay-safe.
  - (b) Replace the `sets` extension functions with metered O(n) versions (hash on the converted values), or cap the size of `sets.*` arguments, for example at 2,000, through a dedicated check.
  - (c) Drop `sets` from `LoopCelEnvironment`: the feature is unreleased, but this needs the user's agreement because it narrows the CEL surface.
  - With any of them:
    - add the one-call shape (two 9,000-element list variables, `sets.equivalent`) to `CelLoopConditionEvaluatorRobustnessTest` and `cel-conditions.json`;
    - correct the three over-claims.
  - If the user prefers to accept the risk, at least correct the docs and record the limit in the Upgrade notes.

### Notes (not findings)
- Other repositories, for the orchestrator to route:
  - `continuum-system-tests/suites/api/loops.js:629-642`: `knownGap('run-with-a-failed-node-is-recorded-FINISHED', …)` now passes silently, and its README/AGENTS still list it. It should become a hard `check` for `FAILED` (system tester).
  - The workbench's generated `workflow-run.ts` / `workflow-run-summary.ts` doc comment still has the old status text; it changes on the next client regeneration (FE).
  - The workbench `FlowVariables.ts` must apply the finished loop's carried set for nodes after the loop (new case, SHA `f419100f…`) (FE-4).
- `CelLoopConditionEvaluatorRobustnessTest` asserts `< 800 ms` for a check measured at about 290 ms. That margin is fine on CI.

### Verdict

**CHANGES REQUESTED**:
- BR-1, BR-2 and F-1 are RESOLVED, and BR-3 is RESOLVED for the reported shape.
- New: 1 MAJOR (BR-9).
- BR-4..BR-8 (3 MINOR, 2 NIT) are unchanged and still open.

## Round 3

Date 2026-11-26. Re-review of `03-backend-dev.md` "Round 7 — fix round 2" in `continuum-platform-core`, branch
`CONTNM-180` (uncommitted). Scope: BR-9 (the user chose a timeout through an executor), the new concurrency code, and
regressions of BR-1/BR-2/BR-3/F-1. Read in full: `TimeBoxedConditionEvaluationRunner`, `ConditionEvaluationRunner`,
`LoopConditionCheck`, `LoopConditionEvaluators`, `LoopConditionEngineConfig`, `EvaluationInterruptCheck`,
`CelLoopConditionEvaluator`, `LoopConditionTimeBudget`, `LoopConditionLimits`, the new and changed tests, and the changed
docs. I also read the Temporal 1.35.0 sources: `SyncWorkflowContext.sideEffect`, `SideEffectStateMachine`,
`WorkflowStateMachines`, and the Spring autoconfigure lifecycle. DOX chain re-read: root, orchestration-service,
loop-condition and commons `AGENTS.md`. Since round 2 this round changed only loop-condition and loop-package files,
`LoopConditionEngineConfig`, commons limits and KDoc, and docs. `WorkflowDagExecutor`, `ContinuumWorkflow` and
`WorkflowLoops` were not touched.

User decisions are not re-raised: the timeout runs through an executor, the budget is 200 ms, an all-skipped loop is
SKIPPED and an empty loop is WARNING.

### What I verified myself

| Check | Result |
|---|---|
| `./gradlew --offline :continuum-commons:test :continuum-loop-condition:test :continuum-orchestration-service:test :continuum-api-server:test :continuum-worker-springboot-starter:test :continuum-message-bridge:test --continue --rerun-tasks` | PASS: 184 / 239 / 171 / 250 / 40 / 3, 0 failures, 0 errors, 0 skipped. Matches the developer's numbers |
| BR-9 at unit level: `LoopConditionCheck` with the production runner and the real evaluator, two 9,000-element one-element-list variables, 3 runs per shape | Idle: `sets.equivalent` 253 to 267 ms, `sets.contains` 254 to 257 ms, LIMIT_EXCEEDED with the budget text; a fast condition 1 to 4 ms. With 54 CPU-bound threads on 18 cores (3× oversubscription): `sets.equivalent` 253 to 260 ms, `sets.contains` 251 to 260 ms, fast 0 to 1 ms. In round 2 the same calls took 750 ms to 3.0 s |
| BR-9 end to end, the round-2 wedge shape (`LoopRunHarness`, real evaluator, production runner, Temporal's default 1 s detector, two members each setting one list), under 3× oversubscription | Nested 7,402 elements: the run ended in 468 ms, 0 `WORKFLOW_TASK_FAILED`, 1 marker, L FAILED with "took longer than 200 ms to evaluate", `WorkflowReplayer` OK. 9,000 one-element lists, twice: 341 ms and 285 ms, the same result. One WARN per check with the loop, iteration, workflow and run id, and no value. In round 2 every attempt failed with TMPRL1101 and the run stayed RUNNING |
| Replay safety (Temporal 1.35.0 source) | `SideEffectStateMachine.createMarkerCommand` calls `func` only when `replaying` is false. A replay takes the payload from the `MARKER_RECORDED` event. `SyncWorkflowContext.sideEffect` wraps any exception from `func` as `new Error(e)`, which fails the workflow task, not the workflow. `Workflow.getInfo()` is allowed in read-only mode (the WARN lines carry the real ids) |
| Shutdown path, probe: a closed runner installed while a run reaches its check, then a live runner installed | "Workflow task processing failure … caused by ConditionEvaluationUnavailable", no marker, run still open. With the live runner installed, the retried task succeeded: L SUCCESS, exactly 1 marker, and the replay is OK. No non-determinism and no recorded failure. Spring stops `temporalWorkerFactory` (destroyMethod `shutdown`, so polling stops), so a closing context cannot keep polling into a closed runner. This is a single retry, not a loop |
| Interrupt hygiene | `FutureTask.cancel(true)` plus `handlePossibleCancellationInterrupt`, and `ThreadPoolExecutor.runWorker` clears the flag before each task, so an interrupt never leaks into the next evaluation. A cancelled queued task never runs its callable (state check in `FutureTask.run`) |
| Thread safety of the shared evaluator | `LoopConditionSizeGuard` and `EvaluationInterruptCheck` are stateless objects. `LoopConditionTimeBudget` and `CombinedEvaluationListener` are new for each evaluation. `CelRuntime.Program` is immutable. The variable map comes from `endOfIterationVariables` (a new map; nothing writes to flow-variable values later), and `execute` gives the happens-before |
| BR-1 probe re-run (`seed`(page=1) → L[`fetch`] → `write`) | `write` inherited `{page=2, hasMore=false, token=t1}`, the loop's `$variable` is `{hasMore=true, page=2, token=t1}`. Unchanged from round 2 |
| BR-2 probes re-run | Unfed member, 10,000, always true: 33 events, 0 markers, L SKIPPED. Nested with an empty inner loop: 99 events, 6 markers, O/I WARNING, run FINISHED. All-skipped inner loop: 56 events, 3 markers, inner SKIPPED, run FINISHED. Unchanged |
| Loop-free runs | No file on the loop-free path changed in this round. The runner is called only from `LoopConditionCheck`. The only global effect is 4 idle, prestarted daemon threads per orchestrator |
| House rule 13 (factory-ID grep over all 154 modified and untracked files) | 0 hits |
| House rule 12 | `TimeBoxedConditionEvaluationRunner` 190 lines, longest function about 22 lines, no injected dependencies besides two function parameters. `LoopConditionCheck` 111, `LoopRunner` 271 (one call site changed). The seam (`ConditionEvaluationRunner`, `DIRECT`) is clean. The `ThreadPoolExecutor`, `Thread` and SLF4J calls with positional arguments are Java APIs, where Kotlin cannot use named arguments. Fine |

### Status of earlier findings

- **BR-9 (MAJOR) — RESOLVED.** The workflow thread waits 250 to 270 ms per check whatever one call costs, idle or at 3× CPU
  oversubscription. The outcome is recorded once, the history replays, and the three over-claims are corrected
  (`LoopConditionLimits` KDoc, release notes 0.0.23 line 113 plus the new line 130, loop-condition README limits
  paragraph and table, `LoopNode.doc.md`, AGENTS chain). The new tests would fail without the change. The developer's
  "failing first" note matches the round-2 wedge I measured.
- **BR-1, BR-2, BR-3, F-1 — still RESOLVED** (probes re-run, see the table; F-1 code not touched in this round).
- **BR-4..BR-8 — unchanged, still open** (MINOR/NIT). For example, `WorkflowDagExecutor.kt:84` still has the positional
  `getNodeInputs(...)`.

### New findings

#### BR-10 — MAJOR — BR-9 follow-up — the "all threads busy" outcome is recorded, so one user's slow conditions permanently fail other users' loops
- **Where:**
  - `continuum-orchestration-service/src/main/kotlin/org/projectcontinuum/core/orchestration/workflow/loop/TimeBoxedConditionEvaluationRunner.kt:107-111` (a rejected check returns `allThreadsBusy()`);
  - `TimeBoxedConditionEvaluationRunner.kt:150-154` (a queued check that has not started returns `allThreadsBusy()`);
  - both returns go through `LoopConditionCheck.kt:89-95` into the side-effect marker.
  - Docs: `release_notes/RELEASE_NOTES_v0.0.23.md:130`, `continuum-orchestration-service/AGENTS.md:35`.
  - The developer's residual-limit note (03, Round 7, "SCOPE / questions") claims "This only affects runs whose condition is itself that slow, and those fail anyway". That claim is wrong.
- **What is wrong:**
  - The 4 threads are shared by every workflow run on the orchestrator, whoever the user.
  - Each abandoned `sets.*` call keeps its thread for about 0.7 to 1.3 s (up to about 3 s under CPU pressure).
  - While 4 such calls overlap, every other check gets a LIMIT_EXCEEDED that is written into its history, so that loop fails for good. The cause is the orchestrator's load, not the condition.
- **Reproduced:**
  - Setup: one Temporal test environment and the production runner. 4 concurrent runs with `sets.equivalent(vars["a"], vars["b"])` over two 9,000-element lists. Then, 400 ms later, unrelated runs one after another, each with a one-member loop whose condition is `vars["x"] == 2` (normally SUCCESS after one iteration).
  - Idle machine: the first unrelated run FAILED with WARN "… did not get an evaluation thread within 250 ms: all 4 are busy", and the next 3 succeeded.
  - Under 3× oversubscription: all 5 unrelated runs FAILED, over about 1.2 s.
  - Each failure is a recorded outcome, so it replays as failed and cannot be retried away.
- **Why it matters:**
  - The runs and their timing are within the caps, so anyone can do this: 4 concurrent runs (or one run with 4 parallel loops) over two "seen ids" lists.
  - Other users' loops then fail with a message about the orchestrator's threads. Their runs end `FAILED` because of someone else's workflow.
  - That is cross-tenant interference with a permanent result, and the docs present the busy case as harmless.
  - The fix is local.
- **Suggested fix:**
  - A missing thread is about orchestrator capacity, not about the condition. Treat it like the shutdown case: throw `ConditionEvaluationUnavailable` from both busy paths and record nothing. Temporal then retries the workflow task, by which time an abandoned call has returned (each frees its thread within about 1 s). The two paths are the rejected check and the check that did not start within the wait.
  - Keep the recorded LIMIT_EXCEEDED only for a check that *started* and ran past the wait, which says something about the condition itself.
  - Optional hardening: allow at most one abandoned evaluation per workflow run (or per user), so one run cannot hold several threads.
  - Change `TimeBoxedConditionEvaluationRunnerTest` (the queued and the rejected check expect `ConditionEvaluationUnavailable`). Add a Temporal-level test: two stuck evaluations on a 2-thread runner and an unrelated fast loop. The fast loop must finish SUCCESS once the threads free up, with no LIMIT_EXCEEDED in its history.
  - Update release notes line 130 and the orchestration AGENTS bullet.

#### BR-11 — MAJOR — AC-43 — the per-check bound does not bound a workflow task: many slow checks in one task still pass the 10 s workflow-task timeout and wedge the run
- **Where:**
  - `.../workflow/WorkflowDagExecutor.kt:122-154`: one workflow task works through every completion that has already arrived and runs each loop's condition check in turn.
  - `.../workflow/loop/LoopRunner.kt:187-192`.
  - `TimeBoxedConditionEvaluationRunner.kt:127-148`: each check waits up to 250 ms.
  - The api-server starts runs with Temporal's default workflow-task timeout of 10 s (`continuum-api-server/.../service/WorkflowService.kt:67-76`, no `setWorkflowTaskTimeout`).
- **What is wrong:**
  - The workflow thread yields at each side effect (`result.get()`), so the deadlock detector never fires.
  - The task's total time still grows by up to 250 ms per slow check, and nothing caps it.
  - At about 40 checks that hit the wait in one task, the task passes 10 s and times out. The markers are never recorded, and every retry evaluates all the checks again.
- **Reproduced:** one source feeding N loops with one member each, all with a slow but valid condition. Production runner, idle machine, default 10 s task timeout.
  - `sets.equivalent` over two 9,000-element lists (set once by the source): N = 24 gave a 6.3 s task, N = 30 a 7.9 s task. Each finished with N markers and 0 task failures.
  - The round-1 macro `lists.range(20).all(i, sets.contains(lists.range(10000), lists.range(10000).reverse())) && vars["x"] == 1`, N = 48: after 45 s the run was still RUNNING, with `WORKFLOW_TASK_TIMED_OUT` ×4, 0 markers and 139 slow-check WARNs (the same checks evaluated again on each retry).
  - Control, the same 48-loop workflow with `vars["x"] == 2`: finished, 48 markers, 6 tasks.
- **Why it matters:**
  - This is the BR-3/BR-9 wedge in another shape: a workflow that passes validation stays RUNNING for ever and holds a workflow executor thread for 10 s on every retry.
  - The shape is unusual (tens of parallel loops whose members finish within one task, each with a slow condition), but a user can build it on purpose.
  - With the uninterruptible `sets.*` shape, each retry also abandons evaluation threads again and again. That keeps BR-10 going for other runs (UNCONFIRMED: I measured the retry wedge with the macro shape, which stops cooperatively).
- **Suggested fix (one of):**
  - (a) A per-workflow-task budget in `LoopConditionCheck`, kept per run, read and updated only inside the side-effect function, so nothing changes on replay.
    - Key the current task by `Workflow.getInfo().historyLength`, which stays the same within a task.
    - Add up the wall time the checks spend in that task.
    - Once the total passes a few seconds (for example 3 s), record LIMIT_EXCEEDED "too many slow condition checks at once" for further checks in that task, without evaluating them.
  - (b) Let the DAG loop start a new workflow task after a slow check, for example with a short timer. This changes the command sequence of loop runs, which is acceptable while the feature is unreleased.
  - (c) At minimum, set an explicit, larger workflow-task timeout when starting runs, and document the limit. This only moves the threshold.
  - With any of them, add a Temporal test with about 48 parallel slow loops that asserts 0 `WORKFLOW_TASK_TIMED_OUT` and a finished run.

#### BR-12 — MINOR — no Temporal-level test of the shutdown path
- **Where:** `TimeBoxedConditionEvaluationRunnerTest` covers only the runner (`close()` gives `ConditionEvaluationUnavailable`). Nothing shows that the exception leaves no marker, causes a workflow-task retry, and lets the run finish once a live runner is installed.
- **Why it matters:** the "a shutdown never fails a user's loop" claim relies on `SyncWorkflowContext.sideEffect` wrapping the exception as an `Error`. A refactor that catches the exception in `LoopConditionCheck.evaluate` (for example a broader `catch`) would silently record a failure. My probe confirms the current behaviour.
- **Fix:** add the probe as a test: install a closed runner, start a loop run, assert no marker, install a live runner, assert SUCCESS, 1 marker, and that the replay is OK. BR-10's fix needs the same test shape.

#### BR-13 — NIT — stale "workflow thread" wording and a racy label
- `continuum-loop-condition/src/main/kotlin/org/projectcontinuum/core/loopcondition/EvaluationLimitExceeded.kt:6`: "thrown on the workflow thread". In production it is thrown on an evaluation thread.
- `continuum-commons/.../validation/loop/LoopConditionLimits.kt:4`: "A condition check blocks the orchestrator's workflow thread, which Temporal's deadlock detector stops after about one second". It is now a bounded wait; reword it to match lines 47-51.
- `TimeBoxedConditionEvaluationRunner.kt:134-137`: `started` is read after `cancel(true)`. A check that a thread picks up between the timeout and the cancel is reported as "did not finish within 250 ms" although it barely ran. This affects the log text only, and the outcome text is the same either way.

### Assessment of the developer's open item
An abandoned cel-java call holds its thread until it returns (about 1 s; it cannot be interrupted inside the call). As
a bound on resources this is acceptable: at most 4 abandoned calls, the 10,000-element and 100,000-character caps bound
each call, and the daemon threads never block shutdown. Recording "busy" as the condition's outcome is **not**
acceptable. That part is BR-10 (MAJOR).

### Verdict

**CHANGES REQUESTED**:
- BR-9 RESOLVED; BR-1, BR-2, BR-3 and F-1 still RESOLVED; loop-free runs unchanged.
- New: 2 MAJOR (BR-10, BR-11), 1 MINOR (BR-12), 1 NIT (BR-13).
- BR-4..BR-8 (3 MINOR, 2 NIT) are unchanged and still open.

## Round 4

Date 2026-11-26. Final verification of `03-backend-dev.md` "Round 8 — fix round 3" and its "Round 8 addendum" in
`continuum-platform-core`, branch `CONTNM-180` (uncommitted). Read in full: `TimeBoxedConditionEvaluationRunner` (219
lines), `EvaluationTicket` (new, 35), `ConditionEvaluationRunner`, `LoopConditionCheck` (unchanged since round 3),
`ContinuumWorkflowLoopEvaluationPoolTest` (new), `TimeBoxedConditionEvaluationRunnerTest`, `LoopRunHarness`, the BR-13
KDoc (`EvaluationLimitExceeded`, `LoopConditionLimits`), release notes 0.0.23 lines 113/130/317-319, `LoopNode.doc.md`,
and the orchestration and commons `AGENTS.md`. Temporal 1.35.0 sources: `ReplayWorkflowTaskHandler.failureToWFTResult`
and `WorkflowWorker.handleTask`. Files changed since round 3: only the runner/ticket/seam KDoc, the three test files,
the BR-13 KDoc and docs. `WorkflowDagExecutor`, `ContinuumWorkflow`, `FlowVariableMerger` and `LoopRunner` have not
changed since round 3.

User decisions are not re-raised: BR-11 is documented only, the own-run rule is removed and the self-wedge is a
documented limit, the timeout runs through an executor, and the budget is 200 ms.

### What I verified myself

| Check | Result |
|---|---|
| `./gradlew --offline :continuum-commons:test :continuum-loop-condition:test :continuum-orchestration-service:test :continuum-api-server:test :continuum-worker-springboot-starter:test :continuum-message-bridge:test --continue --rerun-tasks` | PASS: 184 / 239 / 174 / 250 / 40 / 3, 0 failures, 0 errors, 0 skipped. Matches the developer's numbers. The `docs/api/*.openapi.yaml` hashes are unchanged |
| Round-3 cross-run probe re-run. Setup: real `CelLoopConditionEvaluator`, production runner, Temporal test env with 10 s task timeout. 4 concurrent runs with `sets.equivalent(vars["a"], vars["b"])` over two 9,000-element lists, then 400 ms later 5 unrelated runs one after another (one-member loop, condition `has(vars.neverSet)`, normally SUCCESS after 1 iteration) | The 4 slow runs FAILED with the budget text: 1 marker each, 0 task retries (BR-9 unchanged). **Victim 1:** WARN "did not get an evaluation thread within 250 ms: all 4 are busy; nothing is recorded and the workflow task is retried"; then SUCCESS "Finished after 1 iterations", 1 marker, 1 `WORKFLOW_TASK_TIMED_OUT` (the test server numbered the retry attempt 2), 10.07 s, `WorkflowReplayer` OK. **Victims 2-5:** SUCCESS in 9 to 13 ms, 0 retries, replay OK. 0 recorded "busy" outcomes. In round 3 the same probe FAILED the first victim (idle) or all 5 (3× oversubscription) for good. **BR-10 is fixed: the unrelated run is delayed, not failed** |
| Retry pacing and history growth on a **real server**. Setup: local `temporalio/auto-setup:1.26.2`, SDK 1.35.0, own random task queue, a side effect that throws for 45 s and then for 200 s (the shape of a check that gets no thread), 10 s task timeout | Side-effect calls at +0, +0.03, +10.0, +20.1, +34.3, +53.3, +82.0, +128.3, +205.9 s. So after one immediate retry the server waits at least the task timeout, and then backs off further (intervals 10, 10, 14, 19, 29, 46, 78 s). History while it fails: 9 events, unchanged for 200 s, with one `WORKFLOW_TASK_FAILED` and no `WORKFLOW_TASK_TIMED_OUT` (later attempts are transient). After success: 14 events (`WORKFLOW_TASK_SCHEDULED attempt=9`, started, completed, 1 marker). **History growth on a real server is bounded and constant, about 3 events per stuck check however long it waits.** The SDK side matches the source: `failureToWFTResult` responds `RespondWorkflowTaskFailed` only when `getAttempt() == 1` and rethrows otherwise (`ReplayWorkflowTaskHandler.java:291-305`) |
| "≤ 4 abandoned calls; queued checks withdrawn" | True by construction. The fixed pool has 4 threads. A queued check loses the `tryWithdraw` CAS only if a thread already won `tryStart`. The withdrawn check is `cancel(false)` + `executor.remove`, and even if it is dequeued later, `evaluateUnlessWithdrawn` returns null without evaluating. The runner test asserts no 3rd start and an empty queue |
| Self-wedge combined with other runs (scaled: an interrupt-ignoring call held 1.9 s against a 2 s task timeout, about the 9.4 s / 10 s ratio of the literal shape). One run with 5 parallel stuck loops, then 12 unrelated runs spread over one cycle | The wedged run stays open: 0 markers, 3 task retries, 10 stuck calls started (it re-abandons 4 threads on each retry, as documented). **All 12 unrelated runs finished SUCCESS**: 8 at once, 4 after 1 to 3 retries (up to 4.3 s scaled). Delayed, not failed |
| Determinism and replay | Every retry path throws `ConditionEvaluationUnavailable` before any marker. It leaves the side effect wrapped as `Error` ("SideEffect: failure executing CREATED->SCHEDULE"), which fails the task and not the workflow. No new commands, no `getVersion` needed. Every probe history and both new Temporal tests replay OK. The WARN uses a static logger only inside the side-effect function, which runs only when not replaying; it repeats once per real retry, as intended |
| `EvaluationTicket` race | `tryStart` and `tryWithdraw` both CAS from `WAITING`, so exactly one wins. `runningMillis()` is read before the withdraw. The only interleavings are: the thread wins between the read and the CAS, or `startedAtNanos` is not yet written after a won CAS. Both read 0 ms → "started too late" → `ConditionEvaluationUnavailable`, the conservative side. LIMIT_EXCEEDED is returned only for `!withdrawn && runningMillis >= budgetMillis` (`TimeBoxedConditionEvaluationRunner.kt:165`). The BR-13 label race is gone |
| BR-12 test (`ContinuumWorkflowLoopEvaluationPoolTest`, second test) | Uses the production runner, closed, for the first call, and a live one for the retry. Asserts SUCCESS, exactly 1 marker, ≥ 1 retry, 2 runner calls, and replay. A `catch` inside `LoopConditionCheck` would record a FAILED outcome and break the SUCCESS and marker assertions. Sound. The 2 s task timeout and the attempt-2 explanation in the `LoopRunHarness` KDoc match what I saw on the test server |
| Docs | Shared pool: release notes Upgrade note line 317, `LoopNode.doc.md` "Keep conditions cheap", orchestration AGENTS "Bounded wait". BR-11: release notes line 319 with the measured numbers, doc.md line 121, AGENTS. Self-wedge: release notes line 318, doc.md line 117, AGENTS, runner KDoc. Grep over `*.kt`/`*.md`: no "all 4 condition evaluation threads of the orchestrator are busy" and no "of this run that did not finish" anywhere. The remaining "are busy" strings are WARN log text only (`TimeBoxedConditionEvaluationRunner.kt:112,170`), never `$error`. The "When a run fails" list in `LoopNode.doc.md` has no busy item. Release notes are in 0.0.23 (latest tag `release/0.0.22`) |
| Regressions | BR-1, BR-2, BR-3 and F-1 code paths have not changed since round 3, and their tests pass. BR-9: the 4 slow probe runs and `ContinuumWorkflowLoopReplayTest` (500 ms detector) still fail at the budget with 0 task failures. Loop-free runs: the runner is reached only from `LoopConditionCheck`, so they are unchanged |
| House rule 12 | Runner 219 lines, longest function about 20 lines; `EvaluationTicket` is one concern in 35 lines; no new dependencies. Fine |
| House rule 13 (factory-ID grep over all 156 modified and untracked files) | 0 hits |

### Status of earlier findings

- **BR-10 (MAJOR) — RESOLVED.** Both busy paths, and the new late-start path, throw `ConditionEvaluationUnavailable`
  and record nothing. LIMIT_EXCEEDED is recorded only for a check that started and ran ≥ 200 ms. The cross-run probe
  passes with the real evaluator. The test that failed first now passes.
- **BR-11 (MAJOR) — documented, as the user decided** (release notes line 319, `LoopNode.doc.md`, AGENTS). Not re-raised.
- **BR-12 (MINOR) — RESOLVED** (see the table).
- **BR-13 (NIT) — RESOLVED.** The `EvaluationLimitExceeded` and `LoopConditionLimits` headers are corrected, and the
  label race is removed by `EvaluationTicket`.
- **BR-1, BR-2, BR-3, BR-9, F-1 — still RESOLVED.**
- **BR-4..BR-8 — unchanged, still open** (3 MINOR, 2 NIT).

### New findings

#### BR-14 — MINOR — BR-10 docs — two facts the measurements show are missing from the known-limit and pacing text
- **Where:**
  - `release_notes/RELEASE_NOTES_v0.0.23.md:130` and `:317`, and `continuum-orchestration-service/AGENTS.md:35` ("a retried check runs again at most once per workflow-task timeout");
  - the self-wedge entries: release notes `:318`, `LoopNode.doc.md:117-120`, AGENTS `:35`.
- **What is wrong:**
  - (a) On a real server the first retry is immediate (+33 ms), and only later attempts wait ≥ 10 s. The server's backoff then grows: 14, 19, 29, 46, 78 s. A check that still finds no thread waits longer each time, not "once per 10 s".
  - (b) The self-wedge entries say only that the run itself stays `RUNNING`. They do not say that every retry takes all 4 threads again, for up to about 9 s each with the literal shape. Until that run is terminated, it keeps delaying every other user's loop checks. That is cross-tenant: in my scaled probe 4 of 12 unrelated runs were delayed by up to 3 retries. They still finished.
- **Why it matters:**
  - An operator who reads "stays RUNNING, terminate it" does not learn that the run also slows down other tenants' loops.
  - An operator who reads "once per 10 s" will underestimate how long a delayed loop can wait under sustained load.
- **Fix:**
  - In the self-wedge entries, add: "until it is terminated, each retry holds all 4 threads again and delays other runs' loop checks".
  - Change the pacing sentence to: "one immediate retry, then at least one workflow-task timeout apart, growing with Temporal's backoff".

#### BR-15 — NIT — wording leftovers in the new code
- `ConditionEvaluationRunner.kt:35`: the edited KDoc line is 163 characters long and was not re-wrapped; the rest of the file wraps at 120.
- `TimeBoxedConditionEvaluationRunner.kt:194`: the exception message "all $threads are held by abandoned evaluations" is also thrown in the late-start case, where a thread did start the check.

### Notes (not findings)
- UNCONFIRMED, for the system tester's throttled scenario: the late-start rule needs the evaluation to have run 200 ms when the 250 ms wait ends.
  - If CPU throttling delays the start of an evaluation thread by more than 50 ms (for example by CFS quota periods under a 500m limit), a slow-condition run is retried instead of failed at once.
  - On a later retry, with free threads, it is recorded.
  - That is a delay, not a wedge. The throttled 8 × (8,8) re-check in the tester's list covers it.
- The real-server probe left 2 completed workflows (`br-r4-retry-probe-*`, their own task queues) in the local `default` namespace. I stopped the Gradle daemon I started. The other Gradle and Kotlin daemons belong to the system tester's parallel `continuum-feature-base` build and were left running.

### Verdict

**APPROVED** (no BLOCKER or MAJOR):
- BR-10, BR-12 and BR-13 RESOLVED; BR-11 documented by user decision.
- BR-1, BR-2, BR-3, BR-9 and F-1 still RESOLVED; loop-free runs unchanged.
- New: 1 MINOR (BR-14, docs), 1 NIT (BR-15).
- BR-4..BR-8 (3 MINOR, 2 NIT) unchanged and still open.

## Round 5

Date 2026-11-26. Review of `03-backend-dev.md` "Round 9 — fix round 4" in `continuum-platform-core`, branch
`CONTNM-180` (uncommitted). Scope: the terminate-records-`TERMINATED` change, the bridge upsert guard, the docs
wording, BR-15, and a regression check of the earlier fixes. Read in full: `WorkflowStopScheduler` (new),
`WorkflowService` (237 lines), the api-server `WorkflowRunRepository.markTerminated`, the bridge
`WorkflowRunRepository.upsert`, `WorkflowRunEntity` (both modules), `WorkflowStatusReporter.pinTerminalStatus`,
`ContinuumWorkflow.notifyTerminating`, `WorkflowServiceTest` (diff), `WorkflowRunTerminationRecordTest` (new), the
bridge handler test (diff), the controller description, the OpenAPI diff, `ENDPOINT_INVENTORY.md`, release notes
0.0.23 (lines 138, 328-330), `LoopNode.doc.md` "Keep conditions cheap", orchestration `AGENTS.md`. Temporal SDK
1.35.0 sources: `WorkflowStubImpl.terminate` and `throwAsWorkflowFailureException`.

### What I verified myself

| Check | Result |
|---|---|
| `./gradlew --offline :continuum-commons:test :continuum-loop-condition:test :continuum-orchestration-service:test :continuum-api-server:test :continuum-worker-springboot-starter:test :continuum-message-bridge:test --continue --rerun-tasks`, counts read from the XML results | PASS: 184 / 239 / 174 / 257 / 40 / 4, 0 failures, 0 errors, 0 skipped. Same as the developer's numbers. Gradle daemons stopped afterwards |
| Terminate path | `requireOwned` runs first, so another tenant gets `WorkflowNotFoundException` (404) before any signal, any scheduled stop and any write. The scheduled stop calls `terminate()` and only after it returns without an exception calls `markTerminated(workflowId, ownedBy, now)`. A failed terminate (`Exception`) logs a WARN and writes nothing. The stop runs on the timer thread, with no request scope, which is fine: nothing in it needs one |
| Run ends within the 5 s grace | `WorkflowStubImpl.terminate` maps gRPC `NOT_FOUND` to `WorkflowNotFoundException` (`throwAsWorkflowFailureException`, line ~598), which Temporal returns for an already-completed execution. `terminateAndRecord` catches it, logs INFO and returns without a write, so FINISHED / FAILED stay. A second terminate request (double click) hits the same path |
| `markTerminated` | JPQL `UPDATE ... SET status = 'TERMINATED', updatedAt = :terminatedAt WHERE workflowId AND ownedBy AND status <> 'TERMINATED'`: owner-scoped, idempotent, never a `save()`, so the snapshot, outputs, progress and `run_id` the bridge wrote are untouched. `status` is `NOT NULL`, so `<>` cannot silently skip a NULL row. `@Transactional` is on the repository method, so it works from the timer thread. A DB failure is caught by the scheduler (WARN) and does not kill the timer |
| Bridge upsert guard | `WHERE workflow_runs.status <> 'TERMINATED' OR EXCLUDED.status = 'TERMINATED' OR EXCLUDED.run_id <> workflow_runs.run_id`. A healthy run is not changed: after `notifyTerminating` the reporter pins the status, so every later event of that run is `TERMINATED` and still passes. A stale non-terminal event of the same run is ignored. An event of a different run id (reset) is applied. With a NULL run id on either side the comparison is NULL, so the event is ignored (the safe side). Postgres re-evaluates the `WHERE` against the locked row, and the two writers are single atomic statements, so terminate vs a concurrent bridge upsert has no lost-update: the order is either "RUNNING event, then TERMINATED" or "TERMINATED, then RUNNING event ignored" |
| Race: a run finishes just before the hard stop | If the run completed in Temporal, there is nothing to terminate (see above). The one window left is a run that has published its final `FINISHED` and not yet closed its execution: the terminate then wins in Temporal and the record says `TERMINATED`, which is what Temporal says too. Acceptable |
| Race: late events / signal handled late | Bridge late: api-server writes `TERMINATED` first, then the run's own `TERMINATED` event still updates snapshot and `updated_at` (the guard lets it through), status unchanged. Stale RUNNING event after the write: ignored. Covered in the docs and the developer's postgres run |
| OpenAPI | The spec diff for this round is description text only (`terminateWorkflow`, and `status` / `updatedAt` of `WorkflowRunSummary` and `WorkflowRun`); no path, operationId, field, enum or status code changed. The controller text matches the spec. `OpenApiSpecTest` is inside the 257 passing api-server tests. `ENDPOINT_INVENTORY.md` has the side effect, the idempotency and "no MQTT event" |
| House rule 12 | New `WorkflowStopScheduler` is a `fun interface` seam plus one small implementation (42 lines). `WorkflowService` gets one constructor dependency (4 in total) and one ~20-line private function; the in-line `Timer` moved out. The bridge change is one SQL clause. Fine |
| House rule 13 | Factory-ID grep over all modified and untracked files (`BE|FE|AC|PR|SCOPE|BR|FR|F|O|D|Q|P-n`): 0 hits in code, tests, docs and release notes. Release notes are in 0.0.23 (not a released version) |
| Named arguments | Call sites in the new main code and tests use them (`markTerminated(workflowId = ..., ownedBy = ..., terminatedAt = ...)`, `schedule(delayMillis = ...)`) |

### Status of earlier findings

- **BR-14 (MINOR) — RESOLVED.** Both parts are now in the docs:
  - (a) pacing: release notes (lines 138 and 328) and `LoopNode.doc.md` say "once at once, then at least one workflow-task timeout apart, growing with each attempt" with the measured gaps; `AGENTS.md:35` says the same; the SDK's ERROR stack trace is stated as expected. No "once per 10 s" / "at most once per" text is left;
  - (b) the self-wedge entries (release notes line 329, `LoopNode.doc.md`, `AGENTS.md:35`) say that, until terminated, each retry holds all 4 threads again for up to about 9 s and delays every other run's checks, across tenants, and that terminate now sets `TERMINATED`.
- **BR-15 (NIT) — RESOLVED.** `ConditionEvaluationRunner.kt` has no line over 120 characters. The late-start case throws its own message ("started only N ms before the wait ended, too late to use its 200 ms budget"), the queue-full case has its own text, and the queued-check case keeps "held by abandoned evaluations". The tests assert each text.
- **BR-1, BR-2, BR-3, BR-9, BR-10, BR-12, BR-13, F-1 — still RESOLVED**, and BR-11 stays documented by user decision. Nothing in the loop / orchestration code changed this round except the exception texts and KDoc; all 174 orchestration tests pass.
- **BR-4..BR-8 — unchanged, still open** (3 MINOR, 2 NIT; not part of this round, as the developer stated).

### New findings

#### BR-16 — MINOR — `continuum-message-bridge/src/test/.../WorkflowExecutionSnapshotToDbSyncHandlerTest.kt` — the upsert guard is pinned by SQL text only
- **What is wrong:** `a late event of a terminated run never turns its record back to running` asserts that the normalised query string contains the guard. It does not run the SQL (H2 has no `ON CONFLICT`). The developer did run the five cases once against a throwaway `postgres:16`, but that check is not repeatable in CI. A change that keeps the text but breaks the behaviour (for example moving the `WHERE` to the wrong clause, or a column rename) still passes.
- **Why it matters:** this guard is the only protection against a stale event reopening a terminated run, and it is the one part of the change with no behavioural test.
- **Fix (optional, can be a follow-up):** a Testcontainers-based test of the five cases if the repo already has Testcontainers for PostgreSQL; otherwise keep the text test and note in its KDoc that behaviour was checked on PostgreSQL by hand.

#### BR-17 — MINOR — `continuum-api-server/.../service/WorkflowStopScheduler.kt` — `TimerWorkflowStopScheduler` has no test
- **What is wrong:** the class exists to (1) run the stop after the delay and (2) survive a throwing stop. Its own KDoc says an uncaught exception would end the timer thread and silently drop every later cancel and terminate. No test covers either behaviour; `WorkflowServiceTest` replaces it with an immediate lambda, and the two controller slices only import it.
- **Why it matters:** removing the `try/catch` or the delay would go unnoticed, and the failure mode (cancel and terminate silently stop working after one error) is serious.
- **Fix:** a small unit test: schedule a stop that throws, then a second stop with a short delay; assert the second one runs.

#### BR-18 — NIT — `WorkflowStopScheduler.kt` / `WorkflowService.kt` — a pending stop is lost on api-server restart (pre-existing)
- The hard stop and the new `TERMINATED` write both live only in the in-memory timer. If the api-server restarts within the 5 s, a stuck run is neither terminated nor recorded. This is the same as before this round (the old `Timer` had the same limit), so it is not a regression and not blocking. Worth one line in the api-server `AGENTS.md`, or a follow-up if it matters operationally.

### Notes (not findings)
- If a stuck run's row has a NULL `run_id` (the api-server's `recordRunId` failed and no event arrived) and the api-server has written `TERMINATED`, a later event carrying a run id is ignored by the guard, so `run_id` stays NULL for that row. Needs two unlikely things at once; not worth a change.
- The terminate, the DB write and the earlier cancel all run on one daemon timer thread, so a slow database delays later stops. Same single-thread design as before.

### Verdict

**APPROVED** (no BLOCKER or MAJOR):
- The terminate path is tenant-scoped (404 before any signal, owner-scoped write), idempotent, never a `save()`, and a run that ended within the grace period keeps its final status.
- The bridge guard is correct for the healthy path, both race orders and a reset; the OpenAPI change is description-only; house rules 12 and 13 are met.
- BR-14 and BR-15 RESOLVED; the earlier fixes are not regressed (all module tests pass).
- New: 2 MINOR (BR-16, BR-17), 1 NIT (BR-18). Still open from before: BR-4..BR-8 (3 MINOR, 2 NIT).
