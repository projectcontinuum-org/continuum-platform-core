# Docs — Loops
## Round 1

### Target version
`platformVersion` = 0.0.23 (`continuum-platform-core/gradle.properties`). `continuum-docs/versions.json` = `["0.0.20"]`; 0.0.23 is not released. Edited `continuum-docs/docs/` ("next") only. No `versioned_docs/` file touched, no `docs:version` snapshot run. Branch `platform/0.0.23` (unchanged, no git writes).

### Pre-existing local changes in continuum-docs (left untouched)
- `docs/contributing/create-new-nodes/step-6-ship-it.md` (+5 lines: flow-variable size env var, "keep the worker on the platform version" warning)
- `docs/deployment/helm-charts.md` (+17 lines: "Upgrade" section and the 0.0.23 warning)

Both diffs are identical before and after my work (`git diff --stat`: +5 / +17). I do not link to the `## Upgrade` anchor of `helm-charts.md` (an uncommitted edit); the operator page links to the page only.

### Pages
| Page | Created/Updated | What a reader learns | Source |
|---|---|---|---|
| `docs/feature-nodes/loop.md` (🔁 Loop, position 5) | Created | What a loop does; settings table (`condition`, `check`, `maxIterations`, `onLimit`, `onNonBooleanResult`, defaults); the CEL condition (`vars["name"]`, available functions/libraries, limits); carried flow variables; statuses, messages, every way a loop fails the run (run → `FAILED`); skipped/empty loops; iteration ceiling table; workflow JSON (`type: "Loop"`, `parentNode`, `data.collapsed`) and loop validation errors/warnings on start/validate/schedule; MCP spec `parent`; planned-workbench admonition | `continuum-commons/.../node/builtin/LoopNode.doc.md`, `LoopNodeDescriptor.kt`, `release_notes/RELEASE_NOTES_v0.0.23.md`, `docs/api/ENDPOINT_INVENTORY.md`, `McpWorkflowSpec.kt`, `LoopStatusPublisher.kt`, `LoopRunner.kt`, `LoopReset.kt` |
| `docs/architecture/loops.md` (🔁 Loops: How They Run, position 5) | Created | Components; model + `$builtin` registration with retry (5 attempts, 5/10/20/40 s); iteration engine (start, reset, carried variables, progress never decreases, 40,000-event history guard); FAILED vs FINISHED publishing; `Workflow.sideEffect` per check; time-boxed 4-thread pool (250 ms wait, queue 200, outcome table); Temporal-paced retry; validation; terminate writes TERMINATED after 5 s; contributor notes: `continuum-loop-condition` module, why separate, protobuf-java-util pin 4.36.2, test table, commands | `continuum-loop-condition/README.md`, `TimeBoxedConditionEvaluationRunner.kt`, `LoopConditionCheck.kt`, `LoopConditionEngineConfig.kt`, `gradle/libs.versions.toml`, `WorkflowService.kt` (`STOP_SIGNAL_GRACE_PERIOD_MS = 5000`), release notes |
| `docs/deployment/loops-operations.md` (🔁 Operating Loops, position 2) | Created | Upgrade order and what changes (schema registry needed, protobuf 4 in two images, `FAILED` run status for clients, `TERMINATED` record, new optional JSON fields); the fixed 4 evaluation threads; known limits with symptoms, measured numbers and recovery: cross-tenant delay (never failure), self-wedge, many slow checks vs 10 s workflow-task timeout, terminate ≈ 5 s; iteration ceiling | release notes (Upgrade notes, known limitations), `LoopConditionEngineConfig.kt`, application.yaml |
| `docs/architecture/orchestration-service.md` | Updated | "Failed runs are FAILED" note; new "Loops" section pointing to the design page; terminate row (5 s, API server writes `TERMINATED`); determinism rule for recorded condition checks; schema-registry variable now also used for Loop registration | release notes, `ContinuumWorkflow` publishing notes |
| `docs/architecture/node-model.md` | Updated | New "Built-in nodes" section: the Loop is registered by the orchestrator, `$builtin` marker | release notes, `ReservedIdRules.kt` (checked: `$` is reserved for node keys/port ids) |
| `docs/architecture/overview.md` | Updated | Orchestration service row mentions the built-in Loop | — |
| `docs/contributing/create-new-nodes/recipes.md` | Updated | New recipe "Set a flow variable for a loop condition" (`executionContext.setFlowVariable`), workers need no change for loops | `ExecutionContext.kt` (`setFlowVariable`, `getFlowVariable`) |
| `docs/ecosystem/repositories.md` | Updated | platform-core row mentions the Loop and the `continuum-loop-condition` library | — |
| `docs/feature-nodes/_category_.json` | Updated | Category description includes the built-in Loop | — |

