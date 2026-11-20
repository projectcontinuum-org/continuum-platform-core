## Purpose

Shared library depended on by every module in this repo and every feature repo. Defines the base node model, Parquet I/O utilities, workflow model, shared protocol types, and common constants.

## Ownership

Core platform team. No Spring Boot app — library only. Published to Maven Central as `org.projectcontinuum.core:continuum-commons`.

## Local Contracts

- `ProcessNodeModel` — abstract base for all data-processing nodes. Subclasses must declare `inputPorts` and `outputPorts` and implement `execute()`. Use the `ExecutionContext` overload when credentials are needed; the base overload is for nodes that don't.
- `TriggerNodeModel` — base for source nodes (no inputs, only outputs).
- Property validation: `ProcessNodeModel.run`, `TriggerNodeModel.run` and `KnimeNodeModel.run` call `validatePropertiesOrFail(node)` (`NodePropertiesValidation.kt`) before `execute()`. It validates against the node class's own `metadata.propertiesSchema`, never `node.data.propertiesSchema` from the workflow, which is client-supplied. Invalid properties throw a non-retriable `NodeRuntimeException`, with `PropertiesValidationException` as the cause. A subclass that overrides `run` must keep this call.
- `@ContinuumNode` — required on every concrete node class. Sets `SCOPE_PROTOTYPE` and `@Component`. Absence is caught at build time by the Gradle plugin's `validateContinuumNodeAnnotations` task and at runtime startup.
- `ContinuumWorkflowModel` — canonical JSON-serialisable DAG model (nodes + edges). `getRootNodes()`, `getParentNodes()`, `getParentEdges()` helpers drive the orchestration loop.
- `NodeOutputWriter` / `NodeInputReader` — Parquet I/O. Output files are `output.{portId}.parquet`; input files are `input.{portId}.parquet`. Snappy compression. Avro schema `DataRow`.
- `ExecutionContext` — holds `ownerId` and pre-resolved credentials (label → data map). Populated by `ContinuumNodeActivity` before `execute()` is called.
- `TaskQueues` — object (not enum) of const strings so they can appear in annotations: `WORKFLOW_TASK_QUEUE`, `ACTIVITY_TASK_QUEUE`, `ACTIVITY_TASK_QUEUE_INITIALIZE`.
- `NodeProgressCallback` — rate-limited in the worker starter; nodes call `report(percentage)` or `report(NodeProgress(...))`.
- `ValidationHelper` — JSON Schema checks of node properties, with the same rules as the workbench's NodeDialog (JSONForms validates with Ajv 8: draft 7 by default, `ajv-formats`). Default dialect is draft 7 unless the schema declares `$schema`; `format` is asserted, unknown formats (e.g. `credential`) are ignored. Keep these in step with the workbench: if JSONForms/Ajv changes its default dialect or format handling, change it here too, and re-check every node schema and saved workflow against both validators.
  - Node schemas should not declare `$schema`, or should declare exactly `http://json-schema.org/draft-07/schema#` (the trailing `#` is optional). That id is the only one both validators accept. The backend also accepts draft-04/06, 2019-09, 2020-12 and an `https://` draft-07 id, but the workbench's Ajv (draft-07 build) cannot compile those, so the node dialog would break. An unknown `$schema` is an invalid schema in both.
  - `ValidationHelperRealSchemasTest` pins this behaviour, using schemas copied from real nodes (Rest, Fingerprint, DescriptorCalculation, KNIMEWorkflowExecutor, ClaudeAIAgent). It covers enum (exact match, `enumNames` is a label only, enum arrays), nested objects (errors at the item path, `property` set to the top-level field), and dialects. If a library upgrade changes any of these, the test must fail first.
  - `validateJsonWithSchema` throws `PropertiesValidationException`, which carries every violation. It is an explicit check, never `assert`, because production JVMs run without `-ea`. `ValidationHelperTest` checks this in a class loader with assertions disabled.
  - `findSchemaViolations` returns one `SchemaViolation(keyword, path, property, message)` per problem, for callers that report errors instead of failing. A schema that cannot be compiled is one violation with keyword `INVALID_SCHEMA_KEYWORD` (`$schema`), never an exception.
  - Callers must pass a trusted schema: the registered one (api-server `WorkflowPropertiesValidator`) or the node class's `metadata.propertiesSchema` (worker). Never pass `node.data.propertiesSchema` from a submitted workflow.
  - A new registry per call: the registry caches schemas by `$id`/`$ref`, and node schemas from different workers must not affect each other. Remote `$ref` fetching stays off (networknt default); never enable it, because it would let a node schema make the server fetch URLs.
- `validation.WorkflowValidator` — the one workflow checker, used by the api-server's MCP tools and start endpoint (and later the Kotlin DSL). Takes `GraphNode`/`GraphEdge` lists and a `NodeDescriptorSource` (registry lookup, implemented by the API server, so this module has no API-server dependency) and returns every error and warning as `WorkflowIssue`s. `ValidationMode.AUTHORING` merges given properties over registry defaults and rejects undeclared keys; `RUN` checks properties as sent. Port, schema and default data always come from the descriptor source, never from a submitted workflow; a differing `GraphNode.embedded` definition only adds a warning. Graph checks: unique keys, registered node, edge endpoints and ports, no self or duplicate edges, one edge per input, cycles by Kahn's algorithm. Message texts are asserted by api-server tests (`WorkflowSpecValidatorTest`, `WorkflowControllerGraphValidationTest`); change them deliberately.
- `KnimeNodeModel` — published base for nodes that run a single KNIME node in batch mode. It starts KNIME with `ProcessBuilder(buildKnimeCommand(...))`: an argv list with no shell, so paths, node ids and port ids reach KNIME verbatim. Never reintroduce `bash -c` or string-joined commands. `resolveWorkflowDir(runId, nodeId)` canonicalizes `{knimeWorkflowRootDir}/{runId}/{nodeId}` and throws a non-retriable `NodeRuntimeException` if it is the root itself or outside it. Use `prepareWorkflowInputOptions` (one `-option=...` per element). The space-joined `prepareWorkflowInputs` String API is `@Deprecated`, and only kept for binary compatibility.
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
