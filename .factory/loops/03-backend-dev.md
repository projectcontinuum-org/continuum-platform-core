# Backend development: loops

## Round 1

Date 2026-11-25. Repo `continuum-platform-core`, branch `CONTNM-180` (created by the orchestrator; no git writes by me).
Tasks BE-1, BE-2, BE-4 (Gate 2 approved, backend only; PR-3 and PR-6 accepted, other PR defaults stand).

**ENGINE: cel-java** (`dev.cel:cel` 0.15.0). BE-5 can use the cel-java parser and BE-15 the cel-java evaluator. The
D-7 / D-17 fallbacks are not needed.

### Tasks

| Task | Status | Notes |
|------|--------|-------|
| BE-1 | DONE | The one allowed online resolve succeeded on the first try. `implementation("dev.cel:cel:0.15.0")` is in **both** `continuum-commons` and `continuum-orchestration-service` (no `api` in commons). The spike passes: evaluation inside the workflow, and replay with `WorkflowReplayer`. All five modules pass `--offline`. Protobuf/guava moved (see below), with no failures. |
| BE-2 | DONE | `Node.parentNode`, `NodeData.collapsed` (both `@JsonInclude(NON_NULL)`), `@JsonIgnoreProperties(ignoreUnknown = true)` on `Node`/`NodeData`, `LoopNode`, `TaskQueues.BUILTIN_NO_ACTIVITY`. Spec regenerated and Redocly lint passed. |
| BE-4 | DONE | `validation/loop/LoopCondition.kt` contract types, an 88-row golden vector table, a shared loader, `LoopConditionTest` (19 tests). No parser implementation yet (that is BE-5). |

#### BE-1 details

- **The one online command** (no `--offline`, run exactly once). It used a throw-away init script in `/tmp/loops-initscript.OXop0k/` that resolves every artifact file of `compile/runtime/testCompile/testRuntimeClasspath`:
  `./gradlew --init-script /tmp/.../resolve-cel.init.gradle.kts :continuum-commons:resolveLoopSpikeArtifacts :continuum-orchestration-service:resolveLoopSpikeArtifacts :continuum-api-server:resolveLoopSpikeArtifacts :continuum-worker-springboot-starter:resolveLoopSpikeArtifacts :continuum-message-bridge:resolveLoopSpikeArtifacts` → BUILD SUCCESSFUL in 5s.
  - It ran one Gradle invocation, as the plan's "one command" rule requires, but it downloaded the jar files of the five modules' classpaths, not just the POM metadata. A `dependencies` report alone would have fetched only the POMs.
  - Everything after it ran `--offline`.
- **Dependency reports.** The before/after `runtimeClasspath` reports are in `/tmp/loops-deps.XYcSaW/{before,after}-<module>.txt`. Resolved versions, before → after:

  | Library | commons | orchestration | api-server | worker-starter | message-bridge |
  |---|---|---|---|---|---|
  | `protobuf-java` | 3.25.5 → **4.36.2** | 3.25.8 → **4.36.2** | 3.25.5 → **4.36.2** | 3.25.5 → **4.36.2** | 3.25.5 → **4.36.2** |
  | `protobuf-java-util` | 3.25.5 (unchanged) | 3.25.8 (unchanged) | 3.25.5 | 3.25.5 | 3.25.5 |
  | `guava` | 32.0.1-jre → **33.7.2-jre** | 33.4.8-jre → **33.7.2-jre** | 32.0.1 → 33.7.2 | 32.0.1 → 33.7.2 | 32.0.1 → 33.7.2 |
  | `failureaccess` | 1.0.1 → 1.0.3 | (1.0.3, unchanged) | 1.0.1 → 1.0.3 | 1.0.1 → 1.0.3 | 1.0.1 → 1.0.3 |
  | `re2j` | 1.1 → 1.8 | 1.6 → 1.8 | 1.1 → 1.8 | 1.6 → 1.8 | 1.6 → 1.8 |
  | `antlr4-runtime` | new 4.13.2 | new 4.13.2 | 4.13.2 (unchanged, already present) | new 4.13.2 | new 4.13.2 |
  | `grpc-*` | 1.58.1 (unchanged) | 1.76.0 (unchanged) | 1.58.1 (unchanged) | 1.58.1 (unchanged) | 1.58.1 (unchanged) |
  | also new | `threeten-extra` 1.11.0, `auto-value-annotations` 1.11.1, `snakeyaml` 2.5, `dev.cel:{cel,common,compiler,runtime,protobuf,v1alpha1}` 0.15.0; `error_prone_annotations` → 2.50.0, `j2objc-annotations` → 3.1; `checker-qual` 3.33.0 dropped (commons, worker-starter) | same | same | same | same |

- **BOM check.**
  - The Spring Boot 4.0.6 BOM does not manage `protobuf-java`: its only mention is an exclusion on `mysql-connector-j`. The Temporal BOMs (1.28.0 and 1.35.0) and Spring Cloud 2025.1.1 do not mention protobuf at all. So nothing pinned protobuf, and cel-java's 4.36.2 wins conflict resolution.
  - Temporal `temporal-serviceclient` 1.35.0 declares `protobuf-java-util` 3.25.8 and gRPC BOM 1.76.0. Its generated code extends `GeneratedMessageV3`, which protobuf-java 4.36.2 still ships.
- **Is the major-line move a problem?** The plan's rule was "Temporal 1.35 must keep protobuf within the same major line". The runtime moved from **3.x to 4.x**, so the letter of that rule is broken.
  - What the tests show: every Temporal test in the platform runs Temporal's 3.25 gencode on the 4.36.2 runtime with no failure, and no gencode/runtime version warning appears in the test output. That covers the orchestration `ContinuumWorkflowTest` (60 tests), the worker-starter Temporal activity tests and the new spike.
  - protobuf 4.x is the documented runtime for 3.x gencode: it keeps `GeneratedMessageV3` for that purpose.
  - I judged this a PASS, not a version conflict "that breaks a test". **The user should confirm** (see SCOPE-1).
- **Spike measurements.**
  - Engine first use (build + compile + evaluate, outside workflow code) took **120 ms on a cold JVM**, and 2 ms when the CEL classes were already loaded.
  - A 3-iteration spike history has **20 events**.
  - The heterogeneous numeric option in 0.15.0 is `CelOptions.Builder.enableHeterogeneousNumericComparisons(true)`. That closes the plan's NOT VERIFIED item; it is pinned by a test (`vars["n"] < 2.5` with n = 2L / 3L).
- **cel-java parser facts for BE-5** (from jshell over the golden inputs, not committed):
  - `-1` and `-1.5` parse as **folded constants** (`CONSTANT -1`), not as a unary call.
  - `-vars["a"]` parses as `CALL -_`.
  - `has(vars.a)` parses as `SELECT ... ~presence_test`, not as a call.
  - `.exists(...)` parses as `COMPREHENSION`.
  - `!x` parses as `CALL !_`, and `in` as `CALL @in`.
  - Empty and blank text is a parser error, so BE-5 must check `EMPTY` first.
  - The 2,049-character text parses fine, so `TOO_LONG` must be checked before parsing.
  - 33-deep nesting still parses, so the depth guard is our own pre-check.
  - All syntax-error rows really fail in the cel-java parser, and every other row parses.

### Files changed

- `continuum-platform-core/continuum-commons/build.gradle.kts` adds `implementation("dev.cel:cel:0.15.0")`.
- `continuum-platform-core/continuum-orchestration-service/build.gradle.kts` adds `implementation("dev.cel:cel:0.15.0")`. It is needed because commons' `implementation` does not reach this module's compile classpath.
- `continuum-platform-core/continuum-commons/src/main/kotlin/org/projectcontinuum/core/commons/model/ContinuumWorkflowModel.kt`:
  - `Node.parentNode` and `NodeData.collapsed` (optional, NON_NULL, `@field:Schema`).
  - The `type` description now lists `BaseNode`/`Loop`.
  - `@JsonIgnoreProperties(ignoreUnknown = true)` on `Node` and `NodeData`.
- `continuum-platform-core/continuum-commons/src/main/kotlin/org/projectcontinuum/core/commons/model/LoopNode.kt` (new): the `NODE_TYPE`, `NODE_MODEL` and `CATEGORY` constants and `isLoop(node)`.
- `continuum-platform-core/continuum-commons/src/main/kotlin/org/projectcontinuum/core/commons/constant/TaskQueues.kt`: `BUILTIN_NO_ACTIVITY = "$builtin"`.
- `continuum-platform-core/continuum-commons/src/main/kotlin/org/projectcontinuum/core/commons/validation/loop/LoopCondition.kt` (new), the BE-4 contract:
  - `LoopConditionLimits`, `LoopConditionGrammar.SUPPORTED_SHAPE`, `LoopConditionOperator`, `LoopConditionLiteralType`, the `LoopConditionLiteral` sealed interface.
  - `LoopCondition.canonical()`, the `LoopConditionRejection` reason codes (with the order of the rejection rules in KDoc), `LoopConditionParseResult`, and the `fun interface LoopConditionParser`.
- `continuum-platform-core/continuum-commons/src/test/resources/loop-condition/golden-vectors.json` (new): 88 rows, 41 accepted and 47 rejected; every reason code and every operator per literal type is covered. SHA-256 `768c0d413e48bb140f5529a224d636839e5594475a519be7f77dc311d19bd30e`.
- `continuum-platform-core/continuum-commons/src/test/kotlin/org/projectcontinuum/core/commons/validation/loop/LoopConditionGoldenVectors.kt` (new): the shared loader for BE-4 and BE-5.
- `continuum-platform-core/continuum-commons/src/test/kotlin/org/projectcontinuum/core/commons/validation/loop/LoopConditionTest.kt` (new).
- `continuum-platform-core/continuum-commons/src/test/kotlin/org/projectcontinuum/core/commons/model/ContinuumWorkflowModelLoopFieldsTest.kt` (new).
- `continuum-platform-core/continuum-orchestration-service/src/test/kotlin/org/projectcontinuum/core/orchestration/config/DataConverterLoopFieldsTest.kt` (new).
- `continuum-platform-core/continuum-orchestration-service/src/test/kotlin/org/projectcontinuum/core/orchestration/spike/CelInWorkflowSpikeTest.kt` (new; stays as a regression test).
- `continuum-platform-core/docs/api/continuum-api-server.openapi.yaml` (regenerated): adds `NodeData.collapsed` and `WorkflowNode.parentNode`, and the `WorkflowNode.type` description changed. Nothing else changed.
- `continuum-platform-core/docs/api/ENDPOINT_INVENTORY.md`: a new cross-cutting fact "Workflow nodes and loops", plus notes on the start and validate bodies. Loop validation/execution rules are marked TODO for later tasks.
- `continuum-platform-core/release_notes/RELEASE_NOTES_v0.0.23.md`: a "What's Changed" line; commons details (loop fields, ignore-unknown, `LoopNode`, `$builtin`, `validation.loop`, a binary-compat note); a dependency line with every version move; an orchestration line (spike, numbers); an api-server client-visible schema section; an Upgrade note.
- `continuum-platform-core/AGENTS.md`: Key versions gets a `cel-java` row. I also corrected the stale `platformVersion` row, 0.0.12 → 0.0.23 (a trivial fix).
- `continuum-platform-core/continuum-commons/AGENTS.md`: Local Contracts for the loop fields and `LoopNode`, ignore-unknown, `TaskQueues.BUILTIN_NO_ACTIVITY`, and `validation.loop` with the golden-file single-source rule and the cel-java dependency.
- `continuum-platform-core/continuum-orchestration-service/AGENTS.md`: the loop condition engine line (dependency, CEL options, the spike regression test).

`git -C continuum-platform-core diff --stat`: 10 tracked files changed, 102 insertions(+), 9 deletions(-), plus the new files above.

### Unit tests added/changed

| Test | Covers |
|------|--------|
| `CelInWorkflowSpikeTest` › cel condition evaluated inside workflow code completes and replays deterministically | AC-2: the workflow returns 3, there is no `WORKFLOW_TASK_FAILED` event, and `WorkflowReplayer.replayWorkflowExecution` passes |
| `CelInWorkflowSpikeTest` › heterogeneous numeric comparison option compares an int variable with a double literal | D-2 int vs double; pins the 0.15.0 option name |
| `ContinuumWorkflowModelLoopFieldsTest` (4 tests): no fields parse as null and serialize without the keys; loop + member round-trip; editor-only fields are ignored with a strict `FAIL_ON_UNKNOWN_PROPERTIES` mapper; constants match the contract | AC-3, PR-5 |
| `DataConverterLoopFieldsTest` (3 tests): round trip through `DataConverterConfig().dataConverter()`; null fields are omitted from the payload; unknown node fields are ignored | AC-3, PR-6 groundwork |
| `OpenApiSpecTest` (existing, 8 tests) against the regenerated spec | AC-4 |
| `LoopConditionTest` (19 tests): canonical default and spacing; escapes (quote, backslash, `\n` `\r` `\t`, other control characters as `\u00XX`); non-ASCII written as is; name escaping; double formatting (`1e3` → `1000.0`, `-0.0` → `0.0`, `1e-7` → `0.0000001`); int64 extremes; non-finite doubles refused; operator rules per type; symbol/type lookup; limits; reason codes equal the contract list; supported shape; golden table loads with unique ids; canonical equals the triple's `canonical()`; accepted rows respect the limits and operator rules; every reason code and every (type, operator) pair has a row; limit rows sit exactly at 2048/2049, 256/257, 1024/1025 and depth 32/33; row-shape consistency | AC-12 (table part), contract rows "grammar", "golden vectors", "reason codes" |

### Verification

| Command | Result |
|---|---|
| `./gradlew --offline -q :<m>:dependencies --configuration runtimeClasspath`, five modules, before and after | PASS; reports in `/tmp/loops-deps.XYcSaW/` |
| The one online resolve (above) | PASS: BUILD SUCCESSFUL in 5s |
| `./gradlew --offline :continuum-orchestration-service:test --tests "...spike.CelInWorkflowSpikeTest"` | PASS, 2/2 |
| `./gradlew --offline :continuum-api-server:generateOpenApiSpec` | PASS; the diff shows exactly the three intended changes |
| `npx --yes @redocly/cli@2 lint docs/api/continuum-api-server.openapi.yaml docs/api/continuum-cluster-manager.openapi.yaml docs/api/continuum-credentials-server.openapi.yaml --config redocly.yaml` | PASS: "Your API descriptions are valid", no errors and no warnings printed |
| `./gradlew --offline :continuum-commons:test :continuum-orchestration-service:test :continuum-api-server:test :continuum-worker-springboot-starter:test :continuum-message-bridge:test --continue` (final run, after all changes) | PASS, BUILD SUCCESSFUL in 1m 24s: commons 148 tests, orchestration 63, api-server 234 (incl. `OpenApiSpecTest` 8), worker-starter 40, message-bridge 3; 0 failures, 0 errors |
| Same five-module run after BE-1 only (before BE-2/BE-4) | PASS: 125 / 60 / 234 / 40 / 3, 0 failures |

- AC-11 (no regression) holds for these modules: every existing test passes unchanged.
- `continuum-knime-base` also depends on commons. It is not in the plan's verification list and was not built: NOT VERIFIED.
- Feature repos were not rebuilt against the new commons: NOT VERIFIED. A grep showed none of them pins protobuf, guava or gRPC in Gradle files.

### API contract changes

- **Endpoints whose request/response schema changed:** `POST /api/v1/workflow`, `POST /api/v1/workflow/validate`, `POST /api/v1/workflow/schedule`, and every response that embeds `ContinuumWorkflowModel` (`GET /api/v1/workflow-runs/{workflowId}` snapshot, schedules, node-explorer `NodeData`).
  - New optional `WorkflowNode.parentNode` (string or null).
  - New optional `NodeData.collapsed` (boolean or null).
  - `WorkflowNode.type` is documented as `BaseNode` | `Loop`.
- **Spec file:** `continuum-platform-core/docs/api/continuum-api-server.openapi.yaml`. No other spec changed (only this spec references the commons model).
- **Frontend (FE-1, on hold):** regenerate the api-server typescript-axios client with `bash scripts/generate-api-clients.sh`. FE-1 is unblocked by BE-2.
- **Unchanged:**
  - No operationId, status code or error body changed.
  - Older clients keep working: the fields are optional and omitted when null.
  - Unknown node fields are now ignored instead of rejected.
- **Contract values for FE tasks** (match the frozen contract table, nothing changed):
  - `Node.type = "Loop"`.
  - `LoopNode.NODE_MODEL = "org.projectcontinuum.core.builtin.Loop"`, category `Flow Control`.
  - Task-queue marker `TaskQueues.BUILTIN_NO_ACTIVITY = "$builtin"`.
  - Golden file path as in the contract.
- **For FE-3:** copy the golden file verbatim and pin SHA-256 `768c0d413e48bb140f5529a224d636839e5594475a519be7f77dc311d19bd30e`.

### Docs updated

- **Release notes:** `release_notes/RELEASE_NOTES_v0.0.23.md` (0.0.23 is unreleased; the latest tag is `release/0.0.22`).
  - What's Changed line.
  - commons details, dependency line, orchestration line, api-server client-visible schema section.
  - Upgrade note covering the strict deserializers and the protobuf/guava move for workers.
  - Highlights is unchanged, because loops do not run yet. BE-18 adds the "Loops" highlight.
- **README:** unchanged after checking.
  - The root `README.md` has no library or version list.
  - Its module table is unaffected.
  - "Flow Control … Real loops coming." is still true, because loops do not execute yet. BE-14/BE-18 should update it.
- **AGENTS.md (DOX):** root (Key versions), `continuum-commons/AGENTS.md`, `continuum-orchestration-service/AGENTS.md`.
  - `continuum-api-server/AGENTS.md` is unchanged: no api-server code changed, and its MCP/start rules are unaffected until BE-7/BE-9.
- **OpenAPI:** inventory updated, spec regenerated, lint passed.
  - `docs/api/CONVENTIONS.md` is unchanged: it names no changed schema (checked with grep) and spec building is unchanged.

### SCOPE / questions

- **SCOPE-1 (user confirmation).** cel-java 0.15.0 moves protobuf-java from 3.25.x to **4.36.2**, and guava to 33.7.2-jre, on every classpath that includes commons, feature workers included. That is the protobuf major line, which the plan said Temporal must keep.
  - All Temporal tests pass on the 4.x runtime; protobuf 4 keeps `GeneratedMessageV3` for 3.x gencode.
  - Live stacks, feature workers and `continuum-knime-base` were not exercised: NOT VERIFIED.
  - If the user wants workers untouched, the plan's alternative still applies: keep the dependency out of commons, and put the CEL parser in a new `continuum-loop-condition` module used by orchestration and api-server only. That needs a `settings.gradle.kts` change, so it is SCOPE. BE-5 must know the decision before it starts.
- **Note 1: the online resolve.** It ran as one Gradle invocation through a temporary init script so that the jar files were cached, not only the POMs. It touched only Maven Central and Confluent for the five listed modules, and ran once. Nothing in the repo refers to the init script.
- **Note 2: binary compatibility.** The `Node` and `NodeData` constructors and `copy` gained a parameter. Binaries compiled against an earlier 0.0.23 snapshot that call them must be recompiled. 0.0.22 workers are already incompatible (`SCHEDULED`); this is recorded in the release notes.
- **Note 3: the orchestration test resources.** `src/test/resources/application.yaml` was not changed (no binding needed yet).

## Round 2

Date 2026-11-25. Repo `continuum-platform-core`, branch `CONTNM-180` (no git writes). Tasks: the SCOPE-1 module split (user decision: SEPARATE MODULE), then BE-3, BE-5, BE-7, BE-11, BE-13. House rule 12 (SOLID) applied: new behaviour sits in focused collaborators, and the existing large classes (`WorkflowValidator`, `NodeReadiness`) only delegate.

### Tasks

| Task | Status | Notes |
|------|--------|-------|
| SCOPE-1 split | DONE | New module `continuum-loop-condition` (`settings.gradle.kts`). `dev.cel:cel` removed from commons and moved to the version catalog alias `cel`. Only orchestration and api-server depend on the module. Commons, worker-starter, knime-base and message-bridge runtime classpaths are byte-identical to `main` (reports below). |
| BE-3 | DONE | `commons/node/builtin/LoopNodeDescriptor.kt` and `LoopNode.doc.md`. The schemas are exactly the contract, with titles and descriptions added. The doc has a placeholder line for the ceiling (BE-17). |
| BE-5 | DONE (in the new module) | `CelLoopConditionParser` is a chain of collaborators: pre-check, cel-java syntax, AST gate. All 88 golden rows pass; hostile inputs return in under 100 ms and never throw. |
| BE-7 | DONE | Rules live in `commons/validation/loop/{LoopScopes,LoopStructureRules,LoopEdgeRules}.kt`. `WorkflowValidator` gets about 25 lines of delegation. api-server `WorkflowRunValidator` passes `type` and `parentNode`. |
| BE-11 | DONE | `orchestration/workflow/loop/LoopTopology.kt` and `LoopReadinessGate.kt`. `NodeReadiness` gets one `loopGate` parameter, which defaults to `LoopReadinessGate.NONE`. |
| BE-13 | DONE | The `LoopConditionEvaluator` interface and `CompiledLoopCondition` are in **commons** `validation.loop` (engine-free contract, as instructed). In orchestration: `LoopConditionEvaluators` (holder) and `LoopConditionRuntimeCheck`. Test fixtures: `StructuredTestLoopConditionEvaluator` and `LoopWorkflowBuilder`. |