### Sidebar / navigation changes
Sidebar is auto-generated, so no `sidebars.ts` change. New pages carry `sidebar_label` with an emoji and `sidebar_position` (Loop 5 in Feature Nodes, Loops 5 in Architecture, Operating Loops 2 in Deployment). No new `_category_.json`. `sidebars.ts` not touched (no typecheck needed).

### Verification
- `npm run build` in `continuum-docs` (node_modules present, no network needed; local policy does not restrict docs) → **PASS**: `[SUCCESS] Generated static files in "build"`, no broken-link warnings; `build/docs/next/feature-nodes/loop`, `deployment/loops-operations` exist (`build/` is git-ignored).
- `npm run typecheck` → not needed (no `.ts`/`.tsx` touched).
- Factory-ID / artifact scan (`BE-n`, `AC-n`, `D-n`, `PR-n`, `BR-n`, `F-n`, `SCOPE`, `.factory`, ticket id) over all new and edited pages → no matches.
- Mermaid diagrams are not validated by the build (theme renders client-side): NOT VERIFIED in a browser (syntax kept to constructs already used elsewhere in the docs, plus one `subgraph` with edges to it in `loop.md`).
- The Kotlin recipe in `recipes.md` is NOT compiled (it says so on the page); API names checked against `ExecutionContext.kt`.

### Not documented on purpose
- **Workbench loop UI** (box drawing, drag-in/out membership, collapse, condition dialog / query builder, palette drop, hasCycle in the editor, run display in the editor, Claude Code skill / Theia prompt changes): frontend is not built (on hold). `loop.md` carries a "planned, not shipped" admonition and tells readers to use the REST API or MCP tools today. The operator page says the workbench with loop support is not yet released.
- Open minor/nit review findings (loop-start re-check not in history, positional args, stale wording, bridge SQL-text test, pending stop lost if the API server restarts within 5 s): not user-visible behaviour.
- Cold-start Temporal "deadlock detected" on 0.5 CPU (observed, not a loop fault; user: separate improvement) and "terminate right before a natural end stays TERMINATED"/"terminate after a never-completed cancel shows TERMINATED": omitted, not decided as documented behaviour.
- NOT VERIFIED live (not claimed as tested): the retry of the Loop registration publish (unit-tested only), the literal 9,000-element-list `sets.*` shape, a stuck run that ends by itself within the 5 s terminate grace. Documented behaviour for these comes from the code/KDoc and release notes, without claiming live results.
- Internal class names appear only on the architecture page's contributor section and in the contributor-facing recipe/architecture text; the user guide uses none.

### SCOPE / questions
- SCOPE: the Loop page sits under "Feature Nodes" (its category text was widened) rather than in a new "Workflows" section; a dedicated section can be added later when the workbench UI ships and there is more to say about building loops on the canvas. Tell me if you prefer it elsewhere.
- SCOPE (follow-up for later, not done): when the workbench loop UI ships, `loop.md` needs a "Build a loop on the canvas" section and the "planned" admonition removed; the same for the Operating Loops upgrade line about the workbench.
- The operator upgrade bullets overlap with the user's uncommitted "Upgrade" section in `helm-charts.md`; I did not edit that file. The user may want to link the two pages when they commit it.
