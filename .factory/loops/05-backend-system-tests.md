# Backend system tests: loops

## Round 1

Date 2026-11-25. Plan: `02-plan.md` "Round 2 — full CEL" (round-1 ACs where not superseded or withdrawn). Code under
test: `continuum-platform-core` branch `CONTNM-180`, uncommitted, the state after BE rounds 1-5 of `03-backend-dev.md`.
Tests written in `continuum-system-tests` branch `CONTNM-180` (uncommitted; no git writes).

### Environment

**Everything ran against the changed backend code.** `make up` was not used, because its released images are not this
code.

- **Infra.** `docker-compose -p loops-systest -f continuum-system-tests/docker/docker-compose.yml --env-file docker/.env up -d --wait`, limited to the infra services:
  - postgresql, temporal, temporal-search-attributes-init, mosquitto, kafka1-3, schema-registry, minio;
  - images: postgres 16, temporal auto-setup 1.26.2, cp-kafka latest, cp-schema-registry 7.2.1, silo/minio.
  - This used the system-tests compose (same ports as `continuum-platform-core/docker`, plus healthchecks), under its own project name.
  - Nothing else was running before.
  - Stopped at the end with `down` (no `-v`; the named volumes remain).
- **Platform services: built from `CONTNM-180`, run as boot jars on the host.**
  - Built with `./gradlew --offline -q :continuum-api-server:bootJar :continuum-orchestration-service:bootJar :continuum-message-bridge:bootJar`.
  - Started with `java -jar` in the background (not `bootRun`, to avoid three Gradle daemons racing).
  - Each was stopped by PID at the end.

  | Service | Source | Port | PID |
  |---|---|---|---|
  | continuum-api-server 0.0.23 | CONTNM-180 working tree | 8080 | 17800 |
  | continuum-message-bridge 0.0.23 | CONTNM-180 working tree | 8091 | 17811 |
  | continuum-orchestration-service 0.0.23 | CONTNM-180 working tree (`CONTINUUM_API_SERVER_BASE_URL=http://localhost:8080`) | 8090 | 17909 |
  | continuum-feature-base worker 0.0.10 | feature-base `main`, **rebuilt against the CONTNM-180 platform** (composite build, see below) | 8092 | 18324 |
  | continuum-credentials-server 0.0.23 | unchanged module, existing local jar (only for the credentials and smoke regression suites) | 8083 | 19935 |

- **Feature-base worker.**
  - The existing `worker-0.0.10.jar` (Nov 19) bundles a commons 0.0.23 *without* `DefaultFlowVariables` (it predates `1af31f5`). With it a loop condition could read no variable, so it was only used briefly (PID 18075) and then replaced.
  - The replacement was built with `./gradlew --offline -q --init-script <scratch build-dir redirect> --include-build ../continuum-platform-core :worker:bootJar` in `continuum-feature-base`.
  - Its build output went to `/tmp/loops-systest.Dtcv3h/gradle-build/`, so neither repo's `build/` was touched and `~/.m2` was not changed.
  - That jar carries the CONTNM-180 commons, worker-starter and avro-schemas, still on protobuf-java 3.25.5, with no cel-java.
- **Base URLs.** `API_SERVER_URL=http://localhost:8080` and `CREDENTIALS_URL=http://localhost:8083` (the `lib/config.js` defaults). k6 v2.4.0, node v24.18.0.
- **Loop registration was observed live** (AC-6 precondition):
  - Before the orchestrator started, `POST /node-explorer/nodes/task-queues ["org.projectcontinuum.core.builtin.Loop"]` → `{}`, and the documentation → 404.
  - Orchestrator log: `RetryingRegistration : Published built-in node registration 'org.projectcontinuum.core.builtin.Loop' (worker 'orchestration-…', attempt 1)`, then task-queues → `{"…Loop":"$builtin"}`.
- **Side effect left behind:** the api-server ran with `continuum-platform-core` as its working directory, so it created the git-ignored `continuum-platform-core/.continuum-cache/workflow-data/` (DuckDB cache, its default `CONTINUUM_CACHE_DIRECTORY`). It is not tracked and not in `git status`. I could not remove it (`rm -rf` is denied); the user can delete it.

### Live loop runs: what could and could not run

- No node in `continuum-feature-base` sets a flow variable of the user's choosing (grep for `setFlowVariable`: no hits).
- The live runs therefore steer the condition with the **default flow variables** every worker exports per node:
  - `conditionalSplitterNode_split.executionStatus` = `"SUCCESS"` after every iteration (string);
  - `.durationMs` (int);
  - `createTableNode_seed.executionStatus`, which exists before the loop.
- This drives: stop after iteration 1, both limit policies, `check: before` (zero iterations), missing variable, `||` absorbing a missing variable, `"s" == 1`, `"s" > 1`, int vs double, non-boolean under both `onNonBooleanResult` values (also on the first `before` check), a failing member, and nested loops.
- **Not drivable:** a condition whose value changes between iterations (AC-16's 3-iteration paging run, AC-17's "false after 2 iterations", AC-21 carried values overriding outside values). These stay NOT VERIFIED live.

### AC coverage

Check names are verbatim from `suites/api/loops.js`; "×n" means one check per case. Final run: **162/162 checks
passed, `isolation_violations` 0, `known_gaps` 5** (run 3 times, all the same).