#### Module split details

- **Build files.**
  - `gradle/libs.versions.toml` gains `cel = "0.15.0"` and the library `cel`.
  - `continuum-commons/build.gradle.kts`:
    - drops `dev.cel:cel`;
    - adds `java-test-fixtures`;
    - the test fixtures serve `src/test/resources/loop-condition/**`, so the golden file stays at its frozen path with an unchanged SHA (`768c0d41…d30e`);
    - the `testFixturesApiElements` and `testFixturesRuntimeElements` variants are skipped in the Maven publication. The generated `pom-default.xml` and `module.json` contain no fixtures and no cel.
  - `continuum-loop-condition/build.gradle.kts`: `api(project(":continuum-commons"))`, `implementation(libs.cel)`, `testImplementation(testFixtures(project(":continuum-commons")))`, Boot 4.0.6 and Temporal 1.28.0 BOMs. It is not published.
  - Orchestration: `implementation(project(":continuum-loop-condition"))`, plus `testImplementation(libs.cel)` for the spike test.
  - api-server: `implementation(project(":continuum-loop-condition"))`.
- **No cycle.** Commons never imports `org.projectcontinuum.core.loopcondition`. Commons code that needs a parser takes the `LoopConditionParser` interface.
- **BE-1 spike test.** It stays in orchestration (`spike.CelInWorkflowSpikeTest`, 2/2 pass), with cel-java on the test classpath.
- `LoopConditionGoldenVectors.kt` moved from `commons/src/test` to `commons/src/testFixtures` (same package).
- **CI.** `.github/workflows/openapi-spec-check.yml`: `continuum-loop-condition/**` was added to both path filters, because the api-server classpath changed. Jib, Docker and the release matrix list no library modules, so nothing else changed.
- **Dependency reports** (`./gradlew --offline -q [--init-script include-knime-base] :<m>:dependencies --configuration runtimeClasspath`):
  - Files are in `/tmp/loops-r2.IcQqYS/`: `before-*` = a `git archive main` export, `round1state-*` = before this round's changes, `after-*` = final.
  - knime-base is not in `settings.gradle.kts`. It was included with a throw-away init script, `/tmp/loops-r2.IcQqYS/include-knime-base.init.gradle.kts`, that is not in the repo.

  | Module | protobuf-java main → round 1 → **now** | guava main → round 1 → **now** | dev.cel |
  |---|---|---|---|
  | commons | 3.25.5 → 4.36.2 → **3.25.5** | 32.0.1-jre → 33.7.2 → **32.0.1-jre** | absent |
  | worker-springboot-starter | 3.25.5 → 4.36.2 → **3.25.5** | 32.0.1-jre → 33.7.2 → **32.0.1-jre** | absent |
  | knime-base | 3.25.5 → 4.36.2 → **3.25.5** | 33.3.1-jre → 33.3.1 → **33.3.1-jre** | absent |
  | message-bridge | (3.25.5) → 4.36.2 → **3.25.5** | (32.0.1) → 33.7.2 → **32.0.1-jre** | absent |
  | orchestration-service | 3.25.8 → **4.36.2** | 33.4.8-jre → **33.7.2-jre** | 0.15.0 |
  | api-server | 3.25.5 → **4.36.2** | 32.0.1-jre → **33.7.2-jre** | 0.15.0 |
  | loop-condition | **4.36.2** | **33.7.2-jre** | 0.15.0 |

  - `diff before-<m> after-<m>` gives **0 lines** for commons, worker-starter and knime-base (identical to main).
  - protobuf-java-util stays at 3.25.5 (orchestration 3.25.8).

#### BE-5 design (cel-java facts verified with jshell-style probes in /tmp, not committed)

- **Classes.**
  - `LoopConditionTextPrecheck`: EMPTY; TOO_LONG in code points; nesting > 32 reported as SYNTAX.
  - `LoopConditionNestingDepth`: an iterative scan that skips strings (raw and triple-quoted included) and `//` comments.
  - `CelConditionSyntax`: the cel-java parser.
  - `LoopConditionShapeGate`: rules 2–9 in KDoc order.
  - `LoopConditionLiteralReader`.
  - `CelExpressionShapes`: iterative AST walks.
  - `LoopConditionRejections`: builds messages and 1-based columns.
- **Parser options.**
  - **Standard macros off**, so `has(...)` and `.exists(...)` stay calls and map to FUNCTION_CALL.
  - `retainRepeatedUnaryOperators(true)`. Without it, `!!true` folds to `true` and `--1` to `1`.
  - `maxParseRecursionDepth(100)`, and `StackOverflowError` is caught. A `1+1+…` chain of 2,048 characters overflowed a 256 KiB stack at cel's default limit of 250; at 100 it is a clean parser error.
  - Single `-1` and `- 1.5` are folded into the constant by the parser, so they belong to the literal. `-(1)` and `--1` are UNSUPPORTED_OPERATOR.
- **Pitfall found.** cel's `Operator` enum also lists the macro names (`has`, `all`, `exists`, `exists_one`, …). Operator calls are told apart by their `_…_`, `…_` or `@…` function names. A test pins every macro name as FUNCTION_CALL.

### Files changed (repo `continuum-platform-core`)

New module `continuum-loop-condition/`:
- `build.gradle.kts`: the module; cel-java confined here.
- `README.md`, `AGENTS.md`: the new module's docs and the dependency-boundary contract.
- `src/main/kotlin/org/projectcontinuum/core/loopcondition/`: `CelLoopConditionParser`, `LoopConditionParsers`, `LoopConditionTextPrecheck`, `LoopConditionNestingDepth`, `CelConditionSyntax`, `LoopConditionShapeGate`, `LoopConditionLiteralReader`, `CelExpressionShapes`, `LoopConditionRejections` (BE-5).
- `src/test/kotlin/.../LoopConditionParserGoldenTest.kt`, `CelLoopConditionParserRobustnessTest.kt`.

continuum-commons:
- `build.gradle.kts`: cel removed; test fixtures added; fixtures not published.
- `src/testFixtures/kotlin/.../validation/loop/LoopConditionGoldenVectors.kt`: moved from `src/test`.
- `src/main/kotlin/.../node/builtin/LoopNodeDescriptor.kt` and `src/main/resources/org/projectcontinuum/core/commons/node/builtin/LoopNode.doc.md` (BE-3).
- `src/main/kotlin/.../validation/loop/LoopScopes.kt`, `LoopStructureRules.kt`, `LoopEdgeRules.kt` (BE-7).
- `src/main/kotlin/.../validation/loop/LoopConditionEvaluator.kt`: evaluator interface and `CompiledLoopCondition` (BE-13 contract).
- `src/main/kotlin/.../validation/WorkflowValidator.kt`:
  - `GraphNode.type` and `GraphNode.parent` (both optional);
  - delegation to the loop rules;
  - an edge naming a loop skips the port checks;
  - a loop is never "not connected";
  - AUTHORING mode no longer duplicates the error for an empty required value (see SCOPE-3).
- `src/test/kotlin/.../node/builtin/LoopNodeDescriptorTest.kt`, `validation/WorkflowValidatorLoopStructureTest.kt`.

continuum-orchestration-service:
- `build.gradle.kts`: depends on the new module; `libs.cel` for tests only.
- `src/main/kotlin/.../workflow/loop/LoopTopology.kt`, `LoopReadinessGate.kt` (BE-11); `LoopConditionEvaluators.kt`, `LoopConditionRuntimeCheck.kt` (BE-13).
- `src/main/kotlin/.../workflow/NodeReadiness.kt`: thin `loopGate` delegation, defaulted.
- `src/test/kotlin/.../workflow/loop/LoopTopologyTest.kt`, `LoopConditionRuntimeCheckTest.kt`; `workflow/NodeReadinessLoopTest.kt`; `workflow/fixtures/LoopWorkflowBuilder.kt`, `StructuredTestLoopConditionEvaluator.kt`.

continuum-api-server:
- `build.gradle.kts`: depends on the new module.
- `service/WorkflowRunValidator.kt`: passes `type` and `parentNode`.
- `src/test/.../controller/WorkflowControllerGraphValidationTest.kt`: 3 loop cases.

Repository level:
- `settings.gradle.kts`: `include(":continuum-loop-condition")`.
- `gradle/libs.versions.toml`: `cel`.
- `.github/workflows/openapi-spec-check.yml`: path filters.
- `AGENTS.md` (root): module table, Key versions row, Child DOX Index.
- `README.md` (root): module table row.
- `continuum-commons/AGENTS.md`, `continuum-orchestration-service/AGENTS.md`, `continuum-api-server/AGENTS.md`: contracts.
- `docs/api/ENDPOINT_INVENTORY.md`: loop validation errors in the start section; the TODO line is updated.
- `release_notes/RELEASE_NOTES_v0.0.23.md`: What's Changed; commons details; new section `continuum-loop-condition` with the dependency line ("only api-server and orchestration"); orchestration loops line; api-server client-visible validation errors; Upgrade note rewritten (workers are not affected).

`git diff --stat`: 20 tracked files changed, +303/−34, plus the new files above (about 1,600 lines of new main code across the loop packages).

### Unit tests added/changed

| Test | Covers |
|------|--------|
| `LoopConditionParserGoldenTest`: 88 dynamic tests, one per golden row, plus the SHA-256 pin of the frozen file | AC-12 |
| `CelLoopConditionParserRobustnessTest` (13): 8 hostile 2,048-char inputs within 100 ms and no throw; depth 33 is SYNTAX with column; brackets in strings and comments are ignored; `-1`, `- 1.5`, `Long.MIN_VALUE` literals; `-(1)`, `--1`, `-(-1)` are UNSUPPORTED_OPERATOR; `!!true` is LOGICAL; `1e999` is UNSUPPORTED_LITERAL; int64 overflow is SYNTAX; parser column; gate column; every macro name is FUNCTION_CALL; default instance | AC-12, BE-5 cost test |
| `LoopNodeDescriptorTest` (10): defaults valid; defaults are the contract values; 0, 10,001, bad enums, empty and 2,049-char condition each give one violation on the right property (with the boundaries valid); contract shape (no `$schema`, `additionalProperties: false`, required, titles, descriptions and defaults per property); undeclared key; UI schema order and `format: query` / `language: CEL`; nodeData has no ports and category `Flow Control`; the doc resource loads | AC-7 (schema), contract rows |
| `WorkflowValidatorLoopStructureTest` (17): example workflow valid in both modes, loop not "not connected"; nested ok; 2-cycle and self parent cycle; unknown parent; non-loop parent; type mismatch both ways; no type means no check (MCP); edges to and from a loop (exact messages, `edge` set, no port error); `X1 -> Y -> X2` names L and Y; `A -> X1 -> X2 -> B` ok; nested-scope collapsed cycle; a real cycle keeps the plain message only; empty-loop warning; 6 bad property values × 2 modes give exactly one error with node and property; loop-free regression with exact old messages | AC-7, AC-8, AC-9, AC-10, AC-11 |
| `WorkflowControllerGraphValidationTest` (+3): `X1 -> Y -> X2` gives start 400 with the message (no row, no Temporal) and validate 200 `valid:false` with `errors[0].node = L`; a valid loop workflow (with the editor field `extent`) validates | AC-10 / AC-13 path through HTTP |
| `LoopTopologyTest` (6): members, entry member, boundary edges; nested enclosing loops and descendants; topological order and ties; several entry members; loop-free; malformed parents ignored | BE-11 |
| `NodeReadinessLoopTest` (6): member not ready while its loop is inactive; consumer not ready (edge pending) until the loop finished, then DELIVERED; edge from a failed loop is BROKEN and the consumer is skipped; a failing member does not skip the outside consumer early; members of an inactive loop are not generically skipped; loop-free diamond gives identical results with and without the gate | AC-20 (ordering), AC-11 |
| `LoopConditionRuntimeCheckTest` (6): missing-variable message; string vs number message; 8 mismatches incl. list, map, null; matching classes incl. int vs double; evaluator holder; structured test evaluator | AC-22 (check part) |

### Verification

| Command | Result |
|---|---|
| `./gradlew --offline :continuum-commons:test :continuum-loop-condition:test :continuum-orchestration-service:test :continuum-api-server:test :continuum-worker-springboot-starter:test :continuum-message-bridge:test --continue` (final, after all changes) | **PASS**, BUILD SUCCESSFUL in 1m 6s. commons 175, loop-condition 102, orchestration 81 (spike 2/2 included), api-server 237 (`OpenApiSpecTest` included), worker-starter 40, message-bridge 3; 0 failures, 0 errors |
| `./gradlew --offline -q :continuum-api-server:generateOpenApiSpec`, then diff against the pre-round copy | PASS: spec **unchanged** (no schema change) |
| `./gradlew --offline [init-script] :<m>:dependencies --configuration runtimeClasspath` for 7 modules, before (main export) and after | PASS; table above |
| `./gradlew --offline -q :continuum-commons:generatePomFileForMavenJavaPublication :continuum-commons:generateMetadataFileForMavenJavaPublication` | PASS: no fixtures and no cel in the POM or module metadata |
| `./gradlew --offline --init-script …include-knime-base… :continuum-knime-base:compileKotlin :continuum-knime-base:test` | **NOT VERIFIED (pre-existing failure, same on a clean `main` export)**: see below |
| Redocly lint | NOT RUN: the spec is unchanged since the round-1 lint passed |
| No online resolve | Confirmed: every command ran `--offline`, and everything was in the cache |

- **knime-base compile.** It fails with `Failed to transform jackson-databind-2.18.1.jar … BuildToolsApiClasspathEntrySnapshotTransform … ClasspathEntrySnapshotter$Settings`.
  - Cause: knime-base imports the Spring Boot **3.4.0** BOM, which pins Kotlin 1.9.25. That BOM downgrades its `kotlinBuildToolsApiClasspath` (`kotlin-compiler-embeddable` 2.2.0 → 1.9.25), so the Kotlin 2.2 plugin's snapshot transform breaks.
  - The identical failure happens on a `git archive main` export, and with `-Pkotlin.incremental=false` and `-Pkotlin.compiler.runViaBuildToolsApi=false`. It is pre-existing and unrelated to this change. knime-base is also not in `settings.gradle.kts`, so CI never builds it.
  - Its runtime dependency report is identical to main (0-line diff), which proves the protobuf/guava requirement for knime-base.

### API contract changes

- **No schema change.** `docs/api/continuum-api-server.openapi.yaml` is unchanged since round 1 (regenerated and diffed). No operationId, status code or body shape changed. The frontend has nothing new to regenerate (FE-1 still uses the round-1 spec, which is unchanged).
- **New client-visible validation errors (BE-7).** On `POST /api/v1/workflow` (400), `/validate` (200 `valid:false`) and `/schedule` (400):
  - loop type/nodeModel mismatch;
  - unknown or non-loop `parentNode`;
  - parent-chain cycle;
  - edge to or from a loop;
  - a path that leaves a loop and comes back through an outside node;
  - loop properties checked against the Loop schema;
  - plus the warning "Loop 'L' has no nodes inside it.".
  - Exact texts are in `docs/api/ENDPOINT_INVENTORY.md` and asserted by `WorkflowValidatorLoopStructureTest`. FE-2's `cycleReason` should use the same wording as `LoopEdgeRules` ("The edges leave loop 'L' and come back into it through 'Y'. …").
- **For FE-3:** golden file path and SHA are unchanged: `768c0d413e48bb140f5529a224d636839e5594475a519be7f77dc311d19bd30e`. Rejection messages start with `LoopConditionRejection.reasonText`; columns are 1-based code points.

### Docs updated

- **Release notes 0.0.23:** What's Changed; commons details (descriptor, validator loop rules, AUTHORING duplicate fix, evaluator interface, test fixtures, "no new dependency"); new `continuum-loop-condition` section with the dependency line limited to api-server and orchestration; orchestration loops line; api-server client-visible validation errors; Upgrade note (workers are not affected; api-server and orchestration run protobuf 4).
- **README:** new `continuum-loop-condition/README.md`; root `README.md` module table row.
  - Pre-existing gap, not fixed: the root README module table has no orchestration-service row.
  - "Real loops coming" in the root README is still true.
- **AGENTS.md:** root (module table, Key versions, Child DOX Index), new `continuum-loop-condition/AGENTS.md`, commons, orchestration, api-server.
- **OpenAPI:** inventory start-section loop rules are documented. CONVENTIONS.md is unchanged (it names no changed class or schema). The spec is unchanged.

### SCOPE / questions

- **SCOPE-2 (plan adjustment caused by the split).** BE-8 cannot call `LoopConditionParsers.default` from commons, because that object now lives in `continuum-loop-condition`. Proposal: `WorkflowValidator` gets a constructor parameter `conditionParser: LoopConditionParser? = null`.
  - api-server's `WorkflowRunValidator` and `WorkflowSpecValidator` pass `LoopConditionParsers.default`.
  - Commons tests pass a test double (e.g. a parser over the golden table from the test fixtures).
  - Likewise, BE-16's defensive re-parse in `LoopRunner` uses `LoopConditionParsers.default` from the module (orchestration depends on it), and BE-15's `CelLoopConditionEvaluator` goes into `continuum-loop-condition` (implementing the commons `LoopConditionEvaluator`), wired by an orchestration `@Component`.
  - The plan's file paths for BE-5, BE-8, BE-15 and BE-16 should be read with that substitution.
- **SCOPE-3 (trivial, done).** In AUTHORING mode an empty required string produced two errors ("has no value" plus `minLength`). AC-7 requires exactly one per property, so the schema violation is now dropped for properties already reported as missing.
  - It only affects schemas with `minLength` on a required property; no existing test asserted the duplicate.
  - It is client-visible only through MCP `validateContinuumWorkflow` (one fewer error) and is release-noted.
- **Note (BE-13 placement).** The `LoopConditionEvaluator` and `CompiledLoopCondition` interfaces are in commons `validation.loop` (per the instruction "evaluator INTERFACES in commons"), not in orchestration as the plan's file list said. `LoopConditionEvaluators` (the static holder) and `LoopConditionRuntimeCheck` stay in orchestration.
- **Note (pre-existing).** `continuum-knime-base` does not compile on this machine, on `main` either (Boot 3.4.0 BOM vs Kotlin 2.2 plugin). This is out of scope; report it to the user if knime-base matters.
- **Interim behaviour (unchanged):** nothing executes loops yet. `NodeReadiness` receives the gate, but `WorkflowDagExecutor` still passes the default (BE-10 and BE-14).

## Round 3

Date 2026-11-25. Repo `continuum-platform-core`, branch `CONTNM-180` (no git writes). Inputs:
- The user's decisions after round 2: SCOPE-2 approved (optional `conditionParser` injected into `WorkflowValidator`); the BE-13 placement and SCOPE-3 accepted; the new protobuf-java-util pin with one allowed online resolve.
- Orchestrator course corrections during the round:
  1. **HOLD BE-8.** The user wants full CEL, not the D-2 subset. Do not touch the condition rule, the parser shape gate or the golden vectors. MCP descriptions may only say "properties.condition is a CEL expression".
  2. **House rule 13.** No factory ids in code, docs or release notes; clean up rounds 1–2, except the subset-parser files that the CEL rework will delete.

House rule 12 applied:
- Every new behaviour sits in a focused collaborator.
- The existing large classes get thin delegation only:
  - `WorkflowDagExecutor`: 1 line.
  - `WorkflowActivityInitializer`: 3 lines.
  - `NodeReadiness`: 3 lines.
  - `WorkflowStatusReporter`: progress delegated.
  - `WorkflowSpecBuilder`: layout delegated.

### Tasks

