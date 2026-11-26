# Plan: Loops (while-loop scope around member nodes)

Round 1, 2026-11-25. Source: `01-requirements.md` Round 2 (D-1..D-22 supersede Round 1). Gate 1 approved 2026-11-25.
User decisions applied: P-1 allowed as a one-time online Gradle resolve (spike BE-1). Workbench (FE) tasks are on
hold and will probably run later on another machine, so every FE task is self-contained and names the exact backend
contract or artifact it consumes (see "Contract" below), plus its P-2 status.

Versions being prepared: core `platformVersion` 0.0.23 (`release_notes/RELEASE_NOTES_v0.0.23.md`; tag
`release/0.0.22` is the latest), workbench 0.0.16 (`release_notes/RELEASE_NOTES_v0.0.16.md`; latest tag `release/0.0.15`).

Checked while planning (facts, not assumptions):
- Temporal SDK 1.35.0 (orchestration) has `WorkflowInfo.getHistoryLength()` and `getHistorySize()` (javap on the cached
  jar), and `temporal-testing` 1.35.0 has `io.temporal.testing.WorkflowReplayer`. This closes the D-15 "API not verified" note.
- `continuum-orchestration-service/build.gradle.kts` already has `spring-cloud-starter-stream-kafka`,
  `kafka-avro-serializer` and `kafka-schema-registry-client`, so the registration publisher (BE-5) needs no new dependency,
  only a binding in `application.yaml`.
- `continuum-commons` and `continuum-orchestration-service` import the Spring Boot BOM (directly, or through the Boot
  plugin), and that BOM manages `com.google.protobuf:protobuf-java`. The version it pins, compared with what cel-java 0.15.0
  needs, is NOT VERIFIED (BE-1 checks it).

## Contract (frozen for the frontend; backend tasks implement exactly this)

| Item | Value | Owner task |
|------|-------|------------|
| Loop React Flow type | `Node.type = "Loop"` | BE-2 |
| Loop registered node id (`data.nodeModel`, also `data.id`) | `org.projectcontinuum.core.builtin.Loop` (constant `LoopNode.NODE_MODEL` in commons) | BE-2 |
| Task-queue marker in the registration | `$builtin` (constant `TaskQueues.BUILTIN_NO_ACTIVITY`) | BE-2 / BE-6 |
| Palette category | `Flow Control` | BE-3 / BE-6 |
| Membership field | optional `Node.parentNode: string` = id of the innermost loop; member `position` is relative to that loop (React Flow 11 semantics) | BE-2 |
| Collapsed flag | optional `NodeData.collapsed: boolean` on the loop node only | BE-2 |
| Loop size | `Node.width` / `Node.height` are the expanded box size; defaults 520 x 320 (builder: member bounding box + 40 px side padding, 60 px top header) | BE-9 (builder), FE-7 (editor) |
| Node order | a loop is listed before all of its members in `nodes[]` (React Flow needs parent-first) | BE-9 (builder), FE-7 (editor) |
| `propertiesSchema` | `{type: object, additionalProperties: false, required: [condition, maxIterations, onLimit, check], properties: {condition: {type: string, minLength: 1, maxLength: 2048, default: 'vars["hasMore"] == true'}, maxIterations: {type: integer, minimum: 1, maximum: 10000, default: 50}, onLimit: {type: string, enum: [fail, stop-with-warning], default: fail}, check: {type: string, enum: [before, after], default: after}}}` (titles and descriptions added by BE-3) | BE-3 |
| `propertiesUISchema` | VerticalLayout; `#/properties/condition` control with `options: {format: "query", language: "CEL"}`; then `maxIterations`, `onLimit`, `check` | BE-3 |
| Condition grammar | D-2 exactly; canonical form `vars["<name>"] <op> <literal>` (double quotes, one space around the operator) | BE-4 (contract), BE-5 (Kotlin), FE-3 (TS) |
| Golden vectors (single source) | `continuum-platform-core/continuum-commons/src/test/resources/loop-condition/golden-vectors.json`: array of `{id, input, accepted, variable?, operator?, literalType? (boolean, string, int, double), literal?, canonical?, reason?}`; `literal` is the JSON value (int and double as JSON numbers, `literalType` disambiguates) | BE-4 |
| Rejection reason codes | `EMPTY`, `TOO_LONG`, `SYNTAX`, `NOT_A_COMPARISON`, `UNSUPPORTED_OPERATOR`, `LOGICAL_OPERATOR`, `FUNCTION_CALL`, `FIELD_SELECTION`, `LITERAL_ON_LEFT`, `VARIABLE_COMPARISON`, `MULTIPLE_VARIABLES`, `UNKNOWN_IDENTIFIER`, `UNSUPPORTED_LITERAL`, `OPERATOR_TYPE_MISMATCH`, `VARIABLE_NAME_LENGTH`, `STRING_LITERAL_TOO_LONG` | BE-4 |
| Validator issues | `WorkflowIssue {message, node, edge, property}`, unchanged shape; loop issues listed in BE-7, BE-8, BE-16 | BE-7/8/16 |
| MCP spec | `nodes[]` entries gain optional `parent: "<loopKey>"`; a loop is `{key, nodeId: "org.projectcontinuum.core.builtin.Loop", properties}`; edges never name a loop; still six tools | BE-9 |
| Run display | loop `data.status`: `BUSY` while iterating, then `SUCCESS`, `WARNING` (stopped at the limit with `stop-with-warning`), `FAILED` or `SKIPPED`; `data.nodeProgress = {progressPercentage: iteration*100/maxIterations, message: "Iteration <k> of <max>"}`, final messages `Finished after <k> iterations`, `Stopped at the limit of <max> iterations`, `Condition was false before the first iteration` | BE-14 |
| Loop `$variable` (display only) | when a loop finishes, `nodeToOutputsMap[<loopId>]["$variable"]` holds the carried variables that the last executed iteration's entry members received (omitted when empty). No edge can read it (a loop has no ports). The workbench uses it to recompute inherited variables of entry members | BE-12 (rule), BE-14 (stored) |
| Failure detail | a failed loop has `nodeToOutputsMap`/errors entry `$error` (`text/plain`) with the message (limit, missing variable, type mismatch, history guard, invalid condition) | BE-13 / BE-14 / BE-15 / BE-16 |

Planner choice to confirm at Gate 2: the loop's display-only `$variable` (last row). Without it the workbench cannot
recompute the inherited variables of entry members after a run, because the snapshot only holds the last iteration.

## Acceptance criteria

| ID | Criterion | Verified by |
|----|-----------|-------------|
| AC-1 | After the single online resolve of `dev.cel:cel:0.15.0`, `./gradlew --offline` builds and tests `continuum-commons`, `continuum-orchestration-service`, `continuum-api-server`, `continuum-worker-springboot-starter` and `continuum-message-bridge` with no failures. The before/after `runtimeClasspath` dependency report shows no `protobuf-java`, `guava` or `grpc` version change that Temporal, Avro or Confluent reject | [unit] |
| AC-2 | Spike: a Temporal test workflow that evaluates `vars["n"] < 3` with cel-java inside workflow code completes, and `WorkflowReplayer.replayWorkflowExecution` replays its history with no non-determinism error and no `PotentialDeadlockException` | [unit] |
| AC-3 | `Node.parentNode` and `NodeData.collapsed` are optional: a workflow JSON without them parses as before, a JSON with them round-trips unchanged through the api-server and the Temporal data converter | [unit] |
| AC-4 | The committed `docs/api/continuum-api-server.openapi.yaml` documents `WorkflowNode.parentNode`, `NodeData.collapsed` and `type` values `BaseNode`/`Loop`, and `OpenApiSpecTest` passes. Redocly lint reports no errors | [unit] (lint: [review] if `npx` is refused) |
| AC-5 | At `ApplicationReadyEvent` the orchestration service sends exactly one `FeatureRegistrationRequest` per start, with nodeId `org.projectcontinuum.core.builtin.Loop`, taskQueue `$builtin`, categories `["Flow Control"]`, documentation markdown, and a manifest holding the contract `propertiesSchema` and `propertiesUISchema`. A failed send is logged and does not stop start-up | [unit] |
| AC-6 | On a live stack, after the orchestrator has started, the Loop appears in `getContinuumNodeMetadata`, node search, the node tree under "Flow Control" and the documentation endpoint | [system-api] |
| AC-7 | The Loop schema accepts the defaults. `maxIterations` 0 or 10,001, `onLimit` or `check` outside their enums, an empty `condition` and a `condition` over 2,048 characters each give one error naming the loop key and the property | [unit], [system-api] |
| AC-8 | Structure: a loop nested in a loop is valid. A parent chain that loops back gives an error naming the node. An unknown `parent`/`parentNode`, a parent that is not a loop, and `type: "Loop"` with a different `nodeModel` (or the reverse) each give an error naming the node. "Member of two loops" cannot be expressed (one pointer), checked by reading the model | [unit]; two-loops: [review] |
| AC-9 | An edge whose source or target is a loop gives an error naming the edge and saying loops have no ports. A loop is not reported as "not connected" | [unit], [system-api] |
| AC-10 | `X1 -> Y -> X2` with X1 and X2 in loop L and Y outside is rejected with an error naming L and Y. `A -> X1 -> X2 -> B` (A, B outside) is accepted | [unit], [system-api] |
| AC-11 | Workflows without loops: every existing test of commons, orchestration and api-server passes unchanged, and validation output for them is unchanged | [unit] |
| AC-12 | The Kotlin condition parser returns, for every row of the golden vector table, the accepted triple and canonical string or the rejection reason code given in the row | [unit] |
| AC-13 | Each rejected string of Round 2 (`&&`, `size()`, `vars.a`, literal on the left, variable vs variable, `<` on a string, `vars["a"] ==`, more than 2,048 characters) gives exactly one error with `node` = loop key, `property` = `condition`, a reason and the supported shape; `POST /api/v1/workflow` answers 400 and `/validate` answers 200 with `valid: false` | [unit], [system-api] |
| AC-14 | MCP: `validateContinuumWorkflow` accepts a spec with a loop and members declaring `parent`; the structure and condition errors of AC-8..AC-13 come back in `errors`. `buildContinuumWorkflow` returns a `.cwf` where the loop has `type: "Loop"` and comes before its members, every member has `parentNode` and a relative position inside the box (`0 <= x`, `x + width <= loop width`, same for y), nested boxes contain their members, and edges are unchanged. There are still exactly six MCP tools | [unit] |
| AC-15 | A workflow with a loop requests task queues only for non-loop nodes and does not fail with `InitializationFailed`; a dispatched member receives its `Node` without `parentNode` and `collapsed` | [unit] |
| AC-16 | Example workflow, check `after`, max 50: "Fetch page" sets `hasMore=true` in iterations 1 and 2 and `false` in iteration 3. Then each of the three members runs exactly 3 times, the loop ends `SUCCESS` with message `Finished after 3 iterations`, "Write table" runs once and receives the iteration-3 outputs, and the run is `FINISHED` | [unit] |
| AC-17 | Check `before`, `hasMore=false` from "Set seed": members and outside consumers of member outputs are `SKIPPED`, the loop is `SUCCESS` with `Condition was false before the first iteration`, the run is `FINISHED`. With `before` and a condition that turns false after 2 iterations, members run exactly 2 times | [unit] |
| AC-18 | Max 5, condition always true, `onLimit: fail`: members run 5 times, the loop is `FAILED`, outside consumers are `SKIPPED`, the run fails and the loop's error names the loop and the limit 5. With `stop-with-warning`: the loop is `WARNING`, downstream runs with the iteration-5 outputs, the run is `FINISHED` | [unit] |
| AC-19 | A member that fails in iteration 2 stops the loop (no iteration 3), the loop is `FAILED`, outside consumers are `SKIPPED`, the run fails | [unit] |
| AC-20 | Between iterations every member and every edge inside the loop is reset (status, progress, outputs, errors, edge status, animation); an outside consumer of a member never starts before the loop has finished; nested loops: outer 2 iterations x inner 3 iterations runs the inner members 6 times | [unit] |
| AC-21 | Loop-carried variables (D-14): a member sets `page=2` in iteration 1; in iteration 2 the entry members see `page=2` and `hasMore`, overriding the outside value `page=1`; on a name clash the member later in topological order wins; the condition reads the end-of-iteration set; a node after the loop inherits the last iteration's variables; the finished loop stores the display `$variable` of the contract | [unit] |
| AC-22 | The condition variable missing when the check runs fails the run with a message naming loop, variable and iteration. A string variable against a number literal, or a list, map or null value, fails with a message naming the variable, the actual and the expected type. An int variable against a double literal compares numerically | [unit] |
| AC-23 | History guard: when the history length (or size) passes the guard threshold before an iteration, the loop fails with the guard message even with `onLimit: stop-with-warning` | [unit] |
| AC-24 | `maxIterations x member count` over the calibrated budget gives a warning (not an error) naming the loop and `maxIterations`; at or under the budget, no warning | [unit] |
| AC-25 | Progress: the published `progressPercentage` never decreases during a loop run and ends at 100; the loop's `nodeProgress.message` is `Iteration <k> of <max>` while iteration k runs | [unit] |
| AC-26 | With the real CEL evaluator, AC-16..AC-22 pass, and a recorded 3-iteration loop history replays with `WorkflowReplayer` without error | [unit] |
| AC-27 | A run started directly through Temporal (validator bypassed) with an invalid condition fails at loop start with the reason, not with a workflow-task retry loop | [unit] |
| AC-28 | Release notes (core 0.0.23, workbench 0.0.16) describe the feature with an Upgrade note (deploy orchestrator + api-server + message-bridge together, then the workbench; Loop appears in the palette after the new orchestrator has started; real iteration ceiling). READMEs and the DOX chain match the code | [review] |
| AC-29 | TypeScript: `LoopCondition.ts` gives the same result as the Kotlin parser for every golden vector row; `formatLoopCondition({variable: "hasMore", operator: "==", literal: true})` is exactly `vars["hasMore"] == true`; string escapes round-trip | [unit] (workbench spec) |
| AC-30 | TypeScript topology: a connection that creates `X1 -> Y -> X2` is refused by `isValidConnection`; the derived collapsed view hides members and re-points their edges to the loop outline without changing the stored edges | [unit] (workbench spec), [browser] |
| AC-31 | TypeScript `inheritedFlowVariables` gives the same results as the Kotlin merger for the AC-21 cases (shared case list) | [unit] (workbench spec) |
| AC-32 | Dropping "Loop" from the palette creates a resizable box (`type: "Loop"`, default size). Dropping a node inside makes it a member (`parentNode` set, relative position) that moves with the box; dragging it out clears `parentNode` and keeps its absolute position | [browser] |
| AC-33 | Collapsing a loop hides its members, draws their outside edges to the loop outline and saves `collapsed: true`; expanding restores them; the saved `.cwf` edges are byte-identical before and after collapse/expand | [browser] |
| AC-34 | Loop dialog builder: choosing variable `hasMore`, operator `==`, value `true` stores exactly `vars["hasMore"] == true`; reopening shows those three values; `vars["page"] <= 10`, `vars['x'] != "a\"b"` and `(vars["n"] > -1.5)` open in the builder (canonical form after the next edit) | [browser] (needs P-2) |
| AC-35 | Loop dialog with a rejected string (AC-13 list): no builder; the raw text, the same reason text and a "Start over with the builder" button are shown; the text is kept; typing a valid text brings the builder back. The Retry Policy tab is not shown for a Loop | [browser] (builder part needs P-2) |
| AC-36 | A Loop whose condition fails the TypeScript parser shows a warning badge on the canvas box | [browser] |
| AC-37 | Run viewer: the loop shows its status colour and `Iteration <k> of <max>`; members show their last-iteration status; a collapsed loop stays collapsed in the viewer | [browser] |
| AC-38 | Workflows without loops look and behave the same in the editor and the viewer (open, connect, cycle refusal, run display) | [browser] |
| AC-39 | `claude-code/workflow-builder.md` and `WorkflowBuilderPrompt.ts` carry the same loop rules: loops are `nodes[]` entries with the built-in id, members declare `parent`, edges never name a loop, the D-2 grammar with 3 examples and the D-3 exclusions, `validateContinuumWorkflow` as the check loop; the "one node per build" flow still applies | [review] |
| AC-40 | Only FE-10 imports `react-querybuilder`; every other FE task compiles without it (grep of the changed files) | [review] |

## Backend tasks

Repo for all BE tasks: `continuum-platform-core`. Paths below are relative to it. Package roots:
commons `continuum-commons/src/main/kotlin/org/projectcontinuum/core/commons/`, orchestration
`continuum-orchestration-service/src/main/kotlin/org/projectcontinuum/core/orchestration/`, api-server
`continuum-api-server/src/main/kotlin/org/projectcontinuum/core/api/server/` (tests mirror them under `src/test/kotlin`).
Every task: read the DOX chain (root `AGENTS.md` + the module `AGENTS.md`), named arguments in Kotlin (tests too),
`./gradlew --offline` only (BE-1 has the one allowed exception), release-note line in
`release_notes/RELEASE_NOTES_v0.0.23.md`, root `README.md` check (no module has its own README).

### BE-1 Spike: resolve cel-java once, check alignment, prove in-workflow CEL with replay (P-1)
- Repo/module: `continuum-commons` (dependency), `continuum-orchestration-service` (spike test only).
- Files:
  - modify `continuum-commons/build.gradle.kts`: `implementation("dev.cel:cel:0.15.0")`.
  - create `continuum-orchestration-service/src/test/kotlin/org/projectcontinuum/core/orchestration/spike/CelInWorkflowSpikeTest.kt`
    (a tiny test-only `@WorkflowInterface` whose implementation compiles `vars["n"] < 3` once, evaluates it in a loop of
    `Workflow.sleep(1s)` steps with `n` = 0..3, returns the iteration count).
  - add the orchestration dependency only if the commons `implementation` does not reach it (it does not: `implementation`
    is not transitive for compile). Use `implementation("dev.cel:cel:0.15.0")` in `continuum-orchestration-service/build.gradle.kts`
    too, or `api` in commons; record which.
- Change:
  1. Before touching anything, save `./gradlew --offline :<m>:dependencies --configuration runtimeClasspath` for
     commons, orchestration-service, api-server, worker-springboot-starter and message-bridge into a `mktemp -d` dir.
  2. The ONE allowed online command: `./gradlew :continuum-commons:dependencies --configuration runtimeClasspath`
     (no `--offline`) after adding the dependency, so `dev.cel:cel:0.15.0` and its transitive jars land in the Gradle
     cache. If the orchestration module needs its own resolve for a different transitive set, the same single command may
     list both modules (`:continuum-commons:dependencies :continuum-orchestration-service:dependencies`). No other online run.
  3. Diff the five reports offline. Record in `03-backend-dev.md`: the versions of `protobuf-java`, `guava`, `re2j`,
     `antlr4-runtime`, `grpc-*`, `failureaccess` before/after, and whether Spring's BOM or Temporal (which uses protobuf
     and gRPC) moved. Temporal 1.35 must keep the protobuf version it was built against within the same major line.
  4. Spike test (Temporal `TestWorkflowEnvironment`, as in `ContinuumWorkflowTest`): the workflow completes with 3, then
     `WorkflowReplayer.replayWorkflowExecution(history, SpikeWorkflowImpl::class.java)` with the fetched history passes.
     Measure engine first-use time (`System.nanoTime` around the first compile, outside workflow code) and record it.
  5. `./gradlew --offline :continuum-commons:test :continuum-orchestration-service:test :continuum-api-server:test
     :continuum-worker-springboot-starter:test :continuum-message-bridge:test`.
