# continuum-platform-core 0.0.21-beta (proposed)

Changes on `origin/main` since [0.0.20-beta](https://github.com/projectcontinuum-org/continuum-platform-core/releases/tag/release%2F0.0.20) (`release/0.0.20`).

This release makes the api-server MCP server a complete workflow-building toolset, fixes activity cancellation in the worker starter, and adds licensing, contributor docs and repository cleanup. `platformVersion` is bumped to `0.0.21`. `continuum-orchestration-service` has no changes.

## Highlights

- **Workflow validation and build tools in the MCP server.** Two new tools, `validateContinuumWorkflow` and `buildContinuumWorkflow`, check a compact workflow spec against the live node registry and produce the complete `.cwf` content. Client AI agents (the workbench WorkflowBuilder, Claude Code) now need only a prompt plus these tools.
- **Node cancellation actually stops the node.** A cancelled, timed-out or shut-down activity now interrupts the node's thread and is reported to Temporal as cancelled, even if the node swallows the interrupt.
- **Smaller MCP responses.** The list and search tools return a compact node form without icons or schemas.
- **Apache 2.0 license, `CONTRIBUTING.md`, and a single `AGENTS.md`.**

## What's Changed

- CONTNM-147: Add more tools into the MCP server to make client AI agents as thin as possible (#149)
- CONTNM-131: Add LICENSE and fix links (#132)
- CONTNM-132: Add LICENSE to the README (#134). In practice this adds the consolidated project reference to `AGENTS.md`.
- CONTNM-135: Keep a single `AGENTS.md` (#136)
- CONTNM-140: Stops tracking `package-lock.json` files and edits `.gitignore`.

## Details

### continuum-api-server: MCP server

New tools (`ContinuumWorkflowMcpTools`). Both take the compact spec `(name, nodes[{key, nodeId, properties}], edges[{from, fromPort, to, toPort}])`:

- **`validateContinuumWorkflow`** (`WorkflowSpecValidator`) checks:
  - every `nodeId` is registered and node keys are unique;
  - every edge goes from an existing output port to an existing input port;
  - each input port has at most one edge, and there are no cycles;
  - each node's properties, merged over the registry defaults, match its JSON Schema, with no unknown keys and every required value set.

  It collects every problem in one pass. The result is `{valid, nodeCount, edgeCount, errors[], warnings[]}`, returned as a normal result (never `isError`). Each issue names its `node`, `edge` or `property`. Warnings, such as an unconnected input port, do not make the workflow invalid.
- **`buildContinuumWorkflow`** (`WorkflowSpecBuilder`) validates first. When valid, it returns `{valid, fileName, errors, warnings, workflow}`:
  - `workflow` is the complete `.cwf` content as a `ContinuumWorkflowModel`, with full registry node metadata, properties merged over the defaults, edges, and an automatic layout (longest-path depth).
  - `fileName` is the workflow name as a slug plus `.cwf`.
  - When invalid, `workflow` and `fileName` are omitted.
- An empty `nodes` list is valid (warning "The workflow has no nodes yet.") and builds an empty workflow. A client can save the file first and rebuild it after each added node.

New tool in `ContinuumNodeMcpTools`:

- **`getContinuumNodeDocumentation`** returns a node's markdown documentation. A node with no documentation returns a hint to use the property descriptions instead.

Changed tool behavior (clients may depend on these):

- `listContinuumNodeCategories` and `searchContinuumNodes` now return compact `McpNodeTreeItem` results (type, id, name, description, input and output port names, children). Icons, schemas and defaults are left out. Use `getContinuumNodeMetadata` for the full node.
- `searchContinuumNodes` matches the query case-insensitively as one literal substring of each node's name, description, documentation and id. The tool description now tells models to use a single distinctive word.
- `getContinuumNodeMetadata` and `getContinuumNodeDocumentation` now throw a descriptive error for an unknown node id. Clients receive it as `isError`. Previously the metadata tool returned `null`.

### continuum-commons

- `ValidationHelper.findSchemaViolations(properties, schema)` is new. It returns a `SchemaViolation(keyword, path, property, message)` per JSON Schema problem instead of asserting.
- `validateJsonWithSchema` keeps its behavior (assert on any violation) and now calls the new function.

### continuum-worker-springboot-starter: cancellation

- **Before:** Temporal delivers cancellation through heartbeat exceptions, and a node could swallow the exception or ignore the interrupt, so the activity kept running or finished as if nothing happened.
- **Now:** `ContinuumNodeActivity` handles an `ActivityCompletionException` (cancelled, timed out, or worker shutting down) from a heartbeat. The background heartbeat scheduler interrupts the activity thread, so a node blocked in I/O, `Process.waitFor` or `Thread.sleep` stops. The activity rethrows the cancellation so Temporal records it, even if `execute()` swallowed it or returned normally.
- The interrupt is cleared before the worker thread is reused.
- Covered by the new `ContinuumNodeActivityCancellationTest`.

### Licensing and documentation

- Added Apache License 2.0 (`LICENSE`).
- Added `CONTRIBUTING.md`: reporting issues, JDK 21 setup, per-module build and test, and the pull request process.
- Removed the duplicated `CLAUDE.md`, `.clinerules`, `.cursorrules`, `.windsurfrules` and `.github/copilot-instructions.md`. `AGENTS.md` is now the single source of AI-assistant guidance and holds the project reference.
- README links to sibling repositories (workbench, feature-base, feature-ai, feature-template), the clone URL and the Issues link now point to the `projectcontinuum-org` GitHub organization (previously `projectcontinuum`).
- The cluster-manager, credentials-server and knime-scheduler frontends have updated GitHub/Documentation links in their header and footer.
- Module `AGENTS.md` files for api-server, commons and worker-springboot-starter now describe the changes above.

### Build and repository

- `gradle.properties`: `platformVersion` is `0.0.21` (was `0.0.20`), and `repoName` is `projectcontinuum-org/continuum-platform-core` (was `projectcontinuum/Continuum`).
- `package-lock.json` files are no longer tracked (removed from the cluster-manager, credentials-server and knime-scheduler frontends and from `landing-page`) and are listed in `.gitignore`.

## Upgrade notes

- **MCP clients:** Anything parsing `listContinuumNodeCategories` or `searchContinuumNodes` output must handle the compact shape. Anything relying on `getContinuumNodeMetadata` returning `null` for an unknown id must handle an error instead.
- **Node authors:** A node that runs subprocesses must stop them on `InterruptedException` or on an exception from `NodeProgressCallback.report()`. Cancellation is now enforced by interrupting the node thread.
- **Publishing coordinates:** `repoName` feeds the GitHub Packages and container-registry paths used when publishing. Update anything that pulls artifacts or images from the old `projectcontinuum/Continuum` path.
- **Frontend builds:** Without committed lockfiles, `npm install` in the frontend modules resolves dependency versions at build time, so builds are not reproducible from the repo alone.

**Full Changelog**: https://github.com/projectcontinuum-org/continuum-platform-core/compare/release/0.0.20...main