| Task | Status | Notes |
|------|--------|-------|
| protobuf-java-util pin | DONE | Catalog `protobuf = "4.36.2"` with library `protobuf-java-util`. A `constraints { implementation(libs.protobuf.java.util) }` block in orchestration-service and api-server only. It is a constraint, not a force. The single online resolve succeeded on the first try. Worker-side modules are identical to main (below). |
| BE-6 | DONE | `registration.BuiltinNodeDefinition`/`BuiltinNodes` (definitions), `BuiltinNodeRegistrationRequests` (pure payload builder), `BuiltinNodeRegistrationPublisher` (`@EventListener(ApplicationReadyEvent)`, logs and swallows failures). There is also the binding in `application.yaml`. The test `application.yaml` needed no change (no Spring context test in the module). Read-only checks: message-bridge `FeatureRegistrationHandler`/`NodeTreeSyncService` accept any task-queue string and null ports; api-server `getTaskQueues` returns the stored `$builtin`. No change needed. |
| BE-8 | **HELD — CEL scope change** | Not started, on the orchestrator's instruction. The validator condition rule, the `conditionParser` injection (SCOPE-2), the parser and the golden vectors are untouched. |
| BE-9 | DONE | MCP `parent` field; validator passes `parent` and type `Loop` for loop nodeIds (`LoopNodeSpec`); layout in the new pure `WorkflowSpecLayout`; tool descriptions explain loops and only say the condition is CEL; still six tools. |
| BE-10 | DONE | Loops get no task queue or stub. `NodeReadiness` never returns a loop as ready or skippable. `NodeDispatchPayload` strips `parentNode`/`collapsed`. `WorkflowProgress` (non-loop denominator) and `MonotonicProgress` (highest value so far; updated on replay too) are used by `WorkflowStatusReporter`. **Interim behaviour until BE-14:** the members of a loop run once like ordinary nodes, and the loop node keeps `status == null` (pinned by `ContinuumWorkflowLoopPlumbingTest`). |
| BE-12 | DONE | `FlowVariableMerger.collectInputVariables` gets optional `loopTopology`/`carriedByLoopId`, and `endOfIterationVariables` is new. The memo is refactored into a private `MergeContext`; the public behaviour for loop-free calls is unchanged. `workflow.loop.LoopCarriedVariables.afterIteration` gives the carried-set update rule. `LoopTopology.isEntryMember` is cached. Shared case file `src/test/resources/flow-variables/loop-carry-cases.json`: 8 cases, SHA-256 `8c13acecf761afea131e64eab6af5be6a1008787614fc5b6531cc67a761e7057`. |
| Factory-id cleanup (house rule 13) | DONE | Before: **43** matching lines in changed/new files. After: **9**, all in the exempt subset-parser files (`LoopCondition.kt`, `LoopConditionTest.kt`, `LoopConditionGoldenVectors.kt`, `CelLoopConditionParser.kt`, `LoopConditionShapeGate.kt`, `CelLoopConditionParserRobustnessTest.kt`, `LoopConditionParserGoldenTest.kt`, and two lines of `CelInWorkflowSpikeTest.kt`). Release notes, AGENTS.md, README and the inventory have 0. |

#### protobuf-java-util pin details

- **The one online command** (run once; it used the throw-away init script `/tmp/loops-r3.Ps1NQ8/resolve-util.init.gradle.kts`, which resolves compile/runtime/testCompile/testRuntime artifacts of the two modules):
  `./gradlew --init-script /tmp/loops-r3.Ps1NQ8/resolve-util.init.gradle.kts :continuum-orchestration-service:resolveProtobufUtilPin :continuum-api-server:resolveProtobufUtilPin` → BUILD SUCCESSFUL in 15s. `protobuf-java-util/4.36.2` is now in the Gradle cache. Everything else ran `--offline`.