- Outcome (record one line in `03-backend-dev.md`, `ENGINE: cel-java` or `ENGINE: FALLBACK (<reason>)`):
  - PASS: keep the dependency; BE-5 uses the cel-java parser, BE-14 the cel-java evaluator.
  - FAIL (cannot resolve, version conflict that breaks a test, deadlock detector or replay failure): remove the
    dependency again (the file edit, not git), delete nothing else; BE-5 and BE-14 use the D-7 hand-written
    parser/evaluator. If only the in-workflow part fails, the dependency may stay and BE-14 uses the D-17 local-activity
    fallback; record which. Report the outcome to the orchestrator before BE-5 starts.
  - If the conflict is only that workers would inherit unwanted jars, keep the dependency out of commons and put it in
    orchestration + api-server only, and make BE-5's parser live in commons behind the interface with the CEL
    implementation in a new small module `continuum-loop-condition` (SCOPE: report it; settings.gradle.kts change).
- Unit tests: `CelInWorkflowSpikeTest` (stays as a regression test if PASS; removed with the dependency if FAIL).
- Docs: release notes "Dependencies" line (`dev.cel:cel` 0.15.0 and its transitive jars) under commons; root
  `AGENTS.md` "Key versions" table gets a `cel-java` row; root `README.md` stack section if it lists libraries.
- Depends on: none (user allowed the resolve on 2026-11-25).
- Covers: AC-1, AC-2.

### BE-2 Model: membership field, collapsed flag, Loop constants, OpenAPI
- Repo/module: `continuum-commons`, `continuum-api-server` (spec only).
- Files:
  - modify `commons/model/ContinuumWorkflowModel.kt`: `Node` gets `@param:JsonProperty("parentNode") val parentNode: String? = null`
    (`@field:Schema`: id of the innermost loop the node belongs to; `position` is then relative to that loop; NOT_REQUIRED,
    nullable). `NodeData` gets `@param:JsonProperty("collapsed") val collapsed: Boolean? = null` (loop only, editor state).
    Update the `type` description to "`BaseNode` for registered nodes, `Loop` for a loop box". Add
    `@JsonIgnoreProperties(ignoreUnknown = true)` to `Node` and `NodeData` so React Flow editor fields (`extent`,
    `expandParent`, `style`, `zIndex`) never break a start (pin with a test).
  - create `commons/model/LoopNode.kt`: `object LoopNode { const val NODE_TYPE = "Loop"; const val NODE_MODEL =
    "org.projectcontinuum.core.builtin.Loop"; const val CATEGORY = "Flow Control"; fun isLoop(node: Node) = node.data.nodeModel == NODE_MODEL }`.
  - modify `commons/constant/TaskQueues.kt`: `const val BUILTIN_NO_ACTIVITY = "\$builtin"` (KDoc: registered nodes that run
    inside the orchestrator and have no activity).
  - regenerate `docs/api/continuum-api-server.openapi.yaml` (`./gradlew --offline :continuum-api-server:generateOpenApiSpec`).
  - modify `docs/api/ENDPOINT_INVENTORY.md` (cross-cutting facts: node membership, `type` values, `collapsed`; start and
    validate body notes), `docs/api/CONVENTIONS.md` only if an example names a changed schema (check, report if unchanged).
- Change: as above; no behaviour change for workflows without the fields.
- Unit tests: commons `model/ContinuumWorkflowModelLoopFieldsTest.kt`: JSON without the fields parses (null), with them
  round-trips byte-equal for the fields, unknown editor fields are ignored; orchestration
  `config/DataConverterLoopFieldsTest.kt`: the Temporal mapper from `DataConverterConfig` round-trips `parentNode`/`collapsed`
  and ignores unknown node fields. api-server `OpenApiSpecTest` passes with the regenerated spec.
- Docs: release notes (commons + api-server: new optional fields, client-visible schema change); commons `AGENTS.md`
  Local Contracts line for `parentNode`/`collapsed`/`LoopNode`; root README check. Redocly lint
  (`npx --yes @redocly/cli@2 lint docs/api/*.openapi.yaml --config redocly.yaml`) or report it refused.
- Depends on: none.
- Covers: AC-3, AC-4 (and contract rows "type", "nodeModel", "marker", "membership", "collapsed").

### BE-3 Loop descriptor: properties schema, UI schema, documentation (commons)
- Repo/module: `continuum-commons`.
- Files:
  - create `commons/node/builtin/LoopNodeDescriptor.kt`: `object LoopNodeDescriptor { val nodeData: NodeData; val documentationMarkdown: String; val categories = listOf(LoopNode.CATEGORY) }`
    building `NodeData(id = NODE_MODEL, nodeModel = NODE_MODEL, title = "Loop", subTitle = "Repeat the nodes inside",
    description, icon (inline SVG), inputs = null, outputs = null, properties = defaults (D-18), propertiesSchema,
    propertiesUISchema)` exactly as in the Contract table, with titles/descriptions per property.
  - create `continuum-commons/src/main/resources/org/projectcontinuum/core/commons/node/builtin/LoopNode.doc.md`: what a
    loop does, the D-2 grammar with examples, D-3 exclusions, `vars["name"]` for dotted names, check before/after,
    onLimit, variable carry rule (D-14), runtime failures (D-8), "no edges to the loop", and a placeholder line for the real
    iteration ceiling that BE-16 fills in.
- Unit tests: `node/builtin/LoopNodeDescriptorTest.kt`: defaults validate against the schema
  (`ValidationHelper.findSchemaViolations` empty); 0, 10,001, bad enums, empty and 2,049-char condition each give one
  violation on the right property; schema has no `$schema` or declares draft-07; uischema control for `condition` has
  `format: "query"`, `language: "CEL"`; doc resource loads.
- Docs: commons `AGENTS.md` (descriptor location, keep schema in step with the workbench renderer); release notes.
- Depends on: BE-2.
- Covers: AC-7 (schema part), contract rows "propertiesSchema", "propertiesUISchema".

