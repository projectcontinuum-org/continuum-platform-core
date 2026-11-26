## Purpose

The cel-java implementation of the Loop condition contract defined in `continuum-commons` (`validation.loop`): the authoritative full-CEL validator (api-server start, validate and MCP tools; the orchestrator's re-check at loop start) and the evaluator the orchestration service runs inside workflow code. Keeps cel-java and its protobuf-java 4.x / guava 33.x off every classpath that feature workers share.

## Ownership

Core platform team. Library, not published; consumed only by `continuum-orchestration-service` and `continuum-api-server`.

## Local Contracts

- Dependency boundary: only `continuum-orchestration-service` and `continuum-api-server` may depend on this module. Never add it (or `libs.cel`) to `continuum-commons`, `continuum-worker-springboot-starter`, `continuum-message-bridge`, `continuum-knime-base` or the Gradle plugin; check with `./gradlew :continuum-commons:dependencies --configuration runtimeClasspath` that `protobuf-java` stays on 3.25.x there. The two consumers pin `protobuf-java-util` to the protobuf-java version cel-java brings (catalog `protobuf-java-util`, a `constraints` block in their build files); when a cel-java upgrade moves protobuf-java, move the catalog `protobuf` version with it.
- Dependency inversion: `continuum-commons` owns the interfaces (`LoopConditionValidator`, `LoopConditionEvaluator`, `LoopConditionIssueCode`, `LoopConditionLimits`); this module only implements them. Commons never imports `org.projectcontinuum.core.loopcondition`. `api(project(":continuum-commons"))`; cel-java is `implementation` and its types never appear in a public signature.
- One environment: `LoopCelEnvironment.shared` is the only place cel-java is configured for loops; the validator and the evaluator both use it, so what validates is evaluated with the same functions. It is part of the contract:
  - one declared variable `vars: map(string, dyn)`, empty container;
  - options: heterogeneous numeric comparisons, optional syntax, standard macros, `maxParseRecursionDepth` 100, `comprehensionMaxIterations` 1,000, `maxRegexProgramSize` 1,000 (all from `LoopConditionLimits`);
  - extensions strings, math (v2), lists (v2), sets (v0), regex (v0), encoders, bindings, optional (v2), comprehensions (v0); never protos or native types. Versions are pinned as constants where the factory takes one; `LoopCelEnvironmentTest` pins the full function and macro name list, so review any upgrade that changes it (cost, determinism) before updating the list;
  - the standard (deprecated in 0.15.0) builder: the planner builder rejects null values in `vars`, which flow variables may hold.
- Validator (`CelLoopConditionValidator`, shared as `LoopConditionValidators.default`), one collaborator per step, first problem wins: `LoopConditionTextPrecheck` (EMPTY, TOO_LONG, TOO_DEEP; nesting via `LoopConditionNestingDepth`) → parse (SYNTAX) → type-check (CHECK_ERROR) → `ConditionResultTypeRule` (NOT_BOOLEAN unless `bool` or `dyn`) → `CelLiteralChecks` (INVALID_LITERAL: cel-java's regex, timestamp and duration literal validators) → `ReboundNameRule` (REBOUND_NAME) → `ReadVariableNames`. Never throws; a `StackOverflowError` or cel-java exception is SYNTAX. Messages come from `LoopConditionIssues`: reason text, CEL message on one line, `(column n)` (1-based code points).
- `ReboundNameRule` works around a cel-java 0.15.0 bug: a `cel.bind` that re-binds an outer name overflows the stack at evaluation. It rejects any macro or `cel.bind` variable already bound by an enclosing one, or named `vars`; macro-internal names (`@result`, `#unused`) are ignored. Sibling re-use stays valid.
- Evaluator (`CelLoopConditionEvaluator`): `compile` runs the validator and keeps the program (an invalid text is returned in `CompiledLoopCondition.invalid`); `evaluate` converts the variables (`FlowVariableCelValues`: int64, double, bool, string, lists, key-sorted maps, CEL null; iterative), runs `program.trace` with `CombinedEvaluationListener` of `LoopConditionSizeGuard` (every list/map at most 10,000 elements, string/bytes at most 100,000), a new `LoopConditionTimeBudget` per evaluation (`LoopConditionLimits.MAX_EVALUATION_MILLIS` = 200 ms, read after every value: `LoopConditionLimits.evaluationTimeExceededDetail()`, "the condition took longer than 200 ms to evaluate") and `EvaluationInterruptCheck` (the thread was interrupted: stop at the next value, keep the flag), and maps the result: true/false, any other value as `NonBoolean` (`ValuePreview`, at most 100 characters), every error as `Failed` (`CelEvaluationFailures`: MISSING_VARIABLE for a missing literal key the condition reads, LIMIT_EXCEEDED for the iteration budget, any `EvaluationLimitExceeded` thrown by a guard (size or time) and the regex size, EVALUATION_ERROR otherwise). Never throws (`StackOverflowError` included). `trace` is used because cel-java meters only comprehensions; the size guard bounds `lists.range`, `replace` chains, `cel.bind` doublings and the quadratic `sets.*` functions, and the time budget bounds a macro that repeats an expensive call (each value within the caps). The listeners run only between values: one function call that is already running (`sets.equivalent` over two 9,000-element lists, about 1 s) is never cut short here, so this module alone does not bound how long `evaluate` blocks. The orchestration service does (`TimeBoxedConditionEvaluationRunner`: evaluation thread, bounded wait, interrupt). Keep `evaluate` thread-safe and stateless, because it runs on several evaluation threads at once. The wall clock is safe only because the orchestrator runs the evaluation inside `Workflow.sideEffect`; never call `evaluate` outside a recorded side effect in workflow code.
- AST walks are iterative (`CelAstWalk`), never recursive.

## Work Guidance

- README: every change here updates this module's `README.md` (root `AGENTS.md`, "User Preferences").
- Keep the cel-java version in `gradle/libs.versions.toml` (`cel`); the orchestration test classpath uses the same alias for its spike test.
- A new CEL feature or cap: change `LoopCelEnvironment` / `LoopConditionLimits`, add rows to the condition table, update the pin list and the Loop documentation (`LoopNode.doc.md` in commons) together.

## Verification

```bash
./gradlew :continuum-loop-condition:test
```

- `LoopConditionValidatorTableTest` and `CelLoopConditionEvaluatorTableTest`: every row of `src/test/resources/loop-condition/cel-conditions.json` (valid rows with result type and read variables, invalid rows with code and column; the `evaluations` against the real evaluator). The table must keep a row for every validation code and an evaluation for every runtime code.
- `LoopCelEnvironmentTest`: options in force, protos absent, validator and evaluator share the environment, the pinned function and macro name list.
- `CelLoopConditionValidatorRobustnessTest` and `CelLoopConditionEvaluatorRobustnessTest`: hostile inputs end within 100 ms, cost limits name the limit, nothing throws; a macro repeating `sets.contains` over two 10,000-element lists stops at the time budget well under one second; one `sets.equivalent` over two 9,000-element list variables reports the budget only after the call returns (the orchestrator's bounded wait covers it); an interrupted thread stops at the next value; the budget with a fake clock.
- `CelLoopConditionValidatorTest`, `ReboundNameRuleTest`, `ReadVariableNamesTest`, `LoopConditionTextPrecheckTest`, `FlowVariableCelValuesTest`.