- **runtimeClasspath reports** (`/tmp/loops-r3.Ps1NQ8/{before,after,final}-<module>.txt`; main baselines from round 2 `/tmp/loops-r2.IcQqYS/before-*` and `/tmp/loops-r3.Ps1NQ8/main-continuum-message-bridge.txt`, built from the `git archive main` export):

  | Module | protobuf-java | protobuf-java-util before → after | diff vs pre-pin | diff vs main |
  |---|---|---|---|---|
  | orchestration-service | 4.36.2 | 3.25.8 → **4.36.2** | 21 lines (util only; the util's guava 32.0.1 and j2objc 2.8 edges are gone, and there is a `(c)` constraint line) | n/a (cel) |
  | api-server | 4.36.2 | 3.25.5 → **4.36.2** | 21 lines (same shape) | n/a (cel) |
  | commons | 3.25.5 | 3.25.5 | 0 | **0** |
  | worker-springboot-starter | 3.25.5 | 3.25.5 | 0 | **0** |
  | message-bridge | 3.25.5 | 3.25.5 | 0 | **0** |
  | knime-base (init-script include) | 3.25.5 | 3.25.5 | 0 | **0** |
  | loop-condition | 4.36.2 | 3.25.5 (unchanged; the module has no Temporal, and the pin is only in the two services) | 0 | n/a |

- The final reports (after all of round 3) are byte-identical to the post-pin reports for all six modules.

### Files changed (repo `continuum-platform-core`)

Build / dependency:
- `gradle/libs.versions.toml`: version `protobuf`, library `protobuf-java-util` (with a comment explaining why).
- `continuum-orchestration-service/build.gradle.kts`, `continuum-api-server/build.gradle.kts`: the `constraints` block.

BE-6 (orchestration):
- `src/main/kotlin/.../registration/BuiltinNodeDefinition.kt` (new): `BuiltinNodeDefinition` and `BuiltinNodes` (feature id, `$builtin`, `loop`, `all`).
- `src/main/kotlin/.../registration/BuiltinNodeRegistrationRequests.kt` (new): pure Avro request builder; the manifest is written with a Jackson 2 `ObjectMapper`, like the worker starter.
- `src/main/kotlin/.../registration/BuiltinNodeRegistrationPublisher.kt` (new): the listener; one worker id per start; each send is tried, logged and swallowed; the next definition is still sent.
- `src/main/resources/application.yaml`: the `continuum-core-event-FeatureRegistration-output` binding plus the Kafka producer override (`KafkaAvroSerializer`, `schema.registry.url` from `CONTINUUM_KAFKA_MAIN_SCHEMA_REGISTRY`).

BE-10 (orchestration):
- `workflow/loop/NodeDispatchPayload.kt` (new).
- `workflow/WorkflowProgress.kt` (new): `WorkflowProgress`, `MonotonicProgress`.
- `workflow/WorkflowActivityInitializer.kt`: non-loop nodes only.
- `workflow/NodeReadiness.kt`: `isDecidedByLoopRunner`.
- `workflow/WorkflowDagExecutor.kt`: dispatches `NodeDispatchPayload.of(node)`.
- `workflow/WorkflowStatusReporter.kt`: progress delegated and clamped monotonic.
- Test fixtures:
  - `fixtures/FakeInitializeActivity.kt`: `requestedNodeIds`.
  - `fixtures/FakeContinuumNodeActivity.kt`: `receivedNodes`.
  - `test-workflows/loop-linear-3member.cwf.json` (new): seed → [fetch → parse → append] → write. The loop properties are the defaults with `maxIterations` 50 (valid against the descriptor), and members carry the editor field `extent`.

BE-12 (orchestration):
- `workflow/FlowVariableMerger.kt`: loop rule, `endOfIterationVariables`, private `MergeContext`; 189 lines.
- `workflow/loop/LoopCarriedVariables.kt` (new).
- `workflow/loop/LoopTopology.kt`: `isEntryMember`.
- `src/test/resources/flow-variables/loop-carry-cases.json` (new).

BE-9 (api-server `mcp`):
- `McpWorkflowSpec.kt`: `McpWorkflowSpecNode.parent`.
- `LoopNodeSpec.kt` (new).
- `WorkflowSpecValidator.kt`: passes `type`/`parent`.
- `WorkflowSpecLayout.kt` (new, 184 lines): per-scope longest-path layout, box sizes, parent-first order.
- `WorkflowSpecBuilder.kt`: delegates to the layout; `workflowNode` factory; the constants delegate to the layout.
- `ContinuumWorkflowMcpTools.kt`: `LOOP_RULES` in both descriptions; the nodes param now says `{key, nodeId, properties, parent}`.

Factory-id cleanup (comments, KDoc, assertion messages and docs only; no behaviour change):
- commons: `LoopNodeDescriptor.kt`, `LoopConditionEvaluator.kt`, `LoopEdgeRules.kt`, `LoopStructureRules.kt`, `ContinuumWorkflowModelLoopFieldsTest.kt`, `LoopNodeDescriptorTest.kt`, `WorkflowValidatorLoopStructureTest.kt`, `AGENTS.md`.
- orchestration: `LoopConditionEvaluators.kt`, `LoopConditionRuntimeCheck.kt`, `LoopReadinessGate.kt`, `LoopTopology.kt`, `DataConverterLoopFieldsTest.kt`, `StructuredTestLoopConditionEvaluator.kt`, `LoopConditionRuntimeCheckTest.kt`, `LoopTopologyTest.kt`, `NodeReadinessLoopTest.kt`, `AGENTS.md`.
- loop-condition: `AGENTS.md`, `README.md`.
- api-server: `WorkflowControllerGraphValidationTest.kt`.

Tests changed:
- `NodeReadinessLoopTest` › "a member is not ready while its loop is not in an active iteration". My round-2 test pinned the pre-BE-10 behaviour, "the loop box itself is ready". It now expects the box never to be ready, as BE-10 requires.

Docs:
- `release_notes/RELEASE_NOTES_v0.0.23.md`.
- Root `AGENTS.md` (module table, execution flow, Key versions `protobuf-java-util` row).
- `continuum-orchestration-service/AGENTS.md`, `continuum-api-server/AGENTS.md`, `continuum-loop-condition/AGENTS.md` + `README.md`.
- Root `README.md`: module table, new `continuum-orchestration-service` row; this fixes the round-2 gap.
- `docs/api/ENDPOINT_INVENTORY.md`: task-queues `$builtin`, and when the Loop's registry entry exists.
- `docs/feature-registration-system.md`: new orchestrator producer section.

`git diff --stat`: 32 tracked files, +626/−161. There are 54 untracked paths in total, counting rounds 1–3.

### Unit tests added/changed

| Test | Covers |
|------|--------|
| `BuiltinNodeRegistrationPublisherTest` (5): one send per ready event with every contract field (nodeId, Kafka key, featureId, `$builtin`, `orchestration-` worker id, `["Flow Control"]`, empty extensions, documentation); the manifest reads back (Jackson 3, as the bridge does) as `LoopNodeDescriptor.nodeData` with both schemas and defaults and no ports; one worker id per start; a throwing `StreamBridge` is swallowed and the next node is still sent; a refused send does not throw | AC-5 |
| `ContinuumWorkflowLoopPlumbingTest` (4, Temporal test env): fixture loop properties valid against the descriptor; `FakeInitializeActivity.requestedNodeIds` never contains the loop id and the run does not fail with `InitializationFailed`; members are received with `parentNode == null` and `collapsed == null` and each runs once (interim); progress never decreases, ends at 100, loop status null | AC-15, AC-25 (monotonic), interim behaviour |
| `LoopPlumbingTest` (6): loop never ready (with and without gate, and at the end); never skipped; dispatch strips fields, keeps the run model's own fields, passes loop-free nodes through as the same instance; progress counts only non-loop nodes; skipped counts once; `MonotonicProgress` sequence | AC-15, AC-25, AC-11 |
| `FlowVariableLoopCarryCasesTest` (1 SHA pin + 8 dynamic cases from `loop-carry-cases.json`): `page=2` overrides outside `page=1` in iteration 2; clash: later run-order member wins (also against `nodes` order); parallel-member clash by `nodes` order; variable set once stays carried; nested loops (outer carried reaches the inner entry member through its parent, inner wins on a clash); entry member of both loops (outermost first); entry member with no outside parent; loop-free unchanged. Each case asserts inherited per node, carried-after per loop, and condition variables before/after | AC-21 (merger part), AC-11 |
| `WorkflowSpecLoopTest` (8): spec with loop and `parent` valid with no warnings; unknown parent, edge to a loop and a path through an outside node reach `errors` with exact texts; loop properties merged and checked (`maxIterations` 0 → one error on `L`/`maxIterations`); built example: loop before members, type `Loop`, `collapsed:false`, merged properties, members inside the box at x = 40/370/700, `write` outside, edge ids unchanged; box width = 40 + 660 + 224 + 40 and `write` placed after the box; nested loop contained in its parent and parent-first; empty loop gets 520 x 320 plus the warning; exactly six `@McpTool`s and both workflow descriptions name the loop id, `parent`, "Edges never name a loop" and "properties.condition is a CEL expression" | AC-14 (structure; condition errors are BE-8, held) |
| `NodeReadinessLoopTest` (1 changed, see above) | AC-15 |
| All existing tests unchanged and passing (`WorkflowSpecBuilderTest` layout of loop-free specs keeps the 330/160 grid) | AC-11 |

### Verification

| Command | Result |
|---|---|
| `./gradlew --offline -q [--init-script include-knime-base] :<m>:dependencies --configuration runtimeClasspath` × 7 modules, before the pin | PASS |
| The one online resolve (above) | PASS, BUILD SUCCESSFUL in 15s |
| Same reports after the pin and at the end of the round | PASS; table above. Commons, worker-starter, message-bridge and knime-base have a **0-line diff vs main** |
| `./gradlew --offline :continuum-orchestration-service:test :continuum-api-server:test --continue` right after the pin (before any code change) | **PASS**: orchestration 81/0, api-server 237/0. This includes every Temporal test: `ContinuumWorkflowTest` 35, `WorkflowPropertiesValidationTest` 7, spike 2 |
| `./gradlew --offline :continuum-commons:test :continuum-loop-condition:test :continuum-orchestration-service:test :continuum-api-server:test :continuum-worker-springboot-starter:test :continuum-message-bridge:test --continue` (final) | **PASS**, BUILD SUCCESSFUL in 28s: commons 175, loop-condition 102, orchestration 105 (Temporal: `ContinuumWorkflowTest` 35, `WorkflowPropertiesValidationTest` 7, `CelInWorkflowSpikeTest` 2, `ContinuumWorkflowLoopPlumbingTest` 4), api-server 245 (`OpenApiSpecTest` included), worker-starter 40, message-bridge 3; 0 failures, 0 errors |
| `./gradlew --offline -q :continuum-api-server:generateOpenApiSpec`, then diff against the copy taken at the start of the round | PASS: spec **unchanged** (MCP is not in the OpenAPI spec; no REST schema changed) |
| Redocly lint | NOT RUN: the spec is unchanged since the round-1 lint passed |
| knime-base compile/test | NOT VERIFIED (pre-existing failure on main, see round 2); its dependency report is identical to main |
| Factory-id grep (`git ls-files -m -o --exclude-standard \| xargs grep -n -E '\b(BE\|FE\|AC\|D\|PR\|SCOPE\|P\|Q)-[0-9]+\b'`) | 43 → 9 (all 9 in exempt files) |

### API contract changes

- **REST/OpenAPI: none.** `docs/api/continuum-api-server.openapi.yaml` is unchanged; nothing to regenerate for FE-1.
- **MCP (client-visible for the workbench WorkflowBuilder agent and Claude Code; FE-11):**
  - `validateContinuumWorkflow` and `buildContinuumWorkflow` nodes accept optional `parent` (string). Tool names and other parameters are unchanged, and there are still six tools.
  - A loop is `{key, nodeId: "org.projectcontinuum.core.builtin.Loop", properties}`.
  - Built `.cwf`: the loop has `type: "Loop"`, `data.collapsed: false` and size = members' bounding box + 40 px sides/bottom + 60 px header (min 520 x 320). Members have `parentNode` and relative positions. Nodes are in spec order with each loop moved before its first member. Edge ids are unchanged.
  - Description text (for the FE-11 prompt twins): "Loops: a loop is a node with nodeId 'org.projectcontinuum.core.builtin.Loop'; the nodes inside it set parent to its key (the innermost loop). Edges never name a loop: connect the nodes inside it. The loop's properties.condition is a CEL expression."
- **Registry (AC-6, live):** after the new orchestrator starts, the node-explorer endpoints return the Loop with task queue `$builtin` and category `Flow Control`. NOT VERIFIED live (no stack run).
- **For FE-4:** copy `continuum-orchestration-service/src/test/resources/flow-variables/loop-carry-cases.json` verbatim and pin SHA-256 `8c13acecf761afea131e64eab6af5be6a1008787614fc5b6531cc67a761e7057`. The format is documented in the file's `format` block.
  - The TypeScript twin needs the "last-iteration view": the expected inherited values with the loop's stored `$variable` as the carried set.
  - FE-4 is unblocked by BE-12. The stored `$variable` itself comes with BE-14.

### Docs updated

- **Release notes 0.0.23:**
  - What's Changed line extended.
  - orchestration Details: Built-in Loop registration, Loop plumbing, Loop-carried flow variables, the protobuf-java-util dependency line; the "Nothing executes loops yet" wording replaced by the interim behaviour.
  - `continuum-loop-condition` dependency line: the util pin.
  - api-server: an MCP tools line.
  - Upgrade notes: a new "orchestration service now needs the schema registry and registers the Loop" note; the protobuf note now covers protobuf-java-util.
- **README:** root module table (new orchestration-service row); `continuum-loop-condition/README.md` (util pin, wording without ids). The root "Real loops coming" line is still true.
- **AGENTS.md:** root (module table, execution flow, Key versions row), orchestration (registration, plumbing, carried variables, config), api-server (MCP loops, util pin), loop-condition (util pin rule), commons (defaults described without ids).
- **OpenAPI:** spec unchanged. The inventory has the task-queues `$builtin` marker and when the Loop's registry entry exists; the loop condition rule and execution are still marked TODO there. `docs/api/CONVENTIONS.md` is unchanged (it names nothing that changed).
- `docs/feature-registration-system.md`: orchestrator producer section.

### SCOPE / questions

- **BE-8 HELD (CEL scope change).** Waiting for the revised requirements. Once it is released, the work is: the SCOPE-2 `conditionParser` injection, the validator condition rule, the api-server wiring and tests. `LoopNode.doc.md` and the descriptor's condition `description` still describe the D-2 subset; they belong to the rework.
- **Note (contract value).** `LoopTopology.entryMembers` includes members with no incoming edge from inside the loop, even when they have no outside parent either. Such a member gets only the carried set (case `entry-member-without-outside-parent`). FE-4 should copy the case file as is.
- **Note (condition variables with several outside parents).** `endOfIterationVariables` orders the loop's external incoming edges by their target's run order in the loop, then by target input port, `targetHandle` and source id, with the first value winning. The plan only said "fan-in order as today", and a fan-in order is only defined per node, so this extends it. It is pinned only for one outside parent; the nested case has the outer loop's chain.
- **Note.** The orchestration service now needs a reachable schema registry at start-up to register the Loop. This is release-noted. A failure is logged only, and the node is not registered until the next start (no retry). Ask the user if a retry is wanted (SCOPE, not done).

## Round 4

Date 2026-11-25. Repo `continuum-platform-core`, branch `CONTNM-180` (no git writes). Plan "Round 2 — full CEL" (02-plan.md line 822 on), requirements Round 3 (D-23..D-38). User decisions applied: regex program size cap 1,000; the entry-order rule for carried variables (run order in the loop, then input port, first value wins); `onNonBooleanResult` as a Loop property, default `fail`. House rules 12 and 13 applied: new behaviour sits in focused collaborators. `WorkflowDagExecutor` gets a `WorkflowLoops` seam (about 40 lines), `WorkflowValidator` gets two delegations (about 35 lines), and the publisher is thin. No factory IDs in the product (grep result below).

### javap / probe facts (offline cache, scratch dir `/tmp/loops-r4.dHo2Vf/`, nothing in the repo)
- Literal validator runner: `CelValidatorFactory.standardCelValidatorBuilder(Cel).addAstValidators(RegexLiteralValidator.INSTANCE, TimestampLiteralValidator.INSTANCE, DurationLiteralValidator.INSTANCE).build().validate(ast)`.
- Latest extension versions in 0.15.0 (`getExtensionLibrary(name, options).versions()`): math 2, lists 2, optional 2, sets 0, regex 0, comprehensions 0. Strings, encoders and bindings have no version parameter. `math(CelOptions, int)` and `sets(CelOptions)` are deprecated, so the code uses `math(2)` and `sets(0)`.
- Macro accumulator name: `@result`. The `cel.bind` iteration variable is `#unused`. Two-variable macros fill `iterVar2`.
- `CelFactory.standardCelBuilder()` is deprecated in favour of `plannerCelBuilder()`. The planner rejects null values in `vars` ("Map value cannot be null"), so I kept the standard builder.
  - Verified: the standard builder overflows the stack on a re-bound `cel.bind`. That bug is why the re-bound-name rule exists.
- `Program.trace(Map, CelEvaluationListener)`: a listener that throws aborts the evaluation as a `CelEvaluationException` whose cause is the thrown exception. Verified for `lists.range(1000000)`, the replace chain, 29 nested `cel.bind` doublings and `sets.contains` over two 60,000-element lists; each stops in 0 to 50 ms.
- Error position format: `evaluation error at <input>:<offset>: <message>`.
- Error code mapping:
  - a missing key is `ATTRIBUTE_NOT_FOUND`; the message is the key for `vars["x"]` and `key 'x' is not present in map.` for `vars.x`;
  - `ITERATION_BUDGET_EXCEEDED` is the iteration budget;
  - an oversized regex is `INVALID_ARGUMENT` "Regex pattern exceeds allowed program size".
- A statically typed `1 == 1.0` is a CHECK_ERROR, because heterogeneous equality applies only to `dyn` values. `vars["n"] == 1.0` is true.

### Tasks
| Task | Status | Notes |
|---|---|---|
| BE-19 | DONE | Every DELETE in the plan table is removed, with no dormant copy. `continuum-commons/build.gradle.kts` equals `main` (0-line diff; fixture lines removed by hand). New commons contract: `LoopConditionLimits`, `LoopConditionVariables`, `LoopConditionIssueCode`, `LoopConditionValidator.kt` (result types), `LoopConditionEvaluator.kt` (outcome contract). In the module: `LoopConditionIssues`, `CelAstWalk`, a rewired `LoopConditionTextPrecheck` (TOO_DEEP). `ScriptedLoopConditionEvaluator` replaces the structured one. Spike KDoc rewritten. |
| BE-22 | DONE | New units: `RegistrationRetryPolicy` (5 attempts, waits 5/10/20/40 s, cap 60 s), `BuiltinNodeRegistrationSender` (`RegistrationSender`, returns `Sent`/`NotAccepted`/`Failed`, never throws), `RetryingRegistration` (`RetryScheduler` seam). The publisher is thin: the ready event schedules work and returns; one daemon thread `builtin-node-registration`; `@PreDestroy` calls `shutdownNow`. |
| BE-12r | DONE | New case `several-outside-parents-earliest-entry-member-then-lowest-port-wins`: 3 outside parents, 2 entry members, edges and source ids ordered against the rule. It passed with no logic change. KDoc of `endOfIterationVariables` states the rule. New SHA-256 below. |
| BE-20 | DONE | New units: `LoopCelEnvironment` (shared; options and extensions pinned as constants), `CelLoopConditionValidator`, `ConditionResultTypeRule`, `CelLiteralChecks`, `ReboundNameRule`, `ReadVariableNames`, `LoopConditionValidators.default`. |
| BE-3r | DONE | Descriptor: `onNonBooleanResult` (enum, default `fail`, required, UI control after `onLimit`), new condition description and node description, KDoc no longer points at the plan. `LoopNode.doc.md` rewritten for full CEL, settings and failure cases, and now gives the measured ceiling. |
| BE-21 | DONE | `cel-conditions.json`: 86 rows (62 valid, 24 invalid, 94 evaluations). Every validation code has a row and every runtime code an evaluation. Ids are descriptive. Loader `CelConditionTable` plus table and robustness tests. Regex cap stays at 1,000: no valid row needs more. |
| BE-14 | DONE | New units in `workflow/loop/`: `LoopRunState`, `LoopSettings`, `LoopRunner` (230 lines), `LoopIterationProbe`, `LoopConditionCheck` with `ConditionOutcomeRecorder` (`Workflow.sideEffect`) and `RecordedConditionOutcome`, `ConditionOutcomePolicy` with `NonBooleanResultStrategy` (one object per value), `LoopReset`, `LoopStatusPublisher`, `LoopRunContext`, `WorkflowLoops`/`WorkflowLoopsFactory`. The executor now has a thin `advanceLoops` and passes the gate and carried maps. `FakeContinuumNodeActivity` gains `flowVariablesByInvocation`, `errorOnInvocation`, `invocationCount` and `receivedInputsByInvocation`. Progress needed no change to `WorkflowStatusReporter`: the monotonic clamp already covers resets. |
| BE-9r | DONE | `LOOP_RULES` carries the CEL condition text. Tool names and parameters are unchanged; still six tools. |
| BE-8 | DONE | New `LoopConditionRules`. `WorkflowValidator(conditionValidator: LoopConditionValidator? = null, descriptors)` plus a secondary `(descriptors)` constructor, so existing `WorkflowValidator { ... }` call sites compile unchanged. api-server `WorkflowRunValidator` and `WorkflowSpecValidator` inject `LoopConditionValidators.default` as a constructor default. |
| BE-15 | DONE | New units: `CelLoopConditionEvaluator`, `LoopConditionSizeGuard`, `FlowVariableCelValues`, `CelEvaluationFailures`, `ValuePreview`; orchestration `config.LoopConditionEngineConfig` (`@PostConstruct` installs the evaluator and warms it up). Replay test included. |
| BE-16 | DONE | `LoopHistoryGuard` (`RunHistory` seam; 40,000 events / 40 MiB; tests install a factory with a tiny threshold) and `LoopStartCheck` (injected validator plus the settings). Both are called by `LoopRunner`. |
| BE-17 | DONE | Measured on the Temporal test server: 10 events per member run, 1 per condition check (1-member: 34 events for 1 iteration, 133 for 10; 3-member: 54 and 333). `LoopBudget` (10 / 1 / 40,000), `LoopBudgetRule` (warning on `maxIterations`), `ContinuumWorkflowLoopHistoryBudgetTest` (±20 %). Ceiling documented: 1 member 3,636, 3 members 1,290, 10 members 396. |
| BE-18 | DONE | Greps, build diff, classpath reports and the six-module run below. |

### Files changed (repo `continuum-platform-core`)
- **Deleted** (all untracked from earlier rounds):
  - commons: `validation/loop/LoopCondition.kt`, `src/test/resources/loop-condition/golden-vectors.json`, `src/testFixtures/**` (`LoopConditionGoldenVectors.kt`), `src/test/.../validation/loop/LoopConditionTest.kt`;
  - loop-condition: `LoopConditionShapeGate`, `LoopConditionLiteralReader`, `CelConditionSyntax`, `CelLoopConditionParser`, `LoopConditionParsers`, `LoopConditionRejections`, `CelExpressionShapes`, `LoopConditionParserGoldenTest`, `CelLoopConditionParserRobustnessTest`;
  - orchestration: `workflow/loop/LoopConditionRuntimeCheck.kt`, `LoopConditionRuntimeCheckTest.kt`, `fixtures/StructuredTestLoopConditionEvaluator.kt`.
- **commons:**
  - `build.gradle.kts` (back to main);
  - `validation/loop/{LoopConditionLimits, LoopConditionVariables, LoopConditionIssueCode, LoopConditionValidator, LoopConditionEvaluator, LoopConditionRules, LoopBudget, LoopBudgetRule}.kt`;
  - `validation/WorkflowValidator.kt` (two delegations; it was already about 450 lines before this round, and the new logic is in the rules);
  - `node/builtin/LoopNodeDescriptor.kt`, `LoopNode.doc.md`;
  - tests: `LoopNodeDescriptorTest`, `WorkflowValidatorLoopConditionTest` (new), `WorkflowValidatorLoopStructureTest` (KDoc fix and budget case), `validation/loop/LoopBudgetRuleTest` (new);
  - `AGENTS.md`.
- **loop-condition:**
  - `build.gradle.kts` (fixtures out, test `jackson-module-kotlin` in);
  - main: `LoopCelEnvironment`, `CelLoopConditionValidator`, `LoopConditionValidators`, `LoopConditionTextPrecheck`, `LoopConditionIssues`, `CelAstWalk`, `ConditionResultTypeRule`, `CelLiteralChecks`, `ReboundNameRule`, `ReadVariableNames`, `CelLoopConditionEvaluator`, `LoopConditionSizeGuard`, `FlowVariableCelValues`, `CelEvaluationFailures`, `ValuePreview`;
  - tests: `CelConditionTable`, `LoopConditionValidatorTableTest`, `CelLoopConditionEvaluatorTableTest`, `CelLoopConditionValidatorTest`, `CelLoopConditionValidatorRobustnessTest`, `CelLoopConditionEvaluatorRobustnessTest`, `LoopCelEnvironmentTest`, `ReboundNameRuleTest`, `ReadVariableNamesTest`, `LoopConditionTextPrecheckTest`, `FlowVariableCelValuesTest`;
  - `src/test/resources/loop-condition/cel-conditions.json`;
  - `AGENTS.md`, `README.md`.
- **orchestration:**
  - `registration/{RegistrationRetryPolicy, BuiltinNodeRegistrationSender, RetryingRegistration}.kt` (new), `BuiltinNodeRegistrationPublisher.kt` (thin);
  - `workflow/loop/{LoopRunState, LoopSettings, LoopStartCheck, ConditionOutcomePolicy, LoopConditionCheck, LoopHistoryGuard, LoopRunContext, LoopIterationProbe, LoopReset, LoopStatusPublisher, LoopRunner, WorkflowLoops}.kt` (new);
  - `config/LoopConditionEngineConfig.kt` (new);
  - `workflow/WorkflowDagExecutor.kt` (delegation), `FlowVariableMerger.kt` (KDoc only);
  - tests: `ContinuumWorkflowLoopTest`, `ContinuumWorkflowLoopReplayTest`, `ContinuumWorkflowLoopHistoryBudgetTest`, `loop/{ConditionOutcomePolicyTest, LoopStartCheckTest, LoopHistoryGuardTest, LoopRunnerTest}`, `registration/{RetryingRegistrationTest, VirtualTimeScheduler}` (all new); `BuiltinNodeRegistrationPublisherTest`, `ContinuumWorkflowLoopPlumbingTest` (the interim assertion is gone; the loop now ends SUCCESS), `FlowVariableLoopCarryCasesTest` (pin) and `spike/CelInWorkflowSpikeTest` (KDoc) updated;
  - fixtures: `LoopRunHarness`, `LoopFixtures`, `ScriptedLoopConditionEvaluator` (new), `FakeContinuumNodeActivity`;
  - resources: `flow-variables/loop-carry-cases.json` (+1 case), `test-workflows/loop-linear-3member.cwf.json` (`onNonBooleanResult`, description);
  - `AGENTS.md`.
- **api-server:** `build.gradle.kts` (comment), `service/WorkflowRunValidator.kt`, `mcp/WorkflowSpecValidator.kt`, `mcp/ContinuumWorkflowMcpTools.kt`; tests `mcp/WorkflowSpecLoopTest` and `controller/WorkflowControllerGraphValidationTest`; `AGENTS.md`.
- **Repo docs:** root `AGENTS.md` (module table, execution flow, Child DOX Index), root `README.md` (module row, Flow Control row), `docs/api/ENDPOINT_INVENTORY.md` (the TODO is replaced by the condition rule, run display, warnings and the `onNonBooleanResult` schema entry), `docs/feature-registration-system.md` (retry), `release_notes/RELEASE_NOTES_v0.0.23.md`.
- `git diff --stat`: 31 tracked files, +865/−186; 34 untracked top-level paths (rounds 1 to 4).

### Unit tests added/changed (test → AC)
| Test | AC |
|---|---|
| `RetryingRegistrationTest` (6: success after N < 5 failures, stops there; 5 attempts then one ERROR naming the node and "5 attempts", for exceptions and refusals; waits 0/5/10/20/40 s; same worker id and time on every attempt; shutdown stops retries; delay cap). `BuiltinNodeRegistrationPublisherTest` (7: contract payload; one worker id; a throwing send is retried while the next node is still sent; the ready event returns without sending; refusal retried 5 times; production sends on thread `builtin-node-registration`) | AC-5 |
| `LoopNodeDescriptorTest` (`onNonBooleanResult` enum and required; UI order; defaults; doc has CEL and `onNonBooleanResult`, no "one flow variable compared"); `ContinuumWorkflowLoopTest` › invalid `onNonBooleanResult` started through Temporal | AC-7 |
| `LoopConditionValidatorTableTest` (86 dynamic rows plus code coverage and outcome coverage) | AC-12 |
| `CelLoopConditionValidatorTest` (rule order, message format); `WorkflowSpecLoopTest` › the 8 rejected conditions are exactly one error each in both MCP tools, the 7 accepted give none, `true` gives one warning; `WorkflowControllerGraphValidationTest` › rejected condition: start 400 and validate 200 `valid:false` with node/property; full-CEL condition valid; `WorkflowValidatorLoopConditionTest` (6) | AC-13, AC-14 |
| `FlowVariableLoopCarryCasesTest` (9 cases, new SHA); `ContinuumWorkflowLoopTest` › carried variables reach the entry member and the condition | AC-21 |
| `CelLoopConditionEvaluatorTableTest` (94 evaluations: missing variable, `||` absorbs, `"s"==1` false, `"s">1` fails, int vs double, null, nested maps, division by zero, overflow); `ContinuumWorkflowLoopReplayTest` › missing variable message with the real engine (column 5), `||` absorbs; `ContinuumWorkflowLoopTest` › scripted MISSING_VARIABLE message shape; `FlowVariableCelValuesTest` | AC-22 |
| `ContinuumWorkflowLoopTest` › history guard fails the loop under stop-with-warning; `LoopHistoryGuardTest` | AC-23 |
| `ContinuumWorkflowLoopHistoryBudgetTest`, `LoopBudgetRuleTest`, `WorkflowValidatorLoopStructureTest` › budget warning | AC-24 |
| `ContinuumWorkflowLoopTest` › progress never decreases, iteration messages | AC-25 |
| `ContinuumWorkflowLoopReplayTest` (3 iterations with `vars["page"] < 3 && vars["hasMore"]`, 3 side-effect markers, `WorkflowReplayer` passes; replay with a different evaluator installed passes; engine config installs and warms up) | AC-26 |
| `ContinuumWorkflowLoopTest` › invalid condition or setting at loop start: no workflow-task failure; `LoopStartCheckTest` (6) | AC-27 |
| `ContinuumWorkflowLoopTest` › paging loop 3 iterations; `check before` zero and two iterations; both limit policies; member failure in iteration 2; reset of members and inner edges; nested 2 x 3 = 6; one side-effect marker per check. `LoopRunnerTest` (5, without Temporal); `ContinuumWorkflowLoopPlumbingTest` (4) | AC-15..AC-20 |
| `LoopCelEnvironmentTest` (options, protos absent, shared environment, 118-name pin) | AC-42 |
| `CelLoopConditionEvaluatorRobustnessTest` (6 hostile conditions each under 100 ms with LIMIT_EXCEEDED naming the limit; tiny-stack thread; invalid text compiles to Failed; re-bound bind rejected; MISSING_VARIABLE variable and column; preview at most 100). `CelLoopConditionValidatorRobustnessTest` (9 hostile inputs, depth 32/33, comments, surrogate pairs, tiny-stack parse) | AC-43 |
| `ConditionOutcomePolicyTest` (7), `ContinuumWorkflowLoopTest` › non-boolean under `fail`, `treat-as-false`, and `before` with members skipped; `ContinuumWorkflowLoopReplayTest` › real engine, string result under treat-as-false | AC-44 |
| `LoopConditionTextPrecheckTest`, `ReboundNameRuleTest`, `ReadVariableNamesTest` | AC-12, AC-13 (validator units) |
| BE-18 greps, build diff and classpath reports (below) | AC-41, AC-11 |

### Verification
| Command | Result |
|---|---|
| Baseline before any change: six-module `--offline` run | PASS 175/102/105/245/40/3 |
| `./gradlew --offline :continuum-commons:test :continuum-loop-condition:test :continuum-orchestration-service:test :continuum-api-server:test :continuum-worker-springboot-starter:test :continuum-message-bridge:test --continue --rerun-tasks` (final) | **PASS**, BUILD SUCCESSFUL in 1m 42s. Commons 170 (the 19 deleted subset tests are gone), loop-condition 230, orchestration 153, api-server 250, worker-starter 40, message-bridge 3; 0 failures, 0 errors |
| `git diff main -- continuum-commons/build.gradle.kts \| wc -l` | 0 |
| `./gradlew --offline -q [include-knime-base init script] :<m>:dependencies --configuration runtimeClasspath` against the main baselines in `/tmp/loops-r2.IcQqYS/before-*` and `/tmp/loops-r3.Ps1NQ8/main-continuum-message-bridge.txt` | commons 0, worker-starter 0, knime-base 0, message-bridge 0 diff lines |
| `./gradlew --offline -q :continuum-api-server:generateOpenApiSpec`, then diff | spec **unchanged** (0 lines) |
| Factory-ID grep (`git ls-files -m -o --exclude-standard \| grep -v /build/ \| xargs grep -n -E '\b(BE\|FE\|AC\|D\|PR\|SCOPE\|P\|Q\|V\|W)-[0-9]+\b'`) | **0 hits** |
| Stale-word grep (`golden`, `SUPPORTED_SHAPE`, `LoopConditionRejection`, `LoopConditionParser`, `one flow variable`, `RuntimeCheck`, `StructuredTestLoop`, `testFixtures`) | 1 hit: the descriptor test that asserts the phrase is absent |
| Redocly lint | NOT RUN: the spec is unchanged |
| Online resolve | none; every command ran `--offline` |
| knime-base compile/test | NOT VERIFIED: pre-existing failure on main (round 2); its dependency report equals main |

### API contract changes
- **REST/OpenAPI: none.** The spec is byte-identical, so there is nothing for FE-1 to regenerate.
- **Client-visible validation** (start 400, validate 200 `valid:false`, schedule 400, MCP `errors`/`warnings`):
  - an invalid condition is one error with `node` = loop, `property` = `condition` and the text `Loop '<key>': the condition <reasonText>: <CEL message> (column n).`;
  - reason texts: EMPTY "is empty", TOO_LONG "is longer than 2048 characters", TOO_DEEP "nests brackets deeper than 32 levels", SYNTAX "is not a valid CEL expression", CHECK_ERROR "does not type-check", NOT_BOOLEAN "does not return true or false", INVALID_LITERAL "contains an invalid regex, timestamp or duration literal", REBOUND_NAME "binds a name that is already bound in an enclosing scope, or binds vars";
  - a condition over 2,048 characters gets the schema's maxLength error only;
  - warnings: "Loop 'L': the condition reads no flow variable, so it never changes; the loop runs until maxIterations and then follows onLimit." (`property` = `condition`) and "Loop 'L': maxIterations N x M nodes inside may exceed the run history limit (about K iterations with M nodes inside). The run stops with an error when the history is nearly full." (`property` = `maxIterations`).
- **Loop schema:** `onNonBooleanResult` (`fail` | `treat-as-false`, default `fail`, required) is in the registered `propertiesSchema`, and its UI control comes after `onLimit`. Loops saved before this round lack it:
  - at run (RUN mode) the schema reports it as missing;
  - the workbench dialog fills the default from the schema. Note for FE-1 and FE-8.
- **Run display** (FE-9):
  - statuses: BUSY "Iteration k of max"; SUCCESS "Finished after k iterations" or "Condition was false before the first iteration"; WARNING "Stopped at the limit of N iterations" or "Condition returned <type> in iteration k; treated as false." (`before` first check: "... before the first iteration ...");
  - FAILED `$error` texts:
    - "Loop 'L' reached its limit of N iterations and the condition still holds.";
    - "Loop 'L' stopped in iteration k: node 'm' failed.";
    - "Loop 'L': the condition returned a string ("yes") in iteration 2, not true or false.";
    - "Loop 'L': flow variable 'hasMore' is not set when the condition is checked in iteration 3 (column 1).";
    - "Loop 'L': the condition check in iteration k failed with CODE: the condition <message>.";
    - "Loop 'L' cannot start: ...";
    - "Loop 'L' stopped before iteration k: the run history has N events and would reach Temporal's limit. ...";
  - a finished loop's display-only `nodeToOutputsMap[L]["$variable"]`.
- **MCP description text** (for FE-11): "... The loop's properties.condition is a CEL expression over vars (a map of the flow variables) that must end in true or false; write vars[\"name\"] (also for names with dots). properties.onNonBooleanResult (fail or treat-as-false) decides what a non-boolean result does. validateContinuumWorkflow is authoritative for the condition: fix what it reports."
- **For FE-4:** `loop-carry-cases.json` now has 9 cases, SHA-256 `353fe17f0a3b9c2fee763c4f236396976b9edabba283a194075f9606f8f95706`.

### Docs updated
- Release notes 0.0.23:
  - What's Changed line;
  - Highlights "Loops";
  - commons: the `validation.loop` contract (the golden table and test-fixture wording is gone), descriptor, validator condition and budget rules;
  - loop-condition: environment, validator, evaluator, table (parser and golden lines replaced);
  - orchestration: the runtime-check sentence and "loops do not iterate yet" are gone; added loop execution, recorded outcomes and history guard, the registration retry, the entry-order rule, 9 cases;
  - api-server: MCP and condition validation;
  - Upgrade notes: registration retry, deploy together, iteration ceiling.
- `LoopNode.doc.md`: full CEL, settings, failure cases, ceiling table.
- READMEs: `continuum-loop-condition/README.md` rewritten; root `README.md` module row and Flow Control row ("Real loops coming" removed). The root "How It Works" section is a service-level diagram that loops do not change, so it is unchanged.
- AGENTS.md: root (module table, execution flow, Child DOX Index), commons, loop-condition (rewritten: environment contract, pinned versions, the `cel.bind` workaround, verification), orchestration (loop execution contract, side effect, retry), api-server (condition rule, MCP).
- OpenAPI: `ENDPOINT_INVENTORY.md` cross-cutting loop facts and start-section condition rule. `CONVENTIONS.md` unchanged: no spec building changed and it names no changed class. The spec is unchanged.
- `docs/feature-registration-system.md`: retry.

### SCOPE / questions
- **Note (cel-java builder).** I kept the deprecated `CelFactory.standardCelBuilder()`, because the planner builder rejects null map values in `vars`. The `cel.bind` stack overflow happens only on the standard builder, and the re-bound-name rule plus the catch in the evaluator cover it. Revisit on a cel-java upgrade.
- **Note (contract detail).** `CompiledLoopCondition` gained an optional `invalid: LoopConditionCheckResult.Invalid?`, so `compile` never throws. `evaluate` of such a condition returns `Failed` with the validation code.
- **Note (plan detail).** In the plan's AC-13 list, the 2,049-character text is reported by the schema's maxLength ("must be at most 2,048 characters long"), not by TOO_LONG: the validator does not check a property the schema already rejected, so it stays exactly one error. The validator itself returns TOO_LONG, which the table pins.
- **Note.** `WorkflowValidator.kt` is 478 lines; it was about 450 before this round, and this round adds only delegation. Splitting it further would be SCOPE for later.
- **NOT VERIFIED:** event counts on a real Temporal server (the budget is calibrated on the test server only); the registration retry against a real broker or schema registry outage; knime-base compile (pre-existing failure).
- The FE cross-check rows (`source: "builder"`) cannot be added until FE-10a pins RQB's output. The table format supports `source`.

## Round 5

Date 2026-11-25. Repo `continuum-platform-core`, branch `CONTNM-180` (no git writes). Scope: user decision to split `WorkflowValidator.kt` now (house rule 12). This is a pure refactor: no new rules and no message changes. The constructors and the public API are unchanged. House rule 13 applied: the factory-ID grep finds 0 hits.

Method: before touching the validator I wrote `WorkflowValidatorIssueOrderTest` and ran it against the unchanged 478-line class. The test uses two workflows: one that breaks nearly every node, edge, property and loop rule at once, and one with a path that leaves a loop and comes back. Both run in both modes. I froze the full output (kind, node, edge, property, message, in order; 86 lines) as `src/test/resources/validation/workflow-validator-issues.txt`. The refactored validator reproduces it byte for byte. The pre-refactor copy is kept at `/tmp/loops-r5/WorkflowValidator.before.kt`.

### User confirmations recorded (no code change)
- A condition over 2,048 characters keeps the schema's maxLength error only. The validator adds no TOO_LONG on top, because a property the schema rejected is not checked again. `WorkflowValidatorLoopConditionTest` › "a condition the schema rejected is not checked again" pins it.
- `CompiledLoopCondition.invalid` stays: `compile` never throws, and `evaluate` of such a condition returns `Failed` with the validation code.

### Tasks
| Task | Status | Notes |
|---|---|---|
| Split `WorkflowValidator` (house rule 12) | DONE | `WorkflowValidator` is a 73-line orchestrator: `NodeChecks`, then `LoopScopes`, then `EdgeChecks` build a `CheckedGraph`. A list of `WorkflowRule`s reads it, in reporting order: condition rule (only with a `conditionValidator`), loop structure errors, edge issues, `CycleRule`, `ConnectionWarningRule`, empty-loop warnings, budget warnings. Errors and warnings are concatenated in that order, which is exactly the order of the old `validate`. |
| Record user confirmations | DONE | See above. |

Behaviour-preserving details:
- Each node type is still looked up once, including unregistered (null) ones. `getOrPut` was avoided because it retries null values, and a new test pins this.
- An edge rejected for a port still claims its input port ("already fed by a.nope"). The duplicate-label check still returns before the input check.
- The loop condition's `schemaRejected` still means "the property checks of this node reported `condition`". It is now carried on `ResolvedNode.propertyIssues` instead of `errors.drop(countBefore)`.
- The old ordering subtlety, where condition issues came after all node issues, is unchanged: the condition rule runs as the first graph rule.

### Files changed (repo `continuum-platform-core`, all in `continuum-commons` unless noted)
Line counts: before, `validation/WorkflowValidator.kt` 478. After, the 10 files in `validation/` total 662.

| File | Lines | Why |
|---|---|---|
| `validation/WorkflowValidator.kt` (modified) | 73 | Thin orchestrator; same two constructors, KDoc states the reporting order |
| `validation/WorkflowValidationModel.kt` (new) | 56 | Public types moved verbatim, same package: `NodeDescriptorSource`, `ValidationMode`, `GraphNode`, `GraphEdge`, `WorkflowIssue` (same `@Schema`), `WorkflowValidationResult` |
| `validation/WorkflowRule.kt` (new) | 39 | `internal`: `WorkflowRule` fun interface, `RuleIssues`, `ResolvedNode` (+ `properties`, `propertyIssues`), `CheckedGraph` |
| `validation/NodeChecks.kt` (new) | 121 | Per-node checks (key, nodeId, registry lookup once per type, declared reserved ports, embedded-difference warning) and delegation to `PropertyRules` |
| `validation/PropertyRules.kt` (new) | 69 | Properties against the registered schema, AUTHORING vs RUN |
| `validation/EdgeChecks.kt` (new) | 118 | Per-edge checks; returns the valid edges |
| `validation/ReservedIdRules.kt` (new) | 47 | `$`-prefix messages for node keys, declared ports and edge ports |
| `validation/CycleRule.kt` (new) | 40 | Kahn; the collapsed-loop check runs only when the plain graph is acyclic |
| `validation/ConnectionWarningRule.kt` (new) | 37 | Not-connected and unfed-input warnings |
| `validation/LoopWorkflowRules.kt` (new) | 62 | Adapters over `validation.loop` (structure, empty loop, budget, condition) and the loop-properties extraction |
| `src/test/.../validation/WorkflowValidatorIssueOrderTest.kt` (new) | — | Golden full-output test plus lookup-once test |
| `src/test/.../validation/WorkflowValidatorUnitsTest.kt` (new) | — | Tests of the extracted units |
| `src/test/resources/validation/workflow-validator-issues.txt` (new) | 86 | Output captured from the pre-refactor validator |
| `AGENTS.md` | — | Validator structure contract, rule order, golden file |
| repo `release_notes/RELEASE_NOTES_v0.0.23.md` | — | commons: internal split, no behaviour change |

- Every file is at most 121 lines, and every function is under 40 lines. The longest is `PropertyRules.authoringIssues` at about 21 lines.
- `validation/loop/*` is untouched.
- api-server callers (`WorkflowRunValidator`, `WorkflowSpecValidator`) are unchanged.

### Unit tests added/changed (test → AC)
| Test | AC |
|---|---|
| `WorkflowValidatorIssueOrderTest` › every-rule workflow gets the pinned issues in the pinned order (both modes); collapsed-cycle single error (both modes); unregistered node type looked up once | Refactor guard; behaviour of AC-13/AC-14 (condition), the loop structure ACs and the pre-existing graph rules unchanged |
| `WorkflowValidatorUnitsTest` (11): `PropertyRules` AUTHORING once-only and RUN all violations, empty schema; `ReservedIdRules` input-then-output order; `EdgeChecks` valid-edge filtering and order, loop endpoint one error, rejected edge still occupies its input; `CycleRule` leftover nodes and acyclic case; `ConnectionWarningRule` mode wording and single-node case; `LoopWorkflowRules.loopProperties` AUTHORING merge vs RUN as sent and `schemaRejected` | Unit coverage of the extracted units (house rule 12) |

Existing tests are unchanged and pass: `WorkflowValidatorTest`, `WorkflowValidatorLoopStructureTest`, `WorkflowValidatorLoopConditionTest`, `LoopBudgetRuleTest`, and api-server `WorkflowSpecValidatorTest`, `WorkflowSpecLoopTest`, `WorkflowControllerGraphValidationTest`.

### Verification
| Command | Result |
|---|---|
| Golden capture against the unchanged validator (`--tests ...WorkflowValidatorIssueOrderTest`, capture variant) | 86 lines written, then frozen as the resource |
| `./gradlew --offline :continuum-commons:test :continuum-api-server:test :continuum-orchestration-service:test --continue --rerun-tasks` | **PASS**, BUILD SUCCESSFUL in 47s. commons **184** (170 + 14 new), api-server **250**, orchestration **153**; 0 failures, 0 errors, 0 skipped |
| `./gradlew --offline :continuum-worker-springboot-starter:compileKotlin :continuum-message-bridge:compileKotlin :continuum-loop-condition:compileKotlin` | PASS |
| `./gradlew --offline -q :continuum-api-server:generateOpenApiSpec`, then `diff` against the pre-run copy | **0 lines**: spec unchanged (the `WorkflowIssue` `@Schema` moved files verbatim) |
| Factory-ID grep over modified and untracked files | 0 hits |
| Redocly lint | NOT RUN: the spec is unchanged |

### API contract changes
None. Same spec, same messages, same order, same constructors. FE has nothing to regenerate.

### Docs updated
- `continuum-commons/AGENTS.md`: validator structure contract. The validator only orchestrates, new rules are new `WorkflowRule`s, the reporting order is listed, and the golden file is to be updated only for deliberate changes.
- Release notes 0.0.23: one commons line (internal split, no behaviour change).
- README: no section is affected. The root module table describes commons at module level, and nothing it lists changed.
- OpenAPI inventory and CONVENTIONS.md: unchanged, because no endpoint, model or spec building changed.

### SCOPE / questions
- None. The round-4 note "`WorkflowValidator.kt` is 478 lines" is resolved.

## Round 6 — fix round 1

Date 2026-11-25. Repo `continuum-platform-core`, branch `CONTNM-180`, uncommitted, no git writes. Fixes exactly BR-1, BR-2 and BR-3 from `07-backend-review.md` Round 1, and F-1 from `05-backend-system-tests.md` (the user decided to fix it in this feature). BR-4..BR-8 are not touched. House rules 12 and 13 apply: new behaviour sits in small collaborators, and the factory-ID grep finds 0 hits. Every Gradle command ran `--offline`.

### Tasks
| Task | Status | Notes |
|---|---|---|
| BR-1 node after a loop loses carried variables | DONE | Failing test first: `write received {page=1, hasMore=false}`. Fix: `LoopRunState.passesCarriedVariablesOn` (ITERATING, or FINISHED with SUCCESS/WARNING after at least one iteration). `LoopRunner.carriedByLoopId()` filters on it. Zero-iteration, FAILED and SKIPPED loops pass nothing on. A nested loop's state, and so its set, is still dropped when the outer loop restarts. New shared case; SHA-256 updated. |
| BR-2 loop with no running member spins in one workflow task | DONE | Semantics, consistent with the validator's empty-loop warning and the broken-input rule. (a) No non-loop node inside at any depth: after the start check, no iteration, loop **WARNING** "The loop has no nodes inside it to run; it ran no iteration", no condition check, no side effect, and the nodes after it run. (b) An iteration ended with no node inside having produced outputs or `$error` (all skipped): loop **SKIPPED** "No node inside the loop could run in iteration k", no condition check, and the descendants and the nodes after it are skipped. The run is not failed. New `LoopIterationProbe.hasNodeToRun` / `anyNodeRanInIteration`, `LoopStatusPublisher.skipped`. |
| BR-3 accepted condition blocks the workflow thread > 1 s | DONE | `LoopConditionLimits.MAX_EVALUATION_MILLIS = 200`. New `LoopConditionTimeBudget` is a `CelEvaluationListener` that reads `System.nanoTime` after every value; one instance per evaluation, clock injectable. `CombinedEvaluationListener` runs the size guard plus the budget. The common base `EvaluationLimitExceeded` lets `CelEvaluationFailures` map either guard to LIMIT_EXCEEDED "the condition took longer than 200 ms to evaluate". It stays inside `Workflow.sideEffect`; no change in the orchestrator. |
| F-1 failed run published FINISHED | DONE | `ContinuumWorkflow.publishCompletion()`: when `nodeErrorsMap` is non-empty, it sends `FAILED` plus `WORKFLOW_EXECUTION_FAILED`; otherwise `FINISHED` plus `COMPLETED`. It is still exactly one upsert. Temporal 1.35 `WorkflowStateMachines` matches `UPSERT_WORKFLOW_SEARCH_ATTRIBUTES` by command type only, so recorded runs replay. This holds for loop-free runs too. |

### Files changed (repo `continuum-platform-core`)
- orchestration main:
  - `workflow/loop/LoopRunState.kt`: `passesCarriedVariablesOn`.
  - `workflow/loop/LoopRunner.kt`: `carriedByLoopId` filter; the empty-loop finish in `tryStart`; the nothing-ran skip in `tryEndIteration`; `skipBecauseNothingRan`; KDoc. Now 266 lines, longest function `tryStart` about 35 lines.
  - `workflow/loop/LoopIterationProbe.kt`: two pure questions.
  - `workflow/loop/LoopStatusPublisher.kt`: `skipped`.
  - `workflow/ContinuumWorkflow.kt`: `publishCompletion`.
- loop-condition main:
  - `LoopConditionTimeBudget.kt` (new: budget plus `CombinedEvaluationListener`);
  - `EvaluationLimitExceeded.kt` (new);
  - `LoopConditionSizeGuard.kt` (extends the base);
  - `CelLoopConditionEvaluator.kt` (`timeBudget` constructor seam, combined listener);
  - `CelEvaluationFailures.kt` (any guard cause).
- commons main: `validation/loop/LoopConditionLimits.kt` (`MAX_EVALUATION_MILLIS`), `validation/loop/LoopConditionEvaluator.kt` (KDoc: the clock exception and why it is safe), `node/builtin/LoopNode.doc.md`.
- api-server main: `model/WorkflowRunResponses.kt` (`WorkflowRunStatusValues.DESCRIPTION`).
- tests and resources:
  - orchestration `ContinuumWorkflowLoopTest` (+5 tests, 2 tightened), `ContinuumWorkflowLoopReplayTest` (+1), `ContinuumWorkflowTest` (+2), `FlowVariableLoopCarryCasesTest` (pin), `resources/flow-variables/loop-carry-cases.json` (+1 case);
  - loop-condition `CelLoopConditionEvaluatorRobustnessTest` (+2), `resources/loop-condition/cel-conditions.json` (+1 row).
- docs:
  - `docs/api/continuum-api-server.openapi.yaml` (regenerated) and `docs/api/ENDPOINT_INVENTORY.md` (run status);
  - `release_notes/RELEASE_NOTES_v0.0.23.md`;
  - AGENTS.md of orchestration-service, loop-condition and commons;
  - `continuum-loop-condition/README.md`.
- `git diff --stat` (tracked files, all rounds): 34 files, +894/−560.

### Unit tests added/changed (test → finding / AC)
| Test | Finding / AC |
|---|---|
| `ContinuumWorkflowLoopTest` › "the node after a finished loop gets the last iteration's carried variables, also those set only in iteration 1" (**failing first**: `write received {page=1, hasMore=false}`; after the fix `write` = `{page=2, token=t1, hasMore=false}`; loop `$variable` = `{token=t1, page=2, hasMore=true}` = what `fetch` got in iteration 3 over seed's value) | BR-1, AC-21 |
| `FlowVariableLoopCarryCasesTest` › new case `node-after-finished-loop-gets-last-iteration-carried-set` (3 iterations; `write` expectInherited `{page:2, token:t1, hasMore:false}`); 10 cases | BR-1, AC-21 |
| `ContinuumWorkflowLoopTest` › empty loop at maxIterations 10,000 (WARNING, 0 condition checks, 0 side-effect markers, run FINISHED) | BR-2 |
| `ContinuumWorkflowLoopTest` › loop holding only an empty loop, both 10,000 (outer WARNING, inner SKIPPED, 0 markers) | BR-2 (the reviewer's nested shape) |
| `ContinuumWorkflowLoopTest` › empty inner loop (10,000) inside a 6-iteration outer loop with a member (member 6 runs, inner WARNING, exactly 6 markers) | BR-2 (the reviewer's RESOURCE_EXHAUSTED shape) |
| `ContinuumWorkflowLoopTest` › all members skipped (unconnected input), 10,000 (loop SKIPPED "No node inside the loop could run in iteration 1", member and node after it SKIPPED, run succeeds, 0 markers) | BR-2 |
| `CelLoopConditionEvaluatorRobustnessTest` › "a macro repeating an expensive function stops at the time budget …" (the reviewer's 2.1 s condition: LIMIT_EXCEEDED "the condition took longer than 200 ms to evaluate", measured 262 ms, asserted < 800 ms) | BR-3, AC-43 |
| `CelLoopConditionEvaluatorRobustnessTest` › "the time budget lets a condition finish within it and stops one that runs past it" (fake clock: within budget True; past budget stops at the first value after it) | BR-3 |
| `cel-conditions.json` row `macro-repeating-quadratic-sets-call-time-budget` (validator: valid, bool, reads `x`; evaluator: LIMIT_EXCEEDED, message) | BR-3, AC-12, AC-43 |
| `ContinuumWorkflowLoopReplayTest` › "a valid but slow condition fails the loop at the time budget instead of wedging the workflow task" (real evaluator, loop `$error` has LIMIT_EXCEEDED and the budget text, 0 `WORKFLOW_TASK_FAILED`, history replays) | BR-3, AC-26, AC-43 |
| `ContinuumWorkflowTest` › "a run with a failed node publishes FAILED as its final status and sets the failed search attribute" (loop-free; no FINISHED event at all; `describe()` gives `WORKFLOW_EXECUTION_FAILED`) | F-1 regression |
| `ContinuumWorkflowTest` › "a run whose nodes all succeed publishes FINISHED and sets the completed search attribute" | F-1 (no change for successful runs) |
| `ContinuumWorkflowLoopTest` › limit with fail and member failure now also assert the final run status `FAILED` | F-1, AC-18, AC-19 |

### Verification
| Command | Result |
|---|---|
| `./gradlew --offline -q :continuum-orchestration-service:test --tests '*ContinuumWorkflowLoopTest*last iteration*'` before the fix | FAIL as intended: `write received {page=1, hasMore=false}` |
| `./gradlew --offline :continuum-commons:test :continuum-loop-condition:test :continuum-orchestration-service:test :continuum-api-server:test :continuum-worker-springboot-starter:test :continuum-message-bridge:test --continue --rerun-tasks` | **PASS**, BUILD SUCCESSFUL in 1m 47s: commons 184, loop-condition **234** (+4), orchestration **162** (+9), api-server 250 (`OpenApiSpecTest` included), worker-starter 40, message-bridge 3; 0 failures, 0 errors, 0 skipped |
| `./gradlew --offline -q :continuum-api-server:generateOpenApiSpec`, then `git diff` | **changed**: only the run `status` description in `WorkflowRunSummary` and `WorkflowRun`, the same text. Enum, example, operationIds and schemas unchanged |
| `npx --yes @redocly/cli@2 lint docs/api/*.openapi.yaml --config redocly.yaml` | PASS: all three specs valid, no warnings |
| Factory-ID grep over modified and untracked files | 0 hits |
| `shasum -a 256 .../flow-variables/loop-carry-cases.json` | `f419100f5eb7b188c32dbcbb889749a001166e5c7d102228e150ded0c5779a66` |

### API contract changes
- **Run status (client-visible behaviour, values unchanged):**
  - a run in which any node or loop failed is now published, and stored, as `FAILED` instead of `FINISHED`. This shows in `GET /api/v1/workflow-runs/{id}`, the run list, the `status==` filter and the MQTT events. The `Continuum:ExecutionStatus` search attribute becomes `WORKFLOW_EXECUTION_FAILED`;
  - successful runs are unchanged;
  - the message-bridge stores the string as is, and no api-server code branches on it;
  - the workbench already treats `FAILED` as terminal with an error icon (`WorkflowViewer.tsx`, `WorkflowViewerWidget.tsx`, `WorkflowRunsTreeWidget.tsx`, `IJobUpdate.ts`), so nothing needs to change there;
  - `NodeStatus`, and with it the `IBaseNode.ts` mirror, is unchanged.
- **OpenAPI:** only the `status` description text changed (see above). The FE client regeneration would only refresh a doc comment, so it is not required.
- **Loop display, for FE-9:**
  - new loop end states: WARNING "The loop has no nodes inside it to run; it ran no iteration" and SKIPPED "No node inside the loop could run in iteration k" (no `$error`);
  - new failure text, under the existing LIMIT_EXCEEDED form: "Loop 'L': the condition check in iteration k failed with LIMIT_EXCEEDED: the condition exceeded a cost limit: the condition took longer than 200 ms to evaluate …".
- **For FE-4:** `loop-carry-cases.json` now has 10 cases, SHA-256 `f419100f5eb7b188c32dbcbb889749a001166e5c7d102228e150ded0c5779a66`. The new case expects the node after the loop to inherit with the last iteration's carried set applied. The workbench's `FlowVariables.ts` "last-iteration view" must apply the loop's display `$variable` to entry members when it computes the inheritance of nodes after a finished loop.
- **`continuum-system-tests`:** `suites/api/loops.js` reports F-1 as a known gap through `knownGap()`, and its README and AGENTS.md list it. Those checks should now assert `FAILED`, which is the system tester's job.

### Docs updated
- Release notes 0.0.23:
  - commons limits: 200 ms;
  - loop-condition evaluator: the time budget; the table now has 87 rows and 95 evaluations;
  - orchestration loop execution: the empty and all-skipped semantics;
  - carried variables: the node after the loop; 10 cases;
  - new orchestration bullet: failed runs are FAILED;
  - api-server response schemas: the status description;
  - Upgrade notes: "Failed runs are `FAILED` in the run record".
- `LoopNode.doc.md`:
  - the 200 ms limit, under limits and under failure cases;
  - what a node after the loop inherits, and what the loop shows after a run;
  - new section "When nothing inside the loop can run".
- AGENTS.md:
  - orchestration: execution bullet (empty and nothing-ran), `skipped`, `carriedByLoopId` rule, and a new "Final run status" bullet;
  - loop-condition: evaluator and listener contract, the clock-only-inside-sideEffect rule, robustness tests;
  - commons: the evaluator contract's clock exception and the 200 ms cap.
- READMEs:
  - `continuum-loop-condition/README.md`: limits, class table, robustness tests;
  - root `README.md` is unchanged: its module rows and the Flow Control row stay true.
- OpenAPI: `ENDPOINT_INVENTORY.md` cross-cutting "Run status" fact. `CONVENTIONS.md` is unchanged, because no spec building changed.

### SCOPE / questions
- **Semantics choice, for the user to confirm:** a loop whose members were all skipped in an iteration is **SKIPPED**, so the nodes after it are skipped, by analogy with the broken-input rule. The reviewer's alternative was to apply `onLimit` at once. A purely empty loop is **WARNING** and the nodes after it run, matching the validator's warning.
- **Budget note.** The budget is checked after each value, so one uninterruptible function call can still run past it. The reviewer's single `sets.contains` over two 10,000-element lists took 133 ms; with 200 ms the worst case stays well under 1 s. Lowering `MAX_COLLECTION_SIZE` for `sets.*` was not done; it would be a separate cap change.
- **For the live re-check by the system tester:**
  - (1) `GET /api/v1/workflow-runs/{id}` of the limit-fail, missing-variable, EVALUATION_ERROR, non-boolean-fail, failing-member and loop-free invalid-CreateTable runs must now be `FAILED`, so the 5 known gaps go away;
  - (2) a multi-iteration run with default flow variables: the node after the loop must receive the last iteration's carried defaults;
  - (3) an empty loop, and a loop around only an unconnected node, both at maxIterations 10,000, must end at once (WARNING and SKIPPED respectively) with few history events;
  - (4) a condition like the BR-3 one ends the loop FAILED with "took longer than 200 ms", and `temporal workflow describe` shows no workflow-task failures.

## Round 7 — fix round 2

Date 2026-11-26. Repo `continuum-platform-core`, branch `CONTNM-180`, uncommitted, no git writes. Fixes exactly BR-9 (MAJOR) from `07-backend-review.md` Round 2, as the user decided: **timeout via executor**. BR-4..BR-8 are not touched. House rules 12 and 13 apply: the new behaviour is in new, small collaborators, and the factory-ID grep finds 0 hits. Every Gradle command ran `--offline`.

### Tasks
| Task | Status | Notes |
|---|---|---|
| BR-9: one uninterruptible `sets.*` call blocks the workflow thread for about 1 s | DONE | New `ConditionEvaluationRunner` seam, with production `TimeBoxedConditionEvaluationRunner`. Inside the existing `Workflow.sideEffect`, the evaluation runs on a fixed pool of 4 named daemon threads. The workflow thread waits at most 200 + 50 ms. On timeout: LIMIT_EXCEEDED "the condition took longer than 200 ms to evaluate" (the same text, from the shared `LoopConditionLimits.evaluationTimeExceededDetail()`), `cancel(true)` interrupts the thread, and one WARN is logged. Pool full or check not started in time: LIMIT_EXCEEDED "... all 4 condition evaluation threads of the orchestrator are busy" within the same wait. The evaluator gains `EvaluationInterruptCheck`, so an abandoned evaluation stops at its next value. The cooperative 200 ms budget stays as the first line. Lifecycle: `LoopConditionEngineConfig` `@PostConstruct` / `@PreDestroy` (`shutdownNow`, no wait). |
| BR-9: three wrong claims | DONE | `LoopConditionLimits.kt` KDoc, release notes 0.0.23 evaluator line, `continuum-loop-condition/README.md` table row. Also changed: `LoopNode.doc.md`, the loop-condition, orchestration and commons `AGENTS.md`, and the `LoopConditionEvaluator` / `LoopConditionTimeBudget` / `CelLoopConditionEvaluator` KDoc, which said the same or "runs on the workflow thread". |

### Design choices
- **Fixed pool of 4, queue 200, `AbortPolicy`. Not one thread per evaluator.** One evaluator instance is shared by every workflow, so "one thread per evaluator" would make all checks of all runs wait on a single thread: one stuck call would make every other check time out. Why 4:
  - a normal check takes well under 1 ms, so 4 threads serve thousands of checks a second;
  - more threads would compete with the workflow threads for the pod's CPUs (the chart requests 500m, limit 2);
  - 3 threads can be held by abandoned calls while checks still run on the 4th.
  - The queue of 200 matches Temporal's default number of concurrent workflow tasks per worker.
- **Bounded accumulation.** A check that has not started when its wait ends gets LIMIT_EXCEEDED ("all ... threads are busy") and is removed from the queue (`executor.remove`), so it never runs later. A full queue is refused at once. At most 4 abandoned evaluations exist at any time, and they cannot multiply.
- **Margin 50 ms** (`WAIT_MARGIN_MILLIS`, KDoc'd). The cooperative budget fires at the first value after 200 ms, so a many-short-calls condition ends on its own a few ms later and gives the same outcome. 250 ms in total leaves 750 ms of Temporal's 1 s threshold for scheduling delay and the rest of the task.
- **Cooperative stop.** `EvaluationInterruptCheck` is a `CelEvaluationListener`: when `Thread.currentThread().isInterrupted` it throws an `EvaluationLimitExceeded` subclass and keeps the flag set. `FutureTask.cancel(true)` delivers the interrupt. A call already inside one cel-java function still runs until that call returns (0.7 to 1.3 s for the review's shape), then stops. Its result is discarded.
- **WARN, once per check.** Logged on the calling thread: "Loop 'L': the condition check in iteration k of workflow '<id>' (run '<runId>') did not finish within 250 ms; ...". It never includes a variable value or the condition text (a test asserts a value does not leak). The static SLF4J logger is fine here: the code runs only inside the side effect function, which Temporal never calls on replay, so the line is never duplicated by a replay.
- **Shutdown and interrupt.** `close()` calls `shutdownNow()` and returns at once; the daemon threads never block JVM exit. A check after shutdown, an evaluation interrupted by the shutdown, or an interrupted waiting thread throws `ConditionEvaluationUnavailable` out of the side effect. Nothing is recorded, Temporal fails that workflow task and retries it, normally on a live orchestrator. A shutdown therefore never turns into a recorded LIMIT_EXCEEDED that fails a user's loop.
- **Seam.** `LoopConditionCheck(recorder, runner = { LoopConditionEvaluators.runner })`. `LoopConditionEvaluators.install(evaluator, runner = DIRECT)`, so the scripted loop tests keep evaluating on the workflow thread exactly as before. `LoopRunner` passes a `ConditionCheckLabel(loopId, checkPoint)` (a thin edit at one call site, named arguments).

### Why this is deterministic and replay-safe
1. The runner is called only from inside the function passed to `Workflow.sideEffect` (`ConditionOutcomeRecorder.TEMPORAL`). Temporal 1.35 `SideEffectStateMachine.createMarkerCommand` calls that function only when not replaying, and writes its result into the `MARKER_RECORDED` event. A replay takes the result from the marker (`markerResultFromEvent`) and never calls the function. So the runner, the clock, the thread pool and the timeout never run on replay.
2. Every outcome, the timed-out and "busy" LIMIT_EXCEEDED included, is a normal `RecordedConditionOutcome`. Whatever wall-clock time decided it is recorded, exactly like the existing 200 ms budget. The tests replay these histories with `WorkflowReplayer`.
3. Workflow code blocks on nothing new except `FutureTask.get(250 ms)`, inside that side effect, at record time only. The evaluation thread touches no Temporal API: the evaluator is pure, and `Workflow.getInfo()` for the WARN line is read on the workflow thread. `getInfo` is allowed in the side effect's read-only mode. The evaluation thread receives an immutable variable map that it only reads.
4. The only non-recorded path is `ConditionEvaluationUnavailable` (shutdown or interrupt). It fails the workflow task before any marker exists, so the retry starts from the same history. This is a task retry, not non-determinism.
5. Loop-free runs and the command sequence are unchanged: still one marker per check, and no new commands, so no `getVersion` is needed.

### Files changed (repo `continuum-platform-core`)
- orchestration main:
  - `workflow/loop/ConditionEvaluationRunner.kt` (new): the seam, `DIRECT`, `ConditionCheckLabel`, `ConditionEvaluationUnavailable`.
  - `workflow/loop/TimeBoxedConditionEvaluationRunner.kt` (new, 190 lines; longest function about 22 lines): pool, bounded wait, interrupt, rejection, WARN, `close`.
  - `workflow/loop/LoopConditionCheck.kt`: `runner` constructor parameter, `check` argument, evaluation moved into `evaluateNeverThrowing`.
  - `workflow/loop/LoopConditionEvaluators.kt`: holds the runner too (`install(evaluator, runner)`, `reset`).
  - `workflow/loop/LoopRunner.kt`: passes `ConditionCheckLabel` (one call site).
  - `config/LoopConditionEngineConfig.kt`: starts the runner with the bean, `@PreDestroy stopEvaluationThreads()`.
- loop-condition main:
  - `EvaluationInterruptCheck.kt` (new).
  - `CelLoopConditionEvaluator.kt`: adds the listener; KDoc says the guards act between values and that the evaluator is thread-safe.
  - `LoopConditionTimeBudget.kt`: KDoc corrected; the message now comes from commons.
- commons main:
  - `validation/loop/LoopConditionLimits.kt`: corrected KDoc for the two mechanisms and the 250 ms guarantee; new `evaluationTimeExceededDetail()`.
  - `validation/loop/LoopConditionEvaluator.kt`: contract KDoc (thread-safe, interruptible, not "runs on the workflow thread").
  - `LoopNode.doc.md`.
- tests:
  - orchestration: `workflow/loop/LoopConditionCheckTest.kt` (new), `workflow/loop/TimeBoxedConditionEvaluationRunnerTest.kt` (new), `ContinuumWorkflowLoopReplayTest.kt` (+1 test, engine config test extended), `fixtures/LoopRunHarness.kt` (`runner` and `deadlockDetectionTimeoutMillis` parameters).
  - loop-condition: `CelLoopConditionEvaluatorRobustnessTest.kt` (+2), `resources/loop-condition/cel-conditions.json` (+1 row `sets-equivalent-over-two-list-variables`, 2 evaluations; now 88 rows, 97 evaluations).
- docs: `release_notes/RELEASE_NOTES_v0.0.23.md`, `continuum-loop-condition/README.md`, `continuum-loop-condition/AGENTS.md`, `continuum-orchestration-service/AGENTS.md`, `continuum-commons/AGENTS.md`.
- `git diff --stat` (tracked files, all rounds): 34 files, +896/−560. Most of this round is in untracked files of the new module and package.

### Unit tests added/changed (test → finding / AC)
| Test | Finding / AC |
|---|---|
| `LoopConditionCheckTest` › "one slow sets call over two large flow-variable lists fails within the budget and margin" (real evaluator, `sets.equivalent` over two 9,000-element lists). **Failing first:** with the old `LoopConditionCheck` it blocked 712 ms (assert < 450 ms). After the fix: 264 ms, the exact budget message, 1 WARN | BR-9, AC-43 |
| `LoopConditionCheckTest` › "a fast condition returns its own outcome through the runner" | BR-9 (no regression) |
| `TimeBoxedConditionEvaluationRunnerTest` › "a pool exhausted by stuck evaluations gives LIMIT_EXCEEDED within the wait instead of blocking" (2 threads held by interrupt-ignoring evaluations, a queued check and a rejected check both get LIMIT_EXCEEDED "busy", the rejection returns at once, the queue is empty afterwards, no 3rd evaluation ever starts, 4 WARNs) | BR-9 (bounded accumulation) |
| `TimeBoxedConditionEvaluationRunnerTest` › slow evaluation: budget message, the evaluation is interrupted, one WARN with loop, check point and workflow/run, no value | BR-9 (cooperative stop, logging) |
| `TimeBoxedConditionEvaluationRunnerTest` › `close()` with a stuck evaluation returns in < 100 ms; a later check throws `ConditionEvaluationUnavailable` | BR-9 (shutdown) |
| `TimeBoxedConditionEvaluationRunnerTest` › an evaluation interrupted by the shutdown gives `ConditionEvaluationUnavailable`, not an outcome to record | BR-9 (shutdown never fails a loop) |
| `TimeBoxedConditionEvaluationRunnerTest` › in-time outcome passes through with no WARN; threads are named `loop-condition-evaluation-<n>` daemons | BR-9 |
| `ContinuumWorkflowLoopReplayTest` › "one slow function call over two large list variables fails the loop without a workflow task failure, and replays". Setup: real evaluator, production runner, worker deadlock detector lowered to 500 ms, `fetch` sets `a` and `parse` sets `b` (9,000 one-element lists each), and a warm-up run so class loading is not measured. Result: loop FAILED with LIMIT_EXCEEDED and the budget text, 0 `WORKFLOW_TASK_FAILED`, exactly 1 marker, `WorkflowReplayer` OK. **Failing first:** the same test with `ConditionEvaluationRunner.DIRECT` (the old path) never finished. The 30 s harness wait timed out (`TimeoutException`), and the log shows `TMPRL1101` on the retried tasks: the BR-9 wedge, reproduced on an idle machine | BR-9, AC-26, AC-43 |
| `ContinuumWorkflowLoopReplayTest` › the engine config test now also asserts the runner is a `TimeBoxedConditionEvaluationRunner` and stops it | BR-9 (lifecycle) |
| `CelLoopConditionEvaluatorRobustnessTest` › "one long function call ends with the time budget only after it returns, so the orchestrator must bound the wait" (the review's shape at evaluator level: LIMIT_EXCEEDED, 0.7 s, documents why the runner is needed) | BR-9 |
| `CelLoopConditionEvaluatorRobustnessTest` › "an evaluation on an interrupted thread stops at the next value and keeps the interrupt flag" | BR-9 (cooperative stop) |
| `cel-conditions.json` row `sets-equivalent-over-two-list-variables`: `true` for reordered nested lists, `false` otherwise. The 9,000-element shape itself does not fit the table: it would only reproduce the 0.7 to 1.3 s call that the orchestrator's wait, not the evaluator, bounds. It is tested above | BR-9, AC-12 |

### Verification
| Command | Result |
|---|---|
| `./gradlew --offline -q :continuum-orchestration-service:test --tests '*LoopConditionCheckTest*'` before the fix | FAIL as intended: "took 712 ms" |
| The new Temporal test with `ConditionEvaluationRunner.DIRECT` (temporary local edit, then reverted) | FAIL as intended: `TimeoutException` (run stayed RUNNING), `TMPRL1101` in the log |
| The same test plus `LoopConditionCheckTest` with 54 CPU-bound shell loops on 18 cores (3× oversubscription; each loop stopped itself after 150 s) | PASS: 0 `TMPRL1101`, the 250 ms wait fired. A first stress run without the warm-up run showed one TMPRL1101, during *class loading* in `WorkflowLoopsFactory.create`, before any condition check. That is why the test now warms up first. A cold JVM in production is covered by the existing warm-up at bean start |
| `./gradlew --offline :continuum-commons:test :continuum-loop-condition:test :continuum-orchestration-service:test :continuum-api-server:test :continuum-worker-springboot-starter:test :continuum-message-bridge:test --continue --rerun-tasks` | **PASS**, BUILD SUCCESSFUL in 1m 50s: commons 184, loop-condition **239** (+5), orchestration **171** (+9), api-server 250 (`OpenApiSpecTest` included), worker-starter 40, message-bridge 3; 0 failures, 0 errors, 0 skipped |
| `./gradlew --offline -q :continuum-api-server:generateOpenApiSpec`, then `shasum -a 256 -c` against the pre-run hashes of `docs/api/*.openapi.yaml` | **unchanged**: all three specs OK |
| Redocly lint | not re-run: no spec changed |
| Factory-ID grep over modified and untracked files | 0 hits |

### API contract changes
None. No endpoint, model or status changed, and the specs are byte-identical. The loop failure text is the same LIMIT_EXCEEDED "the condition took longer than 200 ms to evaluate". One new loop failure detail is possible, under the existing LIMIT_EXCEEDED form: "... the condition could not be evaluated in time: all 4 condition evaluation threads of the orchestrator are busy". FE-9 needs nothing new, because it shows the `$error` text as is.

### Docs updated
- Release notes 0.0.23:
  - evaluator line: the corrected claim (the budget acts between values and cannot cut one call short), and the interrupt check;
  - new orchestration bullet "A condition check never blocks the workflow thread for more than about 250 ms" (threads, wait, WARN, busy outcome, queue bound, shutdown);
  - commons limits line names `evaluationTimeExceededDetail()`.
- `continuum-loop-condition/README.md`: limits paragraph, the corrected `LoopConditionTimeBudget` row, and a new `EvaluationInterruptCheck` row.
- `LoopNode.doc.md`: the 250 ms wait also covers one long function call.
- AGENTS.md:
  - loop-condition: listeners, the "between values only" contract, thread-safety, robustness tests;
  - orchestration: engine config lifecycle, a new "Bounded wait" bullet, tests;
  - commons: the evaluator contract (thread-safe, interrupted) and `evaluationTimeExceededDetail()`.
- Root `README.md`: unchanged. Its module table and Flow Control row stay true, and it does not mention timings.
- OpenAPI inventory and CONVENTIONS: unchanged (no API change).

### SCOPE / questions
- None blocking.
- Residual limit, stated in the docs: an abandoned call keeps one evaluation thread busy until that cel-java function returns (about 1 s for the review's shape; it cannot be interrupted inside the call). If 4 such calls overlap, further checks fail with "all 4 ... busy" until a thread frees up. This only affects runs whose condition is itself that slow, and those fail anyway.
- **For the live re-check by the system tester:**
  - (1) A loop whose condition is `sets.equivalent(vars["a"], vars["b"])`, with two members setting `a` and `b` to about 9,000 one-element lists each (each value under the 64 KiB worker cap). The loop must end FAILED with "LIMIT_EXCEEDED ... took longer than 200 ms to evaluate". The run must reach `FAILED` (not stay RUNNING). `temporal workflow show` must have no `WorkflowTaskFailed` and exactly one marker for the check. The orchestrator log must have one WARN "Loop 'L': the condition check in iteration 1 of workflow '...' (run '...') did not finish within 250 ms ...".
  - (2) Repeat (1) with the orchestrator CPU-throttled, for example the chart's 500m request with a `limits.cpu` of 500m: same result, no `TMPRL1101` in the log.
  - (3) The orchestrator log at start says "... with 4 evaluation threads", and a pod stop during a run is not held up.
  - (4) The round-6 checks still hold: the BR-3 macro shape fails at about 260 ms, and paging loops finish normally.

## Round 8 — fix round 3

Date 2026-11-26. Repo `continuum-platform-core`, branch `CONTNM-180`, uncommitted, no git writes. Fixes BR-10 / F-2 (MAJOR), documents BR-11 (MAJOR, docs only, as the user decided), and also fixes BR-12 (MINOR) and BR-13 (NIT), which the same change touched. BR-4..BR-8 stay open. House rules 12 and 13 apply. Every Gradle command ran `--offline`, and no daemon was left running (`./gradlew --stop`).

Resumed attempt: the interrupted run had left only `ContinuumWorkflowLoopEvaluationPoolTest.kt` (untracked) and nothing else. `git status` and the file timestamps showed no other partial edit. Its BR-10 test reproduced the bug (the fast run FAILED). Its BR-12 test was not sound:
- the 10 s and the 0 `WORKFLOW_TASK_FAILED` come from the Temporal test server, which numbered a fresh workflow task as attempt 2 (history dump: `WORKFLOW_TASK_SCHEDULED` attempt 2 right after an activity completion);
- the SDK reports a failure only for attempt 1 (`ReplayWorkflowTaskHandler.failureToWFTResult`: "Only fail workflow task on the first attempt, subsequent failures of the same workflow task should timeout"), so the attempt-2 failure became a `WORKFLOW_TASK_TIMED_OUT` after the 10 s task timeout;
- its fake runner also did not use the production runner.

I rewrote that test (see below).

### Tasks
| Task | Status | Notes |
|---|---|---|
| BR-10 / F-2: a "no free thread" outcome was recorded, so other runs' slow conditions failed a loop for good | DONE | Both pool-busy paths (queue full; still queued when the wait ends) no longer record anything when other runs hold the threads. They throw `ConditionEvaluationUnavailable` instead: nothing is recorded and Temporal retries the workflow task. A recorded LIMIT_EXCEEDED "took longer than 200 ms" is now given only to a check that **started and had run for the whole 200 ms budget** when the 250 ms wait ended. One addition beyond the literal decision, needed to avoid a self-wedge: see "Design" and SCOPE |
| Retry bound (no hot loop) | DONE | Verified in the Temporal 1.35 source and measured, see "Why the retry cannot flood history" |
| Shared pool documented (decision 2) | DONE | Release notes Upgrade notes, `LoopNode.doc.md` "Keep conditions cheap", orchestration `AGENTS.md` |
| BR-11: many slow checks in one workflow task | DONE (docs only) | A known-limitation entry in release notes Upgrade notes, `LoopNode.doc.md` and orchestration `AGENTS.md`, with the review's measured numbers (24 → 6.3 s, 30 → 7.9 s, about 40 → past 10 s, 48 → RUNNING after 45 s). No code change |
| BR-12: Temporal-level test of the shutdown retry path | DONE | A new test with the production runner closed for the first check: no marker, the task is retried, the loop is SUCCESS, the history replays. The mutation check (catching the exception inside the side effect) fails it |
| BR-13 (NIT) | DONE | `EvaluationLimitExceeded` and `LoopConditionSizeGuard` KDoc no longer say "workflow thread". The `LoopConditionLimits` header now describes the bounded wait. The racy `started` label is gone: `EvaluationTicket` decides "started or withdrawn" with one atomic compare-and-set, so the WARN text and the outcome always agree |

### Design (house rule 12)
- **New `workflow/loop/AbandonedEvaluations.kt` (90 lines), with three small units:**
  - `CheckedWorkflowRun(workflowId, runId)`: the run of the check. Read on the workflow thread, inside the side effect, through `Workflow.getInfo()`. It replaces the runner's old `workflowContext: () -> String`.
  - `EvaluationTicket`: an atomic state machine, WAITING → STARTED | WITHDRAWN → ENDED, plus the start time. `tryStart()` on the evaluation thread and `tryWithdraw()` on the waiting thread race, and exactly one of them wins:
    - a withdrawn check never evaluates;
    - a started check that the caller gave up on is known to hold its thread.
  - `AbandonedEvaluations`: the started evaluations the caller gave up on that still hold a thread, per run. Each one leaves the set in the evaluation thread's `finally`.
- **`TimeBoxedConditionEvaluationRunner`** (243 lines; longest function about 20 lines) delegates to these. The outcome on timeout:

  | Situation when the 250 ms wait ends | Result |
  |---|---|
  | The evaluation started and has run ≥ `budgetMillis` (200) | Recorded LIMIT_EXCEEDED "took longer than 200 ms" (as before); the thread is interrupted and the evaluation is tracked as abandoned |
  | Still queued (withdrawn), or started less than 200 ms earlier, or the queue was full | "No free thread in time" |
  | ... and only other runs' abandoned evaluations hold the threads | `ConditionEvaluationUnavailable`: nothing recorded, one WARN "... held by other runs; nothing is recorded and the workflow task is retried" |
  | ... and this run's own abandoned evaluations are among them | Recorded LIMIT_EXCEEDED "the condition could not be evaluated in time: the orchestrator's condition evaluation threads are still held by earlier condition checks of this run that did not finish in time" |
  | Runner stopped, or the waiting thread interrupted | `ConditionEvaluationUnavailable` (unchanged) |

- **Why the own-run case is recorded.** I measured it. With the literal rule ("busy → always retry"), a run with 5 parallel loops whose conditions are one stuck call each wedges itself:
  - the 4 abandoned calls are its own;
  - the 5th check retries;
  - each retry runs the first 4 again, because nothing of that task was recorded, so they are abandoned again.

  Measured with the rule disabled: the new test "a run whose own slow conditions hold every evaluation thread fails its loops instead of retrying for ever" hit `TimeoutException` after 30 s, and the run stayed RUNNING. That is the BR-3/BR-9 wedge again. The run has a slow condition and fails anyway, so recording the failure for it harms nobody else. Other runs are still never failed by it.
- **Late start counts as "no thread".** A check that a thread picks up only near the end of its wait has not had its 200 ms. It is treated like a queued check, not as "took longer than 200 ms". This also removes the BR-13 label race.
- **Seam and contract KDoc.** `ConditionEvaluationRunner.run` and `ConditionEvaluationUnavailable` KDoc now say it is also thrown when other runs hold every thread, and that it must never be caught inside the side effect. `LoopConditionCheck` is unchanged: it never catches.

### Why the retry cannot flood history (Temporal 1.35.0 source plus measurement)
- `SyncWorkflowContext.sideEffect` wraps the exception as `new Error(e)`, so the workflow task fails and no marker is written. The retry starts from the same history, so this is not a non-determinism problem.
- `ReplayWorkflowTaskHandler.failureToWFTResult` sends `RespondWorkflowTaskFailed` only for **attempt 1**. Every later attempt rethrows, and the server lets that task **time out** after the workflow-task timeout (10 s by default, which the api-server keeps). After the first, immediate retry, a waiting check therefore runs again at most once per 10 s.
- The history impact is small. In the BR-10 test the fast run's history has 1 marker and 1 to 2 failed or timed-out task events. On a real server, retried attempts after the first are transient (not written until one succeeds), so the growth is a few events per wait, not one per attempt. That is server behaviour I did not measure here; the test server writes each timeout.
- **Threads free themselves.** An abandoned call frees its thread when its one cel-java call returns (`EvaluationInterruptCheck` stops it at the next value):
  - about 1 s for `sets.equivalent` over 9,000-element lists;
  - up to about 9.4 s for the system tester's literal shape (9,9).

  A check delayed by other runs therefore succeeds on the first or second retry. The only way to keep a victim waiting is to keep starting new slow runs, and each of those fails its own loop.
- A queued check that gets no thread is withdrawn and never runs, so at most 4 abandoned calls exist and the queue cannot grow.

### Files changed (repo `continuum-platform-core`)
- orchestration main:
  - `workflow/loop/AbandonedEvaluations.kt` (new): `CheckedWorkflowRun`, `EvaluationTicket`, `AbandonedEvaluations`.
  - `workflow/loop/TimeBoxedConditionEvaluationRunner.kt`: the outcome rules above; new `budgetMillis` parameter; `workflowRun` replaces `workflowContext`; `allThreadsBusy()` removed; KDoc rewritten.
  - `workflow/loop/ConditionEvaluationRunner.kt`: KDoc of `run` and `ConditionEvaluationUnavailable`.
- orchestration tests:
  - `ContinuumWorkflowLoopEvaluationPoolTest.kt` (new, 3 Temporal tests);
  - `workflow/loop/TimeBoxedConditionEvaluationRunnerTest.kt`: the exhausted-pool test replaced by 3 tests; explicit test budget of 60 ms against a 100 ms wait;
  - `fixtures/LoopRunHarness.kt`: new `workflowTaskTimeout` parameter (default 10 s, as before). KDoc explains the test-server attempt numbering.
- loop-condition main (KDoc only, BR-13): `EvaluationLimitExceeded.kt`, `LoopConditionSizeGuard.kt`.
- commons main (KDoc only, BR-13): `validation/loop/LoopConditionLimits.kt`.
- docs:
  - `release_notes/RELEASE_NOTES_v0.0.23.md`: the orchestration "never blocks … 250 ms" line rewritten, plus 2 new Upgrade notes;
  - `continuum-commons/.../builtin/LoopNode.doc.md`: new "Keep conditions cheap" section;
  - `continuum-orchestration-service/AGENTS.md`: the bounded-wait bullet and the tests line.
- `git diff --stat` (tracked files, all rounds): 34 files, +898/−560. Most of this round is in untracked files of the new package.

### Unit tests added/changed (test → finding / AC)
| Test | Finding / AC |
|---|---|
| `ContinuumWorkflowLoopEvaluationPoolTest` › "a loop whose check finds every evaluation thread held by other runs finishes normally once a thread is free". Setup: production runner; 4 runs whose condition is a stuck, interrupt-ignoring call hold all 4 threads; then an unrelated fast loop runs. Expected: SUCCESS "Finished after 1 iterations", exactly 1 marker, ≥ 1 task retry, a WARN "held by other runs" naming the fast run, and the history replays. **Failing first:** before the fix the fast run FAILED, with a recorded "all 4 ... busy" | BR-10, F-2, AC-43 |
| `ContinuumWorkflowLoopEvaluationPoolTest` › "a run whose own slow conditions hold every evaluation thread fails its loops instead of retrying for ever" (5 parallel stuck loops in one run: all FAILED, 5 markers, 0 task retries, the 5th check never ran, replays). **Failing first** against the literal "always retry" rule: `TimeoutException` after 30 s, run RUNNING | BR-10 (no self-wedge), AC-43 |
| `ContinuumWorkflowLoopEvaluationPoolTest` › "a check that comes after the evaluation threads were stopped records nothing, and the retried task finishes the loop" (the production runner, closed for the first check, a live one for the retry: SUCCESS, 1 marker, ≥ 1 retry, 2 runner calls, replays). Mutation: catching the exception inside `LoopConditionCheck` makes this test and the BR-10 test fail | BR-12 |
| `TimeBoxedConditionEvaluationRunnerTest` › "a check that finds every thread held by other runs records nothing and is retried, both queued and refused" (a queued and a refused check both throw `ConditionEvaluationUnavailable`; the refusal is immediate; the queue empties; no 3rd evaluation starts; 4 WARNs) | BR-10 |
| `TimeBoxedConditionEvaluationRunnerTest` › "a check that finds the threads held by its own run's slow evaluations fails with LIMIT_EXCEEDED instead of retrying" (exact message; the check never ran) | BR-10 (no self-wedge) |
| `TimeBoxedConditionEvaluationRunnerTest` › "once its abandoned evaluation returns, a run no longer counts as holding a thread" | BR-10 (tracking is released) |
| Unchanged and still passing: slow evaluation → budget text + interrupt + WARN without values; close without waiting; interrupted by shutdown → unavailable; `LoopConditionCheckTest` (264 ms one-call shape); `ContinuumWorkflowLoopReplayTest` one-call test (0 task failures, 1 marker) | BR-9 regression |

### Verification
| Command | Result |
|---|---|
| `./gradlew --offline -q :continuum-orchestration-service:test --tests '*ContinuumWorkflowLoopEvaluationPoolTest'` before the fix (the interrupted attempt's file) | FAIL as intended: the fast run FAILED (BR-10). The old BR-12 test failed for the test-server reason explained above |
| The same, after the fix | PASS: 3/3 (3.9 s, 2.7 s, 1.6 s) |
| Mutation: own-run rule disabled | FAIL as intended: `TimeoutException`, run RUNNING (self-wedge) |
| Mutation: `ConditionEvaluationUnavailable` caught in `LoopConditionCheck` | FAIL as intended: 2 of 3 Temporal tests fail (the run FAILED) |
| `./gradlew --offline :continuum-commons:test :continuum-loop-condition:test :continuum-orchestration-service:test :continuum-api-server:test :continuum-worker-springboot-starter:test :continuum-message-bridge:test --continue --rerun-tasks` | **PASS**, BUILD SUCCESSFUL in 1m 58s: commons 184, loop-condition 239, orchestration **176** (+5), api-server 250 (`OpenApiSpecTest` included), worker-starter 40, message-bridge 3; 0 failures, 0 errors, 0 skipped |
| `shasum -a 256 -c` of `docs/api/*.openapi.yaml` before and after the full run | **unchanged** (all 3 OK) |
| Redocly lint | not run: no spec changed |
| Factory-ID grep over all 156 modified and untracked files | 0 hits |
| `./gradlew --stop`; `pgrep GradleDaemon\|KotlinCompileDaemon` | stopped; none left |

### API contract changes
None. No endpoint, model, status or spec changed. The possible `$error` texts of a loop changed:
- "... all 4 condition evaluation threads of the orchestrator are busy" no longer exists;
- new and rare: "Loop 'L': the condition check in iteration k failed with LIMIT_EXCEEDED: the condition exceeded a cost limit: the condition could not be evaluated in time: the orchestrator's condition evaluation threads are still held by earlier condition checks of this run that did not finish in time".

FE-9 shows `$error` as is, so the frontend needs nothing.

### Docs updated
- Release notes 0.0.23:
  - orchestration line "A condition check never blocks the workflow thread …": the shared threads, delay instead of failure, the own-run case, the retry pacing, and the withdrawn queue;
  - Upgrade notes: "Loop conditions share the orchestrator's 4 evaluation threads" (delay, never fail; WARN text), and "Known limitation: many slow condition checks in one workflow task" (measured numbers, author guidance, terminate a stuck run).
- `LoopNode.doc.md`: new "Keep conditions cheap" section, covering the shared pool (delay, not failure), the about-40-parallel-loops limit, and the guidance to do heavy work in a node and avoid many parallel heavy loops.
- Orchestration `AGENTS.md`: the bounded-wait rules (new classes, own-run rule, never catch inside the side effect, retry pacing, the BR-11 limit with numbers) and the tests line.
- The "all 4 threads are busy" LIMIT_EXCEEDED text is gone everywhere (grep over `*.kt`/`*.md`: 0 hits).
- Root `README.md`, `continuum-loop-condition/README.md` and AGENTS, commons `AGENTS.md`: checked and unchanged. They do not describe the busy outcome, and what they say about the evaluation threads, the 250 ms wait and thread-safety stays true.
- OpenAPI inventory and CONVENTIONS: unchanged (no API change).

### SCOPE / questions
- **Deviation from decision 1, for the user to confirm.** The decision said to record LIMIT_EXCEEDED only for a check that started and ran past the wait. I also record it for a check turned away **while its own run's abandoned evaluations hold threads**. Without this, a single run with 5 or more parallel slow loops retries for ever (measured: still RUNNING after 30 s), which is the wedge this round had to rule out. Other runs are never failed by it. If the user prefers the literal rule, the change is one `if` in `TimeBoxedConditionEvaluationRunner.noFreeThread` plus that test, and the self-wedge becomes a documented limit.
- The optional per-run cap ("at most one abandoned evaluation per run") from the review was not done.
- BR-11 is still open in code (decision 3); BR-4..BR-8 are still open.
- **For the live re-check by the system tester:**
  - (1) **F-2 probe again** (`crosstenant.mjs`): tenant A runs 4 × `oneLongCallCondition(9, 9)`, then tenant B runs the plain loop.
    - Tenant B's loop must end as on an idle orchestrator: WARNING at its limit, run FINISHED.
    - Its history: 1 marker per check, plus 1 or more `WorkflowTaskFailed` or `WorkflowTaskTimedOut` events. It may take up to about 10 to 20 s longer.
    - Orchestrator log: a WARN "... held by other runs; nothing is recorded and the workflow task is retried" naming B's run.
    - Tenant A's 4 runs: FAILED with the 200 ms budget text.
  - (2) **The throttled 8 × (8,8) scenario again.** All 8 runs must end FAILED with the **budget** text "took longer than 200 ms" (none with a "busy" text). The 4 later ones fail only after a retry.
  - (3) **One run with 5 parallel loops**, each with `ONE_LONG_CALL_CONDITION`. The run must end FAILED, not RUNNING. Expected: 4 loops with the budget text and 1 with "... still held by earlier condition checks of this run that did not finish in time" (the split can vary). Each check is recorded once.
  - (4) Regression: `suites/api/loops.js` (220 checks), `make api`, smoke, and tenancy isolation. The round-3 one-long-call checks still pass, with 0 `WorkflowTaskFailed` for a single slow run.

### Round 8 addendum

The user decided on the SCOPE item: **remove the own-run rule** and go back to decision 1 as written. A check turned away because the pool is busy (whether other runs or the same run's abandoned evaluations hold the threads) never records an outcome. It throws `ConditionEvaluationUnavailable`, and Temporal retries the task. The self-wedge is documented as a known limitation instead.

#### Changes
- **Removed:**
  - `workflow/loop/AbandonedEvaluations.kt`, with `CheckedWorkflowRun` and `AbandonedEvaluations`, which existed only for that rule;
  - the `if (heldByThisRun > 0)` branch in `TimeBoxedConditionEvaluationRunner.noFreeThread`;
  - the "... still held by earlier condition checks of this run that did not finish in time" `$error` text (code, KDoc, docs, release notes: 0 hits);
  - the runner's per-run tracking.

  The runner again takes `workflowContext: () -> String`, used for the WARN line only.
- **Kept, because it still fixes BR-13:**
  - `EvaluationTicket` (new file `workflow/loop/EvaluationTicket.kt`, 35 lines): an atomic WAITING → STARTED | WITHDRAWN state and the start time;
  - the late-start rule: a check whose evaluation started less than 200 ms before its wait ended is "no thread in time", not "took longer than 200 ms".
- **`TimeBoxedConditionEvaluationRunner`** (219 lines). Only a check that started and has run ≥ 200 ms when its wait ends records LIMIT_EXCEEDED. Every other case throws `ConditionEvaluationUnavailable` and logs one WARN "... nothing is recorded and the workflow task is retried":
  - queue full;
  - still queued when the wait ends;
  - started too late to have run the budget;
  - stopped;
  - interrupted.

  The class KDoc names the self-starvation limit. The `ConditionEvaluationRunner` / `ConditionEvaluationUnavailable` KDoc says "held by slow evaluations that earlier checks gave up on (of any run, this one included)".
- **Tests:**
  - removed: the Temporal test "a run whose own slow conditions hold every evaluation thread fails its loops …", and the unit tests "… held by its own run's slow evaluations fails with LIMIT_EXCEEDED …" and "once its abandoned evaluation returns, a run no longer counts …";
  - renamed and changed: "a check that finds every thread held by abandoned evaluations records nothing and is retried, both queued and refused" (now asserts the new WARN text);
  - added: "a check that gets a thread too late to run for the budget is retried, not failed as too slow" (BR-10, BR-13);
  - kept: the BR-10 Temporal test (its WARN assertion now uses the new text) and the BR-12 shutdown Temporal test.
- **Docs (known limitation, with the measured example):**
  - release notes Upgrade notes: new entry "Known limitation: one run with more slow parallel loops than evaluation threads". It says that more than 4 parallel loops with very slow conditions in one run hold all threads themselves, keep retrying and stay `RUNNING` until a retry finds free threads. Measured: 5 parallel stuck loops, still `RUNNING` after 30 s. Advice: keep conditions cheap, no side-by-side heavy loops, terminate a stuck run. The orchestration line 130 no longer mentions the own-run case;
  - `LoopNode.doc.md` "Keep conditions cheap": a new bullet with the same limit and advice; the pool bullet now says "other loop checks wait";
  - orchestration `AGENTS.md`: the bounded-wait bullet (any holder → retried; the self-starvation known limit with the measurement) and the tests line.

#### Verification
| Command | Result |
|---|---|
| `./gradlew --offline :continuum-commons:test :continuum-loop-condition:test :continuum-orchestration-service:test :continuum-api-server:test :continuum-worker-springboot-starter:test :continuum-message-bridge:test --continue --rerun-tasks` | **PASS**, BUILD SUCCESSFUL in 2m 2s: 184 / 239 / **174** / 250 / 40 / 3; 0 failures, 0 errors, 0 skipped. Orchestration is +3 over round 7: 2 Temporal tests (BR-10, BR-12), plus 2 runner unit tests in place of the old exhausted-pool test |
| `shasum -a 256 -c` of `docs/api/*.openapi.yaml` | unchanged (3 OK) |
| Factory-ID grep over all 156 modified and untracked files | 0 hits |
| `./gradlew --stop`; `pgrep GradleDaemon\|KotlinCompileDaemon` | stopped; none left |

#### API contract changes
None. The "all 4 ... busy" text no longer exists, and no new `$error` text was added. FE needs nothing.

#### For the system tester (replaces the round-8 list)
- (1) **F-2 probe again** (`crosstenant.mjs`):
  - tenant B's loop must end as on an idle orchestrator (WARNING at its limit, run FINISHED), possibly about 10 to 20 s later;
  - its history: 1 marker per check, plus 1 or more `WorkflowTaskFailed` / `WorkflowTaskTimedOut` events;
  - the orchestrator log: a WARN "... nothing is recorded and the workflow task is retried" naming B's run;
  - tenant A's 4 runs: FAILED with the 200 ms budget text.
- (2) **The throttled 8 × (8,8) scenario again.** All 8 runs end FAILED with the **budget** text "took longer than 200 ms", none with a "busy" text. The later 4 fail only after one or more retries.
- (3) **One run with 5 parallel loops**, each with `ONE_LONG_CALL_CONDITION` (or (9,9)). This is now the **documented limit**, not a FAILED run:
  - expected: the run stays `RUNNING` while it retries;
  - its history: repeated `WorkflowTaskFailed` / `WorkflowTaskTimedOut`, one about every 10 s, and no marker (or only a few);
  - repeated WARN "... nothing is recorded and the workflow task is retried";
  - no growth beyond a few events per retry;
  - it can be terminated normally;
  - record how long it stays RUNNING and whether it ever ends.

  Other tenants' loops started meanwhile must still finish (delayed, not FAILED).
- (4) Regression: `suites/api/loops.js` (220 checks), `make api`, smoke, and tenancy isolation. The round-3 single one-long-call checks still pass, with 0 `WorkflowTaskFailed`.

## Round 9 — fix round 4

Date 2026-11-26. Repo `continuum-platform-core`, branch `CONTNM-180`, uncommitted, no git writes, no branch switch. This is an extra round the user approved. It fixes F-3 (terminate leaves a stuck run `RUNNING`; pre-existing code, fixed by user decision), O-2 and BR-14 (docs wording), and BR-15 (NIT). Not touched: O-1 (cold-start warm-up, now a separate improvement), BR-4..BR-8. House rules 12, 13 and 14 apply. Every Gradle command ran `--offline`, and the Gradle daemons were stopped at the end.

### Tasks
| Task | Status | Notes |
|---|---|---|
| F-3: terminate of a stuck run leaves the record `RUNNING` | DONE | Owner: the **api-server terminate path**. After Temporal's hard `terminate()` returns, `WorkflowService` writes `TERMINATED` plus the end time to the owner's row, with a targeted update that skips a row that is already `TERMINATED`. The message-bridge upsert also got a guard, so a late event cannot reopen the row. Details below |
| O-2 / BR-14: retry pacing and self-wedge wording | DONE | Release notes (orchestration line, the shared-threads upgrade note, both known-limit notes), `LoopNode.doc.md` "Keep conditions cheap", orchestration `AGENTS.md` bounded-wait bullet. No "about 10–20 s", "once per 10 s" or "at most once per" text is left in platform-core |
| BR-15 (NIT) | DONE | `ConditionEvaluationRunner.kt` KDoc re-wrapped at 120. The late-start case now throws its own message ("... started only N ms before the wait ended, too late to use its 200 ms budget: the evaluation threads were busy"). The queue-full case also got an accurate text ("... all 4 are busy and the queue is full"). Only the still-queued case keeps "all N are held by abandoned evaluations". WARN texts are unchanged |

### Why the api-server owns the fix (how statuses reach `workflow_runs` today)
- `workflow_runs.status` gets `PENDING` from the api-server at start. After that, only the message-bridge writes it: it upserts every `WorkflowUpdateEvent` that `WorkflowStatusReporter.publishUpdate` sends from inside the workflow.
- `TERMINATED` came only from the `notifyTerminating` signal handler. A run that processes no workflow task never runs that handler, so no event exists to store.
- **Temporal itself emits no Kafka event on terminate**, so the bridge has nothing to react to without a new Temporal-polling component. The api-server is the only place that knows for sure that the terminate succeeded: `WorkflowStub.terminate()` returned without an exception. So it writes the status there, after the existing 5 s grace period.
- **Idempotency with the normal path.** Today a healthy run handles the signal, publishes `TERMINATED`, and the bridge stores it within the 5 s window. `markTerminated` then matches 0 rows (`status <> 'TERMINATED'`), so the healthy run's record, status, snapshot and `updatedAt` stay exactly as they are today.
  - Race 1, the bridge is late: the api-server writes `TERMINATED` first. The run's own `TERMINATED` event then still upserts (the guard lets `TERMINATED` through) and refreshes the snapshot. The status stays the same.
  - Race 2, a stale non-terminal event lands after the api-server write: for example an attempt that published RUNNING before the terminate (the system tester saw unrecorded attempts publish). The bridge guard ignores it, so the record cannot be turned back to `RUNNING`.
- **A run that ended on its own within the 5 s** makes Temporal throw `WorkflowNotFoundException` on terminate. Nothing is written, so it keeps FINISHED or FAILED.
- **Tenancy.** `requireOwned` still runs before any signal: another tenant gets 404 and nothing is scheduled. `markTerminated` is also scoped by `owned_by`.
- **Cancel is unchanged**: it writes no status. A run that cannot process the cancellation stays open in Temporal too (wedge 3), so `RUNNING` is still true there.

### Design (house rule 12)
- New `service/WorkflowStopScheduler.kt` (42 lines): `fun interface WorkflowStopScheduler` plus `TimerWorkflowStopScheduler` (`@Component`, one daemon `Timer`, the same as before). It is now a constructor seam, so tests run the delayed stop at once. It also catches a throwing stop, which the old in-line `TimerTask` did not: an uncaught exception would end the shared timer thread and silently drop every later cancel and terminate.
- `WorkflowService` (237 lines): the companion `Timer` is replaced by the injected scheduler. The delayed terminate delegates to a new private `terminateAndRecord` (about 20 lines). The hard stops use `workflowClient.newUntypedWorkflowStub(workflowId)`, the same Temporal call as `WorkflowStub.fromTyped(stub)`, but it can be faked in tests.
- `WorkflowRunRepository.markTerminated(workflowId, ownedBy, terminatedAt)`: a JPQL `UPDATE` of `status` and `updatedAt` only, scoped by owner, guarded by `status <> 'TERMINATED'`. It is never a `save()`, which would overwrite the bridge's snapshot (the same rule as `updateRunId`).
- Message-bridge upsert: `ON CONFLICT ... DO UPDATE ... WHERE workflow_runs.status <> 'TERMINATED' OR EXCLUDED.status = 'TERMINATED' OR EXCLUDED.run_id <> workflow_runs.run_id`. An event of another run id under the same workflow id (a Temporal reset) is still applied. A NULL run id on either side counts as the same run (it is ignored).

### Files changed (repo `continuum-platform-core`, this round)
- `continuum-api-server/.../service/WorkflowStopScheduler.kt` (new): the delayed hard-stop seam and its timer implementation.
- `continuum-api-server/.../service/WorkflowService.kt`: injects `stopScheduler`; `STOP_SIGNAL_GRACE_PERIOD_MS` is now a public const; adds `terminateAndRecord`; untyped stubs for the hard stops.
- `continuum-api-server/.../repository/jpa/WorkflowRunRepository.kt`: `markTerminated`.
- `continuum-api-server/.../controller/WorkflowController.kt`: `terminateWorkflow` description (the record says `TERMINATED` with `updatedAt` as the end time, also for a stuck run).
- `continuum-api-server/.../model/WorkflowRunResponses.kt`: status description and `updatedAt` description (×2 each), class KDoc.
- `continuum-message-bridge/.../repository/WorkflowRunRepository.kt`: the `TERMINATED` guard and KDoc.
- `continuum-orchestration-service/.../workflow/loop/ConditionEvaluationRunner.kt`: KDoc re-wrapped.
- `continuum-orchestration-service/.../workflow/loop/TimeBoxedConditionEvaluationRunner.kt`: `noFreeThread(check, why, reason)` with a message per case.
- `docs/api/continuum-api-server.openapi.yaml`: regenerated (description text only).
- Docs: `docs/api/ENDPOINT_INVENTORY.md`, `release_notes/RELEASE_NOTES_v0.0.23.md`, `continuum-commons/.../builtin/LoopNode.doc.md`, root `AGENTS.md` (bridge note), `continuum-api-server/AGENTS.md`, `continuum-orchestration-service/AGENTS.md`.
- Tests: `WorkflowServiceTest.kt`, new `repository/WorkflowRunTerminationRecordTest.kt`, `WorkflowControllerGraphValidationTest.kt` and `WorkflowControllerValidationTest.kt` (the `@Import` gains `TimerWorkflowStopScheduler`, because `WorkflowService` has a new constructor dependency), `WorkflowExecutionSnapshotToDbSyncHandlerTest.kt` (bridge), `TimeBoxedConditionEvaluationRunnerTest.kt`.
- `git diff --stat` (tracked, all rounds): 41 files, +1118/−602.

### Unit tests added/changed (test → finding / AC)
| Test | Finding / AC |
|---|---|
| `WorkflowServiceTest` › "terminating a run that never handles the signal records it as terminated once Temporal has stopped it". Setup: an owned run whose signal is accepted but never handled; the scheduler runs the stop at once. Asserts the 5 s grace period, `terminate("stuck")` **then** `markTerminated("run-1", "alice", t)` in that order, and that `t` is not before the call. **Failing first:** before the fix, "Wanted but not invoked: workflowRunRepository.markTerminated" | F-3 |
| `WorkflowServiceTest` › "a run that ended before the hard stop keeps the status it ended with" (`terminate` throws `WorkflowNotFoundException`, so no write) | F-3 (no false TERMINATED) |
| `WorkflowServiceTest` › "terminate of another owner's workflow never touches the run record" (404, no stop scheduled, no write) | F-3 tenancy, AC tenant isolation |
| `WorkflowServiceTest` › "cancel still only requests the Temporal cancellation and leaves the status to the run" | F-3 (cancel unchanged) |
| `WorkflowRunTerminationRecordTest` (full context on H2, real JPQL against a `workflow_runs` table) › "a running record becomes terminated with the end time, and its snapshot and progress are kept"; "a record the run already ended as terminated is not written again" (0 rows, `updated_at` unchanged); "another owner's record with the same workflow id is never changed" | F-3 (idempotent, targeted, tenant-scoped) |
| `WorkflowExecutionSnapshotToDbSyncHandlerTest` › "a late event of a terminated run never turns its record back to running" (pins the guard in the upsert SQL). **Failing first:** with the guard removed, 1 of 4 failed; restored, 4/4 | F-3 (no reopen by a stale event) |
| `TimeBoxedConditionEvaluationRunnerTest`: the late-start test now asserts its message says "too late to use its 60 ms budget" and not "no ... thread is free"; the queued-check test asserts "... all 2 are held by abandoned evaluations" | BR-15 |

The bridge's SQL cannot run on H2 (it has no `ON CONFLICT`). So I also ran the exact upsert text once against a **throwaway `postgres:16` container** (local image, started and stopped by me, `--rm`, nothing else touched), with these 5 cases:
- normal progression;
- stuck → api-server `TERMINATED` → stale RUNNING event with the same run id: ignored (`INSERT 0 0`);
- stale event with no run id: ignored;
- `TERMINATED` row → the run's own late `TERMINATED` event: stored, with a refreshed snapshot;
- an event with a new run id under the same workflow id: applied.

All five gave the expected rows.

### Verification
| Command | Result |
|---|---|
| `./gradlew --offline -q :continuum-api-server:test --tests '*WorkflowServiceTest'` before the fix | FAIL as intended: 1 of 22 ("Wanted but not invoked: workflowRunRepository.markTerminated ...") |
| The same plus `--tests '*WorkflowRunTerminationRecordTest'` after the fix | PASS 22 + 3 |
| `:continuum-message-bridge:test` with the guard removed / restored | FAIL 1 of 4 as intended / PASS 4 of 4 |
| `./gradlew --offline :continuum-commons:test :continuum-loop-condition:test :continuum-orchestration-service:test :continuum-api-server:test :continuum-worker-springboot-starter:test :continuum-message-bridge:test --continue --rerun-tasks` | **PASS**, BUILD SUCCESSFUL in 1m 51s: commons 184, loop-condition 239, orchestration 174, api-server **257** (+7, `OpenApiSpecTest` included), worker-starter 40, message-bridge **4** (+1); 0 failures, 0 errors, 0 skipped. The first full run had 21 api-server failures: the two `@WebMvcTest` slices import `WorkflowService` and lacked the new scheduler bean. Fixed by importing `TimerWorkflowStopScheduler` there |
| `./gradlew --offline -q :continuum-api-server:generateOpenApiSpec` + review | `docs/api/continuum-api-server.openapi.yaml` **changed, description text only**: the `terminateWorkflow` description, the `status` and `updatedAt` descriptions of `WorkflowRunSummary` and `WorkflowRun`. No path, operationId, field, schema, enum or status code changed. The cluster-manager and credentials specs are unchanged (hashes OK) |
| `npx --yes @redocly/cli@2 lint docs/api/*.openapi.yaml --config redocly.yaml` | PASS: "Your API descriptions are valid", all three specs |
| Factory-ID grep (`(BE|FE|AC|PR|SCOPE|BR|FR|F|O)-n`, `D-n`, `Q-n`, `P-n`) over all 165 modified and untracked files | 0 hits |
| `grep` for "10 to 20", "10–20", "once per 10", "at most once per" in `*.md`/`*.kt` | 0 hits |
| `./gradlew --stop`; `pgrep GradleDaemon\|KotlinCompileDaemon` | stopped; none left |

### API contract changes
- **Behaviour:** after `POST /api/v1/workflow/{id}/terminate` (204), `GET /api/v1/workflow-runs/{id}` now always reaches `status: "TERMINATED"`, with `updatedAt` as the end time, about 5 s later, also for a stuck run. Before, a stuck run stayed `RUNNING` for ever and showed up in `status==RUNNING`.
  - A status written this way sends **no MQTT event**. The workbench viewer already sets `TERMINATED` itself after the 204 (`WorkflowViewerWidget.handleTerminateRequest`); the runs tree picks it up on its next list fetch.
- **Spec:** description text only (see above). **No client regeneration is needed**: generated typescript-axios clients carry descriptions only as comments. Regenerating is optional.
- 404 and tenancy are unchanged.

### Docs updated
- Release notes 0.0.23:
  - Highlights line;
  - "What's Changed" line;
  - new "continuum-api-server: terminate" section;
  - message-bridge guard line;
  - Tests line;
  - the orchestration "never blocks ... 250 ms" line (retry pacing with the measured gaps, plus the expected SDK ERROR stack trace);
  - the "share the 4 evaluation threads" upgrade note (growing gaps; measured delays 11–22 s idle and 55–130 s throttled; the ERROR trace);
  - the self-wedge known-limit note (never ends on its own; each retry holds all 4 threads again and delays other users' checks; terminate now sets `TERMINATED`);
  - the many-checks known-limit note (terminate sets `TERMINATED`).
- `LoopNode.doc.md` "Keep conditions cheap": the retry gaps and waits, the expected error log, the self-wedge delaying other runs until terminated, and "it is then shown as terminated".
- Orchestration `AGENTS.md`: the retry pacing with the measurements and the ERROR trace; the self-wedge cross-tenant effect; the final-status bullet (`notifyTerminating` is not the only writer of `TERMINATED`).
- api-server `AGENTS.md`: `WorkflowStopScheduler`, the `markTerminated` contract (never a `save()`, owner-scoped, skips `TERMINATED`), the bridge guard, and cancel writing nothing.
- Root `AGENTS.md`: the message-bridge bullet (the upsert and its `TERMINATED` guard).
- `ENDPOINT_INVENTORY.md`: the run-status cross-cutting fact, and the cancel/terminate section (side effect, idempotency, no MQTT event).
- `CONVENTIONS.md`: unchanged, because how the specs are built did not change.
- READMEs: root `README.md`, `continuum-loop-condition/README.md`: checked and unchanged. Neither describes terminate, run-status writes or retry pacing. The message-bridge has no README of its own.

### SCOPE / questions
- None that blocks this round.
- Note for the user (no decision needed unless they disagree): **cancel** of a stuck run still leaves the record `RUNNING`. This is deliberate: Temporal also keeps such a run open with the cancel requested (wedge 3), so `RUNNING` is true. The fix was asked for terminate only.

### For the system tester (live re-check)
- (1) **Terminate a stuck run.** Wedge a run with 5 parallel `check: before` loops with `ONE_LONG_CALL_CONDITION` (all checks in one task). Wait until it is retrying (WARN "... nothing is recorded and the workflow task is retried"). Then:
  - another tenant `POST .../terminate` → 404, and that tenant's `GET /api/v1/workflow-runs/{id}` → 404;
  - owner → 204;
  - about 5 s later: Temporal `WORKFLOW_EXECUTION_TERMINATED`, and `GET /api/v1/workflow-runs/{id}` shows `status: "TERMINATED"` with `updatedAt` at about the terminate time + 5 s;
  - the run is no longer listed by `?filter=status==RUNNING`;
  - it stays `TERMINATED`: re-read after 1–2 minutes, because a late retry attempt may still publish an event.
- (2) **Healthy run regression.** Terminate a normally running run (for example a long node). The record must end `TERMINATED` exactly as before: one final status, and the snapshot shows the in-flight nodes as published by the run's own handler (not reset).
- (3) Terminate a run that finishes within the 5 s grace period: it must keep FINISHED or FAILED, not `TERMINATED`.
- (4) Cancel of a stuck run is unchanged (it may stay `RUNNING` while Temporal keeps it open).
- (5) Regression: `suites/api/loops.js`, `make api`, smoke, tenancy. The api-server log of a stuck terminate shows no WARN "Failed to terminate".

## Round 10 — fix round 5 (test only)

The previous heading "Round 9" is already used by fix round 4, so this round is numbered 10.

### Tasks
| Task | Status | Notes |
|------|--------|-------|
| Test for the delay and for surviving a throwing stop in `TimerWorkflowStopScheduler` (review finding BR-17) | DONE | New `TimerWorkflowStopSchedulerTest`. BR-16 untouched, as agreed. |

### Files changed
- continuum-platform-core: `continuum-api-server/src/test/kotlin/org/projectcontinuum/core/api/server/service/TimerWorkflowStopSchedulerTest.kt` — new, 2 tests.
- continuum-platform-core: `release_notes/RELEASE_NOTES_v0.0.23.md` — the existing "Terminate" Tests line now says api-server 9 and names the two new tests.
- Production code: no change and no testability seam needed. `TimerWorkflowStopScheduler` takes the delay as a parameter, so the test uses a short real delay.

### Unit tests added/changed
- `the stop does not run before the delay has passed` → BR-17(a): the stop has not run when `schedule` returns, then runs, and not earlier than the delay (400 ms, with 50 ms tolerance for the wall clock vs the monotonic clock).
- `a stop that throws does not prevent later stops from running` → BR-17(b): a stop throws, a second stop is scheduled on the same instance and still runs.
- Both wait on a latch with a 10 s upper bound, so they do not sleep for a fixed time. The delay test takes about 0.4 s.
- Mutation check: with the `catch` temporarily narrowed to `IOException`, the throwing-stop test FAILED (the timer thread died). The `catch (e: Exception)` is restored; the file is as before.

### Verification
| Command | Result |
|---------|--------|
| `./gradlew --offline :continuum-api-server:test --tests '*TimerWorkflowStopSchedulerTest'` | PASS (2 tests) |
| `./gradlew --offline :continuum-api-server:test --rerun-tasks` | PASS, BUILD SUCCESSFUL in 28s: 259 tests, 0 failures, 0 errors, 0 skipped (257 before + 2) |

### API contract changes
none

### Docs updated
- Release notes 0.0.23: the Terminate Tests line only. README, AGENTS.md and the OpenAPI files are unchanged (tests only).

### SCOPE / questions
- None.
