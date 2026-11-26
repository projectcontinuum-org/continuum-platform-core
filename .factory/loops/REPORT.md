# Loops — Feature report
Date: 2026-11-26 · Status: **NOT READY end to end** (backend: READY WITH OPEN ITEMS; frontend not started, on user hold)

## Overview
The platform can now run a **Loop**: a built-in node that repeats the nodes inside it for as long as a condition holds.
The condition is a full [CEL](https://cel.dev) expression over the flow variables, for example
`vars["page"] < 10 && vars["hasMore"]`. The loop checks it before or after each pass, stops at a configurable iteration
limit (fail or stop with a warning), and fails the run when a member node fails. Loops can be nested. Flow variables set
inside a loop reach the next pass and the nodes after the loop. A run with a failed node or loop is now recorded as
`FAILED`, and a terminated run is always recorded as `TERMINATED`.

**The backend is complete and verified. The workbench part (drawing a loop box, the condition dialog, run display) is not
built.** It is on hold, to be done in the codespace. Until then loops can only be created and run through the REST API or
MCP tools, so the feature is not shippable end to end.

> **User story:** As a workflow author, I want a group of nodes to repeat while a condition on flow variables holds, so
> that I can page through an API or retry until a result is ready, without copying nodes.

## How to use it
Today (API only; the workbench cannot draw loops yet):
- **REST:** `POST /api/v1/workflow` (start) or `/validate` with a workflow whose `nodes[]` holds a loop entry
  (`type: "Loop"`, `data.nodeModel: org.projectcontinuum.core.builtin.Loop`, properties `condition`, `check`
  (`before`/`after`), `maxIterations` 1–10,000, `onLimit`, `onNonBooleanResult`). Member nodes carry `parentNode: "<loop id>"`.
  Edges never name the loop. Invalid loops come back as 400 (start) or `valid: false` (validate), one error per problem,
  naming the loop and the property.
- **MCP:** `validateContinuumWorkflow` / `buildContinuumWorkflow` accept a loop node and members declaring `parent`.
- The Loop is listed under the "Flow Control" palette category once the new orchestration service has started.
- Planned in the workbench: drop "Loop" from the palette, drop nodes inside the box, edit the condition in a dialog
  (query builder or CEL text), collapse the box. Not built (see Open items).

## What changed
| Repo | Area | Change | Key files |
|---|---|---|---|
| continuum-platform-core | commons | Loop model (`type: Loop`, `parentNode`, `collapsed`); validation split into small rule files; loop structure/condition rules; built-in Loop descriptor and docs | `model/LoopNode.kt`, `validation/*Rules.kt`, `validation/loop/`, `node/builtin/` |
| continuum-platform-core | new module `continuum-loop-condition` | CEL engine (cel-java 0.15.0) kept out of commons so feature workers get no protobuf 4 | `continuum-loop-condition/` |
| continuum-platform-core | orchestration-service | Iteration engine, carried flow variables, 40,000-event history guard, recorded condition checks (replay-safe), time-boxed 4-thread evaluation pool (200 ms budget), Loop registration with retry, `FAILED` published for failed runs | `workflow/loop/`, `ContinuumWorkflow.kt`, `WorkflowDagExecutor.kt`, `FlowVariableMerger.kt`, `registration/`, `config/LoopConditionEngineConfig.kt` |
| continuum-platform-core | api-server | Loop validation on start/validate/schedule, MCP loop spec (`parent`), terminate writes `TERMINATED` after the 5 s grace | `WorkflowService.kt`, `WorkflowStopScheduler.kt`, `mcp/*`, `WorkflowRunRepository.kt` |
| continuum-platform-core | message-bridge | A later non-`TERMINATED` event no longer reopens a `TERMINATED` run | `repository/WorkflowRunRepository.kt` |
| continuum-platform-core | build / docs | New module in `settings.gradle.kts`, version pins, CI path filter, release notes 0.0.23, AGENTS/README, endpoint inventory | `settings.gradle.kts`, `gradle/libs.versions.toml`, `.github/workflows/openapi-spec-check.yml`, `release_notes/RELEASE_NOTES_v0.0.23.md` |
| continuum-system-tests | API suite | New live loop suite (249 checks) and scenario builders; tenant/config helpers; `make api` includes it | `suites/api/loops.js`, `lib/loopmodel.js`, `lib/loopscenarios.js`, `test/loop*.test.js` |
| continuum-docs | docs ("next") | 3 new pages, 6 updated (below) | see Documentation |
| continuum-workbench, continuum-feature-base | — | **No changes** (workbench on hold; feature-base only used to run tests) | — |

### API changes
No new endpoints and no new operationIds. Spec: `continuum-platform-core/docs/api/continuum-api-server.openapi.yaml`
(Redocly lint PASS, `OpenApiSpecTest` PASS), inventory: `docs/api/ENDPOINT_INVENTORY.md`.
- `Node.parentNode` and `NodeData.collapsed` (both optional, additive); `Node.type` now documents `BaseNode` and `Loop`.
- Descriptions only: run status `FINISHED` now means "no node failed", `FAILED` means a node or loop failed;
  `terminateWorkflow` and `updatedAt` text describe the `TERMINATED` record.
- Clients regenerate nothing (no schema break), but the workbench client should pick up the two new fields before the frontend work.

### Documentation
`continuum-docs`, **next** (0.0.23 is unreleased; no versioned snapshot touched), branch `platform/0.0.23`. `npm run build` PASS, no broken links.
- New: `feature-nodes/loop.md`, `architecture/loops.md`, `deployment/loops-operations.md`.
- Updated: `architecture/orchestration-service.md`, `architecture/node-model.md`, `architecture/overview.md`,
  `contributing/create-new-nodes/recipes.md`, `ecosystem/repositories.md`, `feature-nodes/_category_.json`.
- The workbench loop UI is documented **as planned**, not shipped (admonition on the Loop page); it needs a "Build a loop on the canvas" section when the frontend ships.
- Mermaid diagrams were not rendered in a browser; the Kotlin recipe was not compiled.

### Breaking changes / upgrade notes
- Run status: runs with a failed node or loop are now stored `FAILED` (before: `FINISHED`). Clients filtering on status may see a change (also for loop-free runs).
- A terminate after a cancel that never completed now ends as `TERMINATED` (before: stayed `CANCELLED`).
- Schema registry must be reachable by the orchestration service (it registers the Loop at start, 5 attempts with backoff).
- protobuf 4 / guava arrive only in the orchestration-service and api-server images; workers are unchanged.
- Deploy orchestration-service, api-server and message-bridge together. Details: release notes 0.0.23 and `deployment/loops-operations.md`.

## Acceptance criteria
43 criteria (AC-36 withdrawn): **17 VERIFIED · 15 PARTIALLY VERIFIED · 11 NOT VERIFIED**. IDs are those of `02-plan.md`.
"Live" = loop suite (`suites/api/loops.js`) against the changed stack, final round 249/249.

| AC | Criterion | Status | Evidence |
|---|---|---|---|
| AC-3 | `parentNode`/`collapsed` optional and round-trip | VERIFIED | live: run snapshot and stored schedule keep both fields; unit |
| AC-6 | Loop appears in metadata, search, tree, docs after orchestrator start | VERIFIED | live: 8 registry checks incl. MCP and cross-tenant |
| AC-7 | Loop settings validated (incl. `onNonBooleanResult`) | VERIFIED | live: 7 invalid-setting cases, defaults valid |
| AC-9, AC-10 | Edge to a loop invalid; leave-and-return invalid, valid chain accepted | VERIFIED | live suite |
| AC-11 | Loop-free workflows unchanged | VERIFIED | live: `make api` (7 suites), smoke 10/10, tenancy 32/32, `npm test` 77/77 |
| AC-13 | Rejected/accepted CEL conditions over HTTP | VERIFIED | live: 7 rejected (one error each), 9 accepted, 2,049-char schema error |
| AC-14 | MCP loop spec, build and validate | VERIFIED | live MCP checks (plan: unit) |
| AC-15 | Loop has no activity; no init failure | VERIFIED | live: all loop runs start and finish |
| AC-16, AC-17 | Multi-iteration stop; check before/after | VERIFIED | live, driven by default flow variables; Temporal history shows 3 and 2 runs of the members. Not the literal paging example (no node sets `hasMore`) |
| AC-18, AC-19 | Limit policies; member failure in iteration 2 | VERIFIED | live: statuses, `$error` text, run recorded `FAILED` |
| AC-20 | Nested 2×3, iteration resets | VERIFIED | live; inner count (6) read from Temporal history by hand, field-level reset is unit |
| AC-21 | Carried variables, entry order, node after loop | VERIFIED | live: node after the loop inherits last-iteration variables |
| AC-25 | Progress never decreases; "Iteration k of n" | VERIFIED | live: sampled during run |
| AC-44 | `onNonBooleanResult` fail / treat-as-false | VERIFIED | live: iteration 2 and `check: before` |
| AC-5 | Loop registration at start, with retry | PARTIALLY VERIFIED | live: first attempt seen; retry/backoff unit-only (no broker outage provoked) |
| AC-22 | CEL runtime semantics | PARTIALLY VERIFIED | live (a)–(d); (e) JSON→CEL types live NOT VERIFIED (only Long/String reachable); unit |
| AC-24 | Run-history budget warning | PARTIALLY VERIFIED | live: over-budget warning; under-budget and calibration unit |
| AC-43 | Cost/time limits, no stalled workflow task | PARTIALLY VERIFIED | unit hostile list; live: one 1.2 s call → `FAILED`, 0 failed workflow tasks, also at 0.5 CPU. Literal 9,000-element list shape not live |
| AC-1, AC-2 | Gradle resolve/classpath; cel-java in a Temporal workflow replays | PARTIALLY VERIFIED | unit/build; six modules PASS `--offline` (184/239/174/259/40/4) |
| AC-4 | OpenAPI documents new fields | PARTIALLY VERIFIED | `OpenApiSpecTest` + Redocly PASS |
| AC-8 | Structure rules (nesting, cycles, bad parent, type/model) | PARTIALLY VERIFIED | unit; "member of two loops" by model reading |
| AC-12 | CEL validator table | PARTIALLY VERIFIED | unit |
| AC-23, AC-26, AC-27, AC-42 | History guard; replay; direct-Temporal start; one CEL environment | PARTIALLY VERIFIED | unit/Temporal test server only; not reachable over HTTP |
| AC-28, AC-41 | Docs/release notes; clean-up of the old subset | PARTIALLY VERIFIED | review APPROVED, build + grep; workbench release notes not written (frontend) |
| AC-29–AC-35, AC-37, AC-38 | Query-builder adapter, topology, drag-in/out, collapse, dialog, run viewer, no regression in editor | NOT VERIFIED | frontend not started (user hold) |
| AC-39, AC-40 | Prompt twins carry loop rules; only the adapter imports `react-querybuilder` | NOT VERIFIED | frontend not started |

## Quality
- **Unit tests** (six modules, `--offline`, 0 failures): commons 184 · loop-condition 239 · orchestration 174 · api-server 259 · worker-starter 40 · message-bridge 4.
- **System tests** (round 5, live stack built from the working tree): loop suite 249/249 ×3, `make api` exit 0, smoke 10/10, tenancy 32/32 (`isolation_violations` 0), system-tests own `npm test` 77/77. 0 `WORKFLOW_TASK_FAILED` in loop histories except the deliberate stuck-run probes.
- **Browser tests:** none (no frontend).
- **Reviews:** backend APPROVED (round 5, no BLOCKER/MAJOR); frontend n/a.
- **Fix rounds:** backend 4 used (4th approved by the user) plus one test-only round; frontend 0.
- Findings fixed during the feature: node after a loop lost carried variables; empty/all-skipped loop spinning to 10,000; slow conditions stalling a workflow task; shared evaluation pool failing other runs' loops; failed runs stored `FINISHED`; terminate of a stuck run left `RUNNING`.

## Open items
**Frontend (blocking end-to-end):** FE-1..FE-12 in `02-plan.md` NOT STARTED, on user hold, to be done in the codespace
(`urban-rotary-phone-746rr5q9j62v4v`, branch `CONTNM-180` to be created from origin/main). Precondition outstanding: user adds
`react-querybuilder` and updates `yarn.lock`. Offload to Claude Code in the codespace as agreed.

**Open review findings** (`07-backend-review.md`), none blocking:
| Finding | Severity | Summary |
|---|---|---|
| BR-4 | MINOR | Loop-start condition check is not recorded in history; a cel-java upgrade could break replay of an in-flight run |
| BR-5 | MINOR | Positional argument at a changed call site (`WorkflowDagExecutor.kt`) |
| BR-6 | MINOR | Stale wording from the earlier design in a Gradle comment, version catalog and release notes |
| BR-7, BR-8 | NIT | Literal checks ignore the injected environment; nested loop restarts re-validate the condition |
| BR-16 | MINOR | Bridge `TERMINATED` guard is tested via SQL text only; behaviour checked by hand on Postgres (accepted) |
| BR-18 | NIT | A pending terminate is lost if the api-server restarts within the 5 s grace (pre-existing) |

**Decided behaviours:** terminate after a never-completed cancel shows `TERMINATED`; terminate right before a natural end stays `TERMINATED`.

**Known limits (documented in release notes and docs):**
- Condition checks share one fixed pool of 4 threads across all tenants; a slow condition can delay other runs' loops (never fails them).
- Self-wedge: one run with more than 4 parallel very slow loops can keep retrying and stay `RUNNING` until terminated.
- Many slow checks in one workflow task can exceed the 10 s workflow-task timeout (documented only).
- Cold start on 0.5 CPU can show a one-time Temporal deadlock warning (TMPRL1101) on the first task, for loop-free runs too. Separate improvement, not done.

**NOT VERIFIED live:** Loop registration retry (AC-5); JSON→CEL value types (AC-22e); the literal 9,000-element-list `sets.*` shape;
a stuck run that ends by itself inside the 5 s terminate grace (unit only); all unit-only ACs listed above; mermaid rendering in the docs.

**Other:** leftover git-ignored `continuum-platform-core/.continuum-cache/` (could not be deleted by the factory); delete it by hand.
Docs follow-up: Loop page sits under "Feature Nodes" (alternative: a new Workflows section).

## User actions before merge
1. Commit and push `CONTNM-180` in **continuum-platform-core** and **continuum-system-tests** (91 and 11 uncommitted paths; the factory never commits). Exclude nothing; `.continuum-cache/` is git-ignored.
2. Then pull `CONTNM-180` in the codespace's `continuum-platform-core` (replacing the copied spec snapshot) **before releasing the frontend work**.
3. Frontend precondition: add `react-querybuilder` and update `yarn.lock` (the factory does not run yarn installs); then release the frontend work.
4. continuum-docs: decide how to handle branch `platform/0.0.23`. Commit only the factory's 9 paths; `step-6-ship-it.md` and `helm-charts.md` are your own uncommitted edits. Consider linking the new "Operating Loops" page to the Upgrade section you added in `helm-charts.md`, and confirm the Loop page placement.
5. Release notes for 0.0.23 in platform-core are already updated; a workbench release-notes entry is still due when the frontend ships.
6. continuum-charts: 3 uncommitted files (`README.md`, `Chart.yaml`, `NOTES.txt`) are not part of this feature; handle separately.
7. OpenAPI lint already passed; client regeneration is only needed for the frontend work (in the codespace, with `openapi-generator-cli`).

## Discrepancies
- No artifact claims a file that the diff does not show, and no changed file lacks an artifact entry. Repo states match: platform-core and system-tests on `CONTNM-180` uncommitted; workbench and feature-base clean on `main`; docs on `platform/0.0.23` with 8 modified + 3 new paths, of which `step-6-ship-it.md` (+5) and `helm-charts.md` (+17) are pre-existing user edits; charts has 3 pre-existing user files.
- The system-tests round 5 notes say the `Makefile` is unchanged; that holds for that round only. The diff shows the one-line change from an earlier round (loops added to `API_SUITES`).
- `03-backend-dev.md` numbers the last two rounds "Round 9" and "Round 10"; `state.md` calls them fix rounds 4 and 5. Same work, different counting.
- `state.md` still reads "Phase: 6 — report" and "Fix rounds: BE 4 / FE 0"; the test-only round after the 4th is not counted there.
- Unit-test counts: api-server 259 comes from `03-backend-dev.md` round 10 (the reviewer's 257 predates the test-only round). Not re-run by the reporter.