### BE-4 Condition contract: golden vectors, result types, parser interface (no engine)
- Repo/module: `continuum-commons`.
- Files:
  - create `commons/validation/loop/LoopCondition.kt`: `enum class LoopConditionOperator(symbol)` (`==, !=, <, <=, >, >=`),
    `sealed interface LoopConditionLiteral` (`BooleanLiteral`, `StringLiteral`, `IntLiteral(Long)`, `DoubleLiteral(Double)`),
    `data class LoopCondition(variableName, operator, literal)` with `canonical(): String` (escaping for `"`, `\`, control
    characters per CEL), `enum class LoopConditionRejection` (the reason codes of the Contract table),
    `sealed interface LoopConditionParseResult { Accepted(condition); Rejected(reason, message, column: Int?) }`,
    `fun interface LoopConditionParser { fun parse(text: String): LoopConditionParseResult }`, `object LoopConditionLimits`
    (2,048 / 1..256 / 1,024), and `const val SUPPORTED_SHAPE` text used in messages.
  - create `continuum-commons/src/test/resources/loop-condition/golden-vectors.json`: about 40 rows (format in the Contract):
    every operator per literal type, `'` and `"` strings with escapes (`\"`, `\\`, `\n`, `é`), `-1`, `-1.5`, `1e3`,
    parentheses, whitespace, dotted names; every D-3 construct rejected with its code (`&&`, `||`, `!`, ternary,
    arithmetic, `in`, list/map/null/bytes/`1u`, `size()`, `.startsWith()`, `has()`, macros, `vars.a`, two `vars[...]`,
    literal on the left, variable vs variable, `<` on string and on bool, other identifier, `vars[1]`, empty, 2,049
    chars, 257-char name, 1,025-char string literal, `vars["a"] ==`).
  - create `continuum-commons/src/test/kotlin/org/projectcontinuum/core/commons/validation/loop/LoopConditionGoldenVectors.kt`
    (loader shared by tests) and `LoopConditionTest.kt` (canonical formatting and escaping, limits).
- Unit tests: as above; the golden file parses and has unique ids.
- Docs: commons `AGENTS.md`: the golden file is the single source; the workbench spec copies it verbatim; any grammar
  change edits both parsers and this file. Release notes.
- Depends on: none.
- Covers: AC-12 (table part), contract rows "grammar", "golden vectors", "reason codes".

### BE-5 Condition parser implementation (cel-java AST gate, or D-7 fallback)
- Repo/module: `continuum-commons` (or the module BE-1 chose).
- Files:
  - create `commons/validation/loop/CelLoopConditionParser.kt` (BE-1 PASS): parse with the cel-java parser only (no
    checker needed), walk the `CelExpr` AST and accept only `call(_==_ | _!=_ | _<_ | _<=_ | _>_ | _>=_, [index(ident vars,
    const string), const])`; a `-` call on a numeric constant is folded to a negative literal (pin both shapes, D-5).
    Map every other node kind to the matching reason code; syntax errors carry the column from the parser issue.
  - or create `commons/validation/loop/HandWrittenLoopConditionParser.kt` (BE-1 FAIL): a recursive-descent parser of
    exactly the D-2 grammar with the same reason codes.
  - create `commons/validation/loop/LoopConditionParsers.kt`: `val default: LoopConditionParser` = the chosen one.
- Unit tests: `LoopConditionParserGoldenTest.kt` runs every golden row against `LoopConditionParsers.default`
  (parameterized by id); cost test: a 2,048-char deeply parenthesised input returns within 100 ms and never throws
  (StackOverflow guarded: reject nesting deeper than 32 with `SYNTAX`).
- Docs: commons `AGENTS.md` (which implementation and why); release notes.
- Depends on: BE-1, BE-4.
- Covers: AC-12.

### BE-6 Orchestrator publishes the built-in Loop registration
- Repo/module: `continuum-orchestration-service`.
- Files:
  - create `orchestration/registration/BuiltinNodeRegistrationPublisher.kt`: `@Component`, `@EventListener(ApplicationReadyEvent::class)`,
    sends one `FeatureRegistrationRequest` (nodeId `LoopNode.NODE_MODEL`, workerId `orchestration-<uuid>`, featureId
    `org.projectcontinuum.core.builtin`, taskQueue `TaskQueues.BUILTIN_NO_ACTIVITY`, nodeManifest = JSON of
    `LoopNodeDescriptor.nodeData`, documentation, categories, empty extensions) to
    `Channels.CONTINUUM_FEATURE_REGISTRATION_OUTPUT` with the Kafka key = nodeId, like
    `continuum-worker-springboot-starter/.../registration/FeatureRegistrationPublisher.kt`. Exceptions are logged, never thrown.
  - modify `continuum-orchestration-service/src/main/resources/application.yaml`: binding
    `continuum-core-event-FeatureRegistration-output` (binder `main-kafka-cluster`, destination
    `continuum-core-event-FeatureRegistration`, native encoding) plus the Kafka producer override
    `value.serializer: io.confluent.kafka.serializers.KafkaAvroSerializer` and `schema.registry.url` from
    `CONTINUUM_KAFKA_MAIN_SCHEMA_REGISTRY` (copy of the worker starter's block). No node defaults in yaml (minimal-config rule).
  - modify `continuum-orchestration-service/src/test/resources/application.yaml` if the Spring context tests need the binding.
  - check (read only) `continuum-message-bridge/.../handler/FeatureRegistrationHandler.kt` and `NodeTreeSyncService.kt`
    accept a `$builtin` queue and no ports (expected: yes, no change). Check
    `continuum-api-server/.../service/NodeExplorerService.kt` `getTaskQueues` returns `$builtin` for the loop if asked (no change).
- Unit tests: `registration/BuiltinNodeRegistrationPublisherTest.kt` with a mocked `StreamBridge`: one send per event, the
  payload fields above, Kafka key header; a throwing `StreamBridge` is logged and swallowed.
- Docs: orchestration `AGENTS.md` (new responsibility, binding, upgrade order); root `AGENTS.md` execution-flow and
  module table ("orchestration service also registers built-in nodes"); `docs/api/ENDPOINT_INVENTORY.md` task-queues
  section (`$builtin` marker); root README "How It Works"; release notes Highlights + Upgrade notes (Loop appears after
  the new orchestrator starts; orchestrator now needs the schema registry).
- Depends on: BE-3.
- Covers: AC-5; AC-6 with the live stack.

### BE-7 Validator: loop structure rules and collapsed-graph cycle check
- Repo/module: `continuum-commons` (`validation/WorkflowValidator.kt`), `continuum-api-server` (wiring).
- Files:
  - modify `commons/validation/WorkflowValidator.kt`: `GraphNode` gains `type: String? = null` and `parent: String? = null`.
    A node is a loop when `nodeId == LoopNode.NODE_MODEL`. New checks (each one issue, `node` set):
    - `type` given and `type == "Loop"` xor loop nodeId: "Node 'k': type 'Loop' is only for the built-in loop ..." / the reverse.
    - `parent` unknown key; `parent` names a non-loop; parent chain returns to the node ("Node 'k': its parent chain
      'k' -> 'L1' -> 'k' is a cycle").
    - edge with a loop as source or target (`edge` set): "Edge ...: 'L' is a loop. A loop has no ports; connect the
      nodes inside it instead." These edges are excluded from the port checks (no second "port does not exist").
    - collapsed cycle check replacing the single Kahn pass: for the top level and for every loop scope, Kahn over the
      scope's direct children with each child loop collapsed to one vertex; an edge counts in a scope when both
      endpoints are inside it, mapped to their direct-child ancestors; edges that collapse to a self-edge are dropped.
      Message for a cycle created only by collapsing: "The edges leave loop 'L' and come back into it through 'Y'. A node
      outside a loop cannot run between its iterations; move 'Y' into 'L' or remove one of the edges." The plain-cycle
      message stays as is for real cycles (assert both).
    - warnings: a loop is never "not connected"; a loop with no members gets "Loop 'L' has no nodes inside it.".
  - modify `api-server/service/WorkflowRunValidator.kt`: pass `type = node.type`, `parent = node.parentNode`.
- Unit tests: commons `validation/WorkflowValidatorLoopStructureTest.kt` (descriptor source returning
  `LoopNodeDescriptor.nodeData` + plain test nodes): nested ok; parent cycle; unknown/non-loop parent; type mismatch both
  ways; edge to/from loop; `X1 -> Y -> X2` rejected naming L and Y; `A -> X1 -> X2 -> B` ok; nested-scope cycle;
  schema errors on loop properties (AC-7) in both modes; a loop-free fixture gives exactly the old result.
  api-server `controller/WorkflowControllerGraphValidationTest.kt`: start 400 + validate 200 `valid:false` for one structure case.
- Docs: commons `AGENTS.md` WorkflowValidator line (new rules, messages asserted); api-server `AGENTS.md` start/validate
  rule list; `docs/api/ENDPOINT_INVENTORY.md` start and validate sections (new 400 reasons); release notes (client-visible
  new validation errors).
- Depends on: BE-2, BE-3.
- Covers: AC-7, AC-8, AC-9, AC-10, AC-11 (validator part).

### BE-8 Validator: condition rule
- Repo/module: `continuum-commons`, `continuum-api-server` tests.
- Files: modify `commons/validation/WorkflowValidator.kt` (for a loop node whose `condition` is a string that passed the
  schema, run `LoopConditionParsers.default`; a rejection is one ERROR `node = key, property = "condition"`, message
  "Loop 'L': condition <reason text> (column n). Supported: vars[\"name\"] <op> <literal> ..."). AUTHORING mode reads the
  merged value (defaults + given).
- Unit tests: commons `validation/WorkflowValidatorLoopConditionTest.kt`: every rejected Round-2 string gives exactly one
  error with node/property set and no duplicate schema error; accepted strings give none. api-server
  `mcp/WorkflowSpecValidatorTest.kt` and `controller/WorkflowControllerGraphValidationTest.kt`: one rejected string through
  `validateContinuumWorkflow`, start (400) and validate (200, `valid:false`).
- Docs: commons and api-server `AGENTS.md`, inventory start/validate sections, release notes.
- Depends on: BE-5, BE-7.
- Covers: AC-13, AC-14 (condition errors).

### BE-9 MCP spec `parent`, builder layout of loop boxes, tool descriptions
- Repo/module: `continuum-api-server` (package `mcp`).
- Files:
  - modify `mcp/McpWorkflowSpec.kt`: `McpWorkflowSpecNode.parent: String? = null` with `@McpToolParam(description = "Key of the loop this node is inside (innermost loop). Omit for top-level nodes. Never set on edges.", required = false)`.
  - modify `mcp/WorkflowSpecValidator.kt`: pass `parent` and (for loops) `type = "Loop"` into `GraphNode`.
  - modify `mcp/WorkflowSpecBuilder.kt`: loops get `type = LoopNode.NODE_TYPE`, members `parentNode = <loopKey>`.
    Layout per scope, innermost first: lay out each scope's direct children by longest-path depth on the collapsed
    scope graph (edges mapped to direct-child ancestors, self-edges dropped), with each child loop's size computed first;
    member positions relative to their loop (40 px side padding, 60 px header), loop size = bounding box + padding
    (min 520 x 320); top-level as today (330 x 160 steps, but a column is as wide as its widest box). Emit nodes
    parent-first (a loop before its members, depth-first). Loop `data` = registry `NodeData` with merged properties and
    `collapsed = false`. Edge ids unchanged.
  - modify `mcp/ContinuumWorkflowMcpTools.kt`: both descriptions mention loops ("a loop is a node with nodeId
    org.projectcontinuum.core.builtin.Loop; nodes inside it set parent to its key; edges never name a loop; the condition
    is one CEL comparison vars[\"name\"] <op> <literal>"). Tool names and parameters unchanged.
- Unit tests: `mcp/WorkflowSpecBuilderTest.kt`: example workflow (seed -> [fetch -> parse -> append] -> write) builds with
  loop first, members with `parentNode` and positions inside the box, nested loop contained in its parent box,
  edges unchanged; `mcp/WorkflowSpecValidatorTest.kt`: unknown parent, edge to loop, collapsed cycle reach `errors`;
  `mcp/ContinuumWorkflowMcpToolsCallbackTest.kt` (or the tool-listing test): still exactly six tools.
- Docs: api-server `AGENTS.md` MCP section (spec shape, builder layout rules); release notes (MCP spec gains `parent`).
- Depends on: BE-7.
- Covers: AC-14.

### BE-10 Orchestrator plumbing: no activity for loops, clean dispatch payload, progress
- Repo/module: `continuum-orchestration-service`.
- Files:
  - modify `orchestration/workflow/WorkflowActivityInitializer.kt`: request task queues and build stubs only for
    `nodes.filterNot { LoopNode.isLoop(it) }`; the `InitializationFailed` size check compares with that count.
  - modify `orchestration/workflow/NodeReadiness.kt`: loop nodes are never returned by `getReadyNodes` or
    `getNodesToSkip` (the loop runner of BE-14 owns their status).
  - modify `orchestration/workflow/WorkflowDagExecutor.kt`: the `Node` passed to `run(node, inputs)` is
    `node.copy(parentNode = null, data = node.data.copy(collapsed = null))`, so workers built on older commons never see
    the new fields.
  - modify `orchestration/workflow/WorkflowStatusReporter.kt` `calculateProgressPercentage`: denominator = non-loop
    nodes; the reporter keeps the highest value published so far and never publishes less (resets in BE-14 cannot make
    progress go backwards); 100 at the end.
- Interim behaviour until BE-14 lands (no release in between): members of a loop run once like ordinary nodes, the loop
  node keeps `status == null`. State this in `03-backend-dev.md`.
- Unit tests: `ContinuumWorkflowTest` (new cases) with a new fixture `src/test/resources/test-workflows/loop-linear-3member.cwf.json`
  (seed -> [fetch -> parse -> append] -> write, loop properties valid against `LoopNodeDescriptor`): `FakeInitializeActivity`
  records the requested ids (add a `requestedNodeIds` list to the fixture) and never sees the loop id; the run does
  not fail with `InitializationFailed`; `FakeContinuumNodeActivity` records received nodes with `parentNode == null`;
  `NodeReadinessTest`: loop never ready/skipped; progress test: monotonic.
- Docs: orchestration `AGENTS.md` (loops have no activity, dispatch strips loop fields, monotonic progress); release notes.
- Depends on: BE-2, BE-3 (fixture properties).
- Covers: AC-15, AC-25 (monotonic part), AC-11 (no regression).

### BE-11 Loop topology and deferred outward edges (pure)
- Repo/module: `continuum-orchestration-service`.
- Files:
  - create `orchestration/workflow/loop/LoopTopology.kt` (pure, built once per run from the model):
    `loopIds`, `innermostLoopOf(nodeId)`, `directMembers(loopId)`, `descendants(loopId)`, `enclosingLoops(nodeId)`
    (outermost first), `entryMembers(loopId)` (descendants with no incoming edge from another descendant of the same loop),
    `externalIncomingEdges(loopId)`, `outwardEdges(loopId)` (source inside, target outside), `edgesInside(loopId)`,
    `topologicalOrder(loopId)` (Kahn over descendants, ties by `nodes` list order; used for the variable clash rule).
  - modify `orchestration/workflow/NodeReadiness.kt`: new parameter `loopTopology` + `finishedLoopIds` /
    `failedOrSkippedLoopIds`. An edge leaving a loop L (source in L, target not in L) is pending (`null`) while L is not
    finished, `BROKEN` when L is FAILED or SKIPPED, and otherwise judged by its source as today. A node is eligible for
    readiness only while every enclosing loop is in an active iteration (new parameter `activeLoopIds`).
    Default arguments keep loop-free calls unchanged.
- Unit tests: `workflow/loop/LoopTopologyTest.kt` (nested loops, entry members, outward edges, topo order);
  `NodeReadinessTest` new cases: consumer of a member not ready until the loop finished; BROKEN when the loop failed;
  member not ready while its loop is inactive; loop-free graphs give the old results.
- Docs: orchestration `AGENTS.md` (readiness rule for outward edges).
- Depends on: BE-2.
- Covers: AC-20 (ordering part), AC-11.

### BE-12 Loop-carried flow variables (D-14) in `FlowVariableMerger`
- Repo/module: `continuum-orchestration-service`.
- Files:
  - modify `orchestration/workflow/FlowVariableMerger.kt`: new optional parameters `loopTopology` and
    `carriedByLoopId: Map<String, Map<String, Any>>`. Rule: `inherited(m)` = fan-in merge of parents as today, then
    overridden by `carried(L)` for every enclosing loop L in which `m` is an entry member, outermost first.
    `carried(L)` after iteration k = `carried(L)` after k-1, overridden by `own(x)` of every descendant x that produced
    outputs in iteration k, applied in `loopTopology.topologicalOrder(L)` (the later node wins). Empty before iteration 1.
    New pure function `endOfIterationVariables(loopId, ...)` = merge of the outside parents' `available` (the loop's
    external incoming edges, fan-in order as today) + `carried(L)`; this is what the condition reads (for `before` at
    iteration 1: the outside part only).
  - create `continuum-orchestration-service/src/test/resources/flow-variables/loop-carry-cases.json`: the shared case
    list (graph, per-iteration own variables, expected inherited per node per iteration, expected end-of-iteration set)
    that FE-4 copies verbatim.
- Unit tests: `FlowVariableMergerTest` new cases from the JSON file: `page=2` overrides outside `page=1` in iteration 2;
  clash: later topo member wins; a variable set in iteration 1 and not in 2 is still carried; non-entry members see
  carried values through their parents; node after the loop inherits the last iteration; nested loops (outer carried
  reaches inner entry members); loop-free cases unchanged.
- Docs: orchestration `AGENTS.md` flow-variable section (the loop rule, the shared case file, the workbench twin
  `FlowVariables.ts` must change with it); release notes.
- Depends on: BE-11.
- Covers: AC-21 (merger part).

### BE-13 Condition evaluator interface and runtime checks (D-8), no engine
- Repo/module: `continuum-orchestration-service` (types from commons BE-4).
- Files:
  - create `orchestration/workflow/loop/LoopConditionEvaluator.kt`: `interface LoopConditionEvaluator { fun compile(text: String): CompiledLoopCondition; fun evaluate(compiled: CompiledLoopCondition, variables: Map<String, Any>): Boolean; fun warmUp() }`,
    `CompiledLoopCondition(text, parsed: LoopCondition, engineHandle: Any?)`.
  - create `orchestration/workflow/loop/LoopConditionRuntimeCheck.kt` (pure): before evaluating, returns a failure
    message when the variable is missing ("Loop 'L': flow variable 'hasMore' is not set when the condition is checked
    in iteration 3."), or when its JSON type class does not match the literal ("... is a string, the condition compares
    it with a number."), lists/maps/null included. Int vs double is allowed.
  - create `orchestration/workflow/loop/LoopConditionEvaluators.kt`: `object LoopConditionEvaluators { @Volatile var current: LoopConditionEvaluator }`
    (Temporal instantiates the workflow class, so the evaluator is reached statically; BE-15 sets the real one).
  - create test fixture `src/test/kotlin/.../workflow/fixtures/StructuredTestLoopConditionEvaluator.kt`: `compile` reads
    only the canonical form with a test-only regex (`vars\["(.+)"\] (==|!=|<=|>=|<|>) (.+)`), `evaluate` compares with
    `FlowVariables.valuesEqual` / numeric compare. It needs neither BE-5 nor the engine, so BE-14 is not blocked by BE-1.
- Unit tests: `workflow/loop/LoopConditionRuntimeCheckTest.kt` (missing, each type mismatch, list/map/null, int vs double).
- Docs: orchestration `AGENTS.md` (evaluator seam, D-8 rules).
- Depends on: BE-4.
- Covers: AC-22 (check part).

### BE-14 Loop execution in `WorkflowDagExecutor`
- Repo/module: `continuum-orchestration-service`.
- Files:
  - create `orchestration/workflow/loop/LoopRunner.kt` (workflow-side state, deterministic): per loop
    `LoopRun(loopId, iteration, phase)`; start when every external incoming edge is settled (any BROKEN: loop and all
    descendants SKIPPED, planner choice PR-2); `before`: evaluate first; `after`: start iteration 1 at once. An iteration ends when no
    descendant is ready or running. Then: a FAILED member: loop FAILED (no next iteration); else evaluate
    (`after`) / count; condition true and iteration < max: reset and start the next iteration; true at max: `fail`: loop
    FAILED with `$error` "Loop 'L' reached its limit of 5 iterations and the condition still holds." in `nodeErrorsMap`;
    `stop-with-warning`: loop WARNING, outward edges delivered; false: loop SUCCESS. Zero iterations (`before`, false at
    once): descendants SKIPPED, loop SUCCESS.
    Reset of loop L: for every descendant remove `nodeToOutputsMap`/`nodeErrorsMap` entries, `data.status = null`,
    `data.nodeProgress = null`; for every edge inside L `data = null`, `animated = false`; nested loop runs are discarded.
    Loop status/progress per the Contract "Run display" row; on finish store `nodeToOutputsMap[L]` = `{$variable: carried}`
    (omitted when empty; FAILED loops store `$error` in `nodeErrorsMap` instead).
  - modify `orchestration/workflow/WorkflowDagExecutor.kt`: build `LoopTopology` once; each pass asks `LoopRunner`
    which loops start, finish or restart, then readiness with `activeLoopIds`/finished sets; `getNodeInputs` passes the
    carried maps to `FlowVariableMerger`; the loop condition reads `endOfIterationVariables` and goes through
    `LoopConditionRuntimeCheck`, then `LoopConditionEvaluators.current`. A runtime-check failure fails the loop with the
    message (run fails). Loop-free workflows take exactly the old path.
  - modify `orchestration/workflow/WorkflowStatusReporter.kt` progress: a loop's members count as done for completed
    iterations proportionally (still clamped monotonic by BE-10), loop nodes not counted.
  - create fixtures `test-workflows/loop-before-3member.cwf.json`, `loop-nested.cwf.json`.
  - modify `workflow/fixtures/FakeContinuumNodeActivity.kt`: per-node scripted outputs per invocation
    (`flowVariablesByInvocation: Map<nodeId, List<Map<String, Any>>>`) and an invocation counter per node.
- Unit tests (`workflow/ContinuumWorkflowLoopTest.kt`, Temporal test env, `StructuredTestLoopConditionEvaluator`): AC-16
  example (3 iterations, write table once with iteration-3 data via `receivedInputs`); AC-17 zero and two iterations
  with `before`; AC-18 both limit policies; AC-19 member failure; AC-20 reset (statuses, edge data, animation captured
  from published events) and nested 2 x 3; AC-21 carried variables end to end; AC-22 missing variable and type mismatch
  fail the run with the message; AC-25 progress never decreases (captured events) and iteration messages.
- Docs: orchestration `AGENTS.md` (loop execution contract, reset, statuses, outward-edge delivery), root `AGENTS.md`
  execution flow line, root README "How It Works"; release notes Highlights.
- Depends on: BE-10, BE-11, BE-12, BE-13. (Not on BE-1: it runs against the evaluator seam.)
- Covers: AC-16, AC-17, AC-18, AC-19, AC-20, AC-21, AC-22, AC-25.
- Size note: likely > 300 changed lines; the developer may split it into "iteration engine" and "status/progress/variables"
  commits-in-place, but it is one reviewable unit.

### BE-15 Real evaluator: cel-java (or D-7/D-17 fallback), warm-up, replay test
- Repo/module: `continuum-orchestration-service`.
- Files:
  - create `orchestration/workflow/loop/CelLoopConditionEvaluator.kt` (BE-1 PASS): `CelCompiler` with one variable
    `vars` of type `map(string, dyn)`, heterogeneous numeric comparison enabled (the option name in 0.15.0 is NOT VERIFIED:
    pin int-vs-double with a test), compiled program cached per condition text inside the workflow instance; evaluates the
    original string after the BE-5 AST gate; values converted to CEL types (Integer/Long -> Long, Float/Double -> Double,
    Boolean, String).
  - or create `StructuredLoopConditionEvaluator.kt` (FALLBACK: same semantics on the parsed triple), or a local-activity
    variant `LoopConditionActivity` (D-17, only when BE-1 found an in-workflow problem; counts ~3 events per iteration).
  - create `orchestration/config/LoopConditionEngineConfig.kt`: `@Component` that sets
    `LoopConditionEvaluators.current` and calls `warmUp()` in `@PostConstruct` (compile + evaluate a fixed expression).
  - test: `workflow/ContinuumWorkflowLoopReplayTest.kt`: runs the AC-16 workflow with the real evaluator, fetches the
    history and replays it with `WorkflowReplayer`; re-runs AC-16..AC-22 cases with the real evaluator (parameterize
    `ContinuumWorkflowLoopTest` over both evaluators).
- Unit tests: as above plus `CelLoopConditionEvaluatorTest.kt` over the accepted golden rows with sample values.
- Docs: orchestration `AGENTS.md` (engine, warm-up, replay test); release notes.
- Depends on: BE-1, BE-5, BE-14.
- Covers: AC-26 (and AC-16..AC-22 with the real engine).

### BE-16 History guard and defensive condition check at loop start
- Repo/module: `continuum-orchestration-service`.
- Files:
  - create `orchestration/workflow/loop/LoopHistoryGuard.kt`: before each iteration read `Workflow.getInfo().historyLength`
    and `historySize`; fail the loop (whatever `onLimit` says) when length > `MAX_HISTORY_EVENTS` (40,000, about 78 % of
    51,200) or size > 40 MiB of the 50 MiB limit: "Loop 'L' stopped before iteration 812: the run history has 40,012
    events and would reach Temporal's limit. Lower maxIterations or move work out of the loop." Thresholds are
    constants with a constructor override for tests (planner choice PR-4: workflow code cannot read Spring config).
  - modify `LoopRunner.kt`: at loop start re-parse the condition with `LoopConditionParsers.default` and re-check
    `maxIterations`/`onLimit`/`check`; an invalid value fails the loop with the reason (no exception that would fail the
    workflow task).
- Unit tests: `ContinuumWorkflowLoopTest` new cases: guard with a tiny threshold fails the loop with the message under
  `stop-with-warning` (AC-23); a run started through the Temporal client with an invalid condition fails cleanly with the
  reason, no task retry loop (AC-27).
- Docs: orchestration `AGENTS.md`; release notes.
- Depends on: BE-5, BE-14.
- Covers: AC-23, AC-27.

### BE-17 Budget calibration, validator warning, documented ceiling
- Repo/module: `continuum-orchestration-service` (calibration test), `continuum-commons` (warning, doc).
- Files:
  - create `orchestration/.../workflow/ContinuumWorkflowLoopHistoryBudgetTest.kt`: runs a 1-member and a 3-member loop
    for 10 iterations with the real evaluator (and progress signals as the fake sends them), measures history events per
    member execution; asserts the measured value stays within the constant below (+/- 20 %), so a change in event cost
    fails the test.
  - create `commons/validation/loop/LoopBudget.kt`: `EVENTS_PER_MEMBER_EXECUTION` (measured), `MAX_MEMBER_EXECUTIONS` =
    `LoopHistoryGuard` threshold / events.
  - modify `commons/validation/WorkflowValidator.kt`: warning "Loop 'L': maxIterations 10000 x 3 nodes inside may exceed
    the run history limit (about N node runs per workflow). The run stops with an error when the history is nearly full."
    (`property = "maxIterations"`) when `maxIterations x non-loop descendants > MAX_MEMBER_EXECUTIONS`.
  - modify `LoopNode.doc.md` (real ceiling with the measured numbers).
- Unit tests: the budget test; `WorkflowValidatorLoopStructureTest` warning over/at the budget.
- Docs: release notes Upgrade notes (real ceiling), commons and orchestration `AGENTS.md`.
- Depends on: BE-7, BE-15, BE-16.
- Covers: AC-24, AC-28 (ceiling part).

### BE-18 Closeout: DOX, README and release-note pass across the repo
- Repo/module: `continuum-platform-core` docs only.
- Files: `release_notes/RELEASE_NOTES_v0.0.23.md` (Highlights line "Loops", Upgrade notes: deploy orchestrator +
  api-server + message-bridge together, then the workbench; orchestrator now needs the schema registry and publishes
  the Loop at start; older orchestrators fail loop workflows; real iteration ceiling), root `README.md`, root
  `AGENTS.md` (execution flow, Key versions), module `AGENTS.md` files touched above, `docs/api/ENDPOINT_INVENTORY.md`
  search for every new name (`parentNode`, `collapsed`, `$builtin`, `Loop`).
- Unit tests: full `./gradlew --offline test` of the five modules.
- Depends on: BE-1 .. BE-17.
- Covers: AC-28, AC-11.

## Frontend tasks

ON HOLD (user, 2026-11-25): the workbench tasks will probably run later on another machine. Every FE task below is
self-contained: repo `continuum-workbench` (paths relative to it), the backend artifact it consumes named exactly, and
its P-2 status. Nothing here depends on this machine. Shared rules for every FE task:
- DOX: read `AGENTS.md` (root of `continuum-workbench`) first; update it when a contract it lists changes (it lists the
  `FlowVariables.ts` twin rule, the status presentation table, the NodeDialog renderers).
- Release notes: `release_notes/RELEASE_NOTES_v0.0.16.md` (root `package.json` version 0.0.16; latest tag `release/0.0.15`),
  unless the version moved by then (then the file of the version being prepared). README: root `README.md`
  (`workflow-builder-extension/README.md` for FE-11).
- Pure logic goes in `workflow-editor-extension/src/common/` (the only folder `tsconfig.test.json` compiles for
  `node --test`), with a `*.spec.ts` beside it. React components stay thin over it.
- Verification on a machine with `node_modules`: `yarn workspace @continuum/workflow-editor-extension test`, `tsc --noEmit`
  in the workspace, `yarn build`, then the browser scenarios. Backend artifacts come from a `continuum-platform-core`
  checkout that contains the named BE tasks (sibling checkout, or `CONTINUUM_API_SPEC_DIR` for the client generator).
- "P-2" = the user has added `react-querybuilder` (and a MUI v5 binding if wanted) to
  `workflow-editor-extension/package.json` and regenerated `yarn.lock`. Only FE-10 needs it.
- Frozen contract: the "Contract" table at the top of this plan. If a backend task changed a value, its
  `03-backend-dev.md` entry says so; check it before starting.

### FE-1 Regenerate API clients and extend `@continuum/core` types
- Repo/module: `workflow-editor-extension/src/generated/api-server/` (generated), `continuum-core/src/`.
- Consumes: committed `continuum-platform-core/docs/api/continuum-api-server.openapi.yaml` after BE-2 (fields
  `WorkflowNode.parentNode`, `NodeData.collapsed`).
- Files:
  - regenerate with `bash scripts/generate-api-clients.sh` (needs `openapi-generator`, no network).
  - modify `continuum-core/src/types/IBaseNode.ts`: `IBaseNodeData.collapsed?: boolean`.
  - create `continuum-core/src/model/LoopNode.ts`: `LOOP_NODE_TYPE = "Loop"`, `LOOP_NODE_MODEL =
    "org.projectcontinuum.core.builtin.Loop"`, `LOOP_DEFAULT_WIDTH = 520`, `LOOP_DEFAULT_HEIGHT = 320`,
    `LOOP_HEADER_HEIGHT = 60`, `LOOP_PADDING = 40`, `isLoopNode(node)`, `ILoopProperties {condition, maxIterations, onLimit, check}`;
    export from `continuum-core/src/index.ts` (only what the editor imports, per the workspace rule).
- Unit tests: none new (types); `tsc --noEmit` of both workspaces.
- Docs: release notes (clients regenerated for the loop fields), root `AGENTS.md` `@continuum/core` line if exports change.
- Depends on: BE-2. Blocked until: BE-2 DONE. P-2: not needed.
- Covers: AC-3 (client side), contract rows "type", "nodeModel", "membership", "collapsed".

### FE-2 `LoopTopology.ts`: scopes, collapsed cycle check, collapsed view (pure)
- Repo/module: `workflow-editor-extension/src/common/`.
- Consumes: the Contract rows "membership", "node order", "collapsed" (no file).
- Files:
  - create `src/common/LoopTopology.ts`: from `{nodes: {id, type, parentNode?, data?: {collapsed?}}[], edges}`:
    `innermostLoopOf`, `descendants`, `enclosingLoops`, `wouldCreateCycle(connection)` (collapsed rule, same algorithm as
    BE-7: per scope, child loops collapsed, self-edges dropped), `cycleReason` (names loop and outside node),
    `collapsedView(nodes, edges)` -> `{hiddenNodeIds, displayEdges}` where an edge with an endpoint in a collapsed loop
    is re-pointed to the outermost collapsed ancestor (handles `loop-in` / `loop-out`), edges fully inside a collapsed
    loop are hidden, stored edges never changed; `orderParentFirst(nodes)`; `toAbsolutePosition` /
    `toRelativePosition` helpers; `findDropTarget(point, nodes)` = innermost expanded loop containing the point.
  - create `src/common/LoopTopology.spec.ts`.
- Unit tests: `X1 -> Y -> X2` refused with reason naming L and Y; `A -> X1 -> X2 -> B` allowed; plain cycle refused;
  nested scopes; collapsed view for nested collapsed loops; stored edges deep-equal before/after; parent-first ordering;
  position conversion round trip; drop target picks the innermost loop.
- Docs: root `AGENTS.md` workspace line (the collapsed rule is shared with continuum-platform-core `WorkflowValidator`;
  change both together).
- Depends on: none. Can start now. P-2: not needed.
- Covers: AC-30 (logic), AC-10 (editor side).

### FE-3 `LoopCondition.ts`: hand-written D-2 parser/formatter + golden vectors
- Repo/module: `workflow-editor-extension/src/common/`.
- Consumes: `continuum-platform-core/continuum-commons/src/test/resources/loop-condition/golden-vectors.json` (BE-4)
  and the reason codes of the Contract.
- Files:
  - create `src/common/LoopCondition.ts`: `parseLoopCondition(text): {ok: true, condition: {variable, operator, literal,
    literalType}} | {ok: false, reason, message, column?}`, `formatLoopCondition(condition)` (canonical form, CEL escapes),
    `operatorsFor(literalType)`, `DEFAULT_LOOP_CONDITION = 'vars["hasMore"] == true'`, `SUPPORTED_SHAPE` text; messages
    worded like BE-8's so the dialog and the validator say the same thing.
  - create `src/common/loop-condition-golden-vectors.json`: verbatim copy of the core file.
  - create `src/common/LoopCondition.spec.ts`: every row of the copy; a SHA-256 of the copy equals a constant noted from the
    core file (`shasum -a 256`), so a drifted copy fails; formatter round trip for escapes.
- Unit tests: as above.
- Docs: root `AGENTS.md` (twin of the Kotlin parser; the golden file is copied from core and both change together).
- Depends on: BE-4. Blocked until: BE-4 DONE (golden file committed). P-2: not needed.
- Covers: AC-29, AC-12 (TS side).

### FE-4 `FlowVariables.ts`: loop-carried variables (twin of BE-12)
- Repo/module: `workflow-editor-extension/src/common/`.
- Consumes: BE-12 rule and `continuum-platform-core/continuum-orchestration-service/src/test/resources/flow-variables/loop-carry-cases.json`;
  the Contract row "Loop `$variable` (display only)".
- Files:
  - modify `src/common/FlowVariables.ts` `inheritedFlowVariables`: for an entry member of a finished loop, apply the loop's
    stored `$variable` (outermost loop first) over the fan-in merge, as the Kotlin merger does; loops themselves have no
    parents; everything else unchanged.
  - create `src/common/loop-carry-cases.json` (verbatim copy, SHA-256 pinned in the spec) and extend `FlowVariables.spec.ts`.
- Unit tests: the shared cases (last-iteration view) plus every existing case unchanged.
- Docs: root `AGENTS.md` (twin rule now includes loops).
- Depends on: BE-12 (rule + case file), BE-14 (the stored loop `$variable`). Blocked until: BE-12 DONE. P-2: not needed.
- Covers: AC-31, AC-21 (viewer side).

### FE-5 `LoopNode` component (box, resize, collapse toggle, badges) in editor and viewer
- Repo/module: `workflow-editor-extension/src/browser/components/node/`.
- Consumes: FE-1 constants, FE-2, FE-3; Contract rows "Run display", "Loop size".
- Files:
  - create `components/node/LoopNode.tsx` + `LoopNode.css`: dashed rounded box, header with title, condition summary,
    collapse/expand button (writes `data.collapsed`), `NodeResizer` from `reactflow` (part of the 11.x umbrella package;
    `@reactflow/node-resizer` 2.2.14 is already in `yarn.lock`, so no dependency change), min size = members' bounding
    box; no handles (a loop has no ports); warning badge when `parseLoopCondition` fails (tooltip = reason); status
    colours and badge via `src/common/ExecutionStatusPresentation.ts`; `nodeProgress.message` under the title.
    Collapsed: compact header-only box (contract: stored `width`/`height` stay the expanded size; the collapsed size is a
    display style).
  - modify `components/workflow-editor/WorkflowEditor.tsx` and `components/workflow-viewer/WorkflowViewer.tsx`:
    `nodeTypes = { BaseNode, Loop: LoopNode }`.
- Unit tests: presentational logic (summary text, badge decision) in a `src/common/LoopPresentation.ts` + spec.
- Docs: root `AGENTS.md` (node types), release notes.
- Depends on: FE-1, FE-2, FE-3. P-2: not needed.
- Covers: AC-32 (box/resize), AC-36, AC-37 (box part).

### FE-6 Palette drop of a Loop and drag-in/drag-out membership
- Repo/module: `widgets/workflow-editor/WorkflowEditorWidget.tsx`, `components/workflow-editor/WorkflowEditor.tsx`.
- Consumes: FE-1, FE-2, FE-5; Contract rows "membership", "node order".
- Files:
  - modify `widgets/workflow-editor/WorkflowEditorWidget.tsx` `addNewNode` (today `type: "BaseNode"` at line ~331): a
    palette node whose `nodeModel === LOOP_NODE_MODEL` is created with `type: "Loop"`, default size, `style` width/height;
    a node dropped inside an expanded loop gets `parentNode` and a relative position (FE-2 `findDropTarget`); keep
    parent-first order.
  - modify `components/workflow-editor/WorkflowEditor.tsx` `onNodeDragStop`: recompute membership of the dragged node(s)
    (into the innermost loop under the drop point, or out), convert positions, reorder parent-first, refuse a drop that
    would put a loop into itself or its descendants, refuse a membership change that `wouldCreateCycle` rejects (snap back
    with a notification); deleting a loop asks whether to delete its members or keep them (moved out, absolute positions).
  - modify `service/WorkflowClipboardService.ts` (copy/paste keeps `parentNode` only when the loop is copied too).
- Unit tests: membership decisions live in FE-2 helpers (extend its spec with a drag-out and a refused drop case).
- Docs: release notes, root README (editor features list if present).
- Depends on: FE-2, FE-5. P-2: not needed.
- Covers: AC-32.

### FE-7 Collapsed view, collapsed-graph connection check, loop-aware editor plumbing
- Repo/module: `components/workflow-editor/WorkflowEditor.tsx`, `components/workflow-viewer/WorkflowViewer.tsx`.
- Consumes: FE-2.
- Files:
  - modify `WorkflowEditor.tsx`: render `collapsedView` (hidden members via React Flow `hidden`, display edges re-pointed to
    the loop outline) while saving the real nodes/edges; `isValidConnection` uses `wouldCreateCycle` instead of the local
    `hasCycle`, and refuses a connection whose source or target is a loop; edges cannot be started from a collapsed loop.
  - modify `WorkflowViewer.tsx`: the same derived view (read-only), so a collapsed loop stays collapsed in runs.
- Unit tests: covered by FE-2 spec; add a spec case for "connection to a loop is refused".
- Docs: release notes.
- Depends on: FE-2, FE-5. P-2: not needed.
- Covers: AC-30, AC-33, AC-38.

### FE-8 Loop properties dialog: query renderer (text mode), hide Retry Policy
- Repo/module: `components/node-dialog/`.
- Consumes: FE-3; the Contract rows "propertiesUISchema" (`options.format: "query"`, `options.language: "CEL"`).
- Files:
  - create `components/node-dialog/QueryConditionRenderer.tsx`: tester `rankWith(5, and(isStringControl, optionIs('format', 'query')))`
    (same pattern as `CodeEditorRenderer`/`CredentialRenderer`); for now always text mode: a text field with the raw
    string, an error `Alert` with the FE-3 reason + supported shape when it does not parse, a "Start over with the
    builder" button that sets `DEFAULT_LOOP_CONDITION`; the text is never dropped. Leaves a clear slot where FE-10 mounts
    the builder when the text parses.
  - modify `components/node-dialog/NodeDialog.tsx`: register the renderer in `customRenderers`; hide the Retry Policy tab
    (and do not pass retry options on save) when the node is a Loop (new prop `isLoop`, set by the caller in
    `WorkflowEditor.tsx`).
- Unit tests: tester decision and "show builder or text" decision as pure functions in `src/common/LoopConditionUi.ts` + spec.
- Docs: root `AGENTS.md` NodeDialog renderer list (`format: "query"`), release notes.
- Depends on: FE-3. P-2: not needed.
- Covers: AC-35 (text part, retry tab).

### FE-9 Run display of loops
- Repo/module: `components/node/LoopNode.tsx`, `components/node-output-viewer/`.
- Consumes: Contract rows "Run display", "Loop `$variable`", "Failure detail" (implemented by BE-14/BE-16); FE-4.
- Files:
  - modify `components/node/LoopNode.tsx` (status, iteration message, error tooltip from `$error`).
  - modify `components/node-output-viewer/NodeOutputViewer.tsx` / `flow-variables-view/FlowVariablesView.tsx`: a loop
    opens the viewer with its `$error` or its carried variables; members show their last-iteration outputs.
  - modify `src/common/ExecutionStatusPresentation.ts` only if `WARNING` needs a loop-specific label (no new enum value).
- Unit tests: presentation decisions in `LoopPresentation.spec.ts` (FE-5).
- Docs: release notes.
- Depends on: FE-4, FE-5. Coding can start against the contract; browser verification needs BE-14 and BE-16 deployed.
  P-2: not needed.
- Covers: AC-37.

### FE-10 React Query Builder single-rule builder in the query renderer (P-2)
- Repo/module: `components/node-dialog/QueryConditionRenderer.tsx`.
- Consumes: FE-3, FE-8; P-2 (`react-querybuilder` in `workflow-editor-extension/package.json` + regenerated `yarn.lock`).
- Files:
  - modify `QueryConditionRenderer.tsx` (and optionally a small `LoopConditionBuilder.tsx`): when `parseLoopCondition` succeeds,
    render `QueryBuilder` with exactly one rule: no groups, no add-rule/add-group/remove controls, no combinators;
    field = free text (suggestions from the workflow's known variable names optional), operators from
    `operatorsFor(literalType)`, value editor typed (boolean select, number, text). Every change emits
    `formatLoopCondition(...)` into the property. React Query Builder's own `parseCEL`/`formatQuery('cel')` are NOT used.
    MUI styling follows the repo's MUI v5 conventions (use `@react-querybuilder/material` only if P-2 added it).
- Unit tests: mapping between the builder's rule object and the FE-3 condition as pure functions in
  `src/common/LoopConditionUi.ts` + spec (no `react-querybuilder` import in `src/common`, so the spec runs without it).
- Docs: release notes Upgrade notes (new dependency, `yarn.lock` changed), root README dependencies if listed.
- Depends on: FE-8. Blocked until: P-2 done. This is the ONLY task that imports `react-querybuilder`.
- Covers: AC-34, AC-35 (builder part), AC-40.

### FE-11 Workflow-builder prompt twins learn loops
- Repo/module: `workflow-builder-extension/claude-code/workflow-builder.md`, `workflow-builder-extension/src/browser/WorkflowBuilderPrompt.ts`.
- Consumes: BE-9 (MCP `parent` field, tool descriptions) and BE-8 messages; Contract rows "MCP spec", "Condition grammar".
- Files: both twins, same wording: a loop is a `nodes[]` entry with `nodeId` `org.projectcontinuum.core.builtin.Loop` and
  `properties {condition, maxIterations, onLimit, check}`; members set `parent`; edges never name a loop and connect the
  members; the grammar with 3 examples (`vars["hasMore"] == true`, `vars["page"] <= 10`, `vars["restNode_3.executionStatus"] != "FAILED"`)
  and the D-3 list; flow variables are set by nodes, so the loop needs a member that sets the condition variable; keep
  "each input port accepts one edge" and "never write workflow JSON yourself"; the "one node per build" flow adds the loop
  before its members. `workflow-builder-extension/README.md` if it describes the rules; a spec in
  `workflow-builder-extension/src/common/` only if a pure helper changes (none expected).
- Unit tests: none (prompt text); [review] that the twins match (diff of the rule paragraphs).
- Depends on: BE-9. Blocked until: BE-9 DONE. P-2: not needed.
- Covers: AC-39.

### FE-12 Workbench closeout: release notes, README, AGENTS.md
- Repo/module: `continuum-workbench` docs.
- Files: `release_notes/RELEASE_NOTES_v0.0.16.md` (Highlights "Loops", Upgrade notes: needs platform 0.0.23 with loops;
  `react-querybuilder` dependency), root `README.md`, root `AGENTS.md` (all contracts touched by FE-1..FE-11).
- Depends on: FE-1 .. FE-11. P-2: needed only for the FE-10 lines.
- Covers: AC-28 (workbench part).

## Dependency graph


Edges (authoritative, no cycles): BE-1→BE-5, BE-4→BE-5, BE-2→BE-3, BE-3→BE-6, BE-2→BE-7, BE-3→BE-7, BE-5→BE-8, BE-7→BE-8,
BE-7→BE-9, BE-2→BE-10, BE-3→BE-10, BE-2→BE-11, BE-11→BE-12, BE-4→BE-13, BE-10→BE-14, BE-11→BE-14, BE-12→BE-14,
BE-13→BE-14, BE-1→BE-15, BE-5→BE-15, BE-14→BE-15, BE-5→BE-16, BE-14→BE-16, BE-7→BE-17, BE-15→BE-17, BE-16→BE-17,
BE-*→BE-18; BE-2→FE-1, BE-4→FE-3, BE-12→FE-4, FE-1→FE-5, FE-2→FE-5, FE-3→FE-5, FE-2→FE-6, FE-5→FE-6, FE-2→FE-7,
FE-5→FE-7, FE-3→FE-8, FE-4→FE-9, FE-5→FE-9, FE-8→FE-10, P-2→FE-10, BE-9→FE-11, FE-*→FE-12.

Start immediately: BE-1, BE-2, BE-4 (backend, in parallel; BE-1 is the only one using the network); FE-2 (when the
frontend is resumed). Not dependent on the engine (BE-1): BE-2, BE-3, BE-4, BE-6, BE-7, BE-9, BE-10, BE-11, BE-12,
BE-13, BE-14. Engine-dependent: BE-5 (parser), BE-8, BE-15, BE-16, BE-17 (and FE-3 is not: it only needs BE-4's table).
Suggested backend order: BE-1 | BE-2 | BE-4 → BE-3 → BE-6, BE-7, BE-10, BE-11, BE-13 → BE-9, BE-12, BE-5 → BE-8, BE-14 →
BE-15, BE-16 → BE-17 → BE-18.

## Planner choices (for Gate 2; each is the obvious default, say if wrong)

- PR-1 Loop id `org.projectcontinuum.core.builtin.Loop`, task-queue marker `$builtin`, featureId `org.projectcontinuum.core.builtin`.
- PR-2 A loop whose outside input edge is BROKEN (an outside parent failed or was skipped) is SKIPPED with all its
  members, like any node with a broken input.
- PR-3 The finished loop stores its carried variables as a display-only `$variable` output (Contract table), so the
  workbench can show what entry members inherited.
- PR-4 History guard thresholds are code constants (40,000 events / 40 MiB), not configuration: workflow code cannot
  read Spring properties deterministically. BE-17 calibrates the validator budget from them.
- PR-5 `Node` and `NodeData` get `@JsonIgnoreProperties(ignoreUnknown = true)` (BE-2) so React Flow fields such as `extent`,
  `style` or `zIndex` that the editor may save on loop members never break a start.
- PR-6 Dispatch strips `parentNode`/`collapsed` before sending a node to a worker (BE-10), so older workers need no
  upgrade for loop members. (Requirement risk "unknown-field test" is answered by stripping plus the BE-2 test.)
- PR-7 Deleting a loop in the editor asks whether to delete or keep its members (FE-6).

## Test plan

| AC | Verifier | Test location |
|----|----------|---------------|
| AC-1 | backend-developer | BE-1: five `--offline` test runs + dependency report diff recorded in `03-backend-dev.md` |
| AC-2 | backend-developer | `continuum-orchestration-service/src/test/kotlin/.../spike/CelInWorkflowSpikeTest.kt` |
| AC-3 | backend-developer | commons `model/ContinuumWorkflowModelLoopFieldsTest.kt`; orchestration `config/DataConverterLoopFieldsTest.kt` |
| AC-4 | backend-developer; backend-expert (spec diff review, lint if refused) | api-server `openapi/OpenApiSpecTest.kt`; `docs/api/continuum-api-server.openapi.yaml` diff |
| AC-5 | backend-developer | orchestration `registration/BuiltinNodeRegistrationPublisherTest.kt` |
| AC-6 | backend-system-tester | new `continuum-system-tests/suites/api/loops.js` (node-explorer metadata, search, children "Flow Control", documentation) |
| AC-7 | backend-developer; backend-system-tester | commons `node/builtin/LoopNodeDescriptorTest.kt`, `validation/WorkflowValidatorLoopStructureTest.kt`; `suites/api/loops.js` (validate with bad values) |
| AC-8 | backend-developer; backend-expert (two-loops by construction) | `WorkflowValidatorLoopStructureTest.kt` |
| AC-9 | backend-developer; backend-system-tester | `WorkflowValidatorLoopStructureTest.kt`; `suites/api/loops.js` |
| AC-10 | backend-developer; backend-system-tester | `WorkflowValidatorLoopStructureTest.kt`; `suites/api/loops.js` |
| AC-11 | backend-developer; backend-system-tester | all existing module tests unchanged; existing `suites/api/workflow.js` + `smoke.js` |
| AC-12 | backend-developer | commons `validation/loop/LoopConditionParserGoldenTest.kt` over `src/test/resources/loop-condition/golden-vectors.json` |
| AC-13 | backend-developer; backend-system-tester | commons `WorkflowValidatorLoopConditionTest.kt`; api-server `WorkflowControllerGraphValidationTest.kt`; `suites/api/loops.js` (start 400, validate 200 `valid:false`) |
| AC-14 | backend-developer | api-server `mcp/WorkflowSpecBuilderTest.kt`, `mcp/WorkflowSpecValidatorTest.kt`, `mcp/ContinuumWorkflowMcpToolsCallbackTest.kt` |
| AC-15 | backend-developer | orchestration `ContinuumWorkflowTest.kt` (loop fixture cases), `FakeInitializeActivity.requestedNodeIds` |
| AC-16..AC-22 | backend-developer | orchestration `workflow/ContinuumWorkflowLoopTest.kt` (structured evaluator, then both evaluators after BE-15); `FlowVariableMergerTest.kt`; `LoopConditionRuntimeCheckTest.kt` |
| AC-16 (live) | backend-system-tester | `suites/api/loops.js`: start the example loop with continuum-feature-base nodes if a node that sets a flow variable exists (check `lib/capabilities.js`; skip, not fail, when absent), wait for `FINISHED`, assert loop `SUCCESS` and the member/loop statuses in the snapshot. NOT VERIFIED if no registered node can set a chosen flow variable |
| AC-23, AC-27 | backend-developer | `ContinuumWorkflowLoopTest.kt` (guard, invalid condition through the Temporal client) |
| AC-24 | backend-developer | `ContinuumWorkflowLoopHistoryBudgetTest.kt`; `WorkflowValidatorLoopStructureTest.kt` |
| AC-25 | backend-developer | `ContinuumWorkflowLoopTest.kt` (captured status events) |
| AC-26 | backend-developer | `ContinuumWorkflowLoopReplayTest.kt` |
| AC-28 | backend-expert, frontend-expert | review checklist: release notes core 0.0.23 / workbench 0.0.16, READMEs, DOX chain, inventory |
| AC-29 | frontend-developer (later machine) | `workflow-editor-extension/src/common/LoopCondition.spec.ts` |
| AC-30 | frontend-developer; frontend-tester | `src/common/LoopTopology.spec.ts`; browser scenario "connect X1 -> Y -> X2 refused" |
| AC-31 | frontend-developer | `src/common/FlowVariables.spec.ts` (shared loop cases) |
| AC-32, AC-33 | frontend-tester | browser scenarios "drop loop, drop node in, drag out" and "collapse/expand; compare saved `.cwf` edges" |
| AC-34, AC-35 | frontend-tester | browser scenarios "builder emits canonical CEL; reopen" and "invalid string shows text + reason + Start over"; builder parts need P-2 |
| AC-36, AC-37, AC-38 | frontend-tester | browser scenarios "warning badge", "run view iteration message" (needs a stack with BE-14), "loop-free workflow regression" |
| AC-39 | frontend-expert | review: diff of the rule paragraphs in both prompt twins |
| AC-40 | frontend-expert | review: `grep -rn react-querybuilder workflow-editor-extension/src` only hits FE-10 files |

System-test fixtures (backend-system-tester): `continuum-system-tests/fixtures/workflows/loop-valid.json` (loop + members,
uses only nodes available in the compose stack; the loop needs only the orchestrator's registration) and
`loop-invalid-*.json` (edge to loop, collapsed cycle, bad condition, bad maxIterations). Update
`continuum-system-tests/README.md` and `AGENTS.md` suite list.

## Not verifiable locally

- P-1 is a one-time network action (BE-1). If Maven Central does not resolve `dev.cel:cel:0.15.0` through the VPN,
  BE-5/BE-15 switch to the D-7 fallback; no further online attempt without the user.
- cel-java option name for heterogeneous int/double comparison in 0.15.0, and whether `-1` parses as a folded constant or a
  unary call: NOT VERIFIED until BE-1/BE-5 run; tests pin the behaviour either way.
- Redocly lint (`npx --yes @redocly/cli@2 lint ...`) may be refused here (`npx`); then it is left to the user/CI (BE-2).
- Live stack checks (AC-6, AC-7/9/10/13 via HTTP, AC-16 live): need `make up` in `continuum-system-tests` with
  locally built 0.0.23 images of orchestrator, api-server and message-bridge (Jib to the local Docker daemon:
  `./gradlew --offline :<module>:jibDockerBuild`, if the base image is cached; NOT VERIFIED). Otherwise NOT VERIFIED.
- AC-16 live needs a registered node that sets a named flow variable from its properties; none is known in
  continuum-feature-base (not checked). Otherwise the live loop run covers only zero-iteration and guard paths.
- The whole workbench side (FE-1..FE-12): on hold by the user and planned for another machine. Here: no
  `node_modules`, workbench builds forbidden, npm/yarn registries blocked, so `tsc`, specs, `yarn build` and browser
  scenarios cannot run on this machine. `bash scripts/generate-api-clients.sh` (FE-1) could run here (openapi-generator
  7.27.0 installed), if the user wants FE-1 done early.
- P-2 (`react-querybuilder` + `yarn.lock`) is a user action; it blocks only FE-10.
- Temporal history-limit behaviour of a real server (51,200 events) is only approximated by the test server and the
  calibrated budget (BE-17).

## Requirements not mapped / deviations

- "A member of two loops" is impossible by construction (one `parentNode` pointer); covered by [review] in AC-8.
- Requirement "Q-2 api-server task-queue lookup tolerates the marker": no api-server change is needed because the
  orchestrator never asks for the loop's queue (BE-10); the registry still stores `$builtin` (documented in BE-6).
- "react-querybuilder CEL parser": deliberately unused (D-9).
- continuum-docs website page: out of scope (requirements).

## Changelog

- 2026-11-25 Round 1: first plan. P-1 spike as BE-1 (user allowed one online resolve). FE tasks made portable and on
  hold per the user's instruction; contract table added for them.


---

## Round 2 — full CEL

Date 2026-11-25. Source: `01-requirements.md` "Round 3 — full CEL" (D-23..D-38, Gate 1 approved 2026-11-25; the
user confirmed `onNonBooleanResult` as a Loop property with default `fail`). Code state: `03-backend-dev.md` rounds 1-3
(BE-1..BE-7, BE-9..BE-13 DONE; BE-8 HELD; module split into `continuum-loop-condition` and the `protobuf-java-util`
4.36.2 pin DONE; SCOPE-2 approved: `WorkflowValidator` takes an injected condition checker). Round 1 above stays as
history. Where they conflict, this section wins. House rules 12 (focused units) and 13 (no factory IDs in the product)
apply to every task below. Each task names its units.

Checked while planning this round (offline Gradle cache, `javap`):
- `dev.cel:cel:0.15.0` jar contains `dev.cel.validator.validators.{Regex,Timestamp,Duration}LiteralValidator` and
  `dev.cel.extensions.CelExtensions` with version-pinning overloads `math(CelOptions, int)`, `sets(int)`, `lists(int)`,
  `regex(int)`, `comprehensions(int)`, `optional(int)`. `strings()`, `encoders(CelOptions)` and `bindings()` have no
  version overload, so they cannot be pinned (the function-list pin test covers them).
- `CelOptions.Builder` has `comprehensionMaxIterations`, `maxRegexProgramSize`, `maxParseRecursionDepth`,
  `maxExpressionCodePointSize`, `maxParseExpressionNodeCount`, `enableOptionalSyntax`,
  `enableHeterogeneousNumericComparisons`.
- `dev.cel.runtime.CelRuntime.Program.trace(Map<String, ?>, CelEvaluationListener)` exists; the listener is
  `callback(CelExpr, Object)`.
- The orchestration service has no retry library, no `@EnableScheduling` and no task executor bean today
  (grep of `src/main`); the registration retry (BE-22) therefore brings its own small executor.
- The 9 remaining factory-ID lines are all in files this round deletes or rewrites: `LoopCondition.kt:6`,
  `LoopConditionTest.kt:11`, `LoopConditionGoldenVectors.kt:8`, `CelLoopConditionParser.kt:9`,
  `LoopConditionShapeGate.kt:15`, `CelLoopConditionParserRobustnessTest.kt:13`, `LoopConditionParserGoldenTest.kt:14`,
  `CelInWorkflowSpikeTest.kt:24` and `:112`. Two more lines point at factory artifacts without an ID and are fixed in
  the task that touches the file: `LoopNodeDescriptor.kt:12` ("plan \"Contract\" table", BE-3r) and
  `WorkflowValidatorLoopStructureTest.kt:66` ("the example workflow of the requirements", BE-8).

### Settled decisions (user, 2026-11-25; recorded, no longer open)

- **Entry order of carried variables when a loop has several outside parents** (round-3 note in `03-backend-dev.md`):
  `FlowVariableMerger.endOfIterationVariables` orders the loop's external incoming edges by the target member's run
  order in the loop, then by the target input port index, then `targetHandle`, then source id; the first value per
  name wins. This is the contract. BE-12r pins it with a two-parent case and documents it; FE-4 mirrors it.
- **Built-in Loop registration retries with backoff** at orchestrator start-up (BE-22): 5 attempts over about 2
  minutes, off the start-up thread, then an error log. Workflows run meanwhile; only the palette entry waits.
- **`onNonBooleanResult`** is a Loop property (enum `fail` | `treat-as-false`, default `fail`, required), next to
  `onLimit` (D-31).

### Superseded by this round

| Round 1 item | Status now | Replaced by |
|---|---|---|
| BE-4 (DONE) contract: subset types, 88-row golden table, test fixtures | REWORK | BE-19 (delete + new contract), BE-21 (backend CEL table) |
| BE-5 (DONE) `CelLoopConditionParser` AST gate | REWORK | BE-19 (delete), BE-20 (`LoopCelEnvironment`, `CelLoopConditionValidator`), BE-21 (table and robustness tests) |
| BE-13 (DONE) evaluator interface returning `Boolean`, `LoopConditionRuntimeCheck`, structured test evaluator | REWORK | BE-19 (outcome contract, scripted test evaluator; runtime check deleted) |
| BE-3 (DONE) | small change | BE-3r |
| BE-9 (DONE) tool descriptions | small change | BE-9r |
| BE-12 (DONE) | small addition | BE-12r (entry-order case) |
| BE-8 (HELD) | resumed, rewritten | BE-8 below |
| BE-14, BE-15, BE-16, BE-17, BE-18 (TODO) | rewritten | same IDs below |
| FE-3 (hand-written parser) | rewritten | FE-3 below (RQB adapter) |
| FE-5, FE-8, FE-10, FE-11 | rewritten | same IDs below (FE-10 split into FE-10a spike, FE-10b builder) |
| FE-1, FE-4, FE-9, FE-12 | small changes | notes below |
| AC-12, AC-13, AC-22, AC-26, AC-29, AC-34, AC-35, AC-39, AC-40 | REWRITTEN (same ID) | "Acceptance criteria (round 2)" |
| AC-5, AC-7, AC-14, AC-21, AC-24, AC-27, AC-28 | AMENDED (same ID) | same table |
| AC-36 (canvas badge) | WITHDRAWN (D-32) | none; the box shows the condition text (part of AC-37) |
| Contract rows "propertiesSchema", "propertiesUISchema", "Condition grammar", "Golden vectors", "Rejection reason codes", "Run display", "Failure detail" | REPLACED | "Contract changes" below |
| PR-4 (guard thresholds are code constants) | stands; also covers the D-28 cost caps | |

AC-1..AC-4, AC-6, AC-8..AC-11, AC-15..AC-20, AC-23, AC-25, AC-30..AC-33, AC-37, AC-38 stand unchanged.

### Contract changes (frozen for the frontend; replaces the named rows of the round-1 Contract table)

| Item | Value | Owner task |
|------|-------|------------|
| `propertiesSchema` | as round 1, plus `onNonBooleanResult: {type: string, enum: [fail, treat-as-false], default: fail}`; `required: [condition, maxIterations, onLimit, onNonBooleanResult, check]`; `condition` stays `{type: string, minLength: 1, maxLength: 2048, default: 'vars["hasMore"] == true'}` with a full-CEL description | BE-3r |
| `propertiesUISchema` | VerticalLayout: `condition` (`options: {format: "query", language: "CEL"}`), `maxIterations`, `onLimit`, `onNonBooleanResult`, `check` | BE-3r |
| Condition language | any CEL expression the backend validator accepts (D-23): standard operators, macros and functions plus the cel-java extensions strings, math, lists, sets, regex, encoders, bindings, optional (with `vars[?"x"]`, `vars.?x`), comprehensions; one declared variable `vars: map(string, dyn)`; result type `bool` or `dyn`. There is no TypeScript parser: the backend is the only judge | BE-20 |
| Backend CEL table | `continuum-platform-core/continuum-loop-condition/src/test/resources/loop-condition/cel-conditions.json`: array of `{id, input, valid, code?, resultType? ("bool"\|"dyn"), readsVariables?, source? ("builder"), evaluations?: [{vars, onNonBooleanResult?, expect: true\|false\|"non-boolean"\|<runtime code>, messageContains?}]}`. Backend-only: no workbench copy, no SHA pin | BE-21 (rows), BE-15 (evaluations run) |
| Issue codes (`LoopConditionIssueCode`) | validation: `EMPTY`, `TOO_LONG`, `TOO_DEEP`, `SYNTAX`, `CHECK_ERROR`, `NOT_BOOLEAN`, `INVALID_LITERAL`, `REBOUND_NAME`; runtime: `MISSING_VARIABLE`, `EVALUATION_ERROR`, `LIMIT_EXCEEDED`, `NON_BOOLEAN_RESULT`. Each has a fixed `reasonText`; a message is `reasonText`, then the CEL message, then `(column n)` when known (1-based code points) | BE-19 |
| Validator issues for `condition` | one ERROR per loop (`node` = loop key, `property` = `condition`), text `Loop '<key>': the condition <reasonText>: <CEL message> (column <n>).`; at most one condition WARNING per loop (W-1: `Loop '<key>': the condition reads no flow variable, so it never changes; the loop runs until maxIterations and then follows onLimit.`) | BE-8 |
| Cost caps (code constants, `LoopConditionLimits`) | text 2,048 code points; nesting 32; parse recursion 100; `comprehensionMaxIterations` 1,000; every intermediate list/map at most 10,000 elements, string at most 100,000 characters, bytes at most 100,000 bytes; `maxRegexProgramSize` 1,000; value previews 100 characters | BE-19 (constants), BE-20/BE-15 (use) |
| Run display (added to round 1) | `onNonBooleanResult: treat-as-false` and a non-boolean result: loop `WARNING`, message `Condition returned <type> in iteration <k>; treated as false.`; with `check: before` on the first pass the members are `SKIPPED` and the loop is `WARNING` | BE-14 |
| Failure detail (replaces round 1) | a failed loop's `$error` (`text/plain`) is one of: limit reached (`fail`), `NON_BOOLEAN_RESULT` (`Loop 'L': the condition returned a string ("yes") in iteration 2, not true or false.`), `MISSING_VARIABLE` (`Loop 'L': flow variable 'hasMore' is not set when the condition is checked in iteration 3 (column 1).`), `EVALUATION_ERROR` / `LIMIT_EXCEEDED` (loop, iteration, code, CEL message, column), history guard, invalid condition or settings at loop start | BE-14, BE-15, BE-16 |
| Condition check from the dialog | no new endpoint: the workbench calls the existing `validateWorkflow` (`POST /api/v1/workflow/validate`) with the edited workflow and shows the issues whose `node` is this loop and `property` is `condition` | BE-8 (server side), FE-8 |
| Workbench round-trip table | `continuum-workbench/workflow-editor-extension/src/common/loop-condition-builder-cases.json`: `{id, text, fits, query?, emitted?}` (D-35), frontend-only | FE-10a (pins), FE-3 (spec) |

### Acceptance criteria (round 2)

Rewritten and amended rows replace the round-1 row with the same ID. Test names never carry these IDs (house rule
13); the mapping AC → test lives only in `03-backend-dev.md` / `04-frontend-dev.md`.

| ID | Criterion | Verified by |
|----|-----------|-------------|
| AC-5 (amended) | As round 1, plus: when the send fails or is not accepted, the publisher retries with backoff (5 attempts in total, about 2 minutes overall) on a background thread; `ApplicationReadyEvent` handling returns at once; after the last failure one error is logged naming the node and the attempt count; a success stops the retries; nothing is sent again after a success; shutting the service down stops pending retries | [unit] |
| AC-7 (amended) | As round 1, plus: `onNonBooleanResult` outside its enum gives one error naming the loop key and `onNonBooleanResult`; the defaults (including `onNonBooleanResult: fail`) are valid | [unit], [system-api] |
| AC-12 (rewritten) | Every row of the backend CEL table passes in the validator test (valid rows: `Valid` with the row's `resultType` and `readsVariables`; invalid rows: `Invalid` with the row's `code` and a 1-based column where the row has one). The table has at least one valid row per operator family, macro, enabled extension, optional syntax, timestamp/duration, dotted name, `has`/`in`, literal on the left, two variables and a `dyn` result, and at least one invalid row per validation code | [unit] |
| AC-13 (rewritten) | `now() > 1`, `foo == 1`, `vars["p"] + 1`, `vars["a"] ==`, `vars["s"].matches("(")`, `cel.bind(a, 1, cel.bind(a, a + a, a > 0))`, 33-deep bracket nesting and a 2,049-character text each give exactly one error with `node` = loop key, `property` = `condition` and, in the message, the reason text of `CHECK_ERROR`, `CHECK_ERROR`, `NOT_BOOLEAN`, `SYNTAX`, `INVALID_LITERAL`, `REBOUND_NAME`, `TOO_DEEP`, `TOO_LONG` respectively (no second schema error for the same property). The accepted examples `vars["page"] < 10 && vars["hasMore"]`, `"nextCursor" in vars`, `vars["items"].exists(i, i > 2)`, `vars["name"].lowerAscii() == "x"`, `math.greatest(vars["a"], 1) > 0`, `vars[?"r"].orValue(0) < 3`, `timestamp(vars["ts"]) > timestamp("2026-01-01T00:00:00Z")` give no condition error. `POST /api/v1/workflow` answers 400 and `/validate` 200 `valid:false` for a rejected one | [unit], [system-api] |
| AC-14 (amended) | As round 1, plus: the AC-13 rejected conditions come back in `errors` of `validateContinuumWorkflow` and `buildContinuumWorkflow`, and condition `true` gives exactly one W-1 warning in `warnings` | [unit] |
| AC-21 (amended) | As round 1, plus: with two outside parents feeding two different entry members, the condition reads the variable of the parent whose target member comes first in the loop's run order (then the lower input port index) when both set the same name | [unit] |
| AC-22 (rewritten) | Runtime semantics follow CEL: (a) `vars["hasMore"] == true` with `hasMore` not set fails the loop and the run, the `$error` names the loop, `hasMore`, the iteration and the column; (b) `vars["hasMore"] == true \|\| vars["page"] > 3` with `page = 5` and no `hasMore` evaluates to true (no failure); (c) `vars["s"] == 1` with a string is false; `vars["s"] > 1` fails with `EVALUATION_ERROR`; (d) an int variable against a double literal compares by value; (e) JSON values reach CEL as `Long`, `Double`, `Boolean`, `String`, lists, maps and `null` | [unit] |
| AC-26 (rewritten) | With the real cel-java evaluator: AC-16..AC-22 pass; a recorded 3-iteration loop history with the condition `vars["page"] < 3 && vars["hasMore"]` replays with `WorkflowReplayer` without error; the history contains one side-effect marker per condition check; replaying with an evaluator that would return a different result still replays (the recorded outcome is used) | [unit] |
| AC-27 (amended) | As round 1, with the defensive re-check using the module's validator: a run started directly through Temporal with an invalid condition or an `onNonBooleanResult` outside its enum fails at loop start with the reason, no workflow-task retry loop | [unit] |
| AC-24 (amended) | The validator warning uses `maxIterations x (members + condition-check events)` against the calibrated budget; the per-check event cost is measured by the budget test and kept within its tolerance | [unit] |
| AC-28 (amended) | As round 1, plus: release notes 0.0.23, `LoopNode.doc.md`, the module READMEs and AGENTS.md files describe full CEL (enabled libraries, limits, `onNonBooleanResult`, CEL error semantics, registration retry); no line still describes the single-comparison subset, the golden vector table or the runtime type check | [review] |
| AC-29 (rewritten) | Workbench adapter: every row of `loop-condition-builder-cases.json` passes in `LoopConditionQuery.spec.ts`: builder rows give exactly `emitted` from `query`; text rows give `fits` as stated; for every `fits: true` row, import then export returns the text unchanged after whitespace normalisation; `vars["restNode_3.durationMs"]` and a name with `"` and `\` round-trip | [unit] (workbench spec, needs P-2) |
| AC-34 (rewritten) | Loop dialog builder mode: a condition built with nested `and`/`or` groups, a group `not`, a dotted variable name and a string value with `"` is stored as CEL text, and reopening the dialog shows the same builder tree (opens in builder mode); `vars["hasMore"] == true` opens in the builder with field `hasMore`, operator `=`, value `true` | [browser] (needs P-2) |
| AC-35 (rewritten) | Loop dialog CEL mode: a stored condition that uses a macro, `has()`, arithmetic or an extension (e.g. `vars["items"].exists(i, i > 2)`) opens in CEL mode with the text unchanged; the toggle to the builder is disabled with the reason "This condition uses CEL features the builder cannot show"; "Replace with a new builder condition" asks for confirmation and only then replaces the text. In both modes, about 500 ms after an edit, the issues `validateWorkflow` returns for this loop's `condition` are shown with message and column; when the api-server is unreachable the dialog shows "Not checked", never "valid". Builder → CEL always works and shows the generated text. The `onNonBooleanResult` control appears after `onLimit`; the Retry Policy tab is not shown for a Loop. Saving an invalid draft is not blocked | [browser] (CEL-mode part without P-2; toggle-to-builder part needs P-2) |
| AC-36 | WITHDRAWN (the canvas badge is dropped; validation errors show in the dialog and at validate/run start) | — |
| AC-39 (rewritten) | `claude-code/workflow-builder.md` and `WorkflowBuilderPrompt.ts` carry the same loop paragraph: loops are `nodes[]` entries with the built-in id, members declare `parent`, edges never name a loop; the condition is CEL over `vars` and must end in `true`/`false`; always `vars["name"]`; presence guards `has(vars.x)`, `"x" in vars`, `vars[?"x"].orValue(default)`; the CEL semantics notes; the enabled libraries one line each and what is not available (`now()`, protos, user functions); the limits; `onNonBooleanResult`; the six examples of D-36; a member must set the variables the condition reads; fix what `validateContinuumWorkflow` reports. The "one node per build" flow still applies | [review] |
| AC-40 (rewritten) | Only `src/common/LoopConditionQuery.ts` (the adapter) and the builder component files of FE-10b import `react-querybuilder`; every other FE task compiles and its specs run without it (grep of the changed files) | [review] |
| AC-41 (new) | Clean-up: none of the files and declarations marked DELETE in BE-19 exist (`LoopConditionShapeGate`, `LoopConditionLiteralReader`, `LoopConditionRuntimeCheck`, `StructuredTestLoopConditionEvaluator`, `LoopConditionTest`, `LoopConditionGoldenVectors`, `golden-vectors.json`, `LoopConditionRejection`, `LoopConditionParser`, `LoopConditionParseResult`, `LoopCondition`, `LoopConditionOperator`, `LoopConditionLiteral*`, `quoteCelString`, `SUPPORTED_SHAPE`); `continuum-commons/build.gradle.kts` equals `main` (no `java-test-fixtures`, no fixtures block); the commons, worker-starter, message-bridge and knime-base runtime classpaths equal `main`; a factory-ID grep of the changed files finds nothing; the six-module `--offline` test run passes | [unit] (build + grep), [review] |
| AC-42 (new) | One environment: the validator and the evaluator are built from the same `LoopCelEnvironment`; a pin test lists the environment's function and macro names and fails when they change; `protos` and `nativeTypes` functions are absent; the options include heterogeneous numeric comparisons, optional syntax, macros on, `comprehensionMaxIterations` 1,000, `maxRegexProgramSize`, parse recursion 100 | [unit] |
| AC-43 (new) | Cost limits: `lists.range(1000000).size() > 0`, the 10-step `.replace("a", "aaaaaaaa")` chain, `sets.contains` over two 60,000-element lists, 29 nested `cel.bind` doublings and a 100 x 100 x 100 nested `map` each end within 100 ms with `LIMIT_EXCEEDED` naming the limit; the evaluator never throws for them (no `OutOfMemoryError`, `StackOverflowError` caught); the robustness test runs the whole hostile list | [unit] |
| AC-44 (new) | `onNonBooleanResult`: condition `vars["hasMore"]` and `hasMore = "yes"` in iteration 2: with `fail` the loop is `FAILED`, the run fails, the `$error` names the type, a preview of at most 100 characters and the iteration; with `treat-as-false` the loop ends `WARNING` after iteration 2 with the treated-as-false message and downstream runs with the iteration-2 outputs; with `check: before` and a non-boolean on the first pass under `treat-as-false`, the members are `SKIPPED` and the loop is `WARNING` | [unit] |

### Backend tasks (round 2)

Repo for all: `continuum-platform-core`, branch `CONTNM-180`; paths relative to it. Package roots as in round 1, plus
loop-condition `continuum-loop-condition/src/main/kotlin/org/projectcontinuum/core/loopcondition/` and commons
contract `continuum-commons/src/main/kotlin/org/projectcontinuum/core/commons/validation/loop/`. Every task: DOX chain,
named arguments, `./gradlew --offline` only (no online resolve is needed this round: cel-java 0.15.0, its validator
classes and `protobuf-java-util` 4.36.2 are cached), release-note lines in `release_notes/RELEASE_NOTES_v0.0.23.md`
(unreleased, so subset lines are corrected in place), the owning README, and no factory IDs in code, tests, KDoc or
docs (house rule 13). Verification for every task: the six-module run
`./gradlew --offline :continuum-commons:test :continuum-loop-condition:test :continuum-orchestration-service:test :continuum-api-server:test :continuum-worker-springboot-starter:test :continuum-message-bridge:test --continue`.

#### BE-19 Clean-up: delete the single-comparison subset, introduce the full-CEL contract (replaces done BE-4/BE-5/BE-13 parts)
- Repo/module: `continuum-commons`, `continuum-loop-condition`, `continuum-orchestration-service`.
- DELETE (no dormant copy; this is the explicit delete half of D-38):

  | Path | What |
  |---|---|
  | `continuum-commons/src/main/kotlin/.../validation/loop/LoopCondition.kt` | the whole file: `LoopConditionOperator`, `LoopConditionLiteralType`, `LoopConditionLiteral` (+4 subtypes), `LoopCondition` with `canonical()`, `quoteCelString`, `LoopConditionGrammar.SUPPORTED_SHAPE`, `LoopConditionRejection`, `LoopConditionParseResult`, `LoopConditionParser`, and the old `LoopConditionLimits` (re-created reduced, see ADD) |
  | `continuum-commons/src/test/resources/loop-condition/golden-vectors.json` | 88-row subset table |
  | `continuum-commons/src/testFixtures/` (whole source set: `LoopConditionGoldenVectors.kt`) | loader |
  | `continuum-commons/src/test/kotlin/.../validation/loop/LoopConditionTest.kt` | 19 subset tests |
  | `continuum-commons/build.gradle.kts`: `java-test-fixtures` plugin, `testFixturesImplementation(...)`, the `sourceSets { named("testFixtures") ... }` block, the `AdhocComponentWithVariants` lines | the file returns to `main` byte-for-byte (`git diff main -- continuum-commons/build.gradle.kts` empty) |
  | `continuum-loop-condition/build.gradle.kts`: `testImplementation(testFixtures(project(":continuum-commons")))` | no fixtures; add `testImplementation("tools.jackson.module:jackson-module-kotlin")` only if BE-21's table loader needs it |
  | `.../loopcondition/LoopConditionShapeGate.kt`, `LoopConditionLiteralReader.kt`, `CelConditionSyntax.kt`, `CelLoopConditionParser.kt`, `LoopConditionParsers.kt` | subset parser chain (replaced by BE-20) |
  | `.../loopcondition/LoopConditionRejections.kt`, `CelExpressionShapes.kt` | replaced by `LoopConditionIssues`, `CelAstWalk` (ADD) |
  | `continuum-loop-condition/src/test/.../LoopConditionParserGoldenTest.kt`, `CelLoopConditionParserRobustnessTest.kt` | subset tests (the robustness test is re-created for the validator in BE-21) |
  | `continuum-orchestration-service/src/main/kotlin/.../workflow/loop/LoopConditionRuntimeCheck.kt` (with `FlowVariableTypeClass`) and `src/test/.../workflow/loop/LoopConditionRuntimeCheckTest.kt` | CEL decides at run time |
  | `continuum-orchestration-service/src/test/.../workflow/fixtures/StructuredTestLoopConditionEvaluator.kt` | replaced by `ScriptedLoopConditionEvaluator` |
- ADD / REPLACE (contract, one responsibility per file, all engine-free in commons):
  - `validation/loop/LoopConditionLimits.kt`: `MAX_EXPRESSION_LENGTH` 2,048, `MAX_NESTING_DEPTH` 32,
    `MAX_PARSE_RECURSION_DEPTH` 100, `MAX_COMPREHENSION_ITERATIONS` 1,000, `MAX_COLLECTION_SIZE` 10,000,
    `MAX_STRING_LENGTH` 100,000, `MAX_BYTES_LENGTH` 100,000, `MAX_REGEX_PROGRAM_SIZE` 1,000 (planner default; the
    developer may raise it if a valid table row needs more, and says so), `VALUE_PREVIEW_LENGTH` 100.
  - `validation/loop/LoopConditionVariables.kt`: `object LoopConditionVariables { const val IDENTIFIER = "vars" }`
    (the kept `VARIABLES_IDENTIFIER`).
  - `validation/loop/LoopConditionIssueCode.kt`: enum with `reasonText` per code and `isRuntime`; the 12 codes of the
    contract table.
  - `validation/loop/LoopConditionValidator.kt`: `enum class LoopConditionResultType { BOOL, DYN }`,
    `sealed interface LoopConditionCheckResult { Valid(resultType, readVariables: Set<String>); Invalid(code, message, column: Int?) }`,
    `fun interface LoopConditionValidator { fun check(text: String): LoopConditionCheckResult }` (never throws).
  - `validation/loop/LoopConditionEvaluator.kt` (rewrite): `interface LoopConditionEvaluator { fun compile(text): CompiledLoopCondition; fun evaluate(compiled, variables: Map<String, Any?>): LoopConditionOutcome; fun warmUp() }`;
    `class CompiledLoopCondition(text, resultType, readVariables, engineHandle: Any?)` (no parsed triple);
    `sealed interface LoopConditionOutcome { True; False; NonBoolean(typeName, preview); Failed(code, message, variableName: String?) }`.
    KDoc states the purity rule (runs on the workflow thread) and that `evaluate` never throws.
  - `continuum-loop-condition/.../LoopConditionIssues.kt` (replaces `LoopConditionRejections`): builds
    `Invalid`/`Failed` messages (`reasonText`, CEL message, 1-based code-point column from a 0-based offset).
  - `continuum-loop-condition/.../CelAstWalk.kt` (replaces `CelExpressionShapes`): only the iterative
    `subExpressions(root)` and `directChildren(expr)`; no comparison, logical or literal helpers.
  - `LoopConditionTextPrecheck.kt` (KEEP, rewired): returns `LoopConditionCheckResult.Invalid?` with `EMPTY`,
    `TOO_LONG`, `TOO_DEEP` (was `SYNTAX`). `LoopConditionNestingDepth.kt` KEEP unchanged.
  - `continuum-orchestration-service/src/test/.../workflow/fixtures/ScriptedLoopConditionEvaluator.kt`: returns the
    next scripted `LoopConditionOutcome` per `evaluate` call (or a function of the variables), records the variable maps
    it saw; `compile` does not parse. `workflow/loop/LoopConditionEvaluators.kt` KEEP (holder; KDoc only if it names
    removed types).
  - `continuum-orchestration-service/src/test/.../spike/CelInWorkflowSpikeTest.kt`: rewrite the two comment lines that
    carry factory IDs (class KDoc: "cel-java used inside Temporal workflow code: evaluation and replay regression
    test"; engine KDoc: "one declared variable `vars: map(string, dyn)`; int and double compare by value"). No
    behaviour change.
- Unit tests: `continuum-loop-condition/src/test/.../LoopConditionTextPrecheckTest.kt` (blank, 2,048/2,049 code
  points incl. a surrogate pair, depth 32/33 with brackets in strings and comments ignored, 1-based columns);
  commons `validation/loop/LoopConditionIssueCodeTest.kt` only if `reasonText`/`isRuntime` need pinning (the 12 codes
  listed once). Every remaining test passes. The loop-condition module may have few tests until BE-20/BE-21.
- Docs (house rules 1-3, 13): `continuum-commons/AGENTS.md` `validation.loop` bullet rewritten (contract only; golden
  file, twin rule and test-fixtures sentences removed); `continuum-loop-condition/AGENTS.md` and `README.md` (class
  table reduced; "the validator and the evaluator follow"); `continuum-orchestration-service/AGENTS.md` (runtime-check
  bullet removed, evaluator seam returns an outcome, test evaluator renamed); root `AGENTS.md` Child DOX Index line of
  the module ("cel-java Loop condition validator and evaluator"); `docs/api/ENDPOINT_INVENTORY.md` (no golden/subset
  wording); release notes: the commons `validation.loop` line, the `continuum-loop-condition` parser line and the
  orchestration "runtime check" sentence rewritten for the new contract (no subset, no golden table, no test fixtures).
- Depends on: none (BE-4, BE-5, BE-13 are DONE). Can start now.
- Size: about 1,300 lines deleted, about 250 added; review it as a deletion with a small new contract.
- Covers: AC-41 (with BE-18's final grep), AC-11.

#### BE-20 `LoopCelEnvironment` and `CelLoopConditionValidator` (full-CEL validation rules)
- Repo/module: `continuum-loop-condition`.
- Files (create, `.../loopcondition/`), one unit per responsibility:
  - `LoopCelEnvironment.kt`: the one place that builds cel-java for loops (used by the validator here and the
    evaluator in BE-15). `CelOptions`: `enableHeterogeneousNumericComparisons(true)`, `enableOptionalSyntax(true)`,
    `maxParseRecursionDepth(100)`, `comprehensionMaxIterations(1000)`, `maxRegexProgramSize(...)`, from
    `LoopConditionLimits`. Standard macros ON. Extensions: `strings()`, `math(options, <latest>)`, `lists(<latest>)`,
    `sets(<latest>)`, `regex(<latest>)`, `encoders(options)`, `bindings()`, `optional(<latest>)`,
    `comprehensions(<latest>)` (pinned version numbers where the overload exists, see "Checked while planning"; the
    developer reads the latest supported version per library from the jar and names it as a constant); no `protos`,
    no `nativeTypes`. One declared variable `vars: map(string, dyn)`, empty container. API: `compile(text)` (parse +
    check, returns the checked AST or the first issue with its offset), `program(checkedAst)`, `functionNames()`
    (for the pin test). Immutable and thread-safe; built once (`LoopCelEnvironment.shared`).
  - `CelLoopConditionValidator.kt`: implements commons `LoopConditionValidator`; only chains the steps below in the
    D-26 order and catches `StackOverflowError`/`RuntimeException` from cel-java as `SYNTAX`. Never throws.
  - `ConditionResultTypeRule.kt`: V-6 `NOT_BOOLEAN` when the checked result type is neither `bool` nor `dyn`.
  - `CelLiteralChecks.kt`: V-7 `INVALID_LITERAL` through cel-java's `RegexLiteralValidator`,
    `TimestampLiteralValidator`, `DurationLiteralValidator` (the exact validator-runner factory name in 0.15.0 is NOT
    VERIFIED; check with `javap` on the cached jar before coding).
  - `ReboundNameRule.kt`: V-8 `REBOUND_NAME`, iterative walk (via `CelAstWalk`) over comprehensions with a scope
    stack: an iteration variable (`iterVar`, and `iterVar2` of two-variable comprehensions) or a `cel.bind` name that
    is already bound in an enclosing comprehension, or equals `vars`, is rejected with its column. Pitfall for the
    developer: macro expansions reuse an internal accumulator variable name in every comprehension (cel-java's
    `@result`/`__result__`); nested macros are legal, so the internal accumulator name must be ignored. Pin with
    `vars["a"].exists(x, vars["b"].exists(y, x == y))` valid and `vars["a"].exists(x, vars["b"].exists(x, x > 0))`
    rejected.
  - `ReadVariableNames.kt`: the statically read names: `vars["lit"]`, `vars.x`, `has(vars.x)`, `"lit" in vars`,
    `vars[?"lit"]`, `vars.?x`; a computed key adds nothing.
  - `LoopConditionValidators.kt`: `object LoopConditionValidators { val default: LoopConditionValidator }`
    (replaces `LoopConditionParsers`), the instance api-server injects and orchestration re-checks with.
- Change: no behaviour outside the module yet (BE-8 wires it).
- Unit tests (structural only; the table is BE-21): `LoopCelEnvironmentTest` (options in force: int vs double,
  optional syntax, macros on, `protos`/`nativeTypes` names absent; the function/macro name list equals a constant list
  in the test: the pin, AC-42); `ReboundNameRuleTest` (the cases above, `cel.bind(a, 1, cel.bind(a, a + a, a > 0))`,
  `cel.bind(vars, 1, true)`, `[1].all(vars, true)`, sibling re-use is valid); `ReadVariableNamesTest` (each form,
  dotted names, computed key); `CelLoopConditionValidatorTest` (one case per V-rule in order, the first failing rule
  wins: e.g. a 2,049-character syntax error reports `TOO_LONG`).
- Docs: module `AGENTS.md` (pipeline, environment contract: one factory for validator and evaluator, extensions and
  options are part of the contract, pinned versions, the cel.bind workaround and why) and `README.md` (class table,
  usage `LoopConditionValidators.default.check(text = ...)`); release notes (module section: full CEL validator, the
  enabled libraries, limits).
- Depends on: BE-19.
- Covers: AC-42, AC-13 (validator part).

#### BE-21 Backend CEL table, table-driven validator test, robustness test
- Repo/module: `continuum-loop-condition` (tests and test resources only).
- Files:
  - create `continuum-loop-condition/src/test/resources/loop-condition/cel-conditions.json` (format in the contract
    table, about 60 rows, D-35): valid rows for each operator family (`&& || ! ?:`, arithmetic inside a comparison,
    `in`, index and field selection), each macro (`has`, `all`, `exists`, `exists_one`, `map`, `filter`), each enabled
    extension with one call, optional syntax, timestamp and duration, dotted names, `has`/`in vars`, literal on the
    left, two variables, a `dyn` result (`vars["hasMore"]`), the D-36 examples; invalid rows: at least one per
    validation code including `now() > 1`, `foo == 1`, `vars2["x"]`, `"a" > 1`, `size(1) > 0`, `vars["p"] + 1`,
    `1 + 1`, `null`, `vars["x"] ? 1 : 2`, `vars["s"].matches("(")`, `timestamp("not-a-time") > vars["t"]`,
    `cel.bind(a, 1, cel.bind(a, a + a, a > 0))`, 33-deep nesting, 2,049 characters, blank. Evaluation entries
    (run by BE-15) for valid rows: missing variable, no matching overload, `/ by zero`, iteration budget, size
    guard, a non-boolean `dyn` result under both `onNonBooleanResult` values, `"s" == 1` false, `a || b` absorbing a
    missing variable. Row ids are descriptive (`and-of-two-variables`, `rebind-in-nested-bind`), not factory IDs.
  - create `.../loopcondition/CelConditionTable.kt` (test source: typed loader of the table, unique-id check).
  - create `.../loopcondition/LoopConditionValidatorTableTest.kt`: one dynamic test per row against
    `LoopConditionValidators.default` (code, resultType, readsVariables, column where given); table sanity: every
    validation code has a row, unique ids.
  - create `.../loopcondition/CelLoopConditionValidatorRobustnessTest.kt`: hostile 2,048-character inputs (deep
    parentheses, long `1+1+...` chain, long `a.a.a...` selection, long string of escapes, deep list literal, many
    `exists`) each return within 100 ms and never throw; depth 32 valid vs 33 `TOO_DEEP` with column;
    `StackOverflowError` caught (a forced overflow through a tiny-stack thread returns `SYNTAX`).
- Docs: module `AGENTS.md` Verification section (table test, robustness test, function-name pin); module README Tests
  section; release notes line in the module section (table replaces the golden table).
- Depends on: BE-20.
- Covers: AC-12, AC-13 (table part).

#### BE-3r Loop descriptor: `onNonBooleanResult`, full-CEL condition text, `LoopNode.doc.md`
- Repo/module: `continuum-commons`.
- Files:
  - modify `commons/node/builtin/LoopNodeDescriptor.kt`: `ON_NON_BOOLEAN_RESULT_PROPERTY = "onNonBooleanResult"`,
    `ON_NON_BOOLEAN_RESULT_VALUES = listOf("fail", "treat-as-false")`; schema property (title "When the condition is
    not true or false", description of both values), added to `required` and `defaultProperties` (`fail`); UI-schema
    control after `onLimit`. `condition` description: "Repeat while this CEL expression is true. Read flow variables as
    vars[\"name\"]; for example vars[\"hasMore\"] == true or vars[\"page\"] < 10 && vars[\"hasMore\"]." `nodeData.description`:
    "Repeats the nodes inside the box while a CEL condition over flow variables holds." The `maxLength` comes from the
    new `LoopConditionLimits`. Rewrite the KDoc line "(plan \"Contract\" table)" to name the rule ("the schemas are
    what the workbench dialog renders; change them only together with its condition renderer").
  - modify `continuum-commons/src/main/resources/.../node/builtin/LoopNode.doc.md`: intro line; "The condition"
    rewritten for full CEL (D-23, D-25: `vars["name"]` always, presence guards, CEL semantics notes, enabled libraries
    one line each, not available: `now()`, protos, user functions, limits, the D-36 examples); "Settings" gains
    `onNonBooleanResult`; "When a run fails" lists the D-29/D-31 cases (evaluation error, missing variable unless the
    result does not depend on it, cost limit, non-boolean with `fail`, limit with `fail`, invalid condition at start,
    history guard). The ceiling paragraph stays a placeholder for BE-17.
- Unit tests: `LoopNodeDescriptorTest`: defaults valid incl. `onNonBooleanResult: fail`; a bad `onNonBooleanResult` is
  one violation on that property; `required` and UI order include it after `onLimit`; doc mentions
  `onNonBooleanResult` and has no single-comparison wording (assert "one flow variable compared" is absent).
- Docs: commons `AGENTS.md` `node.builtin.LoopNodeDescriptor` bullet (new property and default); release notes commons
  descriptor line.
- Depends on: BE-19 (new `LoopConditionLimits`).
- Covers: AC-7 (schema part), AC-28 (doc part), contract rows "propertiesSchema", "propertiesUISchema".

#### BE-8 Validator: condition rule with the injected validator (resumed, rewritten)
- Repo/module: `continuum-commons`, `continuum-api-server`.
- Files:
  - create `commons/validation/loop/LoopConditionRules.kt` (focused collaborator; `WorkflowValidator` only
    delegates): for each loop whose `condition` passed the schema (string, 1..2,048), calls the injected
    `LoopConditionValidator`: `Invalid` → one ERROR (`node` = key, `property` = `condition`, contract text);
    `Valid` with empty `readVariables` → one W-1 warning (the only condition warning per loop). With no validator
    injected, the rule is skipped (commons callers without the module, e.g. tests of other rules).
  - modify `commons/validation/WorkflowValidator.kt`: constructor
    `WorkflowValidator(descriptors: NodeDescriptorSource, conditionValidator: LoopConditionValidator? = null)`; a few
    lines of delegation after property validation, reading the property value as validated (AUTHORING: the merged value
    of defaults and given properties; RUN: as sent). No condition issue when the schema already reported the property.
  - modify `api-server/service/WorkflowRunValidator.kt` and `api-server/mcp/WorkflowSpecValidator.kt`: pass
    `conditionValidator = LoopConditionValidators.default` (constructor parameter with that default, so tests can
    inject a fake).
  - fix `continuum-commons/src/test/.../validation/WorkflowValidatorLoopStructureTest.kt:66` KDoc ("the example
    workflow of the requirements" → "the paging example: seed, then fetch, parse and append inside the loop, then write").
- Unit tests: commons `validation/WorkflowValidatorLoopConditionTest.kt` with a fake `LoopConditionValidator`
  (scripted results; commons cannot depend on the module): one error with node/property and the message format; W-1
  for `Valid(readVariables = empty)`; no condition issue when the schema already failed (empty / too long); merged
  default condition used in AUTHORING; no validator → no condition issue; loop-free workflows unchanged. api-server
  `mcp/WorkflowSpecLoopTest.kt` and `controller/WorkflowControllerGraphValidationTest.kt` with the real validator: the
  AC-13 rejected list through `validateContinuumWorkflow` and `buildContinuumWorkflow` (`errors`), start 400, validate
  200 `valid:false`; the accepted list gives no condition error; `true` gives one W-1 warning.
- Docs: commons `AGENTS.md` WorkflowValidator line (condition rule, injected validator, W-1); api-server `AGENTS.md`
  start/validate and MCP rule lists; `docs/api/ENDPOINT_INVENTORY.md` start and validate sections (condition rule
  replaces the TODO, codes listed); release notes api-server client-visible validation errors (condition errors, W-1).
- Depends on: BE-20, BE-21 (real validator for the api-server tests), BE-3r (schema with the new property).
- Covers: AC-13, AC-14 (condition part), AC-11.

#### BE-9r MCP tool descriptions teach the CEL condition
- Repo/module: `continuum-api-server` (`mcp`).
- Files: modify `mcp/ContinuumWorkflowMcpTools.kt` `LOOP_RULES`: append D-36: "The loop's properties.condition is a
  CEL expression over vars (a map of the flow variables) that must end in true or false; write vars[\"name\"]
  (also for names with dots). properties.onNonBooleanResult (fail or treat-as-false) decides what a non-boolean
  result does. validateContinuumWorkflow is authoritative for the condition: fix what it reports." Tool names and
  parameters unchanged.
- Unit tests: `mcp/WorkflowSpecLoopTest.kt` description assertion extended (`vars[\"name\"]`, `onNonBooleanResult`,
  "authoritative"); still exactly six tools.
- Docs: api-server `AGENTS.md` MCP section; release notes MCP line.
- Depends on: BE-3r (property name). Can run with BE-8.
- Covers: AC-14 (descriptions).

#### BE-12r Pin the entry-order rule for the condition's variables (user-confirmed)
- Repo/module: `continuum-orchestration-service`.
- Files: add a case to `src/test/resources/flow-variables/loop-carry-cases.json` with two outside parents feeding two
  entry members (the earlier member in run order wins; same member: lower input port wins), update its SHA-256 pin in
  `FlowVariableLoopCarryCasesTest`; KDoc of `FlowVariableMerger.endOfIterationVariables` states the rule; no logic
  change expected (if the test shows otherwise, fix the ordering in place and report it).
- Docs: orchestration `AGENTS.md` flow-variable loop bullet (the entry-order rule); release notes line in the
  carried-variables entry.
- Depends on: none. Can start now.
- Covers: AC-21 (entry-order part).

#### BE-14 Loop execution in `WorkflowDagExecutor` (revised)
- Repo/module: `continuum-orchestration-service`.
- Units (create in `workflow/loop/`; the executor gets a thin delegation only):
  - `LoopRunState.kt`: per-loop state (`iteration`, phase `NOT_STARTED | ITERATING | FINISHED`, final status).
  - `LoopRunner.kt`: decides per pass which loops start, end an iteration, restart or finish (start rule, `before` /
    `after`, member failure, limit with `onLimit`, zero iterations) from `LoopTopology`, statuses and the outcome of a
    check. Pure apart from the injected collaborators; no `when` chain over policies (see below).
  - `LoopConditionCheck.kt`: one condition check: builds `endOfIterationVariables`, calls
    `LoopConditionEvaluators.current.evaluate` inside `Workflow.sideEffect(LoopConditionOutcome::class.java) { ... }`
    (the outcome, including failure messages, is recorded in history, D-30), returns the outcome. The `sideEffect`
    call is behind a small `fun interface WorkflowSideEffects` so `LoopRunner` tests need no Temporal.
  - `ConditionOutcomePolicy.kt`: maps a `LoopConditionOutcome` plus `onNonBooleanResult` to a loop decision
    (`Continue`, `Stop(SUCCESS)`, `Stop(WARNING, message)`, `Fail(message)`): `True` → continue; `False` → stop
    SUCCESS; `NonBoolean` → `fail` → Fail with the `NON_BOOLEAN_RESULT` text (type, preview ≤ 100 chars, iteration) |
    `treat-as-false` → Stop WARNING with "Condition returned <type> in iteration <k>; treated as false."; `Failed` →
    Fail with the message (loop, iteration, code, CEL message, column; variable name for `MISSING_VARIABLE`).
    The two `onNonBooleanResult` values are two small strategy objects selected by the property value (open/closed).
  - `LoopReset.kt`: reset of a loop's descendants and inner edges between iterations (as round 1 BE-14).
  - `LoopStatusPublisher.kt`: loop `data.status`/`nodeProgress` per the Contract "Run display" rows, `$variable` on
    finish, `$error` on failure.
  - modify `workflow/WorkflowDagExecutor.kt` (thin): build `LoopTopology` once, ask `LoopRunner` each pass, pass the
    `LoopReadinessGate` and carried maps; loop-free workflows take exactly the old path.
  - modify `workflow/WorkflowStatusReporter.kt` only through `WorkflowProgress` (members of completed iterations
    count proportionally; still monotonic).
  - fixtures: `test-workflows/loop-before-3member.cwf.json`, `loop-nested.cwf.json`; `FakeContinuumNodeActivity`
    per-invocation scripted flow variables and invocation counter.
- Unit tests: `workflow/loop/ConditionOutcomePolicyTest.kt` (every outcome x both settings, message texts, preview
  cap); `workflow/loop/LoopRunnerTest.kt` (decisions without Temporal, fake side effects); `workflow/ContinuumWorkflowLoopTest.kt`
  (Temporal test env with `ScriptedLoopConditionEvaluator`): AC-16 example, AC-17 zero and two iterations with
  `before`, AC-18 both limit policies, AC-19 member failure, AC-20 reset and nested 2 x 3, AC-21 carried variables end
  to end, AC-44 non-boolean under both settings (incl. `before` first pass), a scripted `Failed(MISSING_VARIABLE)`
  fails the run with the message (AC-22a message shape), AC-25 progress and iteration messages; one test asserts a
  side-effect marker per check in the history.
- Docs: orchestration `AGENTS.md` (loop execution contract, reset, statuses, outward-edge delivery, side effect per
  check, `onNonBooleanResult`), root `AGENTS.md` execution flow line, root README "How It Works" and the "Real loops
  coming" line, release notes Highlights + orchestration details.
- Depends on: BE-19 (outcome contract, scripted evaluator), BE-3r (property). (BE-10, BE-11, BE-12 DONE.)
- Size: over 300 lines; one reviewable unit split into the collaborators above.
- Covers: AC-16..AC-21, AC-22 (message shape with the scripted evaluator), AC-25, AC-44.

#### BE-15 Real evaluator: cel-java with cost guards, value conversion, warm-up, replay (revised)
- Repo/module: `continuum-loop-condition` (evaluator), `continuum-orchestration-service` (wiring, replay tests).
- Units:
  - `continuum-loop-condition/.../CelLoopConditionEvaluator.kt`: implements `LoopConditionEvaluator` on
    `LoopCelEnvironment.shared`; `compile` validates first (`LoopConditionValidators.default`) and keeps the program as
    `engineHandle`; `evaluate` converts variables, runs `program.trace(vars, sizeGuard)`, maps the result to
    `True`/`False`/`NonBoolean` and every `CelEvaluationException`, `StackOverflowError` and guard abort to `Failed`.
    Never throws.
  - `LoopConditionSizeGuard.kt`: the `CelEvaluationListener` that throws a guard exception when an intermediate
    list/map exceeds 10,000 elements or a string/bytes value exceeds 100,000 (D-28); `LIMIT_EXCEEDED` names the limit.
  - `FlowVariableCelValues.kt`: JSON flow variables → CEL values (Integer/Long → Long, Float/Double/BigDecimal →
    Double, Boolean, String, List, Map, null → CEL null), iterative, `vars` as a key-sorted map (D-30).
  - `CelEvaluationFailures.kt`: maps cel-java error codes to `MISSING_VARIABLE` (with the variable name from the
    message, matched against `readVariables`), `LIMIT_EXCEEDED` (iteration budget, size guard), `EVALUATION_ERROR`
    (rest), with the 1-based column.
  - `ValuePreview.kt`: type name and a preview capped at 100 characters for `NonBoolean`.
  - `continuum-orchestration-service/.../config/LoopConditionEngineConfig.kt`: `@Component` that installs
    `CelLoopConditionEvaluator` into `LoopConditionEvaluators` and calls `warmUp()` at bean start (compile and evaluate
    a fixed condition, outside workflow code).
- Unit tests: `continuum-loop-condition/src/test/.../CelLoopConditionEvaluatorTableTest.kt` runs every `evaluations`
  entry of the backend CEL table (AC-22 semantics, AC-44 values); `CelLoopConditionEvaluatorRobustnessTest.kt` runs the
  hostile evaluation list of AC-43 (each < 100 ms, `LIMIT_EXCEEDED`, never throws); `FlowVariableCelValuesTest`
  (types, nesting, key order); the AC-42 shared-environment test (validator and evaluator report the same function
  names). Orchestration `workflow/ContinuumWorkflowLoopReplayTest.kt`: AC-16 with the real evaluator and condition
  `vars["page"] < 3 && vars["hasMore"]`, fetch history, replay with `WorkflowReplayer`; replay with a different
  installed evaluator still passes (recorded side effect); `ContinuumWorkflowLoopTest` cases re-run with the real
  evaluator (parameterized).
- Docs: module `AGENTS.md` + README (evaluator, guards, conversion, never throws, why `trace`); orchestration
  `AGENTS.md` (engine installed at start, warm-up, replay test); release notes (module + orchestration).
- Depends on: BE-20, BE-21, BE-14.
- Covers: AC-26, AC-42, AC-43, AC-22, AC-44 (real engine).

#### BE-16 History guard and defensive check at loop start (revised)
- Repo/module: `continuum-orchestration-service`.
- Units: `workflow/loop/LoopHistoryGuard.kt` (as round 1: 40,000 events / 40 MiB constants, constructor override for
  tests); `workflow/loop/LoopStartCheck.kt`: at loop start re-checks the condition with an injected
  `LoopConditionValidator` (production: `LoopConditionValidators.default`) and the settings `maxIterations`, `onLimit`,
  `onNonBooleanResult`, `check` against the descriptor values; any problem fails the loop with the reason (no exception
  that would fail the workflow task). `LoopRunner` calls both.
- Unit tests: `ContinuumWorkflowLoopTest` new cases: guard with a tiny threshold fails the loop under
  `stop-with-warning` (AC-23); runs started through the Temporal client with an invalid condition, and with
  `onNonBooleanResult: "maybe"`, fail cleanly with the reason, no task retry loop (AC-27); `LoopStartCheckTest`.
- Docs: orchestration `AGENTS.md`; release notes.
- Depends on: BE-14, BE-20.
- Covers: AC-23, AC-27.

#### BE-17 Budget calibration incl. the condition check, validator warning, documented ceiling (revised)
- Repo/module: `continuum-orchestration-service` (calibration test), `continuum-commons` (warning, doc).
- Units: `orchestration/.../workflow/ContinuumWorkflowLoopHistoryBudgetTest.kt` measures events per member execution
  AND per condition check (side effect) for a 1-member and a 3-member loop over 10 iterations with the real evaluator;
  asserts both stay within ±20 % of the constants. `commons/validation/loop/LoopBudget.kt`:
  `EVENTS_PER_MEMBER_EXECUTION`, `EVENTS_PER_CONDITION_CHECK`, `MAX_HISTORY_EVENTS` (same value as the guard), and a
  pure `exceeds(maxIterations, memberCount)` using `maxIterations x (members x eventsPerMember + eventsPerCheck)`.
  `commons/validation/loop/LoopBudgetRule.kt`: the warning (`property = maxIterations`), called by `WorkflowValidator`
  (one delegation line). `LoopNode.doc.md`: the measured ceiling.
- Unit tests: the budget test; `LoopBudgetRuleTest` (over/at the budget); `WorkflowValidatorLoopStructureTest` one
  end-to-end warning case.
- Docs: release notes Upgrade notes (real ceiling), commons and orchestration `AGENTS.md`.
- Depends on: BE-15, BE-16, BE-8 (validator delegation order).
- Covers: AC-24, AC-28 (ceiling part).

#### BE-22 Retry with backoff for the built-in Loop registration at start-up (new, user decision)
- Repo/module: `continuum-orchestration-service` (`registration`).
- Units:
  - `registration/RegistrationRetryPolicy.kt`: data class `maxAttempts = 5`, `initialDelay = 5 s`,
    `multiplier = 2.0`, `maxDelay = 60 s` (delays 5, 10, 20, 40 s ≈ 75 s waiting plus send time, inside the "about 2
    minutes" the user asked for); pure `delayBeforeAttempt(n)`. Code constants (house minimal-config rule: no yaml).
  - `registration/BuiltinNodeRegistrationSender.kt`: one send of one definition (today's body of `publish`): builds the
    request, sends with the Kafka key, returns `Sent | NotAccepted | Failed(exception)`; never throws.
  - `registration/RetryingRegistration.kt`: runs the attempts for one definition with a `RetryScheduler`
    (`fun interface`, production = a single-thread `ScheduledExecutorService` named `builtin-node-registration`,
    tests = an immediate/virtual-time fake); logs each failed attempt at WARN and the final one at ERROR with node id
    and attempt count; stops at the first success.
  - modify `registration/BuiltinNodeRegistrationPublisher.kt` (thin): `@EventListener(ApplicationReadyEvent)` schedules
    one `RetryingRegistration` per definition and returns at once; `@PreDestroy` shuts the executor down (pending
    retries stop). One worker id per start, reused by every attempt.
- Unit tests (bug-style: write the failing-sender test first, it fails against today's single-shot publisher):
  `registration/RetryingRegistrationTest.kt` with a sender that fails N times (exception and `false`): succeeds on
  attempt N+1 when N < 5; exactly 5 attempts then one ERROR log when it always fails; delays follow the policy; no send
  after success; shutdown cancels pending attempts. `BuiltinNodeRegistrationPublisherTest` updated: the event handler
  returns without blocking on a failing sender (fake scheduler), payload assertions unchanged.
- Docs: orchestration `AGENTS.md` built-in registration bullet (retry policy, background thread, shutdown);
  `docs/feature-registration-system.md` orchestrator producer section; release notes orchestration line and the
  Upgrade note ("a failed registration is retried for about 2 minutes; restart the service if the schema registry was
  down longer").
- Depends on: none (BE-6 DONE). Can start now.
- Covers: AC-5.

#### BE-18 Closeout (revised)
- As round 1, plus: final factory-ID grep of every changed/untracked file in the repo
  (`git ls-files -m -o --exclude-standard | xargs grep -n -E '\b(BE|FE|AC|D|PR|SCOPE|P|Q|V|W)-[0-9]+\b'`, expect 0;
  the V-/W- rule labels are factory names too: code says `TOO_DEEP`, "no flow variable read" etc.); grep for stale
  subset words (`golden`, `SUPPORTED_SHAPE`, `LoopConditionRejection`, `LoopConditionParser`, `one flow variable`,
  `RuntimeCheck`) in docs, release notes and KDoc; `git diff main -- continuum-commons/build.gradle.kts` empty; runtime
  classpath reports of commons, worker-starter, message-bridge and knime-base equal `main`; the six-module test run;
  Redocly lint only if the spec changed (not expected this round).
- Depends on: all BE tasks of both rounds.
- Covers: AC-28, AC-41, AC-11.

### Frontend tasks (round 2; all still HOLD)

Still on HOLD (user). When released they run in the GitHub Codespace (Claude Code offload, see `state.md`), so every
task is self-contained: repo `continuum-workbench`, branch `CONTNM-180` created from `origin/main` before the first
change, paths relative to `workflow-editor-extension/` unless stated. The round-1 shared rules for FE tasks stand
(DOX, release notes `release_notes/RELEASE_NOTES_v0.0.16.md`, pure logic in `src/common/` with a `*.spec.ts`, React
components thin, house rules 12 and 13). Backend contract source: `continuum-platform-core` `CONTNM-180` pulled in
the codespace after the user pushed it (BE complete), replacing the copied spec snapshot.

**P-2 (changed; first FE step in the codespace, with the user's consent there):** add `react-querybuilder` to
`workflow-editor-extension/package.json` in a version that exports `parseCEL` (the parser entry point; exact import
path, e.g. `react-querybuilder/parseCEL`, and minimum version are NOT VERIFIED offline: read them from the installed
package before FE-10a) and `formatQuery(query, 'cel')`; regenerate `yarn.lock` with `yarn install` (CI runs
`--frozen-lockfile`). `@react-querybuilder/material` only if the user wants MUI styling. Blocks: FE-3, FE-10a, FE-10b
and the builder half of FE-8. Does not block: FE-1, FE-2, FE-4, FE-5, FE-6, FE-7, FE-8 CEL mode, FE-9, FE-11.

Unchanged from round 1: FE-2, FE-6, FE-7. Small changes: FE-1 (`ILoopProperties` gains `onNonBooleanResult: 'fail' |
'treat-as-false'`; the spec is unchanged since round 1, so regeneration only confirms it), FE-4 (copy the
`loop-carry-cases.json` from the BE-12r state and pin its new SHA-256 from `03-backend-dev.md`; mirror the entry-order
rule), FE-9 (show the round-2 messages: treated-as-false WARNING, `$error` texts), FE-12 (release notes: `react-querybuilder`
dependency, full CEL, two-mode dialog).

#### FE-3 `LoopConditionQuery.ts`: the RQB adapter and the round-trip table (rewritten; replaces the hand-written parser)
- Units (`src/common/`):
  - `LoopConditionQuery.ts`: pure adapter, the only non-UI file that imports `react-querybuilder`:
    `toCel(query): string` (via `formatQuery(query, 'cel')` with the field mapping), `fromCel(text): {fits: true, query} | {fits: false, reason}`
    (via `parseCEL`), `fits(text)`; field mapping name ↔ `vars["name"]` (CEL string escapes, dotted names); fit test
    of D-33: parse succeeds, every field maps to `vars["<string>"]`, every operator is in `LOOP_BUILDER_OPERATORS`,
    and `toCel(fromCel(text))` equals the text after whitespace normalisation. If FE-10a shows `parseCEL` cannot map
    `vars["x"]`, add the placeholder swap (string-literal-aware tokenizer) inside this file, nowhere else.
  - `LoopBuilderOperators.ts`: the allowed operators (`= != < <= > >=`, contains / begins with / ends with, `in` /
    `not in`, null / not null) as data, no RQB import.
  - `loop-condition-builder-cases.json`: the round-trip table (`{id, text, fits, query?, emitted?}`, D-35): builder →
    text for single rules, nested and/or groups, `not`, each operator, escapes and dotted names; text → fits / not for
    macro, `has`, `in vars`, arithmetic, function, variable-to-variable, optional syntax. Ids are descriptive.
  - `LoopConditionQuery.spec.ts`: runs every row.
- Consumes: no backend artifact (the backend validates). Cross-check output: the list of `emitted` texts, reported in
  `04-frontend-dev.md` as `SCOPE:` rows for a small backend follow-up that adds them to the backend CEL table as
  `source: "builder"` valid rows (D-35).
- Docs: root `AGENTS.md` (the adapter is the only place that knows the RQB ↔ CEL mapping; the backend is authoritative).
- Depends on: FE-10a (pins RQB behaviour), P-2. Blocked until: P-2 done. Backend: none.
- Covers: AC-29, AC-40.

#### FE-5 `LoopNode` component (revised: badge dropped)
- As round 1, without the warning badge and without any condition parsing: the header shows the condition text,
  truncated (pure `src/common/LoopPresentation.ts`: `conditionSummary(text, maxLength)`, status/iteration text).
- Depends on: FE-1, FE-2 (no longer FE-3). P-2: not needed.
- Covers: AC-32 (box/resize), AC-37 (box part). AC-36 withdrawn.

#### FE-8 Loop properties dialog: query renderer with two modes and a toggle; CEL mode first (rewritten)
- Units:
  - `src/common/LoopConditionDialogState.ts` (pure, no RQB import): mode decision (`initialMode(text, fits)`),
    toggle rules (builder → CEL always; CEL → builder only when `fits`; disabled reason text), "replace with a new
    builder condition" confirmation step, debounce bookkeeping, and `conditionIssuesFor(response, loopId)` that picks
    the `validateWorkflow` issues with `node` = loop and `property` = `condition` (message, column) or returns
    "Not checked" on a transport error. `fits` is injected as a function, so the file compiles and its spec runs
    without P-2. Spec beside it.
  - `src/browser/components/node-dialog/loop-condition/useConditionValidation.ts`: hook that debounces (~500 ms) and
    calls `workflowsApi.validateWorkflow(...)` (`src/browser/service/ApiClients.ts`) with the current workflow and the
    edited condition; returns issues or "Not checked".
  - `.../loop-condition/CelConditionEditor.tsx`: CEL mode, reusing the Monaco control of `CodeEditorRenderer.tsx`
    (`MonacoEditorWrapper`), plus the issue list.
  - `.../loop-condition/QueryConditionRenderer.tsx`: JSONForms control, tester
    `rankWith(10, uischema => isControl(uischema) && uischema.options?.format === 'query')` (same pattern as
    `codeEditorTester`); holds the mode, renders `CelConditionEditor` or the builder slot (FE-10b), the toggle and the
    replace action; text never dropped silently; saving not blocked.
  - modify `src/browser/components/node-dialog/NodeDialog.tsx`: register the renderer in `customRenderers`; hide the
    Retry Policy tab for a Loop (new prop `isLoop`, set in `components/workflow-editor/WorkflowEditor.tsx`), no retry
    options on save. The dialog needs the current workflow for validation: pass it as a prop (or a JSONForms config
    entry), not a global.
- The `onNonBooleanResult` control renders from the schema (enum select); check it appears after `onLimit`.
- Depends on: FE-1 (types). CEL mode: no P-2 and no FE-3; the builder slot stays empty until FE-10b. Backend: needs a
  reachable api-server with BE-8 for the live issue list (browser test), not for coding.
- Covers: AC-35 (CEL-mode part, toggle-disabled part, "Not checked", Retry Policy, `onNonBooleanResult`).

#### FE-10a Spike: pin React Query Builder's CEL behaviour (new; first task after P-2)
- Units: a throw-away probe under `.factory/` or a `mktemp -d` dir (not in the repo), then the findings as rows of
  `src/common/loop-condition-builder-cases.json` (FE-3 owns the file; the spike seeds it).
- Pin: how `parseCEL` represents `vars["x"]` and `vars.x`; whether it drops or reports unrepresentable parts; the exact
  `formatQuery(..., 'cel')` text (quoting, parentheses, `!` for `not`, `in`, `contains`/`startsWith`/`endsWith`, null);
  the import path and version. Decide: direct mapping or the placeholder swap (D-33 contingency).
- Output: `04-frontend-dev.md` findings and the `SCOPE:` list of emitted strings for the backend table.
- Depends on: P-2.
- Covers: precondition of AC-29, AC-34.

#### FE-10b React Query Builder in the query renderer (rewritten: groups, combinators, `not`)
- Units: `.../loop-condition/LoopConditionBuilder.tsx` (RQB `QueryBuilder` with groups, `and`/`or`, group `not`;
  fields = free-text variable names, suggestions from known flow variables optional; operators from
  `LoopBuilderOperators.ts`; typed value editors: boolean, number, text); emits through FE-3's `toCel` on every
  change; mounted in the FE-8 builder slot. MUI styling per the repo's MUI v5 conventions
  (`@react-querybuilder/material` only if P-2 added it). The only UI file importing `react-querybuilder`.
- Unit tests: mapping and emit covered by FE-3's spec; mode/toggle by FE-8's spec.
- Docs: release notes Upgrade notes (new dependency, `yarn.lock` changed), root README dependencies if listed.
- Depends on: FE-3, FE-8, P-2.
- Covers: AC-34, AC-35 (toggle-to-builder part), AC-40.

#### FE-11 Workflow-builder prompt twins teach full CEL (rewritten)
- Files: `workflow-builder-extension/claude-code/workflow-builder.md` and
  `workflow-builder-extension/src/browser/WorkflowBuilderPrompt.ts`, same loop paragraph (D-36): loops as `nodes[]`
  entries with nodeId `org.projectcontinuum.core.builtin.Loop` and `properties {condition, maxIterations, onLimit,
  onNonBooleanResult, check}`; members set `parent`; edges never name a loop; `vars["name"]` always; presence guards;
  CEL semantics notes; enabled libraries one line each and what is not available; the limits; the six examples;
  a member must set the variables the condition reads; fix what `validateContinuumWorkflow` reports; keep "each input
  port accepts one edge", "never write workflow JSON yourself" and the "one node per build" flow (loop before its
  members). `workflow-builder-extension/README.md` if it describes the rules.
- Consumes: BE-9r description text and BE-8 messages (from `03-backend-dev.md`).
- Depends on: BE-9r, BE-8. Blocked until: both DONE. P-2: not needed.
- Covers: AC-39.

### Dependency graph (round 2)

Edges (authoritative for the remaining work; no cycles): BE-19→BE-20, BE-20→BE-21, BE-19→BE-3r, BE-3r→BE-9r,
BE-20→BE-8, BE-21→BE-8, BE-3r→BE-8, BE-19→BE-14, BE-3r→BE-14, BE-14→BE-15, BE-20→BE-15, BE-21→BE-15, BE-14→BE-16,
BE-20→BE-16, BE-8→BE-17, BE-15→BE-17, BE-16→BE-17, BE-12r→BE-18, BE-22→BE-18, BE-*→BE-18;
BE-12r→FE-4, BE-14→FE-9 (live check), BE-8→FE-8 (live check), BE-9r→FE-11, BE-8→FE-11, P-2→FE-10a, FE-10a→FE-3,
P-2→FE-3, FE-1→FE-8, FE-3→FE-10b, FE-8→FE-10b, FE-1→FE-5, FE-2→FE-5, FE-*→FE-12. Round-1 edges among FE-2, FE-4,
FE-5, FE-6, FE-7, FE-9 stand; BE-4→FE-3 and FE-3→FE-5 are removed.

**Can start now (next backend round):** BE-19 (the clean-up; first), BE-22 (registration retry, independent),
BE-12r (entry-order pin, independent). Then BE-20 and BE-3r (in parallel after BE-19), BE-14 (after BE-19 + BE-3r;
it does not need the engine), BE-21, then BE-8 and BE-9r, then BE-15, BE-16, then BE-17, then BE-18.

Suggested order: BE-19 | BE-22 | BE-12r → BE-20 | BE-3r → BE-21 | BE-14 | BE-9r → BE-8 → BE-15 → BE-16 → BE-17 →
BE-18.

Frontend (on HOLD; when released in the codespace): FE-1, FE-2, FE-4, FE-11 can start at once (their BE inputs will
be DONE by then); P-2 first if the user consents, then FE-10a → FE-3 → FE-10b; FE-8 CEL mode needs only FE-1.

### Test plan (round 2; rows replace the round-1 rows with the same AC)

| AC | Verifier | Test location (names describe behaviour; no IDs) |
|----|----------|---------------|
| AC-5 | backend-developer | orchestration `registration/RetryingRegistrationTest.kt`, `registration/BuiltinNodeRegistrationPublisherTest.kt` |
| AC-6 | backend-system-tester | `continuum-system-tests/suites/api/loops.js` (unchanged; registry entry after start) |
| AC-7 | backend-developer; backend-system-tester | commons `node/builtin/LoopNodeDescriptorTest.kt`, `validation/WorkflowValidatorLoopStructureTest.kt`; `suites/api/loops.js` (bad `onNonBooleanResult`) |
| AC-12 | backend-developer | loop-condition `LoopConditionValidatorTableTest.kt` over `src/test/resources/loop-condition/cel-conditions.json` |
| AC-13 | backend-developer; backend-system-tester | loop-condition `CelLoopConditionValidatorTest.kt`; commons `validation/WorkflowValidatorLoopConditionTest.kt`; api-server `controller/WorkflowControllerGraphValidationTest.kt`, `mcp/WorkflowSpecLoopTest.kt`; `suites/api/loops.js` (start 400 / validate 200 `valid:false` with a rejected condition, accepted full-CEL condition valid) |
| AC-14 | backend-developer | api-server `mcp/WorkflowSpecLoopTest.kt`, `mcp/WorkflowSpecBuilderTest.kt` |
| AC-21 | backend-developer | orchestration `workflow/FlowVariableLoopCarryCasesTest.kt` (new two-parent case), `ContinuumWorkflowLoopTest.kt` |
| AC-22 | backend-developer | loop-condition `CelLoopConditionEvaluatorTableTest.kt` (table `evaluations`); orchestration `ContinuumWorkflowLoopTest.kt` (real evaluator, parameterized) |
| AC-23, AC-27 | backend-developer | orchestration `ContinuumWorkflowLoopTest.kt`, `workflow/loop/LoopStartCheckTest.kt` |
| AC-24 | backend-developer | orchestration `ContinuumWorkflowLoopHistoryBudgetTest.kt`; commons `validation/loop/LoopBudgetRuleTest.kt` |
| AC-26 | backend-developer | orchestration `workflow/ContinuumWorkflowLoopReplayTest.kt` |
| AC-28 | backend-expert, frontend-expert | review: release notes, `LoopNode.doc.md`, READMEs, AGENTS.md, inventory (no subset wording) |
| AC-29 | frontend-developer (codespace) | `src/common/LoopConditionQuery.spec.ts` over `loop-condition-builder-cases.json` |
| AC-34, AC-35 | frontend-tester (codespace) | browser scenarios "builder condition with nested groups reopens in the builder", "CEL-mode condition keeps its text, toggle disabled with reason, replace asks first", "validation issues shown after typing; Not checked when api-server is down", "Retry Policy hidden, onNonBooleanResult shown"; builder parts need P-2 |
| AC-39 | frontend-expert | review: diff of the loop paragraphs of both prompt twins |
| AC-40 | frontend-expert | review: `grep -rn react-querybuilder workflow-editor-extension/src` hits only `LoopConditionQuery.ts` and `loop-condition/LoopConditionBuilder.tsx` |
| AC-41 | backend-developer (grep + build reports); backend-expert | BE-18 closeout commands in `03-backend-dev.md` |
| AC-42 | backend-developer | loop-condition `LoopCelEnvironmentTest.kt` (function-name pin), evaluator/validator shared-environment test |
| AC-43 | backend-developer | loop-condition `CelLoopConditionEvaluatorRobustnessTest.kt`, `CelLoopConditionValidatorRobustnessTest.kt` |
| AC-44 | backend-developer | orchestration `workflow/loop/ConditionOutcomePolicyTest.kt`, `ContinuumWorkflowLoopTest.kt` (scripted and real evaluator) |

System-test fixtures: `continuum-system-tests/fixtures/workflows/loop-invalid-condition.json` uses a full-CEL rejection
(e.g. `now() > 1`) instead of a subset one; `loop-valid.json` uses `vars["page"] < 10 && vars["hasMore"]`.

### Not verifiable locally (round 2)

- Everything workbench (FE-1..FE-12): workbench builds are forbidden on this machine (local policy) and
  `node_modules` is absent; they run in the codespace when the user releases FE. Specs, `tsc`, `yarn build` and the
  browser scenarios are NOT VERIFIED here.
- P-2: React Query Builder's `parseCEL` / `formatQuery('cel')` behaviour, import path and minimum version are NOT
  VERIFIED (not installed anywhere on this machine); FE-10a pins them in the codespace. The `yarn.lock` change is a
  user action (or a codespace step with the user's consent).
- The backend ↔ builder cross-check (every RQB `emitted` text is a valid backend row) can only be closed after FE-10a;
  it needs a small backend follow-up then.
- cel-java 0.15.0 details the developer must confirm with `javap` before coding (cached jar, offline): the factory
  that runs the literal validators, the latest version number per extension library, and the internal accumulator
  variable name of macro expansions (for the re-bound-name rule).
- Exact history events per `Workflow.sideEffect` on a real Temporal server (BE-17 measures it on the test server only).
- Live stack checks (AC-6, AC-7/AC-13 over HTTP, AC-16 live): need `make up` in `continuum-system-tests` with locally
  built 0.0.23 images (Jib to the local daemon, base images cached: NOT VERIFIED). Otherwise NOT VERIFIED.
- The registration retry against a real broker / schema registry outage (BE-22 is unit-tested with a failing sender
  only).
- `continuum-knime-base` compile/test: pre-existing failure on `main` (Spring Boot 3.4.0 BOM vs Kotlin 2.2); only its
  dependency report can be compared.
- Redocly lint may be refused (`npx`); not needed unless the spec changes (no REST change is planned this round).

### Requirements not mapped / deviations (round 2)

- D-35 "cross-check" is split: FE-10a/FE-3 produce the emitted strings, a backend follow-up (not yet a task, raised
  as `SCOPE:` by the frontend) adds them to the backend table. Nothing can be planned concretely before P-2.
- V-1..V-8 and W-1 are requirement labels only; code and messages use the issue codes and plain texts.
- The cap values of D-28 are planner defaults in `LoopConditionLimits` (regex program size 1,000 is the one value the
  requirements left open).

### Changelog

- 2026-11-25 Round 2 (full CEL): appended this section. New tasks BE-19 (clean-up and full-CEL contract), BE-20
  (environment and validator), BE-21 (backend CEL table and robustness), BE-22 (registration retry), BE-3r, BE-9r,
  BE-12r; BE-8 resumed and rewritten; BE-14..BE-18 revised; FE-3, FE-5, FE-8, FE-11 rewritten, FE-10 split into
  FE-10a (spike) and FE-10b; ACs rewritten (AC-12, AC-13, AC-22, AC-26, AC-29, AC-34, AC-35, AC-39, AC-40), amended
  (AC-5, AC-7, AC-14, AC-21, AC-24, AC-27, AC-28), withdrawn (AC-36) and added (AC-41..AC-44). Entry-order rule and
  registration retry recorded as settled user decisions.
