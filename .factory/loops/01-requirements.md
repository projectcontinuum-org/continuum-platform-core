# Loops: while-loop scope around member nodes (platform core + workbench)
Status: READY FOR PLANNING

## User story
As a workflow author I want to draw a loop box around a group of nodes, set a condition on one flow variable plus a
max-iterations guard, and have the platform re-run those nodes until the condition stops holding, so that I can page
through an API, poll for a result or retry a step without copying nodes or leaving the DAG model.

User-visible outcome: a "Loop" appears in the node palette. Dropped on the canvas it is a resizable, collapsible box.
Nodes inside it repeat. A run shows the loop's progress ("Iteration 3 of 50"). An AI agent (MCP tools, Claude Code
skill, Theia prompt) can build and validate loop workflows with the same six tools as today.

Worked example: `Set seed -> [Fetch page -> Parse page -> Append rows] -> Write table`, loop "Fetch until done", while
`hasMore == true`, max 50. `hasMore` is a flow variable that "Fetch page" (or "Parse page") sets, not a Parquet column.

## Context - what exists today (verified in code)

Execution (`continuum-platform-core/continuum-orchestration-service`)
- The DAG runs in `workflow/WorkflowDagExecutor.kt:run`: a `do { ... } while (hasReadyNodes || promises.isNotEmpty())`
  loop, `NodeReadiness.getReadyNodes` / `getNodesToSkip` decide per pass, `Async.function` dispatches the activity,
  `Promise.anyOf` waits for one. A node is "untouched" only while `data.status == null` and it has no entry in
  `nodeToOutputsMap` / `nodeErrorsMap` (`NodeReadiness.kt:isUntouched`). So today a node can run exactly once per run;
  there is no way to reset a node. `NodeReadiness.toleratesBrokenEdge` is an unused extension point.
- `WorkflowActivityInitializer.initializeNodeActivityStubs` requires a task queue for EVERY node
  (`activityMap.size != nodes.size` throws `InitializationFailed`), and `InitializeActivity` retries until the api-server
  returns a queue for every node id. A node "not backed by any activity" is therefore rejected today.
- `WorkflowStatusReporter` counts progress over all nodes (`doneNodeCount`), so a Loop node must be excluded or counted.
- Flow variables: `FlowVariableMerger.collectInputVariables` walks ancestors through edges; fan-in "first value wins by
  input port index". Mirrored by `continuum-workbench/workflow-editor-extension/src/common/FlowVariables.ts`
  (`inheritedFlowVariables`) and its spec. Default variable names contain a dot (`restNode_3.durationMs`,
  `DefaultFlowVariables.kt`), so a CEL identifier cannot name them directly (see risks).
- No CEL anywhere: no source, build file or doc in any repo mentions CEL. The Gradle cache holds only
  `org.projectnessie.cel` BOM/parent POMs (transitive noise), no cel-java jar, so `--offline` cannot resolve a CEL engine.

Model and validation
- `commons/model/ContinuumWorkflowModel.kt`: `Node(id, type, position, data: NodeData, width, height, selected, ...)`.
  `type` is the React Flow type (`"BaseNode"`); the registered node id is `data.nodeModel`. There is NO `nodeType` field and
  no parent/membership field. `NodeStatus` already has `WARNING` (usable for "stopped at the limit"). `NodeStatus` is
  mirrored by `continuum-core/src/types/IBaseNode.ts`; a new enum value breaks consumers built on older commons.
- `commons/validation/WorkflowValidator.kt` (shared by MCP and run start): unique keys, node must be registered
  (`NodeDescriptorSource`), edge endpoints/ports, one edge per input, Kahn cycle check over real nodes, properties against
  the REGISTERED JSON Schema (draft 7, `ValidationHelper`). Reused as is, a Loop with no ports would pass the port checks
  only if it is registered; edges to or from a Loop key would fail as "port does not exist".
- Node registry: workers publish `FeatureRegistrationRequest` (Avro, Kafka) at `ApplicationReadyEvent`
  (`worker-springboot-starter/.../FeatureRegistrationPublisher.kt`), `continuum-message-bridge` upserts it
  (`NodeTreeSyncService`), the api-server reads it (`NodeExplorerService.getNodeMetadata/getTaskQueues`, cached 30 s). The
  orchestration service does not register anything today; it has Kafka (Spring Cloud Stream) for status events only.
- MCP: `api-server/.../mcp/` has exactly six tools in `ContinuumNodeMcpTools` (4) and `ContinuumWorkflowMcpTools` (2).
  `McpWorkflowSpec.kt` (spec types), `WorkflowSpecValidator` (thin adapter over `WorkflowValidator`, AUTHORING mode),
  `WorkflowSpecBuilder` (registry NodeData + properties merge, layout by longest-path depth 330 x 160, edge ids
  `reactflow__edge-{from}{fromPort}-{to}{toPort}`, `type` `BaseNode`).

Workbench (`continuum-workbench`)
- `WorkflowEditor.tsx`: `nodeTypes = { BaseNode }`; `isValidConnection` + `hasCycle` use `getOutgoers` on real nodes.
  `WorkflowEditorWidget.tsx:331` creates every dropped palette node with `type: "BaseNode"`. `WorkflowViewer.tsx` has its own
  `nodeTypes`. `WorkflowDocument`/`.cwf` is the React Flow node list as is.
- `NodeDialog.tsx` uses JSONForms 3.7.0 with custom renderers selected by `uischema.options.format`
  (`CodeEditorRenderer` for `'code'`, `CredentialRenderer` for `'credential'`). It always shows a Retry Policy tab.
- `react-querybuilder` is not in any `package.json` or `yarn.lock` of the workspace or the playground. `node_modules` is
  absent in the workbench (policy: no installs or builds).
- `workflow-builder-extension/claude-code/workflow-builder.md` and `src/browser/WorkflowBuilderPrompt.ts` are twins; both
  currently say "each input port accepts one edge", "never write workflow JSON yourself" and know nothing of containers.

## Feasibility

| Part | Verdict | Evidence | Notes |
|------|---------|----------|-------|
| Loop as `ContinuumWorkflowModel` node with a Loop React Flow type | FEASIBLE WITH CHANGES | `ContinuumWorkflowModel.kt` Node | Needs an optional membership field (React Flow `parentNode`) and the loop `type`. "nodeType" is read as `Node.type = "Loop"` plus a reserved built-in `data.nodeModel`; no new `nodeType` JSON field (assumption A-1). |
| Registered by the orchestrator, no activity | FEASIBLE WITH CHANGES | `FeatureRegistrationPublisher`, `WorkflowActivityInitializer`, `InitializeActivity`, `NodeExplorerService.getTaskQueues` | Orchestrator needs a registration publisher; initializer/readiness/progress must skip Loop nodes; task-queue lookup must not require one for it (Q-2). |
| Loop execution (iterate, reset members, evaluate, guard, limit policy) | FEASIBLE WITH CHANGES | `WorkflowDagExecutor`, `NodeReadiness.isUntouched` | Biggest backend change: iteration state, reset of member status/outputs/edge statuses, outward edges delivered only when the loop finishes, deterministic under replay. Testable offline with Temporal `testing` + `FakeContinuumNodeActivity`. |
| Condition on ONE flow variable, structured, plus CEL generated and evaluated | FEASIBLE WITH CHANGES | no CEL dependency; `FlowVariables.valuesEqual` | Internal tension in the requirement; options and recommendation in Q-1. CEL engine is a new dependency not in the offline cache (NOT VERIFIED locally). |
| Loop-carried flow variables (variable set in iteration N seen in N+1 and by the condition) | FEASIBLE WITH CHANGES | `FlowVariableMerger`, `FlowVariables.ts` | The merger walks ancestors through edges and has no notion of "previous iteration". Needs a new rule, mirrored in the workbench `inheritedFlowVariables` and both specs (Q-3). |
| Strict nesting, one innermost loop, collapsed super-node cycle check | FEASIBLE | `WorkflowValidator.findCycle` | Single parent pointer makes nesting a tree; validator adds "no parent cycles", "member of exactly one loop", and runs Kahn on the collapsed graph. The collapsed check is stricter than the real one: `X1 -> Y -> X2` with X1, X2 in one loop and Y outside is acyclic in reality but cyclic collapsed, and must be rejected (Y would have to run between iterations). Frontend `hasCycle` needs the same rule. |
| Max-iterations 1..10,000 | FEASIBLE WITH CHANGES | Temporal history limits | A Temporal run history is capped (51,200 events hard; each node execution costs several events incl. every progress signal). 10,000 iterations of a 3-node body cannot fit without continue-as-new, which changes the run id that outputs and `workflow_runs` are keyed by. See Q-4. |
| JSONForms properties schema + custom `query`/CEL renderer with React Query Builder | FEASIBLE WITH CHANGES | `NodeDialog.tsx` customRenderers; no `react-querybuilder` | New dependency + `yarn.lock` = user action (policy). Renderer follows the existing convention (`options.format`), see A-4. Q-5. |
| Loop box UI (resize, drag-in/out membership, collapse, edges re-attached to outline when collapsed) | FEASIBLE WITH CHANGES | `WorkflowEditor.tsx`, React Flow 11 | React Flow 11 supports `parentNode`/`extent` and `hidden`, but not edge re-routing to a collapsed parent: collapsed display needs a derived view (hide members, draw their edges to the loop outline) while the saved edges keep pointing at the real members. Largest frontend task. Cannot be built or run here. |
| Loop status/iteration display in runs | FEASIBLE | `NodeStatus.WARNING`, `INodeProgress.message` | Use existing statuses and `nodeProgress.message`; no new enum value (A-6). |
| MCP spec + validator + builder + skill + Theia prompt | FEASIBLE | `McpWorkflowSpec.kt`, `WorkflowSpecValidator`, `WorkflowSpecBuilder`, both prompt files | Stays at six tools. Builder must add relative member positions, loop box size, `type: "Loop"`. Skill/prompt rules must change ("no edge to a loop", membership, condition shape) and their "one node per build" flow must still work. |
| Verification here | PARTIAL | `.factory/environment.md` | Gradle `--offline` unit tests of commons, orchestration, api-server run; Temporal test env covers loop semantics. Workbench: no `yarn install/build`, no `node_modules`: `tsc`, specs and the browser are NOT VERIFIED locally; the user builds. A new Maven or npm dependency cannot be fetched here. Redocly lint may be refused (`npx`). |

API impact: no new endpoint. `ContinuumWorkflowModel.Node` gains an optional field (and documented `type` values), so the
api-server OpenAPI spec changes. Follow the root `AGENTS.md` API contract: inventory, `CONVENTIONS.md`, annotations,
`./gradlew :continuum-api-server:generateOpenApiSpec --offline`, `OpenApiSpecTest`, regenerate the workbench clients with
`bash scripts/generate-api-clients.sh` (allowed), release notes. New validation errors on start/validate need the
`WorkflowIssue` wording and tests.

Data/model: no Postgres, Avro or Parquet change. Loop registration reuses the existing Avro `FeatureRegistrationRequest`
(`nodeManifest` = NodeData JSON). No new `NodeStatus`/`EdgeStatus` value is needed (keeps the commons <-> `IBaseNode.ts`
sync trivial). Member outputs are overwritten each iteration (same S3 key `{runId}/{nodeId}/output.{port}.parquet`).