| AC | Check name(s) | File | Result |
|---|---|---|---|
| AC-6 Loop in registry after the orchestrator starts | "the Loop is registered with the built-in task-queue marker $builtin"; "search finds the Loop with its node info"; "search nests the Loop under the 'Flow Control' category"; "the palette roots include 'Flow Control'"; "'Flow Control' lists the Loop as a node"; "the Loop documentation is 200 markdown starting with \"# Loop\""; "getContinuumNodeMetadata returns the Loop with its schemas" (MCP); "another tenant sees the same Loop (the registry is shared by design)" | `suites/api/loops.js` | PASS |
| AC-5 registration (live part only: one publish at start, attempt 1) | observed in the orchestrator log, not a k6 check. The retry and backoff (5 attempts, shutdown) were not provoked against a real broker outage | — | PASS (first attempt only); retry: NOT VERIFIED (no broker outage provoked; unit-tested by the developer) |
| Contract: registered `propertiesSchema` / `propertiesUISchema` | "the registered schema requires condition, maxIterations, onLimit, onNonBooleanResult and check"; "the registered schema rejects undeclared properties"; "onNonBooleanResult is fail \| treat-as-false with default fail"; "condition is a string of 1..2048 characters with the default vars[\"hasMore\"] == true"; "maxIterations is 1..10000, default 50"; "the registered defaults include onNonBooleanResult: fail"; "the UI schema renders the condition as a CEL query control, then onLimit before onNonBooleanResult"; "the registered Loop has no ports" | `suites/api/loops.js` | PASS |
| AC-7 (amended) settings | "a loop with the default settings (onNonBooleanResult: fail included) is valid"; "loop setting: <case>: validate is 200 with valid=false / exactly one error, on loop 'L' and '<property>' / the error says '…'" ×7 (maxIterations 0, 10001, onLimit, check, onNonBooleanResult outside the enum, empty condition, 2,049-character condition); "start with onNonBooleanResult outside its enum is 400 ProblemDetail"; "the start 400 names the loop and onNonBooleanResult" | `suites/api/loops.js` | PASS |
| AC-9 edge to a loop | "an edge into a loop is invalid"; "the edge into the loop gets one error naming the edge and saying a loop has no ports"; "a loop is never reported as not connected" | `suites/api/loops.js` | PASS |
| AC-10 leave and come back | "X1 -> Y -> X2 (Y outside loop L) is invalid with an error on 'L' naming 'Y'"; "start of X1 -> Y -> X2 is 400"; "A -> X1 -> X2 -> B with X1, X2 in the loop is valid" | `suites/api/loops.js` | PASS |
| AC-13 (rewritten) rejected and accepted conditions over HTTP | "condition <label> (<text>): validate is 200 with valid=false / exactly one error, on loop 'L' and 'condition' / the error says 'Loop 'L': the condition <reasonText>'" ×7 (`now() > 1`, `foo == 1` → does not type-check; `vars["p"] + 1` → does not return true or false; `vars["a"] ==` → is not a valid CEL expression; `vars["s"].matches("(")` → invalid regex…; the re-bound `cel.bind` → binds a name…; 33-deep nesting → nests brackets deeper than 32 levels); "condition of 2,049 characters (schema maxLength, no second error): …" (one error, the schema's "must be at most 2,048 characters", as the user confirmed); "full-CEL condition accepted: <text>" ×9 (the 7 AC-13 examples, plus depth 32 and exactly 2,048 characters); "start with a rejected condition is 400 ProblemDetail"; "the start 400 has one error on loop L / condition with the reason"; "a start rejected for its condition returns no workflowId" | `suites/api/loops.js` | PASS |
| AC-13 / AC-14 constant-condition warning | "condition `true` is valid with exactly one warning on the condition"; "the warning says the condition reads no flow variable" | `suites/api/loops.js` | PASS |
| Schedule create validates loops (inventory: start/validate/schedule share the validator) | "a schedule whose loop condition is rejected is 400 with the error on L / condition"; "a schedule of a valid loop workflow is 201"; "the stored schedule keeps the loop type and the members parentNode"; "the owner can read the loop schedule"; "loop schedule: hidden from other tenant (404)" | `suites/api/loops.js` | PASS |
| AC-14 (amended) MCP | "MCP initialize is 200 with a session id"; "MCP still has exactly six tools"; "the workflow tool descriptions teach the loop: nodeId, parent, vars[\"name\"], onNonBooleanResult, validator authoritative"; "the validate tool says edges never name a loop"; "MCP validate accepts a spec with a loop and members declaring parent"; "MCP validate reports <label> as one error on L / condition" ×7; "MCP validate gives one warning on L / condition for condition `true`"; "MCP build refuses a rejected condition with the error and no workflow"; "MCP build of a loop spec is valid and returns a workflow"; "the built loop has type Loop and the Loop node model"; "the built loop comes before its members"; "every built member has parentNode L and a position inside the box"; "the built edges never name the loop" | `suites/api/loops.js` | PASS (live; AC-14 is [unit] in the plan, this is extra) |
| AC-24 budget warning (live part) | "maxIterations 10000 x 2 members is valid but warns on maxIterations about the run history limit" | `suites/api/loops.js` | PASS |
| AC-15 loop has no activity, run does not fail at init | every "loop run \"<name>\" starts (200)" ×14, and each run reached a terminal status with member statuses set (no `InitializationFailed`) | `suites/api/loops.js` | PASS |
| AC-16 (live) example paging run: 3 iterations, `Finished after 3 iterations` | Not drivable (see above). What ran instead: "a loop whose condition turns false ends SUCCESS \"Finished after 1 iterations\""; "the members ran and the node after the loop ran"; "a loop run that succeeds is FINISHED" | `suites/api/loops.js` | NOT VERIFIED: no feature-base node sets a chosen flow variable, so a condition that is true for 2 iterations and false in the 3rd cannot be built. The 1-iteration SUCCESS path PASSES |
| AC-17 check before | "check before with a false condition: loop SUCCESS \"Condition was false before the first iteration\""; "check before with a false condition: members and the consumer after the loop are SKIPPED"; "check before with a false condition: the run is FINISHED" | `suites/api/loops.js` | PASS (zero-iteration part); "false after 2 iterations": NOT VERIFIED (same reason as AC-16) |
| AC-18 limit policies | "the limit with stop-with-warning ends WARNING \"Stopped at the limit of 3 iterations\""; "after a stop-with-warning limit the node after the loop runs and the run is FINISHED"; "the limit with fail fails the loop (FAILED) with the $error \"Loop 'L' reached its limit of 2 iterations and the condition still holds.\""; "the limit with fail: the node after the loop is SKIPPED"; run status FAILED → KNOWN GAP | `suites/api/loops.js` | PASS; "the run fails" (run record): KNOWN GAP (see Failures F-1) |
| AC-19 member failure | "a failing member fails the loop (FAILED) with the $error \"Loop 'L' stopped in iteration 1: node 'bad' failed.\""; "a failing member: the node after the loop is SKIPPED"; run status → KNOWN GAP | `suites/api/loops.js` | PASS (failure in iteration 1, not 2: a member cannot be made to fail only in iteration 2 with feature-base nodes); run status: KNOWN GAP (F-1) |
| AC-20 nested loops, iteration resets | "nested loops: the outer loop stops at its limit of 2 and the inner one at its limit of 3, with warnings"; "nested loops: the run is FINISHED and the node after the outer loop ran"; "nested loops: while iterating, a loop shows \"Iteration k of max\"" | `suites/api/loops.js` | PASS (outer 2 x inner 3 ended as specified; the 6 inner-member executions cannot be counted over HTTP, because the snapshot holds only the last iteration: NOT VERIFIED) |
| AC-21 carried variables | "the finished loop stores the variables carried into its last iteration as its display $variable" | `suites/api/loops.js` | PASS (display `$variable` present with the members' variables); override of an outside value: NOT VERIFIED (no settable variable) |
| AC-22 (rewritten) CEL runtime semantics | (a) "a missing variable fails the loop (FAILED) with the $error \"Loop 'L': flow variable 'hasMore' is not set when the condition is checked in iteration 1 (column…\""; (b) "a \|\| whose other side is true absorbs the missing variable (no failure; …)"; (c) "a string compared == with an int is false, so the loop ends after iteration 1" and "a string > int comparison fails the loop (FAILED) with the $error \"Loop 'L': the condition check in iteration 1 failed with EVALUATION_ERROR…\""; (d) "an int variable compares with a double literal by value (true: …)" | `suites/api/loops.js` | PASS for (a)-(d) (AC-22 is [unit] in the plan; this is live with the real cel-java evaluator); (e) JSON → CEL types: NOT VERIFIED live (only Long and String reach the condition from default variables) |
| AC-25 progress and iteration message | "run progress was sampled while the loop ran, never decreased and ended at 100"; "while the loop iterates it shows \"Iteration k of 3\""; "nested loops: run progress was sampled while running and never decreased" | `suites/api/loops.js` | PASS |
| AC-44 onNonBooleanResult | "a non-boolean result with fail fails the loop (FAILED) with the $error \"Loop 'L': the condition returned a string (\"SUCCESS\") in iteration 1, not true or false.\""; "a non-boolean result with fail: the node after the loop is SKIPPED"; "a non-boolean result with treat-as-false ends the loop WARNING with the treated-as-false message"; "with treat-as-false the node after the loop runs and the run is FINISHED"; "check before and a non-boolean first check under treat-as-false: members SKIPPED, loop WARNING" | `suites/api/loops.js` | PASS (iteration 1, not the plan's iteration 2: the value cannot change between iterations here); run status for `fail`: KNOWN GAP (F-1) |
| AC-3 loop fields round-trip (live) | "the run snapshot keeps the loop type and the members parentNode"; "the stored schedule keeps the loop type and the members parentNode" | `suites/api/loops.js` | PASS |
| Tenant isolation of loop resources | "loop run record: hidden from other tenant (404)" + "the owner can read a loop run"; "loop schedule: hidden from other tenant (404)" + "the owner can read the loop schedule" | `suites/api/loops.js` | PASS (`isolation_violations` 0) |
| AC-11 loop-free workflows unchanged | "a workflow without loops still runs to FINISHED"; plus the existing suites against the changed services: smoke 10/10, `api/workflow` 15/15, `api/runs` 30/30, `api/schedules` 36/36, `api/node-explorer` 19/19, `api/data` 45/45, `api/credentials` 54/54, `tenancy/isolation` 32/32; `make api` (all 7 incl. loops) exit 0 | `suites/api/loops.js`, existing suites | PASS |
| AC-23 history guard, AC-26 replay, AC-27 run started directly through Temporal, AC-42, AC-43 | not reachable over HTTP (they need a tiny guard threshold, Temporal-client starts that skip the validator, or engine internals) | — | NOT VERIFIED: not system-testable; [unit] in the plan |

### Failures

No check failed. One known gap, reported through `knownGap()` and not counted as a pass:

- **F-1 (KNOWN GAP, pre-existing, not loop-specific): a run with a failed node is recorded `FINISHED`.**
  - Request: `GET /api/v1/workflow-runs/{workflowId}` of each run whose loop FAILED: limit with `fail`, missing variable, EVALUATION_ERROR, non-boolean with `fail`, failing member. That is 5 samples of `gap:run-with-a-failed-node-is-recorded-FINISHED`.
  - Expected: `status: "FAILED"`. The plan's AC-18/19/22a/44 say "the run fails", and Temporal agrees: `temporal workflow describe` of the limit-fail run → `Status FAILED`, `Message: Workflow execution failed`.
  - Actual: `status: "FINISHED"`, `progressPercentage: 100`.
  - A loop-free control run behaves the same: `CreateTable` with an invalid JSON array, node FAILED, run record `FINISHED`.
  - Cause (`continuum-orchestration-service/.../workflow/ContinuumWorkflow.kt:115-153`):
    - `start()` publishes `statusReporter.publishUpdate("FINISHED")` and the COMPLETED search attribute right after `dagExecutor.run(...)`;
    - only after that does it throw `ApplicationFailure("Workflow execution failed")` when `nodeErrorsMap` is non-empty;
    - so the message-bridge stores `FINISHED`.
  - This code is unchanged on `CONTNM-180` (it exists on `main`), so it is not a regression of the loops work. Loops make it more visible, because every loop failure ends here.
  - Owner: backend developer. It is outside the loops plan, so: `SCOPE:` publish `FAILED` (and the FAILED search attribute) when `nodeErrorsMap` is non-empty.

Observation (no failure, for the backend developer / FE-1):
- A loop node that carries the Loop's exact registered `data` (from node search) produces **no** "submitted propertiesSchema differ" warning.
- A loop built from a hand-written `data` without schemas does get one, like any node.
- So the warning itself is correct.

### Files changed (repo `continuum-system-tests`, branch `CONTNM-180`, uncommitted)

- `suites/api/loops.js` (new): the suite (registry, settings, structure, condition, MCP, schedules, live runs, loop-free regression).
- `lib/loopmodel.js` (new, k6-free): loop workflow builders (`workflowNode`, `loopNode`, `edge`, `workflowOf`, `chainLoopWorkflow`), run-record readers (`nodeState`, `nodeOutput`, `errorText`, `snapshotNode`), `issuesAt`, `isNonDecreasing`, boundary inputs (`conditionOfLength`, `nestedCondition`), and MCP streamable-HTTP readers (`mcpMessage`, `mcpToolResult`).
- `test/loopmodel.test.js` (new): 13 `node --test` cases for `lib/loopmodel.js`.
- `lib/tenant.js`: `post()` takes optional extra headers (MCP `Accept` / `Mcp-Session-Id`), merged under the tenant headers, so they can never replace `x-continuum-user-id`. The unused `extra` request-params spread was removed.
- `lib/config.js` + `run.sh`: `LOOP_REGISTRATION_TIMEOUT_SEC` (default 150): `setup()` waits for the orchestrator's Loop registration.
- `Makefile`: `loops` added to `API_SUITES`.
- `README.md`: suite table row, status line and `make api` count, known-gap table (F-1), the new env var, layout (`loopmodel.js`), and a "Loop suite: what it can and cannot run" section.
- `AGENTS.md`: Ownership lists the new suite, lib and test; Local Contracts gain the extra-headers rule, the registration wait and default-variable steering, and the F-1 known gap.
- `fixtures/workflows/loop-valid.json` / `loop-invalid-*.json` (planned in the test plan) were **not** added: the loop workflows are built in code from the existing fixture's node templates. Static JSON fixtures would have duplicated the node metadata and gone stale with the registry.
- Factory-ID grep over every modified or untracked file of the repo: 0 hits.

### Commands run

| Command | Result |
|---|---|
| `docker ps -a`; port probe 8080/8081/8083/7233/39092/35432/38080/39000/31883 | no containers, all free |
| `docker-compose -p loops-systest -f docker/docker-compose.yml --env-file docker/.env up -d --wait <infra services>` | all healthy |
| `./gradlew --offline -q :continuum-api-server:bootJar :continuum-orchestration-service:bootJar :continuum-message-bridge:bootJar` (platform-core) | PASS |
| `java -jar` api-server / message-bridge / orchestration (background) | api-server health UP; orchestration published the Loop on attempt 1 |
| `./gradlew --offline -q --init-script /tmp/loops-systest.Dtcv3h/scratch-build.init.gradle.kts --include-build ../continuum-platform-core :worker:bootJar` (feature-base) | PASS; the jar bundles CONTNM-180 commons (with `DefaultFlowVariables`) |
| `./gradlew --offline ... -Dmaven.repo.local=<scratch> publishToMavenLocal` (first attempt to give feature-base the libs) | FAILED offline: jackson-databind 2.20.0 and commons-lang3 3.18.0 are not cached for avro-schemas' publication. Replaced by the composite build above; nothing was downloaded |
| Manual probes (node scripts in `/tmp/loops-systest.Dtcv3h/probe/`) of validate, start, runs and MCP | used to pin the live texts before writing the checks |
| `docker run --rm --entrypoint temporal temporalio/admin-tools:… workflow describe -w <limit-fail run>` | `Status FAILED` (diagnosis of F-1). A first attempt without `--entrypoint` hung; I removed that container (`objective_napier`, mine) |
| `npm test` | PASS, 68/68 (55 existing + 13 new) |
| `npm run check` | PASS |
| `./run.sh suites/api/loops.js` (runs 2, 3, 4; run 1 had one check failing on the sampling method, which I fixed in the test) | PASS 162/162, `isolation_violations` 0, `known_gaps` 5, exit 0 |
| `./run.sh` smoke, `api/{workflow,runs,schedules,node-explorer,data,credentials}`, `tenancy/isolation` | all PASS (10, 15, 30, 36, 19, 45, 54, 32 checks) |
| `make api` | exit 0; all 7 API suites 100 % (loops 162/162) |
| `kill` PIDs 17800, 17811, 17909, 18324, 19935 (18075 earlier) | all stopped |
| `docker-compose -p loops-systest ... down` (no `-v`) | containers and network removed; volumes kept |

Logs and k6 outputs: `/tmp/loops-systest.Dtcv3h/` (`api-server.log`, `orchestration.log`, `message-bridge.log`,
`worker.log`, `k6-run{1..4}.txt`, `k6-reg-*.txt`, `make-api.txt`, `pids.txt`).

## Round 2

Date 2026-11-25. Re-check after BE fix round 1 (`03-backend-dev.md` "Round 6 — fix round 1": BR-1, BR-2, BR-3, F-1). Code under test: `continuum-platform-core` `CONTNM-180` working tree (uncommitted, rounds 1-6) and `continuum-feature-base` `main` rebuilt against it. Tests: `continuum-system-tests` `CONTNM-180` (uncommitted; no git writes).

### Environment

**Everything ran against the changed code**, the same way as round 1. `make up` was not used.

- **Infra:**
  - command: `docker-compose -p loops-systest -f continuum-system-tests/docker/docker-compose.yml --env-file docker/.env up -d --wait` with postgresql, temporal, temporal-search-attributes-init, mosquitto, kafka1-3, schema-registry and minio. The `--wait` exit code was 1 only because the one-shot init container exits (it exited 0); every service was healthy;
  - nothing was running before I started;
  - stopped at the end with `down`, without `-v`.
- **Builds:**
  - all build output went to the scratch dir `/tmp/loops-systest-r2.77C4vM/gradle-build/` through an init script that redirects `layout.buildDirectory`. No repo `build/` and no `~/.m2` was touched;
  - platform: `./gradlew --offline -q --init-script … :continuum-api-server:bootJar :continuum-orchestration-service:bootJar :continuum-message-bridge:bootJar` → OK;
  - feature-base worker: `./gradlew --offline -q --init-script … --include-build ../continuum-platform-core :worker:bootJar` → OK. The jar bundles the CONTNM-180 `continuum-commons`, `continuum-worker-springboot-starter` and `continuum-avro-schemas` 0.0.23, with protobuf-java 3.25.5 and no cel-java.
- **Processes**, all `java -jar` in the background and all stopped by PID at the end:

  | Service | Source | Port | PID |
  |---|---|---|---|
  | continuum-api-server 0.0.23 | CONTNM-180 (`CONTINUUM_CACHE_DIRECTORY` set to the scratch dir, so no new `.continuum-cache` in the repo) | 8080 | 24764 |
  | continuum-message-bridge 0.0.23 | CONTNM-180 | 8091 | 24765 |
  | continuum-orchestration-service 0.0.23 | CONTNM-180, `CONTINUUM_API_SERVER_BASE_URL=http://localhost:8080` | 8090 | 24821 |
  | feature-base worker 0.0.10 | feature-base `main` built against CONTNM-180 | 8092 | 24822 |
  | continuum-credentials-server 0.0.23 | unchanged module, existing jar (only for the credentials and smoke regression suites) | 8083 | 24859 |

- **Loop registration:** orchestration log `Published built-in node registration 'org.projectcontinuum.core.builtin.Loop' (… attempt 1)`, and `task-queues` → `{"…Loop":"$builtin"}`.
- **Base URLs:** the `lib/config.js` defaults (`http://localhost:8080`, `http://localhost:8083`). k6 v2.4.0, node v24.18.0.
- **Temporal CLI:** there is none on the host. I used the one in the temporal container: `docker exec temporal temporal workflow {list,describe,show} --address temporal:7233`. `/tmp/loops-systest-r2.77C4vM/history-summary.py` reads each history: event types, `WORKFLOW_TASK_FAILED` count, markers, and per-node `Run` activities (decoded from the activity input).

### How the multi-iteration runs are driven (user instruction: default flow variables)

`lib/loopscenarios.js` (new, k6-free) builds every run only from feature-base nodes. The conditions read only `<prefix>.startTimestamp`, `.endTimestamp` and `.executionStatus`, with `prefix` = `defaultVariablePrefix`, a mirror of `DefaultFlowVariables.prefix`. Two facts make them deterministic. Neither depends on how long anything takes, only on the order the DAG imposes:

1. Inside an iteration a node starts after its parent ended. So `x.startTimestamp >= score.endTimestamp` (where `score` is the entry member that runs every iteration) is true exactly when x ran in *this* iteration.
2. A member's variables stay in the carried set, unchanged, until it runs again. So `"x.startTimestamp" in vars` is true from the iteration after x first ran.

A nested `check: before` loop with the condition "x has not run" therefore runs x exactly once (a "once gate"). Chaining gates gives exact iteration counts.

| Scenario | Shape | Result proved |
|---|---|---|
| 3 iterations | `seed → [L: score → {I1: m1}, {I2 (after m1): m2}] → after → [V: obs]`. L's condition: "m1 or m2 ran in this iteration" | true, true, false → L "Finished after 3 iterations". The observer loop V, after the loop, reads `m1.start < m2.start < score.start` from what `after` inherited: SUCCESS after 1 iteration only if `after` got the carried set (m1 from iteration 1, m2 from iteration 2); otherwise MISSING_VARIABLE → FAILED |
| check before, 2 iterations | `[L before: score → {I1: m1}]`, condition `!(m1 ran) \|\| m1 ran in this iteration` | true before 1, true after 1, false after 2 |
| member fails in iteration 2 | gate I2 (opens once m1 ran) holds `bad` (invalid JSON) | L FAILED "stopped in iteration 2: node 'I2' failed." |
| non-boolean in iteration 2 | `m1 ran in this iteration ? true : score.executionStatus` | iteration 1 true, iteration 2 `"SUCCESS"` |
| carried overrides outside | outside `src-x` and member `src_x` both export `createTableNode_src_x.*`. A gate J inside L opens only for the member's value (start after `pre` ended) | J opens in iteration 2 → L "Finished after 2 iterations". If the outside value had won, L would fail at its limit of 3 |
| later member wins | `c-1 → mid → c_1` inside L (same prefix). The condition `c_1.start < mid.end` is false only for the later member's value | "Finished after 1 iterations" |

Probed with node scripts first (`probe/designs*.mjs`), then written as k6 checks. 3 suite runs, all identical.

### AC coverage (round 2)

Final run: **213/213 checks passed** (162 in round 1), `isolation_violations` 0, **no `known_gaps` samples** (all `knownGap()` calls removed). 3 consecutive runs plus the one inside `make api`: all 213/213.

| AC | Check name(s) (verbatim, `suites/api/loops.js`) | Result |
|---|---|---|
| F-1 fix: the 5 failed-loop runs recorded FAILED (AC-18 fail, AC-22a, AC-22c, AC-44 fail, AC-19) | "<what>: the run is recorded FAILED" for: the limit with fail, a missing variable, a string > int comparison, a non-boolean result with fail, a failing member (plus the same check for the new iteration-2 member failure, the iteration-2 non-boolean with fail, and the slow condition) | PASS. The known gap is gone: these are now real checks |
| F-1 fix: loop-free run with a failed node | "a loop-free run whose node fails: the node is FAILED and the node after it SKIPPED"; "a loop-free run whose node fails is recorded FAILED"; "the run list filter status==FAILED finds the failed run" | PASS. Temporal agrees: `WORKFLOW_EXECUTION_FAILED` for every one of them, and the search attribute `Continuum:ExecutionStatus` is 3 |
| AC-16 (live) multi-iteration stop | "the three-iteration loop over default timestamps is valid"; "a condition true in iterations 1 and 2 ends the loop SUCCESS \"Finished after 3 iterations\""; "while the three-iteration loop runs it shows \"Iteration k of 10\""; "after the three-iteration loop the node after it ran and the run is FINISHED" | PASS. Temporal history: `score` ran 3 times; `after` and `obs` once. This is not the literal paging example: `hasMore` cannot be set by any feature-base node. The 3-iteration stop, the single run of the node after the loop and the FINISHED run are the same behaviour |
| AC-17 check before | round-1 zero-iteration checks; new: "check before with a condition that turns false after 2 iterations: loop SUCCESS \"Finished after 2 iterations\""; "check before, two iterations: the run is FINISHED" | PASS. History: `score` ran exactly 2 times |
| AC-18 limit policies | round-1 checks plus "the limit with fail: the run is recorded FAILED" | PASS |
| AC-19 member failure in iteration 2 | "a member failing in iteration 2 fails the loop (FAILED) with the $error \"Loop 'L' stopped in iteration 2: node 'I2' failed.\""; "…: the node after the loop is SKIPPED"; "…: the run is recorded FAILED"; "…: the nested loop holding it is FAILED naming the node" | PASS. History: `score` 2 runs (no iteration 3), `bad` 1 |
| AC-20 nested 2 x 3, inner count | round-1 nested checks (outer WARNING at 2, inner WARNING at 3, FINISHED, iteration messages, progress) | PASS. The inner count was **verified from the Temporal history, not by a k6 check**: `score` 6 runs and `split` 6 runs, `seed` and `after` 1 each (runs `b2340cde…` and `88a1d19e…`). It cannot be checked over HTTP, because the run record keeps only the last iteration. Reset of statuses and edges between iterations: covered indirectly (every multi-iteration run re-ran its members and ended with consistent statuses); field-level reset stays [unit] |
| AC-21 carried variables | "the three-iteration loop shows as $variable the set carried into iteration 3 (m1 from iteration 1, then m2 from iteration 2)"; "the node after the loop inherits the last iteration's carried variables (the observer loop reads m1 < m2 < score and stops after 1 iteration)"; "in iteration 2 the entry member sees the carried value, not the outside value of the same name (loop ends after 2 iterations)"; "on a carried name clash the member later in run order wins (the condition reads its value: loop ends after 1 iteration)"; round 1: "the finished loop stores the variables carried into its last iteration as its display $variable" | PASS. **BR-1 confirmed fixed live**: the node after the loop received m1's variables, which only iteration 1 set |
| AC-22 runtime semantics | (a)-(d) as in round 1, plus the run is recorded FAILED for (a) and (c) | PASS for (a)-(d). (e) NOT VERIFIED live: only Long and String values were driven. The `inputCount`/`outputCount` maps exist but no check reads them |
| AC-44 onNonBooleanResult in iteration 2 | "a non-boolean result in iteration 2 with fail fails the loop (FAILED) with the $error \"Loop 'L': the condition returned a string (\"SUCCESS\") in iteration 2, …\""; "…: the node after the loop is SKIPPED"; "…: the run is recorded FAILED"; "a non-boolean result in iteration 2 with treat-as-false ends the loop WARNING after iteration 2"; "with treat-as-false in iteration 2 the node after the loop runs and the run is FINISHED"; round-1 `before` checks | PASS. These now run in iteration 2, as the plan specifies |
| BR-2 empty loop at 10,000 | "the empty loops validate with a \"has no nodes inside it\" warning each"; "an empty loop at maxIterations 10,000 ends WARNING without an iteration"; "a loop holding only an empty loop ends WARNING without an iteration, the inner loop SKIPPED"; "next to empty loops the other nodes run and the run is FINISHED within 30 s" | PASS. Message "The loop has no nodes inside it to run; it ran no iteration". History: 57 events (54 in the probe), **0 markers**, only `seed` and `after` ran, 0 workflow-task failures |
| BR-2 loop around only an unconnected node at 10,000 | "a loop whose only member cannot run ends SKIPPED \"No node inside the loop could run in iteration 1\""; "the skipped member and the node after the skipped loop are SKIPPED"; "a loop skipped for nothing to run does not fail the run (FINISHED within 30 s)" | PASS. History: **13 events**, 0 markers, no activity run |
| BR-3 slow condition, 200 ms budget | "the slow condition is accepted by the validator"; "a condition slower than the time budget fails the loop (FAILED) with the $error \"Loop 'L': the condition check in iteration 1 failed with LIMIT_EXCEEDED…\""; "…: the node after the loop is SKIPPED"; "…: the run is recorded FAILED"; "the slow condition's $error says it took longer than 200 ms"; "the slow condition fails the run within 30 s (no wedged workflow task)" | PASS. Full `$error`: "Loop 'L': the condition check in iteration 1 failed with LIMIT_EXCEEDED: the condition exceeded a cost limit: the condition took longer than 200 ms to evaluate (column 20).". Temporal: `WORKFLOW_EXECUTION_STATUS_FAILED`, 52 events (55 in the probe), 1 marker, **`WORKFLOW_TASK_FAILED` 0**. The orchestration log has 0 `PotentialDeadlock`, 0 `NonDeterministic` and 0 ERROR lines |
| All 26 workflows of one loop-suite run | — | Temporal: `WORKFLOW_TASK_FAILED` = 0 in every history. COMPLETED ↔ record FINISHED and FAILED ↔ record FAILED, one to one |
| AC-6, AC-7, AC-9, AC-10, AC-13, AC-14 (MCP), AC-24, AC-25, AC-3, schedule validation, tenant isolation | unchanged round-1 checks | PASS (`isolation_violations` 0) |
| AC-11 loop-free unchanged + regression | "a workflow without loops still runs to FINISHED"; `make api`, smoke and isolation (below) | PASS |
| AC-5 retry, AC-23, AC-26, AC-27, AC-42, AC-43 | — | NOT VERIFIED: not system-testable, as in round 1 (unit-tested by the developer) |

### Failures

None. No check failed in any run, and there are no known gaps left. Nothing to send to the backend developer.

Observations, not failures:
- Worker ERROR log lines (10) come only from the deliberately invalid `CreateTable` nodes (`bad`, `seed` in the failing-node scenarios).
- The round-1 leftover `continuum-platform-core/.continuum-cache/workflow-data/` (git-ignored) is still there; this round created nothing in the repos.

### Files changed (repo `continuum-system-tests`, branch `CONTNM-180`, uncommitted; on top of round 1)

- `lib/loopscenarios.js` (new, k6-free):
  - `defaultVariablePrefix` / `defaultVariable` (mirror of `DefaultFlowVariables.prefix`), `varRef`, `varSet`, `conditionVariableNames`;
  - the scenario builders `threeIterationLoop`, `beforeTwoIterationsLoop`, `memberFailsInIteration2Loop`, `nonBooleanInIteration2Loop`, `carriedOverridesOutsideLoop`, `laterMemberWinsLoop`, `emptyLoops`, `unconnectedMemberLoop`, `slowConditionLoop` (`SLOW_CONDITION`) and `failingNodeWithoutLoop`.
- `test/loopscenarios.test.js` (new): 6 `node --test` cases. Among them: the prefix rule on the KDoc examples, the scenario shapes and the variables each condition reads.
- `suites/api/loops.js`:
  - removed the `knownGap` import and call;
  - `failedLoop` now asserts "the run is recorded FAILED" and takes an optional loop id;
  - new sections `multiIterationChecks` and `nothingToRunChecks`, with the helpers `scenario`, `runAll` and `recordSpanMs`;
  - `loopFreeChecks` gained the failed-node run and the `status==FAILED` list filter;
  - header comment updated.
- `README.md`:
  - the suite table row;
  - Known gaps: back to "no open gaps", with the failed-run fix added to the list;
  - layout gains `loopscenarios.js`;
  - "Loop suite: what it can and cannot run" rewritten (timestamp steering, once gates; run counts only in the Temporal history).
- `AGENTS.md`:
  - Ownership: `lib` and `test` list `loopscenarios`;
  - the k6-free rule now covers `loopscenarios.js`;
  - the loop-suite contract: default-variable steering, the prefix mirror to change together, conditions deterministic by DAG order only;
  - the F-1 known-gap contract is replaced by "a failed run must be recorded FAILED".
- Factory-ID grep over every modified and untracked file: 0 hits.

### Commands run

| Command | Result |
|---|---|
| `docker ps -a`; port probe | nothing running |
| `docker-compose -p loops-systest … up -d --wait <infra>` | all healthy (the init container exited 0) |
| platform `bootJar` ×3 (`--offline`, scratch build dir) | OK |
| feature-base `:worker:bootJar` (`--offline`, `--include-build ../continuum-platform-core`, scratch build dir) | OK; CONTNM-180 libs in the jar |
| `java -jar` ×5 (background) | api-server UP; the Loop published on attempt 1 |
| `node probe/multi.mjs`, `probe/designs.mjs`, `probe/designs2.mjs` | every design ended as intended (see scenario table) |
| `docker exec temporal temporal workflow describe/show/list …`, `python3 history-summary.py <ids>` | event counts, 0 `WORKFLOW_TASK_FAILED`, per-node run counts (quoted above) |
| `npm test` | PASS 74/74 (68 + 6 new) |
| `npm run check` | PASS |
| `./run.sh suites/api/loops.js` ×3 | 213/213, `isolation_violations` 0, no known gaps, exit 0 |
| `make api` | exit 0: workflow 15/15, runs 30/30, schedules 36/36, node-explorer 19/19, data 45/45, credentials 54/54, loops 213/213 |
| `./run.sh smoke` | 10/10, exit 0 |
| `./run.sh tenancy` | 32/32, `isolation_violations` 0, exit 0 |
| `kill` 24764 24765 24821 24822 24859 | all stopped (verified with `kill -0`) |
| `docker-compose -p loops-systest … down` (no `-v`) | containers and network removed; no containers left |

Logs and outputs: `/tmp/loops-systest-r2.77C4vM/`. It holds the service logs, `k6-run{1,2,3}.txt`, `make-api.txt`, `k6-smoke.txt`, `k6-isolation.txt`, `history-run1.txt`, `list-run1.json`, `probe/` and `pids.txt`.

## Round 3

Date 2026-11-26. Re-check after BE fix round 2 (`03-backend-dev.md` "Round 7 — fix round 2": BR-9, the bounded wait on condition evaluation). Code under test: `continuum-platform-core` `CONTNM-180` working tree (uncommitted, rounds 1-7) and `continuum-feature-base` `main` rebuilt against it. Tests: `continuum-system-tests` `CONTNM-180` (uncommitted; no git writes).

### Environment

**Everything ran against the changed code.** `make up` was not used.

- **Infra.** The same as round 2: `docker-compose -p loops-systest -f docker/docker-compose.yml --env-file docker/.env up -d --wait <postgresql temporal temporal-search-attributes-init mosquitto kafka1-3 schema-registry minio>`. All were healthy. Nothing was running before. Stopped at the end with `down`, without `-v`.
- **Builds.** `--offline`, with all output in the scratch dir `/tmp/loops-systest-r3.4ls9Vt/gradle-build/` (init script redirecting `layout.buildDirectory`). No repo `build/` and no `~/.m2` were touched.
  - platform: `./gradlew --offline -q --init-script … :continuum-api-server:bootJar :continuum-orchestration-service:bootJar :continuum-message-bridge:bootJar` → OK;
  - feature-base worker: `./gradlew --offline -q --init-script … --include-build ../continuum-platform-core :worker:bootJar` → OK.
  - The orchestrator jar contains round-7 code: `TimeBoxedConditionEvaluationRunner.class`, `EvaluationInterruptCheck` in the nested `continuum-loop-condition-0.0.23.jar`, and `evaluationTimeExceededDetail` in commons.
- **Processes.** All `java -jar` in the background with explicit `SERVER_PORT`s, and all stopped by PID at the end (list in `pids.txt`):

  | Service | Port | PID(s) |
  |---|---|---|
  | api-server (CONTNM-180; `CONTINUUM_CACHE_DIRECTORY` in the scratch dir) | 8080 | 47551 |
  | message-bridge (CONTNM-180) | 8091 | 47552 |
  | orchestration-service (CONTNM-180) on the host, unthrottled | 8090 | 47589 → 48771 → 48897 → 49098 → 49836 (restarts after each stop test) |
  | orchestration-service (the same jar) in container `loops-systest-orch-throttled`, `--cpus=0.5` | — | removed at the end |
  | feature-base worker 0.0.10 (built against CONTNM-180; `CONTINUUM_NODE_TASK_QUEUE=CONTINUUM_FEATURE_BASE_TASK_QUEUE`) | 8092 | 47674 |
  | credentials-server 0.0.23 (unchanged module, existing jar, dev master key from `docker/docker-compose.yml`) | 8083 | 50941 |

- **Start-up mistakes, corrected before any result was taken:**
  - My first start had no `SERVER_PORT`. The credentials server took 8080, and the api-server, bridge and credentials server were stopped (47112-47114).
  - The first worker (47590) exited because `CONTINUUM_NODE_TASK_QUEUE` was unset.
  - The first credentials server (47561) had no `CREDENTIALS_ENCRYPTION_MASTER_KEY`. The first `make api` / smoke / tenancy runs failed only on credential creation; they were re-run after the fix (both outputs are kept).
- **Base URLs:** the `lib/config.js` defaults. k6 v2.4.0, node v24.18.0. Temporal CLI via `docker exec temporal temporal …`.

### Item 1: the review's one-call shape, live

**The literal shape cannot be built live:**
- No feature-base node sets a flow variable of the user's choosing. `grep setFlowVariable` over feature-base finds 0 hits, and `KotlinScriptNodeModel` gets no `ExecutionContext`.
- A run cannot be started with initial variables.
- The only list- or map-valued default variables are `inputCount` / `outputCount`, which are maps with one or two entries.

So `sets.equivalent(vars["a"], vars["b"])` over two ~9,000-element list variables is **NOT VERIFIED live** (it is unit- and Temporal-tested by the developer).

**Closest live shape: one cel-java call built from literals.** I timed candidates with `jshell` against the orchestrator jar's own libraries (`CelLoopConditionEvaluator`, scratch dir only):

| Literal condition | One evaluation |
|---|---|
| `cel.bind(s, "%s".format([lists.range(10000)]).split(", "), sets.equivalent(s, s.reverse()))` | 320-360 ms |
| `sets.intersects` over two disjoint 9,000 / 10,000-string lists | 195 / 245 ms |
| **`sets.intersects(a8, b7)`**: `a8` = 256 copies of `lists.range(10000)`, `b7` = 128 copies of `lists.range(9999) + [0]`, built by `cel.bind` doublings (477 characters, nesting under 32, every list ≤ 10,000 elements) | **1,173 ms** (one call, no early exit) |
| the same at (8,8) / (9,8) / (9,9) | 2.4 s / 4.8 s / 9.4 s |

So **`oneLongCallCondition(8, 7)`** is the live equivalent of the review's 1.3 s call. It is the same kind of cost: one uninterruptible O(n·m) `sets.*` call that the between-values budget cannot stop.

**Live result, unthrottled host orchestrator** (run `49289ffd`, plus 3 suite runs):
- Validate: `valid=true`, no error on L.
- The loop is FAILED: `$error` "Loop 'L': the condition check in iteration 1 failed with LIMIT_EXCEEDED: the condition exceeded a cost limit: the condition took longer than 200 ms to evaluate." There is no "(column N)", which shows the orchestrator's wait fired, not the evaluator's own budget. `after` is SKIPPED.
- The run record is **FAILED** (not RUNNING), 2.7 s after creation.
- Temporal history: `WORKFLOW_EXECUTION_FAILED`, **0 `WORKFLOW_TASK_FAILED`**, 0 timed out, **1 marker**. The workflow task that recorded the marker took **287 ms**.
- Orchestrator log: exactly **one WARN** for the run: "Loop 'L': the condition check in iteration 1 of workflow '49289ffd-…' (run '3c06f1a4-…') did not finish within 250 ms; it fails with LIMIT_EXCEEDED and its evaluation thread was interrupted". No value and no condition text in it. 0 TMPRL1101, 0 ERROR.

### Item 2: CPU throttling

**How I throttled:**
- I stopped the host orchestrator and ran the **same jar** in a container: `docker create --name loops-systest-orch-throttled --cpus=0.5 --memory=1g --network temporal-network … eclipse-temurin:21-jre java -jar /orch.jar`, then `docker cp` the jar in and start it.
- Verified inside the container: cgroup `cpu.max` = `50000 100000` (0.5 CPU, the chart's 500m), `nproc` = 1, `UseContainerSupport=true`. So the JVM sees 1 processor.
- The start-up line said "warmed up in 627 ms, with 4 evaluation threads" (220 ms on the host).

| Scenario (all on the throttled orchestrator) | Loop / run | Histories | Recording task | Log |
|---|---|---|---|---|
| 1 × `oneLongCall(8,7)`, then 1 × the `sets.equivalent` string shape | FAILED / FAILED, budget text | 0 WT failed, 0 timed out, 1 marker each | 516 ms, 413 ms | 1 WARN each |
| plus 2 shell busy-loops inside the container (sharing the 0.5 CPU, container at 51 %), 4 concurrent `oneLongCall(8,7)` | 4 × FAILED / FAILED, budget text | 0 WT failed, 1 marker each | 498-574 ms | 4 WARN |
| the busy-loops plus **8 concurrent ~2.4 s calls** (`(8,8)`, pool of 4 saturated) | 8 × FAILED: 4 with the budget text, 4 with "… all 4 condition evaluation threads of the orchestrator are busy" | 0 WT failed, 1 marker each | 525-921 ms | 4 "did not finish", 4 "all 4 are busy" |

Whole throttled log: **0 `TMPRL1101`, 0 `PotentialDeadlockException`**, 0 `NonDeterministic`. The 33 ERROR lines are all "Unable to send status … application context is not ready (or stopped)" during the final `docker stop` (shutdown noise from the progress signal handler, not a loop fault).

The slowest recording task under throttling plus saturation was 921 ms, under Temporal's 1 s detector but with little margin. That margin is the scheduling delay of 4 evaluation threads plus the workflow threads on 0.5 CPU, not the condition. It did not trip the detector in any run.

### Item 3: start-up line and stop time

- **Every orchestrator start** (5 host starts plus the container) logged "Loop condition evaluator installed and warmed up in 205-627 ms, with 4 evaluation threads".
- **Stopping during a running loop is not held up:**

  | Stop | What was running | Exit after SIGTERM |
  |---|---|---|
  | host, `kill -TERM`, 1 | a 40-iteration loop at iteration 7 + 3 runs with ~2.4 s calls just started | 585 ms |
  | host, 2 | a 300-iteration loop at iteration 7 + 6 runs with ~2.4 s calls, 150 ms after their start | 547 ms |
  | host, 3 | SIGTERM right after the first "did not finish within 250 ms" WARN, so 3 abandoned ~2.4 s evaluations were still running on the evaluation threads | 541 ms |
  | container, `docker stop -t 30` | a 200-iteration loop + 4 runs with ~2.4 s calls | 1,974 ms (exit code 143, no SIGKILL) |

- **After every stop the interrupted runs resumed on the next orchestrator and ended correctly:**
  - the 40- and 300-iteration loops ended FINISHED, "Stopped at the limit of 40/300 iterations", with 40 and 300 markers, `score` run 40 and 300 times;
  - every slow run ended FAILED with 1 marker.
- **No workflow-task failures, only task timeouts during the stops:**
  - Across all **160** workflows of this round: **`WORKFLOW_TASK_FAILED` = 0**.
  - The 15 `WORKFLOW_TASK_TIMED_OUT` events all fall in the stop windows (22:14:32-22:15:00 and 22:22:11-12 UTC) on runs that were in flight at a stop.
  - That is Temporal re-dispatching tasks of a stopped worker. Expected; not a fault.

### Item 4: regression

| Suite | Result |
|---|---|
| `./run.sh suites/api/loops.js` ×3 | **220/220** each (213 from round 2 + 7 new), `isolation_violations` 0, no `known_gaps`, exit 0 |
| `make api` | exit 0: workflow 15/15, runs 30/30, schedules 36/36, node-explorer 19/19, data 45/45, credentials 54/54, loops 220/220 |
| `./run.sh suites/smoke.js` | 10/10 |
| `./run.sh suites/tenancy/isolation.js` | 32/32, `isolation_violations` 0 |
| Temporal, the 27 workflows of loops run 1 | 0 `WORKFLOW_TASK_FAILED`. The round-2 BR-3 macro shape (`cbb5c38c`): FAILED, 1 marker, recording task 268 ms (the evaluator's own budget fired, "(column 20)"). Multi-iteration and paging-style loops all COMPLETED, as in round 2 |

### AC coverage (round 3)

| Item / AC | Check name(s) / evidence | File | Result |
|---|---|---|---|
| (1) BR-9 / AC-43: one long call fails the loop at the budget, the run FAILED, no wedge | "the one-long-call condition is accepted by the validator"; "one function call longer than the time budget fails the loop (FAILED) with the $error \"Loop 'L': the condition check in iteration 1 failed with LIMIT_EXCEEDE...\""; "one function call longer than the time budget: the node after the loop is SKIPPED"; "one function call longer than the time budget: the run is recorded FAILED"; "the one-long-call $error says it took longer than 200 ms"; "the one-long-call condition fails the run within 30 s (no wedged workflow task)"; plus history (0 WT failed, 1 marker) and log (1 WARN "did not finish within 250 ms") by hand | `suites/api/loops.js`, `lib/loopscenarios.js` | **PASS** with the literal one-call shape (1.17 s per call). The review's literal `sets.equivalent(vars["a"], vars["b"])` over two 9,000-element list variables: **NOT VERIFIED live** (no node can set list variables; unit/Temporal-tested) |
| (2) the same under CPU throttling, no TMPRL1101 | probe runs on the `--cpus=0.5` container (single, concurrent with CPU burn, pool saturated) | `/tmp/…/probe/onecall.mjs` (probe, not in the suite) | **PASS**: 0 TMPRL1101 / PotentialDeadlockException, 0 WT failed, every run FAILED with 1 marker |
| (3a) start-up "with 4 evaluation threads" | orchestrator logs | — | **PASS** (6 of 6 starts) |
| (3b) stop during a running loop is not held up | SIGTERM timing (table above) | — | **PASS**: 541-585 ms on the host, 1.97 s in the throttled container, also with abandoned evaluations running. The interrupted runs completed correctly on the next orchestrator |
| (4) round-6 checks: BR-3 macro shape ~260 ms, paging/multi-iteration loops finish | all 213 round-2 checks | `suites/api/loops.js` | **PASS** (×3 + `make api`) |
| Round-2 ACs (AC-3, 6, 7, 9, 10, 11, 13, 14, 16-22, 24, 25, 44, BR-1/2/3, F-1, tenant isolation) | unchanged round-2 checks | `suites/api/loops.js`, other suites | **PASS** |
| AC-5 retry, AC-23, AC-26, AC-27, AC-42, AC-43 (evaluator list) | — | — | NOT VERIFIED: not system-testable, as before |

### Failures

No check failed. One **finding from a probe** (not a k6 check), for the backend developer:

- **F-2 (BE-15 / BR-9 follow-up, cross-tenant): one tenant's slow conditions fail another tenant's ordinary loop.**
  - Request:
    - tenant `systest-probe-r3-tenantA` starts 4 runs whose condition is `oneLongCallCondition(9, 9)`, one cel-java call of about 9.4 s. It is valid (546 characters), and the validator gives no warning about its cost;
    - as soon as those 4 checks are abandoned (4 "did not finish within 250 ms" WARNs), tenant `systest-probe-r3-tenantB` starts a plain loop with `vars["anomalyDetectorZScoreNode_score.executionStatus"] == "SUCCESS"`, `maxIterations` 3, `stop-with-warning`;
    - probe `/tmp/loops-systest-r3.4ls9Vt/probe/crosstenant.mjs`, unthrottled host orchestrator.
  - Expected: tenant B's loop stops at its limit with WARNING and the run is FINISHED. That is the result on an idle orchestrator (round-2 checks).
  - Actual: tenant B's run `9c6fdb2b-…` is **FAILED**. Loop L `$error`: "Loop 'L': the condition check in iteration 1 failed with LIMIT_EXCEEDED: the condition exceeded a cost limit: the condition could not be evaluated in time: all 4 condition evaluation threads of the orchestrator are busy."
  - The history records the failure: 1 marker, `WORKFLOW_EXECUTION_FAILED`. A retry of B's run would fail again until the abandoned calls return.
  - The same happened unprompted under throttling: a 200-iteration plain loop started while 4 earlier abandoned calls still ran failed in iteration 1 (run `496f3eef-…`).
  - Cause:
    - the 4-thread pool is shared by all workflows and tenants;
    - an abandoned call keeps its thread until the cel-java function returns. The interrupt is seen only between values, and the developer documents this as the "residual limit";
    - text and element caps bound one call only to about 2.4 s at (8,8), 4.8 s at (9,8) and 9.4 s at (9,9), all well under 2,048 characters;
    - so 4 cheap-to-write conditions starve every other loop on that orchestrator for seconds per check, and each re-run of them renews the block. The developer's note says this "only affects runs whose condition is itself that slow". That is not true across runs or across tenants: the busy outcome is recorded on the victim's run.
  - Owner: backend developer. Possible directions, for the orchestrator to raise with the user, since the user chose "timeout via executor":
    - bound the cost of one `sets.*` / list-equality call before it runs (fix option (b) from the BR-9 review: metered/hashed `sets.*`, or an argument-size cap);
    - and/or do not fail an innocent check when the pool is busy: give each workflow its own slot, or fall back to evaluating a check on the workflow thread when the check is cheap;
    - and/or document it as a known limit in the Upgrade notes.

Observations, not failures:
- Under 0.5 CPU with the pool saturated, a recording workflow task took up to 921 ms (detector: 1 s). No TMPRL1101 occurred, but the margin is thin on a throttled pod.
- The 33 shutdown ERRORs "Unable to send status … application context is not ready (or stopped)" come from the progress-signal handler while the context stops. Not loop-specific.
- The round-1 leftover `continuum-platform-core/.continuum-cache/` (git-ignored) is still there; this round created nothing in the repos.

### Files changed (repo `continuum-system-tests`, branch `CONTNM-180`, uncommitted; on top of rounds 1-2)

- `lib/loopscenarios.js`:
  - `oneLongCallCondition(doublingsA, doublingsB)`: one `sets.intersects` over two lists of 10,000-element lists, built by `cel.bind` doublings;
  - `ONE_LONG_CALL_CONDITION` (= (8,7));
  - `oneLongCallLoop`.
- `test/loopscenarios.test.js`: the new builder in the nothing-to-run test, plus 1 new test (one `sets.` call, ≤ 2,048 characters, nesting ≤ 32, reads no variable, the exact text at (1,0)).
- `suites/api/loops.js`: the 'one long function call' scenario in `nothingToRunChecks` (validator check, `failedLoop`, budget text, within 30 s); import; section comment.
- `README.md`: suite table row; "Loop suite: what it can and cannot run" gains the time-budget bullet (why the real-world shape is not drivable).
- `AGENTS.md`: the loop-suite contract names the two variable-free time-budget conditions and their caps.
- Factory-ID grep over the changed files: 0 hits.

### Commands run

| Command | Result |
|---|---|
| `docker ps -a`; port probe | nothing running |
| `docker-compose -p loops-systest … up -d --wait <infra>` | all healthy, exit 0 |
| platform `bootJar` ×3 and feature-base `:worker:bootJar` (`--offline`, scratch build dir, `--include-build`) | OK |
| `java -jar` api-server / bridge / credentials / orchestration / worker | see Environment (3 start-up corrections) |
| `jshell --class-path <orchestrator BOOT-INF/lib> probe{1..9}.jsh` | the timing table in item 1 |
| `node probe/onecall.mjs intersects87 1` (host) | FAILED / FAILED, budget text, 1 WARN, 0 WT failed, 1 marker, 287 ms task |
| `node probe/longloop.mjs`, `onecall.mjs intersects88 N`, `kill -TERM <orchestrator>` ×3, timed | exit 585 / 547 / 541 ms; the runs completed after a restart |
| `docker create --cpus=0.5 …` + `docker cp` + `docker start`; `cat /sys/fs/cgroup/cpu.max`, `nproc` | 50000 100000, 1 CPU |
| `onecall.mjs` ×(1, 1, 4 with burn, 8 with burn) on the container | all FAILED / FAILED, 0 TMPRL1101, 0 WT failed |
| `docker stop -t 30 loops-systest-orch-throttled` | 1,974 ms, exit 143 |
| `node probe/crosstenant.mjs` (host) | F-2 reproduced |
| `python3 history-summary.py`, `task-timing.py` (per history: WT failed / timed out, markers, recording-task ms) | quoted above; all 160 workflows: 0 WT failed |
| `npm test` | PASS 75/75 (74 + 1 new) |
| `npm run check` | PASS |
| `./run.sh suites/api/loops.js` ×3 | 220/220, exit 0 |
| `make api` (after the credentials key fix) | exit 0, all 7 suites 100 % |
| `./run.sh suites/smoke.js`, `suites/tenancy/isolation.js` | 10/10, 32/32 |
| `kill` 47551 47552 49836 47674 50941; `docker rm loops-systest-orch-throttled` | all stopped (`kill -0` verified; the bridge took 8-18 s to exit on SIGTERM, no `kill -9` needed) |
| `docker-compose -p loops-systest … down` (no `-v`) | no containers left; ports free |

Logs and outputs: `/tmp/loops-systest-r3.4ls9Vt/`:
- `orchestration-{1..5}.log`, `orchestration-throttled.log`, `api-server.log`, `message-bridge.log`, `worker.log`, `credentials{-1,}.log`;
- `k6-run{1,2,3}.txt`, `make-api.txt`, `k6-smoke.txt`, `k6-isolation.txt` (and the `*-1-credentials-misconfigured.txt` first attempts);
- `probe-*.txt`, `history-*.txt`, `stop-timing.txt`, `list-all.json`, `probe/`, `jsh/`, `pids.txt`.

## Round 4

Date 2026-11-26. Final re-check after BE fix round 3 (`03-backend-dev.md` "Round 8 — fix round 3" plus "Round 8 addendum": the own-run rule was removed, and every busy-pool case is now retried). I used the addendum's "For the system tester" list. Code under test: `continuum-platform-core` `CONTNM-180` working tree (uncommitted, rounds 1-8 plus the addendum) and `continuum-feature-base` `main` rebuilt against it. Tests: `continuum-system-tests` `CONTNM-180` (uncommitted; no git writes).

### Environment

**Everything ran against the changed code.** `make up` was not used.

- **Infra:** the same as rounds 2-3, `docker-compose -p loops-systest -f docker/docker-compose.yml --env-file docker/.env up -d --wait` with postgresql, temporal, temporal-search-attributes-init, mosquitto, kafka1-3, schema-registry and minio. All were healthy and nothing was running before. Stopped at the end with `down`, without `-v`.
- **Builds** (`--offline`; all output in the scratch dir `/tmp/loops-systest-r4.pfJvoH/gradle-build/` through an init script that redirects `layout.buildDirectory`; no repo `build/` and no `~/.m2` touched):
  - platform: `./gradlew --offline -q --init-script … :continuum-api-server:bootJar :continuum-orchestration-service:bootJar :continuum-message-bridge:bootJar` → OK;
  - worker: `./gradlew --offline -q --init-script … --include-build ../continuum-platform-core :worker:bootJar` (feature-base) → OK.
  - The orchestrator jar holds the addendum code: `workflow/loop/EvaluationTicket.class` is present and `AbandonedEvaluations` is gone. `TimeBoxedConditionEvaluationRunner` has the texts "; nothing is recorded and the workflow task is retried" and "are held by abandoned evaluations".
- **Processes:** all `java -jar` in the background with explicit `SERVER_PORT`s, every one stopped by PID at the end (`pids.txt`). The round-3 start-up settings were applied from the first start, so there were no start-up corrections this time.

  | Service | Port | PID |
  |---|---|---|
  | api-server (`CONTINUUM_CACHE_DIRECTORY` in the scratch dir) | 8080 | 82185 |
  | message-bridge | 8091 | 82186 |
  | credentials-server 0.0.23 (unchanged module, existing jar, `CREDENTIALS_ENCRYPTION_MASTER_KEY` = the dev key from `docker/docker-compose.yml`) | 8083 | 82187 |
  | orchestration-service, host, unthrottled (`CONTINUUM_API_SERVER_BASE_URL=http://localhost:8080`) | 8090 | 82240 (items 1 and 3); after the throttled container, 87134 (item 4, wedge runs 2-3) |
  | orchestration-service, the same jar in container `loops-systest-orch-throttled` (`--cpus=0.5 --memory=1g`, network `temporal-network`) | — | removed at the end |
  | feature-base worker 0.0.10 (`CONTINUUM_NODE_TASK_QUEUE=CONTINUUM_FEATURE_BASE_TASK_QUEUE`) | 8092 | 82241 |

- **Start-up line:** every orchestrator start logged "Loop condition evaluator installed and warmed up in … ms, with 4 evaluation threads": 282 and 212 ms on the host, 706 and 683 ms in the container. The Loop was published on attempt 1.
- **Base URLs:** the `lib/config.js` defaults. k6 v2.4.0, node v24.18.0. Temporal CLI via `docker exec temporal temporal …`.
- **Two workflows `br-r4-retry-probe-*` (`RetryProbeWorkflow`, own task queues) are not mine.** They were started on the shared Temporal at 06:44 and 06:52 UTC by another process, probably the reviewer. They are excluded from every count below.

### Item 1: F-2 cross-tenant probe, now also a suite check

**Probe** (`probe/crosstenant4.mjs`, unthrottled host orchestrator):
- tenant A starts 4 runs with `oneLongCallCondition(9, 9)` (about 9 s per call);
- as soon as the 4 "did not finish within 250 ms" WARNs are logged, tenant B starts a plain loop (`vars["anomalyDetectorZScoreNode_score.executionStatus"] == "SUCCESS"`, maxIterations 3, stop-with-warning).

I ran it 3 times:

| Run | Tenant B: run / loop | B's delay (record span) | B's history | WARN "… nothing is recorded and the workflow task is retried" naming B's run | Tenant A: 4 runs |
|---|---|---|---|---|---|
| `9d75d1e4` | FINISHED / WARNING "Stopped at the limit of 3 iterations", `after` SUCCESS | 11.5 s | 171 events, `COMPLETED`, **3 markers (1 per check)**, 1 `WORKFLOW_TASK_FAILED`, 0 timed out, `score` ran 3× | 2 | FAILED, `$error` "…LIMIT_EXCEEDED: the condition exceeded a cost limit: the condition took longer than 200 ms to evaluate.", `after` SKIPPED; 1 marker, 0 WT failed each |
| `2f25c759` | FINISHED / WARNING, the same | 11.2 s | 180 events, 3 markers, 2 WT failed | 3 | the same |
| `45f4256a` | FINISHED / WARNING, the same | 21.5 s | 168 events, 3 markers, 1 WT failed | 3 | the same |

- The WARN is "Loop 'L': the condition check in iteration 1 of workflow '<B>' (run '<B run>') did not get an evaluation thread within 250 ms: all 4 are busy; nothing is recorded and the workflow task is retried". No `$error` contains "busy".
- Tenant A reading B's run gets 404 each time.

**Suite check: reliable, so it is now in the suite.** The new `sharedThreadChecks` section in `suites/api/loops.js` works like this:
1. the second tenant (`…-loops-other`) starts 4 loops with the new `THREAD_HOLDING_CONDITION` (= `oneLongCallCondition(9, 9)`);
2. it waits until their loops are decided (the checks were given up);
3. the owner then starts `cheapLimitLoop`.

The checks:
- "a cheap loop whose check finds every thread held by another tenant still ends WARNING \"Stopped at the limit of 3 iterations\"";
- "a cheap loop delayed by another tenant's held threads: the node after it ran and the run is FINISHED, not FAILED";
- "the cheap loop was delayed by the held threads (record span over 3 s), so its check really met a busy pool";
- "the other tenant's thread-holding loops fail with the 200 ms budget text, none with a busy text";
- "the other tenant's thread-holding runs are recorded FAILED";
- "delayed loop run: hidden from other tenant (404)", paired with the owner's own reads;
- plus 5 start / precondition checks.

Results: 4 of 4 suite runs passed (3 direct runs plus `make api`). Each one shows one retried task on the cheap run in the history sweep, with cause "no loop condition evaluation thread is free". The timing margin is wide: the holders keep the threads for about 9 s, and B's check comes 1-2 s after the abandons. The section adds about 20 s to the suite.

### Item 2: throttled orchestrator (`--cpus=0.5`), 8 × `oneLongCall(8,8)` with 2 CPU busy-loops in the container

- Container check: cgroup `cpu.max` = `50000 100000`, `nproc` = 1.
- I ran the batch twice: once on a **cold** container (the first workflows after its start) and once on the **warm** container.

| Batch | Runs / loops | `$error` | Busy text | Histories | Recording task |
|---|---|---|---|---|---|
| cold (06:45 UTC) | **8/8 FAILED / FAILED** | all 8 "…the condition took longer than 200 ms to evaluate." | none | 1 marker each. 4 recorded at once (record span about 8.8 s); 4 only after 2-4 retries (spans 61.9-64.8 s) | 523-977 ms |
| warm (06:49 UTC) | **8/8 FAILED / FAILED** | all 8 the budget text | none | 1 marker each. 4 at once (1.7-2.0 s); 3 after retries (55.5-58.0 s); 1 straggler `676ae955` after 7 retries, ended on its own after **130 s** | not measured |

- In the log, the retried checks show "did not get an evaluation thread within 250 ms: all 4 are busy; …retried", and once the late-start variant "got an evaluation thread only 198 ms before its 250 ms wait ended: the threads were busy; …". That second line shows the BR-13 late-start rule working live.
- **TMPRL1101:**
  - **warm batch: 0**. All 6 `WORKFLOW_TASK_FAILED` there are "no loop condition evaluation thread is free".
  - **cold batch: one TMPRL1101 on the very first workflow task of each of the 8 runs.** Each one came before any loop code ran. The workflow thread was parked on Jackson's `DeserializerCache` lock while it decoded the workflow input (`POJOWorkflowImplementation.execute` → `JacksonJsonPayloadConverter.fromData`).
  - It is **not loop-specific**. 8 **loop-free** runs (seed → after) on a freshly restarted throttled container hit it 3 times each: the same Jackson lock, then `StreamBridge.resolveDestination` in `StatusHelper.publishWorkflowSnapshot`, then a kotlin-reflect lazy lock. All of them completed normally after the retry.
  - So it is JVM warm-up of the orchestrator on 0.5 CPU under a burst. It is not the condition evaluation, and it changed no outcome.
  - The SDK does not log these: `grep TMPRL1101` over the orchestrator logs is 0, and only the history shows them. Round 3's "0 TMPRL1101" came from that log grep. Its history sweep (0 WT failed over 160 workflows) still holds, because round 3 warmed the container with single runs first.
- The longest recording task under throttling was 977 ms (round 3: 921 ms). The margin against the 1 s detector is still thin, but it was not tripped by a loop check.

### Item 3: documented self-starvation (evidence for a known limit, not an AC verdict)

**Wedge 1, `e4ca01f0`** (5 parallel loops, `ONE_LONG_CALL_CONDITION`, `check: after`, host orchestrator):
- validate: valid, with the 5 "reads no flow variable" warnings;
- **stayed RUNNING for 12 min 22 s**, from 06:30:33 to my terminate at 06:42:54. It **never ended on its own**;
- the Temporal history stayed at **97 events from the first failure until terminate**: 1 `WORKFLOW_TASK_FAILED` (event 97), 0 `WORKFLOW_TASK_TIMED_OUT`, **0 markers**. That is **0 history events per further retry**, because the later attempts are transient and never written;
- retries (one "did not get an evaluation thread … all 4 are busy; … retried" WARN for L5 each) at local 12:00:34.5, :45.9, :55.9, 12:01:10.8, :28.8, :55.9, 12:02:42.9, 12:04:12.6, 12:06:35.1 and 12:11:05.7. The **gaps grow**: about 11, 10, 15, 18, 27, 47, 90, 143 and 270 s. At terminate, the pending workflow task was on **attempt 11**;
- each attempt logs 5 WARNs (L1-L4 "did not finish within 250 ms; it fails with LIMIT_EXCEEDED…", L5 "…retried"): 51 WARN lines in all, plus 15 ERROR stack traces "uncaught exception … Failure processing workflow task … Attempt=n … ConditionEvaluationUnavailable" from the SDK's poller;
- the run record meanwhile shows L1-L4 FAILED with the budget `$error`, L5 BUSY "Iteration 1 of 3" and progress 83 %, published by attempts that were never recorded;
- **can it be terminated:** yes. Another tenant's `POST /api/v1/workflow/{id}/terminate` → 404; the owner's → 204; Temporal `WORKFLOW_EXECUTION_TERMINATED` 5 s later. **But the run record stays `RUNNING`** (still RUNNING 12 minutes later, and listed by `status==RUNNING`): see F-3.

**Other tenants during wedge 1: all FINISHED, none FAILED.**
- tenant C: 1 plain loop, FINISHED WARNING in 1.0 s;
- tenant D: **150 plain loops** started 2 s apart (06:37-06:42 UTC): **150/150 FINISHED**, loop WARNING "Stopped at the limit of 3 iterations"; median 1.0 s, max 11.2 s;
- the one slow run, `cfbc24d6`, met the busy pool during a wedge retry. It logged 2 "…retried" WARNs and finished 10 s later: delayed, not failed.

**Wedge 2, `ba20ccc5`** (the same shape, on the restarted orchestrator) **did not wedge**:
- only 4 of the 5 checks fell into one workflow task, because L4's member activity completed into the next task;
- L4's check met its own run's 4 abandoned calls, was retried once, got a thread 300 ms later (an `(8,7)` call returns in about 1.2 s) and was recorded;
- result: run FAILED after 2 s, 5 markers, 1 WT failed.

So the limit applies only when all checks land in one workflow task, as the release note says.

**Wedge 3, `fa4b6644`** (5 loops with `check: before`, so all 5 checks always come in one task):
- stayed RUNNING for 4 min 47 s with 25 history events (1 WT failed, 0 markers, no growth);
- retries at growing gaps (1, 10, 10, 15, 19, 27, 44, 82 s);
- tenant E's 3 plain loops meanwhile: FINISHED in 0.5-1.0 s;
- cancel: another tenant → 404; owner → 204;
- **the record became `CANCELLED`**, because a retry attempt after the cancel ran the signal handler, published, then failed again;
- Temporal stayed RUNNING with cancel requested, because the wedged task can never process it. A follow-up terminate (204) ended it in Temporal (`TERMINATED`), and the record stayed `CANCELLED`.

At the end, `temporal workflow list` shows **no workflow still running**.

### Item 4: regression

| Suite | Result |
|---|---|
| `./run.sh suites/api/loops.js` ×3 | **232/232** each (the 220 from round 3 plus 12 new shared-thread checks), `isolation_violations` 0, no `known_gaps`, exit 0 |
| `make api` | exit 0: workflow 15/15, runs 30/30, schedules 36/36, node-explorer 19/19, data 45/45, credentials 54/54, loops 232/232 |
| `./run.sh smoke` | 10/10 |
| `./run.sh tenancy` | 32/32, `isolation_violations` 0 |
| History sweep over the 136 workflows of the 4 loops runs (`sweep.py`) | The single one-long-call run of each suite run: **0 `WORKFLOW_TASK_FAILED`**, 1 marker (4 of 4). The 16 thread-holding runs: 0 WT failed, 1 marker. The only 4 WT failures are the 4 intended retries of the cheap shared-thread loop. 0 TMPRL1101 |

History sweep over **all 332 of my workflows** of the round:
- the TMPRL1101 seen are only the 32 on the cold throttled container (item 2);
- the 22 "no thread free" failures are only the retried checks of items 1-3 and of the shared-thread suite check;
- there are no other workflow-task failures.

### AC coverage (round 4)

| Item / AC | Check name(s) / evidence | File | Result |
|---|---|---|---|
| (1) BR-10 / F-2 / AC-43: another tenant's held threads delay a cheap loop, never fail it; the holders fail with the budget text | probe ×3 (table above), plus the suite checks: "a cheap loop whose check finds every thread held by another tenant still ends WARNING …"; "… the node after it ran and the run is FINISHED, not FAILED"; "the cheap loop was delayed by the held threads (record span over 3 s) …"; "the other tenant's thread-holding loops fail with the 200 ms budget text, none with a busy text"; "the other tenant's thread-holding runs are recorded FAILED"; "delayed loop run: hidden from other tenant (404)" | `suites/api/loops.js` (`sharedThreadChecks`), `lib/loopscenarios.js`; probe `/tmp/…/probe/crosstenant4.mjs` | **PASS** (probe 3/3, suite 4/4). B's history has 1 marker per check, 1-2 WT failed, and the WARN names B's run |
| (2) throttled 8 × (8,8): all FAILED with the budget text, none busy, 0 TMPRL1101 | probe `onecall.mjs intersects88 8` on the `--cpus=0.5` container, cold and warm | probe | **PASS** for the loop criteria (16/16 FAILED with the budget text, 0 busy texts, 1 marker each). 0 TMPRL1101 on the warm orchestrator. On the cold container: 1 TMPRL1101 per run on the first workflow task, in Jackson start-up and not loop code, and loop-free runs show the same (observation O-1, not a loop defect) |
| (3) one run, 5 parallel slow loops (documented limit) | wedges 1-3 (above) | probes `wedge.mjs`, `watch.mjs`, `plain.mjs` | **Documented-limit evidence, no verdict.** It stays RUNNING when all checks fall into one task (12 min 22 s and 4 min 47 s observed; never ends on its own), with 0 history growth after the first failure and retries at growing intervals. It can be cancelled or terminated. Other tenants' loops all finished (151 + 3; 1 delayed by 10 s). Terminate leaves the record RUNNING (F-3) |
| (4) round-3 one-long-call checks, 0 WT failed for a single slow run; full regression | all round-1..3 checks | `suites/api/loops.js`, other suites | **PASS** |
| Round 1-3 ACs (AC-3, 6, 7, 9, 10, 11, 13, 14, 16-22, 24, 25, 44, BR-1/2/3/9, F-1, tenant isolation) | unchanged checks | `suites/api/loops.js`, other suites | **PASS** |
| AC-5 retry, AC-23, AC-26, AC-27, AC-42 | — | — | NOT VERIFIED: not system-testable, as before |

### Failures

No k6 check failed, and no AC failed. One finding and two observations come from the probes:

- **F-3 (finding; reached through the documented limit; the terminate code itself predates loops): terminating a self-starved run leaves its run record `RUNNING` for good.**
  - Request: `POST /api/v1/workflow/e4ca01f0-…/terminate` (owner) on wedge 1 → 204; Temporal → `WORKFLOW_EXECUTION_TERMINATED`.
  - Expected: `GET /api/v1/workflow-runs/{id}` shows `status: "TERMINATED"`, as for any terminated run.
  - Actual: `status: "RUNNING"`, `updatedAt` frozen at the last failed attempt (06:41:05). It was still RUNNING 12 minutes later and is listed by `status==RUNNING`.
  - Cause: the api-server relies on the `notifyTerminating` signal handler to publish `TERMINATED` (`WorkflowService.terminateWorkflow` → `ContinuumWorkflow.notifyTerminating`). A self-starved run processes no workflow task: the next attempt was minutes away because of the growing backoff, and the hard terminate follows 5 s later. So the handler never runs and nothing publishes the final status.
  - Cancel only worked in wedge 3 because a retry attempt happened to run inside the window.
  - The release notes' advice for this limit ("terminate a run that is stuck this way") therefore leaves a run that the UI shows as running forever.
  - Owner: backend developer (BE-15, the documented-limit remedy). `SCOPE:` the terminate path is outside the loops plan. Possible fixes: the api-server or message-bridge writes `TERMINATED` (or `CANCELLED`) to the record itself once Temporal confirms the stop, or the limit's docs say how to clean up the record.
- **O-1 (observation, not loops): TMPRL1101 on a cold orchestrator with 0.5 CPU.**
  - The first workflow tasks after start-up exceed the 1 s detector while Jackson, Spring Cloud Stream and kotlin-reflect initialise. This was seen on loop-free runs as well.
  - Temporal retries the task and the run completes, so no outcome changes. The SDK does not log it; only the history shows it.
  - `SCOPE:` the backend developer may want to warm these up at start, as is already done for cel-java.
- **O-2 (observation, docs): retries are not paced at one per 10 s; the gaps grow.**
  - Measured gaps were about 10 s at first, then 15, 18, 27, 47, 90, 143 and 270 s (attempt 11 after 12 min).
  - So "a waiting check runs again at most once per 10 s" is true as an upper rate. But "delayed about 10 to 20 s" holds only when the threads free up within the first one or two retries. Under throttling, delayed runs ended after 55-65 s, and one after 130 s.
  - The release notes and orchestration `AGENTS.md` could say that the retry interval grows with each attempt.
  - Also: every retry after the first logs a full ERROR stack trace ("uncaught exception … ConditionEvaluationUnavailable") from the SDK poller, which operators may read as a fault.
- While a run self-starves, its record shows loops FAILED (L1-L4) that are not in the history. They come from attempts that were never recorded. This is informational.

### Files changed (repo `continuum-system-tests`, branch `CONTNM-180`, uncommitted; on top of rounds 1-3)

- `lib/loopscenarios.js`:
  - `THREAD_HOLDING_CONDITION` (= `oneLongCallCondition(9, 9)`);
  - `EVALUATION_THREADS` (4);
  - `oneLongCallLoop(base, condition = ONE_LONG_CALL_CONDITION)`;
  - new `cheapLimitLoop`.
- `suites/api/loops.js`:
  - new `sharedThreadChecks` section (plus `waitForLoopsDecided`), run after `nothingToRunChecks`;
  - imports;
  - header comment.
- `test/loopscenarios.test.js`: 1 new test (the thread-holding condition is within the caps and reads no variable; the shape of the cheap loop).
- `README.md`:
  - the suite table row;
  - "Loop suite: what it can and cannot run" gains the shared-thread bullet ("a loop's `$error` never names busy threads"; delayed about 10-20 s; about 20 s more per suite run) and a bullet on why the self-starvation limit is not in the suite.
- `AGENTS.md`: the loop-suite contract names `THREAD_HOLDING_CONDITION`, plus a new contract: shared threads; never assert a "busy" `$error`; never put a self-starving run in a suite.
- `Makefile`: unchanged (`loops` already in `API_SUITES`).
- Factory-ID grep over the changed files: 0 hits.

### Commands run

| Command | Result |
|---|---|
| `docker ps -a`; port probe | nothing running |
| `docker-compose -p loops-systest … up -d --wait <infra>` | exit 0, all healthy |
| platform `bootJar` ×3 and feature-base `:worker:bootJar` (`--offline`, scratch build dir, `--include-build`) | OK; addendum classes in the jar |
| `java -jar` api-server / bridge / credentials / orchestration / worker | all up at the first try; Loop published on attempt 1 |
| `node probe/crosstenant4.mjs <log> r4a|r4b|r4c` | 3/3: B FINISHED/WARNING after 11.2-21.5 s; A 4× FAILED with the budget text |
| `python3 history-summary.py`, `task-timing.py` | quoted above |
| `node probe/wedge.mjs after 8 7 5`, `watch.mjs` (about 13 min in all), `plain.mjs` ×151 (tenants C, D) | wedge 1 RUNNING for 12 min 22 s; 151/151 other-tenant loops FINISHED |
| `POST …/terminate` (tenant C → 404, owner → 204) | Temporal TERMINATED; record RUNNING (F-3) |
| `kill -TERM 82240`; `docker create --cpus=0.5 …`, `docker cp`, `docker start`; `cat /sys/fs/cgroup/cpu.max`, `nproc` | `50000 100000`, 1 |
| 2 × `sh -c 'while :; do :; done'` in the container + `onecall.mjs intersects88 8` (cold, then warm) | 16/16 FAILED with the budget text |
| `docker restart` + `loopfree.mjs 8` (with CPU burn) | 8/8 FINISHED, 3 TMPRL1101 each (O-1) |
| `docker stop -t 30` / `docker rm loops-systest-orch-throttled`; host orchestrator restarted (87134) | OK |
| `npm test` | PASS 76/76 (75 + 1 new) |
| `npm run check` | PASS |
| `./run.sh suites/api/loops.js` ×3 | 232/232, exit 0 |
| `make api` | exit 0, all 7 suites 100 % |
| `./run.sh smoke`, `./run.sh tenancy` | 10/10, 32/32 |
| `node probe/wedge.mjs after 8 7 5` (wedge 2), `wedge.mjs before 8 7 5` (wedge 3) + `plain.mjs` ×3 (tenant E), `watch.mjs` | wedge 2 FAILED after 2 s; wedge 3 RUNNING for 4 min 47 s, others FINISHED |
| `POST …/cancel` (tenant E → 404, owner → 204), then `…/terminate` (204) | record CANCELLED; Temporal TERMINATED |
| `python3 sweep.py 2026-11-26T06:20:00Z` (every history of the round) | 334 workflows (2 not mine), counts above; none left running |
| `kill -TERM 87134 82185 82186 82187 82241` | all stopped (`kill -0` verified) |
| `./gradlew --stop` (platform-core, feature-base); `pgrep GradleDaemon\|KotlinCompileDaemon` | stopped; none left |
| `docker-compose -p loops-systest … down` (no `-v`) | no containers left; ports free |

Logs and outputs: `/tmp/loops-systest-r4.pfJvoH/`:
- `orchestration-1.log`, `orchestration-2.log`, `orchestration-throttled{,-1,-2}.log`, `api-server.log`, `message-bridge.log`, `worker.log`, `credentials.log`;
- `k6-run{1,2,3}.txt`, `make-api.txt`, `k6-smoke.txt`, `k6-isolation.txt`;
- `probe-*.txt`, `history-*.txt`, `sweep-{suites,all}.txt`, `probe/`, `pids.txt`.

## Round 5

Date 2026-11-26. Re-check of BE fix round 4 (`03-backend-dev.md` "Round 9 — fix round 4": F-3 terminate of a stuck run, plus the bridge `TERMINATED` guard), items (1)-(5) of "For the system tester (live re-check)". Code under test: `continuum-platform-core` `CONTNM-180` working tree (uncommitted) and `continuum-feature-base` `main` against it. Tests: `continuum-system-tests` `CONTNM-180` (uncommitted; no git writes).

This round ran in two sittings. The first (14:31-15:06 IST) built the jars, started the stack, wrote the probes and ran the live probes and the first suite run. It was interrupted with the stack still up. In the second (15:23-15:35) I confirmed that the stack was mine (`pids.txt` matched the five `java -jar` PIDs, the jars were my scratch builds, nothing had changed in the sources after the build), re-ran every regression suite, and cleaned up. The probe outputs below come from the first sitting; the suite runs and the log checks from the second.

### Environment

**Everything ran against the changed code.** `make up` was not used.

- **Infra:** `docker-compose -p loops-systest -f docker/docker-compose.yml --env-file docker/.env up -d --wait` (postgresql, temporal, temporal-search-attributes-init, mosquitto, kafka1-3, schema-registry, minio), all healthy. Stopped at the end with `down`, without `-v`.
- **Builds** (`--offline`, output in the scratch dir `/tmp/loops-systest-r5.acEOht/gradle-build/` through the init script that redirects `layout.buildDirectory`; no repo `build/` touched): platform `:continuum-api-server:bootJar :continuum-orchestration-service:bootJar :continuum-message-bridge:bootJar`; feature-base `:worker:bootJar --include-build ../continuum-platform-core`. Jar check: the api-server jar holds `WorkflowStopScheduler`, `TimerWorkflowStopScheduler` and `WorkflowRunRepository.markTerminated`; the bridge jar holds the `TERMINATED` guard. No file in `continuum-platform-core` is newer than the build (14:32).
- **Processes** (all `java -jar`, explicit ports, all stopped by PID at the end; the bridge needed about 40 s to exit on SIGTERM, no `kill -9`):

  | Service | Port | PID |
  |---|---|---|
  | api-server | 8080 | 98244 |
  | message-bridge | 8091 | 98245 |
  | credentials-server 0.0.23 (existing jar, unchanged module) | 8083 | 98246 |
  | orchestration-service (host, unthrottled) | 8090 | 98247 |
  | feature-base worker 0.0.10 | 8092 | 98248 |

- Base URLs: the `lib/config.js` defaults. k6 v2.4.0, Node v24.18.0, Temporal CLI through `docker exec temporal temporal …`.

### Item 1: terminate of a stuck run (F-3)

**Probe** `probe/stuckterminate.mjs` (5 parallel `check: before` loops with `ONE_LONG_CALL_CONDITION`; waits for 2 "… nothing is recorded and the workflow task is retried" WARNs naming the run; polls every 500 ms):

| Step | Observed |
|---|---|
| Run is stuck | after 5.7 s: record `RUNNING`, Temporal `RUNNING`, 25 events, 1 `WORKFLOW_TASK_FAILED`, 0 markers, last event `WORKFLOW_TASK_FAILED` |
| Other tenant `POST …/terminate` | **404**; its `GET /api/v1/workflow-runs/{id}` **404** |
| Owner `POST …/terminate` | **204** at 09:11:24.189Z |
| +23 ms | record `RUNNING`, `updatedAt` unchanged (last failed attempt) |
| **+5189 ms** | record **`TERMINATED`**, `updatedAt` 09:11:29.221Z = **stop + 5032 ms** |
| After 20 s | record `TERMINATED`; Temporal `WORKFLOW_EXECUTION_TERMINATED` (27 events, 3 signals); **not** in `?filter=status==RUNNING`; **is** in `status==TERMINATED` |
| After 60 s and 120 s | still `TERMINATED`, `updatedAt` unchanged (09:11:29.221Z), not in the RUNNING list |
| End | owner `GET` 200; other tenant `GET` 404 |

Repeats: the suite's own stuck run passed 2 more times (suite runs 1 and 2) and a third time inside `make api`; every history sweep entry for these runs (`WORKFLOW_EXECUTION_TERMINATED`, 27 events, 0 markers) agrees.

**Suite checks (new, `terminateChecks` in `suites/api/loops.js`, 12 checks for the stuck run):** run starts (200); still `RUNNING` 6 s after its start (so it really is stuck); other tenant's terminate 404; other tenant's record read 404; another tenant's terminate left it `RUNNING`; owner's terminate 204; recorded `TERMINATED`, and no status other than `RUNNING` or `TERMINATED` ever seen; `updatedAt` about 5 s after the terminate (dated with the server's `Date` header); not listed under `status==RUNNING`; listed under `status==TERMINATED`; still `TERMINATED` 15 s later; owner can read, other tenant 404 (`expectHidden`, paired with the owner reads). The run is stopped in a `finally`.

**api-server log:** 0 lines "Failed to terminate" and 0 ERROR lines over the whole life of the process (200 lines in total; the only WARNs are the start-up bean-post-processor and cache-metrics ones). The message-bridge log has 0 ERROR lines.

### Item 2: healthy-run regression

**Probe** `stopprobe.mjs long` (loop with a cheap condition, `maxIterations` 200; terminate once iterating, then poll every 200 ms for 25 s):
- terminate **204** at 1.1 s after the start, run at "Iteration 5 of 200";
- the status sequence was `RUNNING` → `TERMINATED`, **one final status**. `TERMINATED` appeared at +234 ms (178 ms after the stop), written by the run's own signal handler, **long before the 5 s grace period**;
- the snapshot kept the in-flight nodes as the run published them (`L=BUSY` "Iteration 7 … 41 of 200", `score` `SCHEDULED`/`BUSY`, `seed` `SUCCESS`, `after` unset). It was **not reset**: the run's own events kept updating it during the grace period (`updatedAt` moved on to stop + 5010 ms, the last event before Temporal's hard stop). The api-server's `markTerminated` matched 0 rows, as designed;
- Temporal `WORKFLOW_EXECUTION_TERMINATED`.

Suite checks (3): "the healthy loop run is iterating before it is terminated"; "terminate of a healthy running loop run is 204"; "a terminated healthy loop run is recorded TERMINATED and shows no other final status"; "… the record keeps the loop as the run last reported it (BUSY, not reset)" (4 checks incl. the iterating precondition).

### Item 3: terminate of a run that finishes within the grace period

The expected outcome in the task list ("keeps FINISHED/FAILED") **cannot be produced as stated; what happens is below. No assertion about it is in the suite.**

| Probe (`stopprobe.mjs`) | Terminate | Record | Temporal |
|---|---|---|---|
| `shortfail`: loop-free run, first node fails; terminate 310 ms after the start | **404** (already over) | `FAILED`, unchanged | `FAILED` |
| `shortloopfail`: loop with `onLimit: fail`; terminate 609 ms after the start (run already over) | **404** | `FAILED`, unchanged | `FAILED` |
| `short`: 3-iteration loop, terminate 302 ms after the start (run still running) | 204 | `RUNNING` → **`TERMINATED`** at +233 ms | `COMPLETED` |
| `shortloopfail`: terminate 101 ms after the start | 204 | **`TERMINATED`** at +643 ms (451 ms after the stop) | `FAILED` |

- A run that has ended before the call keeps its final status (404, nothing written): **PASS**.
- A run that is still running when the signal arrives handles it (`notifyTerminating` → `pinTerminalStatus("TERMINATED")`, which exists since the CONTNM-96 commit) and records `TERMINATED` by itself, in 0.2-0.6 s. It then ends on its own, so Temporal says `COMPLETED` or `FAILED` but the record says `TERMINATED`. This is the existing behaviour. It is **not** the new write: the `updatedAt` is 0.2-0.5 s after the stop, not 5 s.
- The new api-server path for "ended within the grace period" did run live: the api-server logged "Workflow … ended before it could be terminated; its run record keeps its final status" for those runs (at the 5 s mark, `WorkflowNotFoundException`, nothing written). I can only show that nothing wrote `TERMINATED` there, because the record was already `TERMINATED` from the run itself. Whether a **signal-never-handled run that then ends on its own within 5 s** keeps `FINISHED`/`FAILED` cannot be reached over HTTP (a run that handles no task cannot end by itself); the unit test `WorkflowServiceTest` covers it. **NOT VERIFIED live.**
- Side effect to note: with the new bridge guard, a run that was terminated and ends on its own afterwards stays `TERMINATED` even if its last event says `FINISHED`/`FAILED` (`short`, `shortloopfail` above: the record kept `TERMINATED` over the following 25 s). Before the guard, the later event would have overwritten it. I take this as intended ("one final status") and report it so the item 3 wording can be adjusted.

### Item 4: cancel of a stuck run

**Probe** `stuckterminate.mjs … cancel`: other tenant cancel **404**, read 404; owner cancel **204**. The record stayed `RUNNING` for 9 s, then became **`CANCELLED`** (09:21:04) because a retry attempt ran the signal handler, published, and failed again; `updatedAt` kept moving with every later failed attempt (stop + 20 s, 60 s, 120 s: all `CANCELLED`, not in the `status==RUNNING` list). Temporal stayed `RUNNING` with the cancel requested (30 events, 2 WT failed, 0 markers). This is the same as round 4 (wedge 3): **unchanged**.

Follow-up: a **terminate after that cancel** (204) ended Temporal as `TERMINATED`, and the record **changed from `CANCELLED` to `TERMINATED`** (`updatedAt` 09:23:17). In round 4 the record stayed `CANCELLED` there. This is the new `markTerminated` (it skips only a record that is already `TERMINATED`). It is correct in my view, because Temporal's own state is `TERMINATED`; I report it as a behaviour change (observation O-3), not as a defect.

### Item 5: regression

| Command | Result |
|---|---|
| `npm test` | **PASS 77/77** (the same count as round 4 plus one test in `test/loopscenarios.test.js` for the self-starving run shape) |
| `npm run check` | PASS |
| `./run.sh suites/api/loops.js` ×3 (run 1 in sitting 1, run 2 in sitting 2, and inside `make api`) | **249/249** each, `isolation_violations` 0, no `known_gaps`, exit 0 (232 from round 4 + 17 new terminate checks) |
| `make api` | exit 0: workflow 15/15, runs 30/30, schedules 36/36, node-explorer 19/19, data 45/45, credentials 54/54, loops 249/249 |
| `./run.sh smoke` | 10/10 |
| `./run.sh tenancy` | 32/32, `isolation_violations` 0 |
| History sweep (`sweep.py`) over every workflow of the round | thread-holding runs `FAILED` with the budget text, 1 marker, 0 WT failed; the cheap shared-thread loops 1-2 retried tasks with cause "no loop condition evaluation thread is free"; the stuck and terminated runs `TERMINATED`, 27 events, 0 markers; 0 TMPRL1101 in the orchestrator log; **no workflow left running** |

### AC coverage (round 5)

| Item | Check name(s) / evidence | File | Result |
|---|---|---|---|
| (1) F-3: stuck run, other tenant terminate and read 404 | "stuck run terminate: hidden from other tenant (404)", "stuck run record: hidden from other tenant (404)", "another tenant's terminate left the stuck run RUNNING"; probe | `suites/api/loops.js` (`terminateChecks`) | **PASS** |
| (1) owner 204; record `TERMINATED` about 5 s later with `updatedAt` = end time | "terminate of the owner's stuck run is 204", "a terminated stuck run is recorded TERMINATED after the grace period, never another final status", "…updatedAt is the end time, about 5 s after the terminate"; probe (+5189 ms, stop + 5032 ms) | same | **PASS** |
| (1) not under `status==RUNNING`, under `status==TERMINATED` | "a terminated stuck run is no longer listed by status==RUNNING", "…is listed by status==TERMINATED" | same | **PASS** |
| (1) stays `TERMINATED` (a late attempt cannot reopen it) | "a terminated stuck run stays TERMINATED 15 s later"; probe 60 s and 120 s | same | **PASS** (bridge guard works live) |
| (1) no WARN "Failed to terminate" in the api-server log | `grep` over the log | — | **PASS** (0 hits, 0 ERROR) |
| (2) healthy run still ends `TERMINATED` once, snapshot not reset | "a terminated healthy loop run is recorded TERMINATED and shows no other final status", "…keeps the loop as the run last reported it (BUSY, not reset)"; probe | same | **PASS** |
| (3) terminate of a run already over → 404, final status kept | probes `shortfail`, `shortloopfail` (404) | probes | **PASS** (probe only) |
| (3) terminate of a run that ends within the grace period keeps FINISHED/FAILED | probes `short`, `shortloopfail`: record `TERMINATED` by the run's own handler (existing behaviour, 0.2-0.6 s); the api-server wrote nothing ("ended before it could be terminated") | probes | **NOT VERIFIED as worded** (see item 3 above; the api-server's no-write path is only covered by a unit test) |
| (4) cancel of a stuck run unchanged | probe: 404 / 204; record `RUNNING` then `CANCELLED` after a retry attempt; Temporal stays `RUNNING` | probe | **PASS** (unchanged) |
| (5) regression: loops 249/249 ×3, `make api`, smoke 10/10, tenancy 32/32 | see above | all | **PASS** |
| Earlier ACs (rounds 1-4) | unchanged checks | `suites/api/loops.js`, other suites | **PASS** |
| AC-5 retry, AC-23, AC-26, AC-27, AC-42 | — | — | NOT VERIFIED: not system-testable, as before |

### Failures

No k6 check failed and no AC failed. Observations:
- **O-3 (behaviour change, not a defect):** a terminate after a cancel that never completed now turns the record from `CANCELLED` to `TERMINATED` (Temporal state is `TERMINATED`). Before the fix it stayed `CANCELLED`.
- **O-4 (item 3 wording):** a terminate of a run that is still running at the signal and ends on its own within the grace period leaves the record `TERMINATED` (the run's own handler, existing behaviour), and now the bridge guard keeps it so even when a later `FINISHED`/`FAILED` event arrives. The "keeps FINISHED/FAILED" expectation holds only for a run that is already over when terminate is called (404).
- O-1 (cold-start warm-up) was not re-tested; it is a separate improvement.

### Files changed (repo `continuum-system-tests`, branch `CONTNM-180`, uncommitted; on top of rounds 1-4)

- `suites/api/loops.js`: new last section `terminateChecks` (+ helpers `readRun`, `listedWithStatus`, `watchStatuses`, `terminate`, `STOP_GRACE_MS`, `RUN_PATH`); header comment.
- `lib/loopscenarios.js`: `selfStarvingLoops(base)` (5 parallel `check: before` loops fed by `seed`, `ONE_LONG_CALL_CONDITION`).
- `test/loopscenarios.test.js`: 1 test for the self-starving run shape.
- `README.md`: the loop-suite bullet "Not in the suite … starves itself" replaced by the terminate section description (it is now in the suite, and what is still not).
- `AGENTS.md`: the shared-thread contract now says only `terminateChecks` may start a self-starving run, and must stop it in a `finally`.
- `Makefile`: unchanged (`loops` already in `API_SUITES`).

### Commands run

| Command | Result |
|---|---|
| `docker-compose -p loops-systest … up -d --wait <infra>`; platform and worker `bootJar` (`--offline`, scratch build dir) | all healthy; jars hold the fix classes |
| `java -jar` api-server / bridge / credentials / orchestration / worker | all up |
| `node probe/stuckterminate.mjs <log> r5a 2 120` | item 1 table above |
| `node probe/stopprobe.mjs long\|short\|shortfail\|shortloopfail …` | items 2 and 3 |
| `node probe/stuckterminate.mjs <log> r5d 2 120 cancel`, then a terminate | item 4 |
| `npm test`; `npm run check` | 77/77; PASS |
| `./run.sh suites/api/loops.js` ×2, `make api`, `./run.sh smoke`, `./run.sh tenancy` | 249/249 ×3; all 7 suites 100 %; 10/10; 32/32 |
| `python3 sweep.py 2026-11-26T09:00:00Z`; `grep` of api-server, bridge, orchestration logs | see above |
| `kill -TERM` 98244 98245 98246 98247 98248 (one call each) | all gone (verified with `ps -p`; the bridge took about 40 s) |
| `docker-compose -p loops-systest … down` (no `-v`); `./gradlew --stop` in platform-core and feature-base | no containers left; no Gradle or Kotlin daemons left |

Logs and outputs: `/tmp/loops-systest-r5.acEOht/`: `orchestration.log`, `api-server.log`, `message-bridge.log`, `worker.log`, `credentials.log`, `k6-run{1,2}.txt`, `make-api.txt`, `k6-smoke.txt`, `k6-isolation.txt`, `probe-*.txt`, `probe/`, `sweep.py`, `pids.txt`.
