## Purpose

Shared library depended on by every module in this repo and every feature repo. Defines the base node model, Parquet I/O utilities, workflow model, shared protocol types, and common constants.

## Ownership

Core platform team. No Spring Boot app — library only. Published to Maven Central as `org.projectcontinuum.core:continuum-commons`.

## Local Contracts

- `ProcessNodeModel` — abstract base for all data-processing nodes. Subclasses must declare `inputPorts` and `outputPorts` and implement `execute()`. Use the `ExecutionContext` overload when credentials are needed; the base overload is for nodes that don't.
- `TriggerNodeModel` — base for source nodes (no inputs, only outputs).
- `@ContinuumNode` — required on every concrete node class. Sets `SCOPE_PROTOTYPE` and `@Component`. Absence is caught at build time by the Gradle plugin's `validateContinuumNodeAnnotations` task and at runtime startup.
- `ContinuumWorkflowModel` — canonical JSON-serialisable DAG model (nodes + edges). `getRootNodes()`, `getParentNodes()`, `getParentEdges()` helpers drive the orchestration loop.
- `NodeOutputWriter` / `NodeInputReader` — Parquet I/O. Output files are `output.{portId}.parquet`; input files are `input.{portId}.parquet`. Snappy compression. Avro schema `DataRow`.
- `ExecutionContext` — holds `ownerId` and pre-resolved credentials (label → data map). Populated by `ContinuumNodeActivity` before `execute()` is called.
- `TaskQueues` — object (not enum) of const strings so they can appear in annotations: `WORKFLOW_TASK_QUEUE`, `ACTIVITY_TASK_QUEUE`, `ACTIVITY_TASK_QUEUE_INITIALIZE`.
- `NodeProgressCallback` — rate-limited in the worker starter; nodes call `report(percentage)` or `report(NodeProgress(...))`.
- Spring `context` and `beans` are `compileOnly` — this library does not pull in a Spring runtime.

## Work Guidance

- Keep this module free of Spring Boot runtime dependencies. `compileOnly` is intentional.
- `DataRow` Avro schema is in `continuum-avro-schemas` (a sibling dep). Do not duplicate it here.
- `NodeProgressCallback` no-op defaults in `ProcessNodeModel` are for testing convenience; the real implementation is in the worker starter.
- Doc markdown for a node class must be at `resources/<package>/<ClassName>.doc.md` relative to the class — `loadDocumentationFromResources()` derives this path automatically.

## Verification

```bash
./gradlew :continuum-commons:test
```