Multi-tenancy/security: no new endpoint, so the `x-continuum-user-id` ownership rules are unchanged. The condition runs
inside the owner's run and only sees that run's flow variables. Whatever evaluates the condition must be sandboxed (no I/O,
bounded cost, linear-time regex if `matches` is allowed), the variable name and literal sizes must be capped, and the
condition must never be built by string concatenation without escaping the literal.

Compatibility/upgrade: workflows with a Loop need an orchestrator and api-server that know it (an older orchestrator would
fail `InitializationFailed`); the new optional `Node` field must be tolerated by older workers, which receive `Node` in
`IContinuumNodeActivity.run` (NOT VERIFIED whether Temporal's default mapper ignores unknown properties; a test must pin it).
Workflows without loops are unchanged. Release-note "Upgrade notes": deploy orchestrator + api-server + message-bridge
(registration) together, then the workbench. `platformVersion` 0.0.23 and workbench 0.0.16 are unreleased (latest tags
`release/0.0.22`, `release/0.0.15`), so notes go in `RELEASE_NOTES_v0.0.23.md` and `RELEASE_NOTES_v0.0.16.md`.

## Scope

In scope
- Core: `Loop` node definition (properties schema, UI schema, documentation markdown) registered by the orchestrator; Node
  membership field; loop execution in the orchestrator with `before`/`after` check, guard, `fail | stop-with-warning`;
  validator rules (membership, nesting, collapsed-graph cycle check, no edges to the loop, condition shape, guard range);
  MCP spec extension, `WorkflowSpecValidator`/`WorkflowSpecBuilder`, loop issues in the existing `errors`/`warnings`; tests;
  OpenAPI, README, DOX, release notes.
- Workbench: Loop node type in editor and viewer, drag-in/out membership, resize, collapse with outline edges, loop
  properties dialog (JSONForms) with the query/CEL renderer, `hasCycle` on the collapsed graph, run display (status,
  iteration message), `FlowVariables.ts` mirror if the merger rule changes, specs.
- `claude-code/workflow-builder.md` and `WorkflowBuilderPrompt.ts` updated to the same rules.
- System tests: a k6 API check that a loop workflow is accepted/rejected by validate (needs a live stack).

Out of scope
- A seventh MCP tool; any create/update-workflow tool.
- Multiple condition rules, AND/OR groups, conditions on Parquet columns or port fields, `break`/`continue` nodes,
  for-each over rows, parallel iterations, per-iteration data retention.
- Loop variables exported by the loop itself (e.g. an iteration counter), iteration-keyed outputs.
- An auth layer; changes to feature repos; a continuum-docs website page (follow-up).

Assumptions (obvious default, tell me if wrong)
- A-1 `nodeType "Loop"` means React Flow `Node.type = "Loop"` plus a reserved built-in `data.nodeModel` (planner picks the
  id); no new `nodeType` field in the JSON.
- A-2 Membership is the React Flow `parentNode` pointer on the member (one pointer, so strict nesting and "at most one
  innermost loop" hold by construction); positions of members are relative to the loop; `collapsed` is stored in the
  loop's `data`. In the MCP spec a loop is an entry of `nodes[]` (key, built-in nodeId, properties) and a member declares
  `parent: "<loopKey>"`; edges never name a loop.
- A-3 Edges always reference real members. A loop has no ports. An edge from a member to an outside node is delivered only
  when the loop finishes (the last iteration's output); an edge into a member is normal.
- A-4 "type query, language CEL" is implemented in the repo's existing renderer convention:
  `options.format: "query"` with `options.language: "CEL"`. The variable name is free text (suggesting default variable names
  is optional). Operators: `==, !=, <, <=, >, >=` plus boolean/string literal forms.
- A-5 Condition on a variable that is missing at check time fails the run with a clear message (not silently false).
  Zero iterations (a `before` check that is false at once) completes the loop with its members SKIPPED; outside consumers
  of member outputs are SKIPPED by the existing broken-edge rule.
- A-6 A member node that fails fails the loop (and the workflow, as today). Reaching the guard with `stop-with-warning`
  ends the loop with status `WARNING` and the workflow continues; with `fail` the loop is `FAILED`. Progress is shown via
  `nodeProgress.message`, no new `NodeStatus`.
- A-7 Retry policy tab is hidden for a Loop (no activity).

## Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| Temporal history limit vs 10,000 iterations | Run dies mid-loop after thousands of events | Q-4: ceiling, validator budget error/warning, fail with an explicit message before the limit |
| New CEL engine dependency not resolvable offline | Backend task blocked | Q-1; user adds/approves it; keep the evaluator behind a small interface so a fallback exists |
| CEL engine start-up or evaluation inside workflow code trips Temporal's deadlock detector or determinism | Workflow task failures, retries forever | Pre-compile, keep the engine stateless, prove with a replay test; alternative is a local activity |
| Dotted default variable names (`restNode_3.durationMs`) are not CEL identifiers | Query builder emits an expression that selects a field of a missing variable | Bind all variables as one map and generate `vars["name"]` (also for plain names); pin with tests |
| Collapsed-graph cycle check stricter than the real one | Valid-looking drawings rejected | Clear issue message naming the loop and the outside node; same rule in validator and editor |
| Resetting members per iteration misses edge/node state (status, `edge.data.status`, error maps, Kafka snapshot) | Wrong colours, stuck skips, wrong progress % | One reset function, replay-safe, covered by Temporal-test scenarios (zero, one, many iterations, failure, guard) |
| Old workers/orchestrators receive loop workflows or the new `Node` field | Run fails or node fails to deserialize | Upgrade note, version-gate by validation message, unknown-field test |
| Collapsed edge re-routing and drag-in/out parenting in React Flow 11 | Largest frontend effort, cannot be built or browser-run here | Derived collapsed view; the user builds; FE tester needs a live stack |
| `react-querybuilder` needs `package.json` + `yarn.lock` change | FE task blocked | Q-5: user action; prototype in the playground (has `node_modules`) |
| Loop-carried variable rule diverges between Kotlin and TypeScript | Run viewer shows wrong inherited variables | Change `FlowVariableMerger` and `FlowVariables.ts` together with both specs |

## Size
XL. Backend L (executor + registration + validator + MCP + OpenAPI), frontend L-XL (box UI, collapse, renderer), plus
prompts/skill and tests. Suggest the planner phases it: model + validator + MCP, then orchestrator execution, then
workbench, then prompts.

## Draft acceptance outcomes

- Given the example workflow with a loop `hasMore == true`, max 50, check `after`, When the Fetch node sets `hasMore=true`
  for 3 runs then `false`, Then the three members run 3 times, the loop ends `SUCCESS`, and "Write table" runs once with
  the last iteration's data.
- Given check `before` and `hasMore` is `false` from "Set seed", When the run starts, Then the members are SKIPPED,
  outside consumers of member outputs are SKIPPED, and the workflow does not fail.
- Given max 5 and `hasMore` stays true, When on-limit is `fail`, Then the loop is `FAILED`, the run fails and the message
  names the loop and the limit; When on-limit is `stop-with-warning`, Then the loop ends `WARNING` and downstream runs.
- Given a member sets a variable `page=2` in iteration 1, When iteration 2 starts, Then the entry node sees `page=2`
  (per the Q-3 rule) and the condition is evaluated on the end-of-iteration values.
- Given a loop nested in a loop, When the workflow is validated, Then it is accepted; Given a node whose parent chain
  loops back to itself, or a member of two loops, Then validation returns an error naming the node.
- Given `X1 -> Y -> X2` with X1, X2 in one loop and Y outside, When validated or connected in the editor, Then it is
  rejected as a cycle through the collapsed loop.
- Given an edge whose source or target is the Loop node, Then validation returns an error naming the edge.
- Given `maxIterations` 0 or 10,001, a missing condition variable, an unknown operator or an on-limit other than the two
  values, Then validation returns one error per problem naming the node and property, in the existing `errors` list.
- Given a spec with a loop and its members passed to `validateContinuumWorkflow` / `buildContinuumWorkflow`, Then loop
  problems appear in `errors`/`warnings`; a valid spec builds a `.cwf` whose members have `parentNode`, relative positions
  and a loop box that contains them; there are still exactly six MCP tools.
- Given the Loop node is dragged from the palette onto the canvas, When a node is dropped into it, Then it becomes a member
  and moves with the box; when dragged out, it stops being a member.
- Given a collapsed loop, Then its members are hidden and edges to hidden members are drawn to the loop outline; expanding
  restores them; the saved edges are unchanged.
- Given the loop dialog, When the user sets variable, operator, value, Then the stored properties hold the structured
  condition and a valid CEL string, and the dialog shows validation errors from the registered schema.
- Given a workflow without loops, Then start, validation, readiness and the viewer behave exactly as before.

## Decisions
See "Round 2" below (D-1 .. D-22). All six open questions are answered and closed.

## Open questions

Round 1 questions, kept for history. ALL ANSWERED in Round 2: Q-1 = option 3, Q-2 = 1, Q-3 = 1, Q-4 = 1, Q-5 = 1,
Q-6 = 1. No open questions remain.

**Q-1. Condition: structured fields or CEL, and what does the orchestrator evaluate?**
Why it matters: the requirement says both "variable + operator + literal value" (structured, one variable) and "query
builder generates a CEL expression the orchestrator evaluates". These conflict if CEL is the stored truth, because CEL can
express many variables and the MCP/skill (an LLM) would have to write CEL strings. It also decides whether a CEL library
(not in the offline Gradle cache) is added to the orchestrator.
Options:
1. (Recommended) Structured `condition {variable, operator, value}` is the source of truth (validated by the JSON Schema,
   easy for the MCP/skill). A CEL `expression` (`vars["hasMore"] == true`) is derived from it by the UI and by the builder
   in Kotlin and stored beside it; validation rejects a stored `expression` that differs from the canonical rendering of
   `condition`. The orchestrator evaluates the CEL with a real CEL engine (new dependency, user must allow/provide it;
   NOT VERIFIED offline). Meets both statements; "one variable" is enforced structurally.
2. Same structured source of truth, but the orchestrator evaluates the structured condition directly with a small
   operator switch; CEL is generated only for display/export. No new dependency, simplest and safest, but the orchestrator
   no longer "evaluates CEL" as stated.
3. CEL string is the only stored truth (free-form), validated by parsing; "one variable" enforced by an AST check. Most
   flexible later, but the LLM must emit CEL and the UI must parse CEL back into the builder.

**Q-2. How does the Loop get "registered by the orchestrator"?**
Why it matters: today every node must come from a worker via Kafka and have a task queue; the loop has neither.
Options:
1. (Recommended) The orchestration service publishes the same `FeatureRegistrationRequest` at startup for the built-in Loop
   (reserved marker instead of a task queue, category e.g. "Flow Control", doc markdown, properties schema and UI schema).
   Palette, `getContinuumNodeMetadata`, docs, schema validation and the MCP tools then work with no special cases; the
   initializer, readiness and progress skip Loop nodes and the api-server's task-queue lookup tolerates the marker.
   Needs a new output binding in the orchestrator.
2. The api-server serves the Loop descriptor from code (merged into tree, search, metadata, documentation) and the
   orchestrator only executes it. No Kafka change and no startup ordering, but it is not registered by the orchestrator and
   the registry has two sources.
3. A tiny built-in worker feature in `continuum-platform-core` registers the Loop on a real task queue and its activity is a
   no-op. Fits the existing path, but contradicts "not backed by any real activity".

**Q-3. Which flow variables does the next iteration see, and which does the condition read?**
Why it matters: the loop only terminates if something changes between iterations (your example: `hasMore`, a page counter),
and the Kotlin merger and `FlowVariables.ts` must stay in sync.
Options:
1. (Recommended) After each iteration, all variables set by members flow into the next iteration for the entry nodes (plus
   the normal variables from outside parents); on a clash the member latest in topological order wins. The condition reads
   this end-of-iteration set (`before` check on the first pass reads the outside ancestors' variables). Outside nodes after
   the loop inherit the last iteration's variables through their member parents, as today.
2. Only the condition variable is carried; all other member-set variables are reset to the entry values each iteration.
   Simpler to explain, but a page counter that is not the condition variable would not advance.
3. Variables are not carried; each iteration starts from the outside inputs (then a loop can only end through the guard).
   Not useful for the example.

**Q-4. How should the 10,000-iteration ceiling be honoured given Temporal's history limit?**
Why it matters: Temporal caps a run at 51,200 history events and every node execution (and each progress signal) adds
several, so a 3-node body hits the cap far below 10,000 iterations. Continue-as-new would change the run id, which the
stored outputs, `workflow_runs.run_id` and the output preview use.
Options:
1. (Recommended) Keep the schema range 1..10,000 but add a run-time guard: the loop fails (regardless of on-limit) with a
   clear message when the run history nears the limit, and the validator warns when `maxIterations x members` exceeds a
   budget. No run-id change; real ceiling is documented (hundreds to low thousands of iterations).
2. Lower the allowed range to what fits (for example 1..1,000) and document why.
3. Implement continue-as-new or a child workflow per iteration so 10,000 is real. Large extra scope (run id, signals,
   cancel/terminate, snapshots, message-bridge keys); would be its own feature.

**Q-5. Query builder: add the `react-querybuilder` dependency?**
Why it matters: the workbench cannot install packages here (registry blocked, policy), and a `package.json` change needs a
matching `yarn.lock`, which is a user action. The frontend renderer is blocked until it exists.
Options:
1. (Recommended) You add `react-querybuilder` (and its MUI/CEL-capable pieces, whichever the planner names) with the
   regenerated `yarn.lock` before the frontend task starts; the renderer uses it as the requirement states, restricted to a
   single rule. I prototype nothing that needs the install.
2. Build a minimal single-rule condition editor with the existing MUI controls and render the CEL ourselves. No dependency
   or lockfile change, and fully buildable, but not React Query Builder as specified.
3. Start with option 2 and swap in React Query Builder once you have added it (two renderers' worth of work).

**Q-6. How does the MCP/skill describe a loop, and may the builder lay out the box?**
Why it matters: it fixes the spec shape the skill and the Theia prompt teach, and it must stay within the six tools.
Options:
1. (Recommended) Loops are entries of `nodes[]` (key, built-in loop nodeId, `properties` = condition, maxIterations,
   onLimit, check) and members carry an optional `parent: "<loopKey>"`; the builder derives relative positions and the box
   size, and generates the CEL from the structured condition (Q-1).
2. A separate top-level `loops: [{key, members[], condition, maxIterations, onLimit, check, parent?}]` list, with `nodes[]`
   unchanged. The spec reads closer to the picture, but a loop is then a node in the model but not in the spec, so two
   representations must be kept in step.

---

# Round 2

Status: READY FOR PLANNING (no new open questions; two user actions are recorded as preconditions P-1 and P-2).

## Answers received

| Q | Answer |
|---|--------|
| Q-1 | Free-form CEL only (option 3, not the recommendation). The CEL string is the only stored truth for the condition. |
| Q-2 | Orchestrator publishes the built-in Loop registration at startup (option 1). |
| Q-3 | Carry all member variables into the next iteration (option 1). |
| Q-4 | Keep range 1..10,000, add a run-time history guard and a validator warning (option 1). |
| Q-5 | User adds `react-querybuilder` and regenerates `yarn.lock` before the frontend task starts (option 1). |
| Q-6 | Loop is an entry of `nodes[]`; members carry optional `parent: "<loopKey>"` (option 1). |

## Checked in this round

- CEL engine availability, offline. `~/.gradle/caches/modules-2/files-2.1/org.projectnessie.cel` and `~/.m2/repository/org/projectnessie/cel`
  contain only `cel-bom` and `cel-parent` POMs (0.3.12, 0.5.3), no jar. There is no `dev.cel` group in either cache and no
  CEL reference in any `build.gradle.kts` of `continuum-platform-core`. Result: NO CEL engine resolves with `--offline`.
- Maven Central metadata answered HTTP 200 from this shell at the time of the check (`dev.cel:cel` latest 0.15.0,
  `org.projectnessie.cel:cel-core` latest 0.8.0). Local policy still forbids dropping `--offline`, so the first resolve is a
  user action (P-1). That 200 is not proof that the artifact resolves through the user's VPN or proxy.
- Likely transitive jars of the Google engine (`dev.cel:cel` 0.15.0: guava, protobuf-java, re2j, antlr4-runtime, jspecify,
  auto-value-annotations, threeten-extra). The cache already holds guava 33.4.8, protobuf-java 3.25.5/3.25.8, re2j 1.6,
  antlr4-runtime 4.13.2, jspecify 1.0.0. `~/.m2` also has protobuf-java 4.28.3. Whether the versions the engine needs match
  these, and whether Spring's dependency management forces a conflicting protobuf, is NOT VERIFIED (needs one online resolve).
- `react-querybuilder` ships a CEL parser/formatter, but how it maps `vars["name"]` is NOT VERIFIED (not installable here), so
  D-9 does not depend on it.

## Preconditions (user actions)

- **P-1 (blocks BE tasks that touch the CEL engine).** Allow one non-offline Gradle resolve of the chosen CEL engine
  (D-6: `dev.cel:cel:0.15.0`) so it lands in the Gradle cache / `~/.m2`, then confirm `./gradlew --offline` still builds.
  If it cannot be resolved or conflicts with the Spring Boot 4 / Avro / Confluent protobuf versions, the fallback in D-7
  applies. The planner must put this first and must not mark BE tasks needing the engine as startable before it is done.
- **P-2 (blocks FE tasks that import it).** User adds `react-querybuilder` (plus a MUI binding if wanted, for MUI v5; see the
  repo's JSONForms/MUI conventions) to the correct `package.json` and regenerates `yarn.lock` (CI uses `--frozen-lockfile`).
  FE tasks that import it start only after this. FE tasks that do not import it (editor box, collapse, membership, the
  hand-written CEL subset parser and its spec) are not blocked.

## Decisions

Loop condition (consequences of Q-1 = free-form CEL)

- **D-1 Stored shape.** The Loop's `properties` hold `condition` (string, a CEL expression), `maxIterations`
  (integer 1..10,000, default 50), `onLimit` (`fail` | `stop-with-warning`) and `check` (`before` | `after`). There is no
  structured `condition {variable, operator, value}` and no stored second `expression`. This supersedes the Round 1 text
  of Q-1 option 1, A-4 and the "structured condition" parts of the scope, feasibility and acceptance outcomes. JSON Schema
  (draft 7) can only bound the string (`type: string`, 1..2,048 chars); the CEL subset is checked semantically (D-4, D-5).
- **D-2 Exact supported CEL subset.** One comparison: `vars["<name>"] <op> <literal>`, nothing else.
  - Left operand: the index expression on the identifier `vars` with ONE string-literal key (the flow variable name). The
    key is the exact flow variable name (may contain dots, e.g. `vars["restNode_3.durationMs"]`).
  - Operator and literal type: booleans `==`, `!=`; strings `==`, `!=`; integers (int64) and doubles (finite) `==`, `!=`,
    `<`, `<=`, `>`, `>=`. A negative number is accepted as a literal.
  - Literal: `true`/`false`, a quoted string (single or double quotes, standard CEL escapes), an integer or a double.
  - Surrounding parentheses and whitespace are allowed (they are not kept in the AST). The canonical form the builder emits is
    `vars["name"] <op> <literal>` with double quotes and one space around the operator.
  - Caps: whole expression at most 2,048 characters, variable name 1..256 characters, string literal at most 1,024 characters.
- **D-3 Deliberately out of scope for the subset.** `&&`, `||`, `!`, unary minus on a non-literal, the ternary, arithmetic,
  `in`, lists/maps/`null`/bytes/`uint` (`1u`) literals, any function or method call (`size`, `startsWith`, `matches`,
  `has`, ...), macros (`exists`, `all`, `map`, ...), field selection (`vars.hasMore`), more than one `vars[...]`, a literal on the
  left (`true == vars["x"]`), variable-to-variable comparison, ordering (`<`, `>`) on strings or booleans, other bound
  names, timestamps/durations. A condition on an Parquet column or port field stays out of scope.
- **D-4 How the variables reach CEL.** The engine declares one variable, `vars`, of type `map<string, dyn>`, bound to the
  loop's end-of-iteration flow variable set (D-14). Every variable is addressed as `vars["<name>"]`, also plain names, so dotted default names
  work and the editor has one rule. No other identifiers are declared.
- **D-5 Enforcing "ONE flow variable" and the subset.** A single Kotlin component in `continuum-commons`
  (`validation`, e.g. `LoopConditionParser`) parses the string with the CEL parser, walks the AST and only accepts the D-2
  shape. It returns a parsed value (`variableName`, `operator`, typed `literal`) or a reason. Use it from `WorkflowValidator`
  (so run start, `validateContinuumWorkflow` and `buildContinuumWorkflow` all enforce it), and from the orchestrator when
  a run starts. Type rules are checked at validation time: operator allowed for the literal type (D-2). Runtime type rule
  in D-8. Whether `-1` parses as a folded constant or as unary minus on a constant is NOT VERIFIED; the parser test pins
  both shapes as accepted.
- **D-6 Engine choice.** Google `dev.cel:cel` (cel-java, official, exposes the parsed AST, supports int/double
  heterogeneous comparison), version 0.15.0 at the time of writing, as `implementation` of `continuum-commons` (validator
  lives there) and used by the orchestrator for evaluation. The planner verifies the resolve in P-1. Alternative if
  P-1 fails: `org.projectnessie.cel:cel-core` (pure-Java port; latest 0.8.0). Risks: workers also depend on commons, so
  the jars (guava, protobuf, re2j, antlr) reach them transitively; the planner checks version alignment (NOT VERIFIED) and
  may move the parser into a small dedicated module if it conflicts.
- **D-7 Fallback if no engine can be added.** Because the subset is one comparison, the evaluator stays behind a small
  interface (`LoopConditionEvaluator`). Fallback: a hand-written Kotlin parser/evaluator for exactly the D-2 grammar,
  tested against the same golden vectors as the TypeScript parser (D-9). No user decision needed unless P-1 fails; then the
  orchestrator reports it and the planner swaps the implementation.
- **D-8 Evaluation semantics.** The expression is parsed and compiled once when the loop starts (the validator already
  rejected unsupported text at run start; the orchestrator re-checks defensively and fails the run with the reason). The
  engine evaluates the original CEL string, after the AST gate, so the engine only ever sees comparison/index/constant
  nodes: bounded cost, no I/O, no regex. Before evaluating, the orchestrator checks: the variable exists (otherwise fail the run, A-5,
  message names loop, variable, iteration) and its JSON type class (boolean / number / string) matches the literal class
  (otherwise fail with a message naming the variable, actual and expected type; no silent `false`). Variable values that are
  lists, maps or null fail the same way. An int variable against a double literal compares numerically.
- **D-9 Free-form CEL in the workbench.** The query renderer (`options.format: "query"`, `options.language: "CEL"`):
  1. loads the stored string by parsing it with a small hand-written TypeScript parser/formatter of exactly the D-2 grammar
     (shared file used by the renderer and by the editor), NOT by relying on React Query Builder's own CEL parser/
     `formatQuery('cel')` (their `vars["x"]` handling is NOT VERIFIED);
  2. shows React Query Builder restricted to one rule (no groups, no add-rule/add-group, no combinators): variable
     (free text, optionally suggested from flow variables), operator (filtered by the literal type), value (typed input);
  3. on every change emits the canonical CEL string into the property;
  4. React Query Builder is only the UI; the stored truth is the string.
  Kotlin and TypeScript parsers are two implementations of one grammar. Drift risk is controlled by one shared golden vector table
  (input string -> accepted triple or rejected, with reason code) copied into the Kotlin test and the TS spec, both
  deliberately listing the same ~40 cases (valid, escapes, negative numbers, every rejected construct in D-3).
- **D-10 A CEL string outside the subset.**
  - Backend: `WorkflowValidator` returns an ERROR issue on node `<loopKey>`, property `condition`, with a reason
    (syntax error with column, "uses N variables", "function `size` is not supported", "operator `<` is not allowed for
    strings", "put the variable on the left", ...) and the supported shape. Run start is refused as for any validation
    error; the MCP tools return it in `errors`.
  - Workbench renderer: it does NOT show the builder (it cannot represent the expression). It shows the raw string in a plain
    text/code field, an error alert with the same reason text and the supported shape, and a "Start over with the builder"
    button that replaces it with the default condition. While the user types a text that becomes valid, the builder reappears.
    The dialog keeps the text (it is not dropped). Saving an invalid draft is not blocked by the renderer; the validator
    catches it at validate/run start. Whether `NodeDialog` can block saving on renderer errors is for FE planning.
  - Editor (canvas): a Loop whose condition fails the TS parser gets a warning badge on the box, so the author sees it before running.
- **D-11 MCP/skill.** The spec carries the CEL string as `properties.condition`; the builder passes it through unchanged
  (no generation, no escaping duty in Kotlin). The skill and Theia prompt (twins) teach the grammar with 3 examples and the
  list in D-3, so the LLM emits only valid shapes. `validateContinuumWorkflow` is the check loop for the LLM. Still six tools.

Other answers

- **D-12 (Q-2) Registration.** The orchestration service publishes the built-in Loop `FeatureRegistrationRequest` at
  `ApplicationReadyEvent` on every start (idempotent upsert in message-bridge), with a reserved marker instead of a task
  queue (planner chooses the value), category "Flow Control", documentation markdown, properties schema (D-1) and UI schema
  (`options.format: "query"` for `condition`). `getTaskQueues`/`WorkflowActivityInitializer`/`InitializeActivity`, readiness and
  progress skip Loop nodes (they have no activity). Needs a new Spring Cloud Stream output binding plus Avro schema
  registry access in the orchestrator, and its README, the root `AGENTS.md` DOX note and the minimal-config rule (node defaults
  via `@Value` in code). Consequence: Loop appears in the palette only after the orchestrator has started once with the new
  version (upgrade note).
- **D-13 (Q-6) Spec and model shape.** A loop is an entry of `nodes[]` (key, built-in loop nodeId, `properties`), members
  carry optional `parent: "<loopKey>"`, edges never name a loop. In the model a member has React Flow `parentNode`
  (plus relative position); the builder derives relative positions and the box size; `type: "Loop"` for the box. (A-1, A-2
  stand.)
- **D-14 (Q-3) Variable flow.** After each iteration every variable set by any member flows into the next iteration's entry
  nodes (plus the normal outside-parent variables); on a name clash the member latest in topological order wins. The
  condition reads this end-of-iteration set; a `before` check on the first pass reads the outside ancestors' variables.
  Outside nodes after the loop inherit the last iteration's variables through their member parents. `FlowVariableMerger.kt` and
  `FlowVariables.ts` get the same rule and their specs are changed together.
- **D-15 (Q-4) Range and guard.** `maxIterations` stays 1..10,000 (schema). Run-time guard: before starting each iteration the
  orchestrator reads the Temporal history length (`Workflow.getInfo().getHistoryLength()`; API name and availability in
  the pinned SDK are NOT VERIFIED) and, once it passes a configured fraction of the 51,200 hard limit, fails the loop (whatever
  `onLimit` says) with a message naming the loop, the iteration and the cause. The validator warns (not errors) when
  `maxIterations x member count` exceeds a budget (the planner calibrates it with a Temporal test that counts events per
  iteration). The real ceiling is documented in the Loop documentation markdown and release notes. The run id never changes.
- **D-16 (Q-5)** see P-2. Renderer uses React Query Builder restricted to one rule (D-9).

Evaluation placement and replay

- **D-17 In-workflow evaluation.** The condition is evaluated inside the workflow code as a pure function (no clock, random,
  I/O). The engine is warmed up at orchestrator bean start so first-use class loading does not hit Temporal's deadlock
  detector. A replay test pins determinism. Fallback if the deadlock detector or replay test fails: evaluate in a Temporal
  local activity (result recorded in history, immune to engine upgrades), at a cost of about 3 history events per iteration, to be
  counted in D-15.

Clarified defaults (stated, not asked)

- **D-18** Schema default for `condition` is the valid example `vars["hasMore"] == true`; `check` default `after`,
  `onLimit` default `fail`, `maxIterations` default 50.
- **D-19** No validator check that the named variable is produced by a member or ancestor (the set of produced variables
  is not known statically); a missing variable fails at run time (D-8). Optional later: a warning.
- **D-20** The Retry Policy tab is hidden for Loop (A-7). A-3, A-5, A-6 stand, with A-5 now enforced by D-8.
- **D-21** The Loop node's `data.nodeModel` is a reserved built-in id (planner picks it, e.g. a `...Loop` id), validated
  against the registration of D-12 like any node, so `WorkflowValidator` keeps its "node must be registered" rule.
- **D-22** Collapsed state lives in the loop node `data` (A-2); edges always reference real members (A-3).

## Superseded by this round (planner: read these as replaced)

- Round 1 Feasibility row "Condition on ONE flow variable, structured, plus CEL": now FEASIBLE WITH CHANGES per D-1..D-11.
- Round 1 A-4 (operators `==, !=, <, <=, >, >=` plus "boolean/string literal forms" and a free variable-name field): replaced by D-2.
- Round 1 acceptance outcome "the stored properties hold the structured condition and a valid CEL string": replaced by
  the outcomes below.
- Round 1 risk "CEL generated from literals needs escaping": now the emitter is the TS formatter (escaping tested) and
  the LLM, whose strings are validated.

## Updated risks (added to Round 1)

| Risk | Impact | Mitigation |
|------|--------|------------|
| CEL engine cannot be resolved offline (verified: not in cache) | Backend condition tasks blocked | P-1; evaluator interface and D-7 fallback; planner starts with a spike task |
| cel-java transitive jars (protobuf, guava) conflict with Spring/Avro/Confluent versions; workers inherit them through commons | Boot or worker start failures | Resolve and run existing tests after P-1; move the parser to its own module if needed |
| Two parsers (Kotlin AST walk, hand-written TS) accept different strings | Editor shows the builder for a string the backend rejects, or the reverse | One shared golden vector table in both test suites (D-9); the backend is authoritative |
| LLM/MCP emits free-form CEL outside the subset | Validation loops | Error messages name the reason and the supported shape; prompt and skill list grammar and examples |
| Free-form CEL invites features later (and, or) | Scope creep, parser divergence | D-3 lists exclusions; additions must change both parsers and the vector table |
| Runtime type mismatch (string variable vs numeric literal) | Run fails mid-way | D-8 explicit message; validator cannot know the type, documented in the Loop docs |
| In-workflow engine use trips the deadlock detector or replay | Workflow task failures | Warm-up, replay test, local-activity fallback (D-17) |
| `NodeDialog` may not block saving on renderer errors | Invalid draft saved | Backend validation at validate/run start, canvas warning badge (D-10) |

## Revised draft acceptance outcomes (replace/extend Round 1)

- Given the Loop dialog, When the user sets variable `hasMore`, operator `==`, value `true` in the builder, Then the stored
  `condition` is exactly `vars["hasMore"] == true`; and When a stored string `vars["hasMore"] == true` is opened, Then the
  builder shows variable `hasMore`, operator `==`, value `true`.
- Given a stored `condition` `vars["page"] <= 10`, `vars['x'] != "a\"b"`, `(vars["n"] > -1.5)`, Then each is accepted and the
  builder shows it (canonical form on the next edit).
- Given `condition` `vars["a"] == true && vars["b"] == true`, `size(vars["a"]) > 0`, `vars.a == 1`, `true == vars["a"]`,
  `vars["a"] == vars["b"]`, `vars["a"] < "x"`, `vars["a"] ==`, or a string over 2,048 characters, When the workflow is validated or
  started, Then one error per problem names the loop key and property `condition` and a reason; the run does not start;
  and the dialog shows the text field, the reason and "Start over with the builder", not the builder.
- Given the same rejected strings sent through `validateContinuumWorkflow`/`buildContinuumWorkflow`, Then they appear in `errors`.
- Given a Kotlin and a TypeScript golden vector table, Then both suites contain the same cases and pass.
- Given the loop's condition variable is not set when the check runs, Then the run fails with a message naming loop, variable and
  iteration; Given the variable is a string and the literal a number, Then the run fails with a type message.
- Given `before` and a false condition at once, Then the members are SKIPPED (A-5, unchanged).
- Given a loop whose iteration would pass the history-length guard, Then the loop fails with the guard message regardless of
  `onLimit`; Given `maxIterations x members` over the budget, Then the validator returns a warning, not an error.
- Given the orchestrator starts, Then the Loop descriptor is published once per start and then appears in the palette,
  `getContinuumNodeMetadata`, search and documentation; Given a workflow with a Loop node, Then no task queue is requested
  for it and progress counts it consistently (D-12).
- Given member variables `page=2` (iteration 1) and `hasMore=true`, Then iteration 2's entry nodes see both and the condition
  reads the end-of-iteration values (D-14), in both the Kotlin merger and `FlowVariables.ts`.
- Given the frontend renderer file is imported only after P-2, Then FE tasks that do not need it still build.

## Size (unchanged)
XL, with the same suggested phases. The CEL decision adds a spike (engine resolve, protobuf alignment, in-workflow replay) at the very
start of the backend phase, and a shared golden-vector test in two languages.

## Open questions
None. No new user decisions needed; P-1 and P-2 are user actions, not questions.

---

# Round 3 — full CEL

Status: READY FOR PLANNING (no new open questions; P-2 changes, see below).

Date 2026-11-25. Trigger: user scope change. The loop condition is FULL CEL, not the single comparison
`vars["x"] <op> <literal>`. This round supersedes D-2, D-3, D-5, D-7, D-8, D-9, D-10, D-11 and D-17, and amends D-1
(new property) and D-15 (the budget counts evaluations). Earlier rounds stay as history; where they conflict, Round 3 wins.

## Answers received (user, 2026-11-25)

| # | Answer |
|---|--------|
| 1 | Standard CEL (all operators, standard macros `has/all/exists/exists_one/map/filter`, standard functions incl. conversions, timestamp/duration) plus the cel-java extension libraries. Keep cost/nesting/length limits so a condition cannot stall a Temporal workflow task. |
| 2 | Dialog: React Query Builder (RQB) when the expression fits it (and/or groups of comparisons), else a CEL text mode, with a toggle. The backend validator is authoritative for both. |
| 3 | Flow variables stay one map `vars: map<string, dyn>`, read as `vars["name"]`; presence with `has(vars.x)` / `"x" in vars`. |
| 4 | Non-boolean result: "let the user configure the behaviour from settings" (pinned down in D-31). |
| 5 | (via orchestrator) Clean up `continuum-loop-condition` and the commons condition contract: delete the subset-only code, do not keep it dormant (D-38). |

## Checked in this round (facts from the offline Gradle cache, `dev.cel:*:0.15.0`)

Method: `javap` over the cached jars and small Java probes compiled and run against them in a `mktemp -d` scratch dir
(offline; nothing in any repo). Probe environment: `CelFactory.standardCelBuilder()`, standard macros,
`vars: map(string, dyn)`, all extensions below, `enableHeterogeneousNumericComparisons(true)`.

**Extension libraries in cel-java 0.15.0** (`dev.cel.extensions.CelExtensions` factory methods; function lists from each
library's `Function` enum and `CelExtensions.getAllFunctionNames()`):

| Library (factory) | CEL functions/macros | Use for loops |
|---|---|---|
| strings (`strings()`) | `charAt`, `format`, `indexOf`, `join`, `lastIndexOf`, `lowerAscii`, `strings.quote`, `replace`, `reverse`, `split`, `substring`, `trim`, `upperAscii` | enable |
| math (`math(options)`) | `math.greatest`, `math.least`, `math.ceil`, `math.floor`, `math.round`, `math.trunc`, `math.isFinite`, `math.isNaN`, `math.isInf`, `math.abs`, `math.sign`, `math.bitAnd/bitOr/bitXor/bitNot/bitShiftLeft/bitShiftRight`, `math.sqrt` | enable |
| lists (`lists()`) | `slice`, `flatten`, `lists.range`, `distinct`, `reverse`, `sort`, `sortBy` | enable |
| sets (`sets(options)`) | `sets.contains`, `sets.equivalent`, `sets.intersects` | enable |
| regex (`regex()`) | `regex.replace`, `regex.extract`, `regex.extractAll` (return `optional`) | enable (needs optional) |
| encoders (`encoders(options)`) | `base64.encode`, `base64.decode` | enable |
| bindings (`bindings()`) | macro `cel.bind(name, value, expr)` | enable, with the shadowing rule V-8 |
| optional (`optional()`) | `optional.of/none/ofNonZeroValue/unwrap`, `.value()`, `.hasValue()`, `.or()`, `.orValue()`, list `.first()/.last()`; syntax `vars[?"x"]`, `vars.?x` (needs `CelOptions.enableOptionalSyntax(true)`) | enable |
| comprehensions (`comprehensions()`) | two-variable `all/exists/existsOne(k, v, ...)`, `transformList`, `transformMap`, `transformMapEntry` | enable |
| protos (`protos()`) | `proto.hasExt/getExt` for protobuf messages | NOT enabled: no proto types are declared, it would only add confusing names |
| nativeTypes (`nativeTypes(Class...)`) | binds Java classes as CEL types | NOT enabled: not a function library |

Standard functions present (`CelStandardDeclarations.StandardFunction`): logical/comparison/arithmetic operators, `in`,
ternary, index, `size`, `int`, `uint`, `double`, `string`, `bytes`, `bool`, `dyn`, `type`, `duration`, `timestamp`,
`matches`, `contains`, `startsWith`, `endsWith`, and the timestamp accessors `getFullYear`, `getMonth`, `getDayOfYear`,
`getDayOfMonth`, `getDate`, `getDayOfWeek`, `getHours`, `getMinutes`, `getSeconds`, `getMilliseconds`.

Verified behaviour (each expression compiled and evaluated in the probe):
- Accepted and correct: `vars["hasMore"]`, `vars.page < 10 && vars["hasMore"]`, `has(vars.page)`, `"page" in vars`,
  `vars["restNode_3.durationMs"] > 1000`, `exists/all/exists_one/map/filter`, `lowerAscii`, `matches`, `regex.extract`,
  `math.greatest`, `sets.contains`, `lists.range`, `base64.encode`, `cel.bind`, `vars[?"missing"].orValue(0)`,
  `timestamp(vars["ts"]).getHours()` (UTC) and `.getHours("Europe/Berlin")`, timestamp + `duration("1h")`, `int(2.5)`,
  `type(vars["page"]) == int`, `"%s-%d".format(["a", 1])`, `vars.all(k, v, k != "")`, `vars.transformList(k, v, k)`.
- **No `now()`.** Standard CEL has no clock function: `now()` is a compile error "undeclared reference to 'now'". The
  only clock call in all six jars is `ProtoTimeUtils.now()`, and no class calls it. No class uses `Random`/`UUID`; the
  strings library formats with `Locale.ROOT`. Timestamp accessors default to UTC; with a time-zone argument they use
  `ZoneId.of(name)`, so the result depends on the JDK's tz database version. `matches` and the regex library use re2j
  (linear time); `CelOptions.maxRegexProgramSize` exists.
- **Result type is known after type-checking.** With `setResultType(BOOL)` the checker rejects `vars["page"] + 1`,
  `1 + 1`, `null`, `vars["x"] ? 1 : 2` ("expected type 'bool' but found 'int'", with column). Expressions of type `dyn`
  pass (`vars["hasMore"]`, `dyn(1)`, `vars[?"x"].orValue(false)`) and can return a non-boolean at run time
  (`vars["hasMore"]` = `"yes"` returned the string). So a non-boolean result is only possible for a `dyn`-typed condition.
- **Runtime errors** are `CelEvaluationException` with a `CelErrorCode` and a source offset: a missing key gives
  `ATTRIBUTE_NOT_FOUND` with message `fooBar` (index) or `key 'fooBar' is not present in map.` (field selection);
  `vars["name"] > 1` on a string gives "No matching overload for function '_>_'"; `&&` on a string gives
  `INVALID_ARGUMENT` "expected boolean value, found: Ab-12"; `/ by zero`; a bad regex at run time "error parsing regexp".
- **CEL semantics differ from D-8:** `vars["name"] == 1` with a string is `false`, not an error (heterogeneous
  equality). `vars["missing"] || vars["hasMore"]` is `true` (error absorbed by the commutative `||`). So "missing
  variable / type mismatch always fails" can no longer be promised; it fails only when the result depends on it.
- **Cost limits that exist:** `CelOptions.comprehensionMaxIterations(n)` works and counts across nested comprehensions
  ("Iteration budget exceeded: 1000" for a 100 x 100 x 100 nested `map`, and for `lists.range(2000).map(...)`).
  `maxParseRecursionDepth`, `maxParseExpressionNodeCount`, `maxExpressionCodePointSize` exist, and the validator package
  ships `AstDepthLimitValidator`, `RegexLiteralValidator`, `TimestampLiteralValidator`, `DurationLiteralValidator`,
  `HomogeneousLiteralValidator`.
- **Cost that is NOT bounded by those options (verified):** non-comprehension functions run unmetered.
  `lists.range(1000000)` allocates a million-element list in 22 ms (it fails above about 3 million);
  `sets.contains` of two 60,000-element lists took 3.5 s (quadratic); a 264-character chain of ten
  `.replace("a", "aaaaaaaa")` ran a 1 GB heap out of memory (`OutOfMemoryError`); 29 nested `cel.bind` doublings
  (733 characters) did the same. A 3.5 s evaluation on the workflow thread would trip Temporal's 1 s deadlock detector,
  and an OOM would hit the whole orchestrator JVM.
- **A guard that works (verified):** `Program.trace(vars, listener)` calls the listener with every intermediate value; a
  listener that throws aborts the evaluation as a `CelEvaluationException` (aborted the `replace` chain at 32,768
  characters and the 60,000-element list before `sets.contains` ran). So a size cap on every intermediate string, bytes,
  list and map is possible, and with it the quadratic functions only see capped inputs.
- **cel-java 0.15.0 bug:** `cel.bind` that re-binds a name already bound outside, e.g.
  `cel.bind(a, 1, cel.bind(a, a + a, a > 0))`, throws `StackOverflowError` at evaluation (even 2 levels deep). It must be
  rejected by the validator (V-8) and `StackOverflowError` must be caught at evaluation.
- Map iteration order: `vars.map(k, k)[0]` follows the iteration order of the Java map that is bound to `vars`.

**React Query Builder** is not installed anywhere on this machine (no `node_modules`, npm cache or Yarn cache entry), so
everything about `parseCEL` / `formatQuery(query, 'cel')` below is **NOT VERIFIED offline** (from its public
documentation): it exports a CEL formatter and a CEL parser; how `parseCEL` maps `vars["x"]` (index with a string key)
and `vars.x` to a field name, and whether it reports or silently drops parts it cannot represent, is unknown.
The generated workbench client already has `validateWorkflow` (`POST /api/v1/workflow/validate`,
`workflow-editor-extension/src/generated/api-server/api/workflows-api.ts:168`).

## Decisions (Round 3)

Language and environment

- **D-23 Language (replaces D-2, D-3).** The `condition` is any CEL expression that passes the validator (D-25):
  all standard operators (`&& || ! ?: + - * / % == != < <= > >= in`, index, field selection), the standard macros
  (`has`, `all`, `exists`, `exists_one`, `map`, `filter`), all standard functions (conversions `int uint double string
  bytes bool dyn type`, `size`, `matches`, `contains`, `startsWith`, `endsWith`, `duration`, `timestamp` and the timestamp
  accessors), and these cel-java 0.15.0 extensions: **strings, math, lists, sets, regex, encoders, bindings, optional
  (with optional syntax `vars[?"x"]` / `vars.?x`), comprehensions**. Not enabled: `protos` (no proto types are
  declared) and `nativeTypes` (not a function library).
- **D-24 One environment, two users.** One factory in `continuum-loop-condition` (e.g. `LoopCelEnvironment`) builds the
  CEL environment: the options, the macros, the extensions, and `vars: map(string, dyn)` as the only declared variable,
  with an empty container. The validator (api-server, and the orchestrator's defensive re-check, BE-16) and the evaluator
  (orchestrator) both build from it, so the extensions enabled in validation and at run time are the same by
  construction. Where cel-java allows it, extension versions are pinned (`math(options, n)`, `sets(n)`, `lists(n)`,
  `regex(n)`, `comprehensions(n)`, `optional(n)`), so a cel-java upgrade cannot silently add functions. A test lists the
  environment's function names and fails when they change. Options: `enableHeterogeneousNumericComparisons(true)`,
  `enableOptionalSyntax(true)`, plus the limits in D-26.
- **D-25 "ONE flow variable" is dropped (confirmed).** A condition may read any number of flow variables, compare
  variables with each other, put a literal on the left, and use `vars.x` as well as `vars["x"]`. The docs and prompts
  still teach `vars["name"]` as the normal form (it works for dotted default names such as `vars["restNode_3.durationMs"]`);
  presence is `has(vars.x)` (plain names only) or `"x" in vars` (any name). D-4 (one map `vars`) stands.

Validator (backend, authoritative; replaces D-5, D-10 backend part)

- **D-26 Rules, in this order; the first failing rule gives one ERROR** on node `<loopKey>`, property `condition`, with
  a reason code, the CEL message and a 1-based column when known:
  - V-1 `EMPTY`: blank text.
  - V-2 `TOO_LONG`: over 2,048 code points (the schema `maxLength` stays).
  - V-3 `TOO_DEEP`: brackets nested deeper than 32 (the existing iterative text pre-check), and parser recursion
    limited to 100 (`maxParseRecursionDepth`), `StackOverflowError` caught.
  - V-4 `SYNTAX`: cel-java parse error.
  - V-5 `CHECK_ERROR`: type-check error against D-24. This covers undeclared names and functions (`foo`, `now()`,
    `vars2`), and calls with no matching overload on statically known types (`"a" > 1`, `size(1)`).
  - V-6 `NOT_BOOLEAN`: the checked result type is neither `bool` nor `dyn` (`vars["page"] + 1`, `1 + 1`, `"x"`, `null`,
    `vars["x"] ? 1 : 2`). A `dyn` result (`vars["hasMore"]`, `vars[?"x"].orValue(false)`) is valid and is governed at run
    time by D-31.
  - V-7 `INVALID_LITERAL`: an invalid regex, timestamp or duration literal (cel-java `RegexLiteralValidator`,
    `TimestampLiteralValidator`, `DurationLiteralValidator`).
  - V-8 `REBOUND_NAME`: `cel.bind` or a comprehension variable that re-binds a name already bound in an enclosing scope,
    or binds `vars`. This is a workaround for a verified cel-java 0.15.0 `StackOverflowError`.
  - Valid result: the result type (`bool` or `dyn`) and the set of flow variable names read statically (`vars["lit"]`,
    `vars.x`, `has(vars.x)`, `"lit" in vars`, `vars[?"lit"]`), used in messages (D-29) and the warning below.
- **D-27 Warnings (the condition's warning budget).** At most ONE condition warning per loop, so the MCP/LLM loop is not
  flooded:
  - W-1: the condition reads no flow variable (`true`, `1 < 2`), so the loop only ends at `maxIterations` ("Loop 'L':
    the condition reads no flow variable, so it never changes; the loop runs until maxIterations and then follows
    onLimit.").
  - There is no warning for a `dyn` result, because the common `vars["hasMore"]` is `dyn`. D-31 and the docs cover it.
  - The history-budget warning of D-15 stays a separate warning (`property = maxIterations`) and is recalibrated
    (D-30).
  - Workflow-level warnings are unchanged.

Runtime (replaces D-8, D-17)

- **D-28 Cost limits, so a condition cannot stall a workflow task.**
  - Text 2,048 code points; nesting 32; parse recursion 100 (D-26).
  - `comprehensionMaxIterations` 1,000 per evaluation (verified to count across nested comprehensions).
  - Size guard, through the evaluation listener (`Program.trace`, verified to abort): every intermediate list or map
    has at most 10,000 elements and every string or bytes value at most 100,000 characters/bytes. This bounds the
    functions the iteration budget does not meter. Verified unbounded without it: `lists.range(1000000)`;
    quadratic `sets.contains`, 3.5 s at 60,000 elements; `replace` chains and `cel.bind` doublings ran a 1 GB heap out
    of memory.
  - `maxRegexProgramSize` set (the planner picks the value; re2j is linear time).
  - `StackOverflowError` caught at evaluation.
  - Exceeding a limit fails the loop with `LIMIT_EXCEEDED` and names the limit.
  - A robustness test runs a list of hostile conditions and asserts each finishes in under 100 ms and never throws.
  - Exact cap values are planner choices if these prove too tight; they are code constants, not configuration (PR-4).
- **D-29 Runtime errors.** There is no pre-check before evaluation: the D-8 `LoopConditionRuntimeCheck` and its
  type-class rule are dropped, because they contradict CEL (`has()`, `||` absorbing an error, heterogeneous equality).
  CEL decides:
  - an evaluation error fails the loop (status `FAILED`, `$error`, run fails; A-5 stands for errors);
  - the message names the loop, the iteration, the reason code (`EVALUATION_ERROR`, `LIMIT_EXCEEDED`) and the CEL
    message with its column;
  - for `ATTRIBUTE_NOT_FOUND` it names the variable: from the CEL message (the key), matched against the statically
    read names of D-26. Example: "Loop 'L': flow variable 'hasMore' is not set when the condition is checked in
    iteration 3 (column 1).";
  - a key computed at run time (`vars[vars["k"]]`) is reported with the CEL message only.
  - Documented semantics: `vars["s"] == 1` with a string is `false` (no error); `vars["s"] > 1` is an error; a missing
    variable in `a || b` does not fail when the other side is `true`.
  - Values reach CEL converted from the JSON flow variables: integers to `Long`, decimals to `Double`, `Boolean`,
    `String`, lists and maps (recursively, iteratively), `null` to CEL `null`.
- **D-30 Determinism and Temporal replay.**
  - Verified: CEL has no `now()`; cel-java calls no clock, random or UUID; string functions use `Locale.ROOT`; regex is
    re2j.
  - Two remaining sources of drift:
    - timestamp accessors with a time-zone argument depend on the JDK's tz database;
    - a cel-java upgrade can change an extension's result.
  - Both would only matter if a replay re-evaluates the condition on a different orchestrator build. Decision: evaluate
    inside `Workflow.sideEffect`. The outcome (true / false / non-boolean / failure + message) is recorded in history,
    so a replay never re-evaluates. This replaces D-17's "pure in-workflow + local-activity fallback". It still runs on
    the workflow thread, so D-28 and the engine warm-up stay.
  - `vars` is bound as a key-sorted map, so map iteration order (`vars.map(k, k)`) is the same everywhere.
  - Cost: about one history event per condition check (NOT VERIFIED; BE-17 measures it). It is counted in the D-15
    budget and the guard. The validator warning budget formula becomes `maxIterations x (members + check events)`.
  - The replay test stays and covers a full-CEL condition.

Non-boolean result (answer 4)

- **D-31 "Settings" = a Loop node property, `onNonBooleanResult`, next to `onLimit`.** This is not ambiguous enough to
  ask:
  - the Loop's properties are literally its "Settings" (the section of that name in `LoopNode.doc.md`, shown in the
    dialog);
  - a workbench preference never reaches the orchestrator;
  - a platform setting cannot be read deterministically in workflow code (PR-4) and would make one workflow behave
    differently per deployment.
  - Values (enum, required, default `fail`):
    - `fail` (default): the loop fails with `NON_BOOLEAN_RESULT`: "Loop 'L': the condition returned a string ("yes")
      in iteration 2, not true or false.";
    - `treat-as-false`: the loop stops as if the condition were false and ends `WARNING` (not `SUCCESS`, so it stays
      visible), message "Condition returned <type> in iteration <k>; treated as false." Downstream runs with that
      iteration's outputs (`before` on the first pass: zero iterations, members SKIPPED, loop WARNING).
  - Not offered: `treat-as-true`, which would run on to the limit, and "truthiness" (non-empty = true), which is not CEL.
  - The setting applies only to a `dyn` result. A static non-bool is a validation error (V-6), and evaluation errors
    always fail (D-29).
  - Value previews in messages are capped at 100 characters.
  - D-1 is amended: the properties are `condition`, `maxIterations`, `onLimit`, `onNonBooleanResult`, `check`.

Workbench editor (answer 2; replaces D-9, D-10 renderer and canvas parts)

- **D-32 Two modes with a toggle; the backend decides validity.** The `options.format: "query"` renderer:
  - **Builder mode**: React Query Builder with groups, the `and`/`or` combinators and group `not`.
    - Fields are flow variable names (free text; suggestions from known variables optional). The adapter maps field
      `name` to `vars["name"]` (always the index form).
    - Operators limited to those whose CEL output is valid in D-24: `= != < <= > >=`, contains / begins with /
      ends with (`.contains/.startsWith/.endsWith`), `in` / `not in` (list), null / not null.
    - Typed values (boolean, number, text).
  - **CEL mode**: the existing code editor control (like `CodeEditorRenderer`) with the raw text.
  - **Toggle**:
    - Builder → CEL is always allowed and shows the generated text.
    - CEL → Builder is allowed when the text "fits" (D-33). Otherwise the toggle is disabled with the reason "This
      condition uses CEL features the builder cannot show", plus a "Replace with a new builder condition" action that
      asks for confirmation before it discards the text.
  - The dialog opens in Builder mode when the stored text fits, else in CEL mode. The stored truth is always the
    string; the text is never dropped silently.
  - **Validation in both modes** (debounced, about 500 ms) is the existing `POST /api/v1/workflow/validate`
    (`validateWorkflow` in the generated client): it sends the current workflow with the edited condition and shows
    the issues whose `node` is this loop and whose `property` is `condition` (message and column).
    - No new endpoint, so no OpenAPI work.
    - When the api-server is unreachable the dialog shows "Not checked", never "valid".
  - Saving an invalid draft is not blocked (D-10's rule stands); validate and run start reject it.
  - The canvas warning badge of D-10 (which relied on the TS parser) is dropped. AC-36 is withdrawn.
- **D-33 Use React Query Builder's own CEL import/export (reverses D-9).** D-9 wrote a TS parser because the TS side had
  to accept exactly the Kotlin subset. That reason is gone: the backend now validates, and the editor only has to decide
  "can the builder show this?".
  - So the workbench has no hand-written CEL parser. It uses RQB's `formatQuery(query, 'cel')` to emit and `parseCEL`
    to import, through one pure adapter (`src/common/LoopConditionQuery.ts`) that owns the field mapping and the fit
    test.
  - **Fit test**: a text fits when `parseCEL(text)` succeeds, every field it yields is a `vars["<string>"]` index
    (or a plain identifier path the adapter can map), every operator is in the D-32 list, and
    `formatQuery(parseCEL(text), 'cel')` equals the text after whitespace normalisation (a lossless round trip).
    Anything else opens in CEL mode. That covers macros, functions, `has()`, `in vars`, arithmetic, extensions and any
    part RQB would drop.
  - Builder output therefore always fits, so a text the builder produced always reopens in the builder.
  - **NOT VERIFIED offline (RQB is not installed; P-2):**
    - how `parseCEL` represents `vars["x"]` (index with a string key) and `vars.x`;
    - whether it drops unknown parts or reports them;
    - the exact text `formatQuery(..., 'cel')` emits (quoting, parentheses, `!` for `not`).
  - Contingency, if `parseCEL` cannot represent `vars["x"]`: the adapter swaps each `vars["…"]` for a placeholder
    identifier before `parseCEL`, and swaps it back after `formatQuery`. That needs only a small string-literal-aware
    tokenizer, not a CEL parser.
  - The first FE task after P-2 is a spike that pins these behaviours in the RQB round-trip table (D-35).
- **D-34 Builder field names.** A field is a flow variable name, emitted as `vars["name"]` with CEL string escapes, so
  dotted default names work. Every value is a literal; variable-to-variable comparison stays CEL mode only.

Test tables (replace the D-9 golden table and its two-language drift rule)

- **D-35 Two tables, one per side; no verbatim copy across repos.**
  - **Backend CEL table** (new, owned by `continuum-loop-condition`:
    `src/test/resources/loop-condition/cel-conditions.json`). Rows look like `{id, input, valid, code?, resultType?
    (bool|dyn), readsVariables?, evaluations?: [{vars, expect: true|false|"non-boolean"|<runtime code>}]}`. About 60
    rows:
    - valid: each operator family; every macro; each enabled extension with one call; optional syntax; timestamp and
      duration; dotted names; `has` / `in`; a literal on the left; two variables; a `dyn` result;
    - invalid: one or more per V-1..V-8 (incl. `now()`, an unknown name, `vars["p"] + 1`, a bad regex literal, a
      re-bound `cel.bind`, depth 33, 2,049 characters);
    - runtime: a missing variable (message names it), no overload, `/ by zero`, iteration budget, size guard
      (`lists.range(1000000)`, the `replace` chain), a non-boolean `dyn` under both `onNonBooleanResult` values, and
      `"s" == 1` is false.
    The validator and evaluator tests run every row. There is no SHA pin and no workbench copy.
  - **Workbench RQB round-trip table** (new, `continuum-workbench` `workflow-editor-extension/src/common/loop-condition-builder-cases.json`).
    Rows look like `{id, text, fits, query? (RQB JSON), emitted?}`:
    - builder → text for single rules, nested and/or groups, `not`, each D-32 operator, escapes and dotted names;
    - text → fits / does not fit for each excluded feature (macro, `has`, `in vars`, arithmetic, function,
      variable-to-variable comparison, optional syntax).
    `LoopConditionQuery.spec.ts` runs it (needs P-2).
  - **Cross-check**: every `emitted` text of the RQB table is also a `valid` row (with `source: "builder"`) in the
    backend table. The FE spike reports those strings as a `SCOPE:` row list for a small backend follow-up, because
    the exact RQB output is NOT VERIFIED until P-2.

Prompts and MCP (replaces D-11)

- **D-36 Teaching.**
  - The MCP tool descriptions (BE-9) say:
    - the condition is a CEL expression over `vars` (a map of flow variables) that must end in `true`/`false`;
    - write `vars["name"]`;
    - `onNonBooleanResult` decides what a non-boolean `dyn` result does;
    - `validateContinuumWorkflow` is authoritative.
    Tool names and parameters are unchanged; still six tools.
  - The skill and the Theia prompt (twins, FE-11) teach:
    - `vars["name"]` always, also for dotted names;
    - presence guards: `has(vars.x)` for plain names, `"x" in vars` for any name, `vars[?"x"].orValue(default)`;
    - CEL semantics to remember: a missing variable is an error unless guarded; `"a" == 1` is false, `"a" > 1` is an
      error; integers and doubles compare by value;
    - the enabled libraries in one line each, and what is not available (`now()`, protos, user functions);
    - the limits (2,048 characters, nesting 32, 1,000 comprehension iterations, size caps);
    - about six examples: `vars["hasMore"] == true`;
      `vars["page"] < 10 && vars["hasMore"]`; `"nextCursor" in vars && vars["nextCursor"] != ""`;
      `vars["items"].exists(i, i.status == "PENDING")`; `!vars["status"].matches("^(DONE|FAILED)$")`;
      `vars[?"retries"].orValue(0) < 3`;
    - a member must set the variables the condition reads;
    - fix what `validateContinuumWorkflow` reports.

Cleanup (answer 5; supersedes the subset parts of BE-4, BE-5 and BE-13)

- **D-37 Reason codes.** A new enum, `LoopConditionIssueCode`, replaces `LoopConditionRejection`:
  - validation: `EMPTY`, `TOO_LONG`, `TOO_DEEP`, `SYNTAX`, `CHECK_ERROR`, `NOT_BOOLEAN`, `INVALID_LITERAL`, `REBOUND_NAME`;
  - runtime: `MISSING_VARIABLE`, `EVALUATION_ERROR`, `LIMIT_EXCEEDED`, `NON_BOOLEAN_RESULT`.
  Messages start with a fixed reason text, then the CEL message and the column; the validator and the loop runner use
  the same texts.
- **D-38 Delete the subset code; keep only what full CEL needs.** One rework task. Paths are relative to
  `continuum-platform-core`. "Delete" means remove the file or the declarations, with no dormant copy.

| Item | Action | Why / replacement |
|---|---|---|
| `continuum-commons/.../validation/loop/LoopCondition.kt`: `LoopConditionOperator`, `LoopConditionLiteralType`, `LoopConditionLiteral` (+4 subtypes), `LoopCondition` (triple) with `canonical()`, `quoteCelString`, `LoopConditionGrammar.SUPPORTED_SHAPE`, the 16-code `LoopConditionRejection`, `LoopConditionParseResult`, `fun interface LoopConditionParser` | DELETE | subset only |
| same file: `LoopConditionLimits` | KEEP, reduced: `MAX_EXPRESSION_LENGTH` 2,048, `MAX_NESTING_DEPTH` 32, plus the new D-28 caps; drop `MIN/MAX_VARIABLE_NAME_LENGTH`, `MAX_STRING_LITERAL_LENGTH` | full CEL still needs the text caps |
| same file: `LoopConditionGrammar.VARIABLES_IDENTIFIER` | KEEP (`vars`) | D-4 |
| same file (new content) | REPLACE with `LoopConditionIssueCode` (D-37), `LoopConditionCheckResult { Valid(resultType: BOOL\|DYN, readVariables: Set<String>); Invalid(code, message, column?) }`, `fun interface LoopConditionValidator { fun check(text): LoopConditionCheckResult }` | the contract WorkflowValidator (SCOPE-2 injection, renamed) and BE-16 use |
| `continuum-commons/.../validation/loop/LoopConditionEvaluator.kt` | REPLACE: `compile(text)`, `evaluate(compiled, variables): LoopConditionOutcome` (`True`, `False`, `NonBoolean(typeName, preview)`, `Failed(code, message, variableName?)`), `warmUp()`; `CompiledLoopCondition(text, resultType, readVariables, engineHandle)` without `parsed: LoopCondition` | D-29, D-31 |
| `continuum-commons/src/test/resources/loop-condition/golden-vectors.json` (88 rows) | DELETE | replaced by the backend CEL table in `continuum-loop-condition` (D-35) |
| `continuum-commons/src/testFixtures/.../LoopConditionGoldenVectors.kt` | DELETE | the new table is loaded inside the module that owns it |
| `continuum-commons/build.gradle.kts`: `java-test-fixtures`, `testFixturesImplementation`, the `sourceSets.testFixtures` block, the two publication `skip()` lines | DELETE (nothing else uses the fixtures) | commons' build returns to `main` except intended changes; the published POM is unaffected either way |
| `continuum-commons/src/test/.../validation/loop/LoopConditionTest.kt` (19 tests) | DELETE; a small replacement only if the new result types have logic | subset formatting and table checks |
| `continuum-loop-condition/build.gradle.kts`: `testImplementation(testFixtures(project(":continuum-commons")))` | DELETE | no fixtures |
| `continuum-loop-condition/.../LoopConditionShapeGate.kt` | DELETE | single-comparison gate |
| `.../LoopConditionLiteralReader.kt` | DELETE | literal triple |
| `.../CelExpressionShapes.kt` | REPLACE with a small `CelAstWalk` (keep the iterative `subExpressions`/`directChildren`; drop the comparison, logical and literal helpers) | iterative walk still needed for V-8 and the read-variable set |
| `.../CelConditionSyntax.kt` | REPLACE with `LoopCelEnvironment` (D-24: one place for options, macros ON, extensions, `vars` declaration, compile = parse + check) | macros OFF and `retainRepeatedUnaryOperators` were subset tricks |
| `.../LoopConditionTextPrecheck.kt`, `.../LoopConditionNestingDepth.kt` | KEEP (codes renamed: nesting becomes `TOO_DEEP`) | V-1..V-3 |
| `.../LoopConditionRejections.kt` | REPLACE with `LoopConditionIssues` (builds issue messages and 1-based columns; same idea) | D-37 |
| `.../CelLoopConditionParser.kt` | REPLACE with `CelLoopConditionValidator` (chain: precheck → environment compile → result-type rule → literal validators → `ReboundNameRule` → read-variable extraction) | D-26 |
| `.../LoopConditionParsers.kt` | REPLACE with `LoopConditionValidators.default` | injected by api-server, re-check in orchestration |
| new in the module | `CelLoopConditionEvaluator` (BE-15), `LoopConditionSizeGuard` (trace listener, D-28), `FlowVariableCelValues` (JSON → CEL value conversion, key-sorted map) | D-28..D-30 |
| `continuum-loop-condition/src/test/.../LoopConditionParserGoldenTest.kt` | REPLACE with `LoopConditionValidatorTableTest` over the new table | |
| `.../CelLoopConditionParserRobustnessTest.kt` | REWRITE: keep the hostile-input timing/no-throw, depth 32/33, column and `StackOverflowError` cases; drop unary-minus folding, macro-as-function and literal cases; add the D-28 hostile evaluation list and the function-list pin | |
| `continuum-orchestration-service/.../workflow/loop/LoopConditionRuntimeCheck.kt` (with `FlowVariableTypeClass`) and `LoopConditionRuntimeCheckTest.kt` | DELETE | D-29: CEL decides |
| `continuum-orchestration-service/src/test/.../fixtures/StructuredTestLoopConditionEvaluator.kt` | REPLACE with `ScriptedLoopConditionEvaluator` (outcomes per call, no text parsing) | BE-14 keeps an engine-free seam |
| `.../workflow/loop/LoopConditionEvaluators.kt` (holder) | KEEP | |
| `continuum-orchestration-service/src/test/.../spike/CelInWorkflowSpikeTest.kt` | KEEP (regression test of in-workflow cel-java + replay) | |
| `continuum-commons/.../node/builtin/LoopNodeDescriptor.kt` + `LoopNode.doc.md` + `LoopNodeDescriptorTest` | CHANGE: condition description (full CEL), new `onNonBooleanResult` property (enum `fail`/`treat-as-false`, default `fail`, required) and UI-schema control after `onLimit`; the doc's "The condition" and "When a run fails" sections rewritten | D-23, D-31 |
| Docs: root `AGENTS.md` (module table and Child DOX Index text "parser"), `continuum-commons/AGENTS.md` (`validation.loop` bullet: golden-file/twin rule removed, test-fixtures sentence removed), `continuum-loop-condition/AGENTS.md` + `README.md` (pipeline, options, environment, evaluator), `continuum-orchestration-service/AGENTS.md` (runtime-check bullet, evaluator seam), `docs/api/ENDPOINT_INVENTORY.md` (condition rule TODO becomes the V-rules), `release_notes/RELEASE_NOTES_v0.0.23.md` (lines describing the subset, `CelLoopConditionParser`, golden table) | CHANGE | house rules 1-3; 0.0.23 is unreleased, so the lines are corrected in place |

## Impact on plan tasks (for the planner)

Done tasks that need rework:
- **BE-4 (DONE → REWORK)**: the contract becomes D-37/D-38: validator/evaluator interfaces, issue codes and limits,
  plus the backend CEL table (D-35), now inside `continuum-loop-condition` instead of commons. The commons golden file,
  its fixtures and `LoopConditionTest` go away. The Contract rows "Condition grammar", "Golden vectors" and "Rejection
  reason codes" are replaced. The "propertiesSchema" row gains `onNonBooleanResult`.
- **BE-5 (DONE → REWORK)**: the AST shape gate becomes `CelLoopConditionValidator` (D-26: pre-checks kept; macros ON;
  type-check with `vars: map(string, dyn)`; bool-or-dyn result rule; literal validators; re-bound-name rule;
  read-variable extraction) on the shared `LoopCelEnvironment` (D-24). The robustness test is rewritten (D-38).
- **BE-13 (DONE → REWORK)**: the evaluator interface returns `LoopConditionOutcome` (D-29, D-31). The runtime
  pre-check `LoopConditionRuntimeCheck` is deleted. The structured test evaluator becomes a scripted one.
- **BE-3 (DONE → small change)**: `onNonBooleanResult` in the schema, UI schema and doc; condition description.
- **BE-8 (HELD → resume, changed)**: the condition rule calls the injected `LoopConditionValidator` (SCOPE-2 stands,
  renamed), gives one error per V-rule and W-1 within the D-27 budget, and carries over the AUTHORING merged value.
- **BE-9 (IN PROGRESS)**: the tool-description text per D-36 (already told to stay grammar-neutral).

Not done yet, also affected:
- BE-14: `onNonBooleanResult` handling; `Workflow.sideEffect` around the evaluation (D-30); outcome-based branching.
- BE-15: real evaluator with the D-28 guards, value conversion and warm-up; the replay test uses a full-CEL condition.
- BE-16: the defensive re-check uses `LoopConditionValidators.default`; it also checks `onNonBooleanResult`.
- BE-17: measure the per-check event cost and include it in the budget.

Frontend:
- **FE-3**: no hand-written D-2 parser. It becomes `LoopConditionQuery.ts`, the RQB adapter (fit test, field mapping,
  emit) with the RQB round-trip table. It now needs P-2, and the backend dependency becomes "none" (it no longer
  consumes the BE-4 golden file).
- **FE-8**: the renderer has two modes plus a toggle (D-32). CEL mode needs no P-2 (code editor + debounced
  `validateWorkflow`), so it can land first. It adds the `onNonBooleanResult` control (the schema renders it; just
  check it appears) and hides Retry Policy as before.
- **FE-10**: the builder supports groups, combinators and `not` (no longer one rule). It uses RQB `formatQuery('cel')`
  and `parseCEL` through FE-3's adapter. It starts with the D-33 spike that pins RQB behaviour.
- **FE-11**: teach D-36 instead of D-2/D-3.
- **FE-5**: the warning badge from `parseLoopCondition` is removed (D-32). The condition summary on the box shows the
  text, truncated.
- **AC-29, AC-34, AC-35, AC-36, AC-39, AC-40** need rewriting. AC-40's "only FE-10 imports react-querybuilder" becomes
  "only FE-3's adapter and FE-10".

- **P-2 (changed)**: still a user action (`react-querybuilder` in `workflow-editor-extension/package.json` plus the
  regenerated `yarn.lock`; `@react-querybuilder/material` only if the user wants MUI styling). It now blocks FE-3
  (adapter + table), FE-10 and the builder half of FE-8, not FE-10 alone. It has to include the version that ships
  `parseCEL` (the RQB parser entry point; the exact import path and version are NOT VERIFIED offline). The user said
  they will add it. The workbench machine (codespace) can install it, so the planner may make P-2 the first FE step
  there, with the user's consent.

## Updated risks (Round 3)

| Risk | Impact | Mitigation |
|---|---|---|
| Unmetered extension functions (`lists.range`, quadratic `sets.*`, `replace`, `cel.bind` doubling) stall or OOM the workflow thread (verified) | Deadlock-detector failures, orchestrator OOM | D-28 size guard through `trace`, iteration budget, robustness test with the verified hostile list |
| cel-java `cel.bind` re-binding bug (`StackOverflowError`, verified) | Workflow task failure | V-8 rejects it; `StackOverflowError` caught at evaluation |
| Validator and evaluator environments drift | Text valid at validate time fails at run time, or the reverse | one `LoopCelEnvironment` factory (D-24) and a function-list pin test |
| cel-java or JDK tz-data upgrade changes a result mid-run | Non-determinism on replay | `Workflow.sideEffect` records the outcome (D-30) |
| CEL error semantics surprise authors (`"a" == 1` false; `||` absorbs a missing variable) | A loop runs longer than expected | documented in the doc, prompts and table; `maxIterations` guard |
| RQB `parseCEL`/`formatQuery('cel')` cannot map `vars["x"]` or is lossy (NOT VERIFIED) | The builder cannot reopen its own output | lossless round-trip fit test; placeholder contingency (D-33); FE spike first |
| Server-side validation from the dialog adds latency or needs a reachable api-server | Feedback lag; "Not checked" offline | debounce; explicit "Not checked" state; validate/run start stay authoritative |
| Rework of done BE tasks (BE-4/5/13) touches tested code | Regressions | D-38 table as one rework task; full five-module `--offline` test run |
| `sideEffect` adds history events | Lower real iteration ceiling | BE-17 measures it; budget formula includes it |

## Revised draft acceptance outcomes (Round 3; replace the condition-related ones of Rounds 1 and 2)

- Given conditions `vars["page"] < 10 && vars["hasMore"]`, `"nextCursor" in vars`,
  `vars["items"].exists(i, i > 2)`, `vars["name"].lowerAscii() == "x"`, `math.greatest(vars["a"], 1) > 0`,
  `vars[?"r"].orValue(0) < 3`, `timestamp(vars["ts"]) > timestamp("2026-01-01T00:00:00Z")`, When validated, Then there
  are no condition errors.
- Given `now() > 1`, `foo == 1`, `vars["p"] + 1`, `vars["a"] ==`, `vars["s"].matches("(")`,
  `cel.bind(a, 1, cel.bind(a, a + a, a > 0))`, 33-deep nesting, or 2,049 characters, Then exactly one error each with
  `node` = loop key, `property` = `condition` and the matching code (V-1..V-8). Start answers 400, validate answers
  200 `valid:false`, and the MCP tools return it in `errors`.
- Given condition `true`, Then exactly one condition warning (W-1) and no error.
- Given `vars["hasMore"]` (a `dyn` result) and the variable is `"yes"` in iteration 2: with `onNonBooleanResult: fail`
  the loop is FAILED and the message names the type, value preview and iteration; with `treat-as-false` the loop ends
  WARNING after iteration 2 and downstream runs.
- Given `vars["hasMore"] == true` and `hasMore` is not set, Then the run fails with a message naming loop, variable
  `hasMore` and the iteration. Given `vars["hasMore"] == true || vars["page"] > 3` with `page = 5` and no `hasMore`,
  Then the loop continues (CEL semantics).
- Given `lists.range(1000000).size() > 0`, the 10-step `replace` chain, or `sets.contains` over two 60,000-element
  lists, Then the evaluation stops within 100 ms with `LIMIT_EXCEEDED`, and the orchestrator neither throws nor runs
  out of memory.
- Given a recorded 3-iteration run with a full-CEL condition, Then `WorkflowReplayer` replays it without
  non-determinism, and the condition outcome is in history (side effect).
- Given the validator and the evaluator, Then both report the same enabled function names (pin test), and every row of
  the backend CEL table passes in both.
- Given the dialog and a condition the builder made (nested `and`/`or`, `not`, dotted names, escapes), When reopened,
  Then it opens in the builder unchanged; Given a condition with a macro, `has`, arithmetic or an extension, Then it
  opens in CEL mode, the toggle to the builder is disabled with the reason, and the text is kept; in both modes the
  issues returned by `validateWorkflow` for this loop's `condition` are shown, or "Not checked" when the api-server is
  unreachable.
- Given the RQB round-trip table, Then the spec passes, and every `emitted` text is a valid row in the backend table.
- Given the skill and the Theia prompt, Then both carry the D-36 text and the same examples.
- Given the cleaned-up modules, Then none of the D-38 "DELETE" classes or files exist, commons has no test fixtures,
  and the five-module `--offline` test run passes.

## Superseded by this round (planner: read these as replaced)

- D-2, D-3 (subset grammar and exclusions) → D-23, D-25.
- D-5 (AST gate, "ONE flow variable") → D-26; the one-variable rule is dropped.
- D-7 (hand-written fallback) → withdrawn; cel-java is in place (BE-1).
- D-8 (pre-evaluation runtime check) → D-29, D-31.
- D-9 (hand-written TS parser, single-rule RQB, shared golden table) → D-32, D-33, D-35.
- D-10 (invalid string handling) → D-26 (backend) and D-32 (dialog); the canvas badge is dropped.
- D-11 (MCP/skill teaching) → D-36.
- D-17 (pure in-workflow evaluation, local-activity fallback) → D-30.
- D-1 amended by D-31 (new property); D-15 budget amended by D-30; D-19 stands (no static "variable is produced" check;
  W-1 only covers "reads none").
- Round 1/2 acceptance outcomes about the triple, the canonical form, the golden table in two languages, AC-22's
  type-class rule and AC-36's badge → the Round 3 outcomes above.
- Plan Contract rows "Condition grammar", "Golden vectors", "Rejection reason codes" → D-35, D-37.

## Size (Round 3)

Still XL. Backend: +M (one cleanup/rework task over BE-4/5/13, the validator rules, the evaluator guards and the side
effect). Frontend: about the same as before (the hand-written parser is gone, the two-mode renderer and RQB groups are
added), with the RQB behaviour NOT VERIFIED until P-2.

## Open questions

None. Answer 4 is resolved as a Loop node property (D-31), for the reasons given there. The user can still change it at
Gate 1, for example by renaming `treat-as-false` or making it the default. A separate "stop with warning" value would
behave exactly like `treat-as-false`, so it is not offered. The cap values in D-28 are planner defaults, not user
decisions.
