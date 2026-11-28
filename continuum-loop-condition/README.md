# continuum-loop-condition

The cel-java ([`dev.cel:cel`](https://github.com/google/cel-java) 0.15.0) implementation of the Loop node's condition:
a full [CEL](https://cel.dev) validator and evaluator. The contract itself (`LoopConditionValidator`,
`LoopConditionEvaluator`, `LoopConditionOutcome`, `LoopConditionIssueCode`, `LoopConditionLimits`) lives in
`continuum-commons` (`org.projectcontinuum.core.commons.validation.loop`) and has no engine dependency.

## Why a separate module

cel-java brings `protobuf-java` 4.x and `guava` 33.x. `continuum-commons` is on every feature worker's classpath, so
the engine is kept out of it: only `continuum-orchestration-service` and `continuum-api-server` depend on this module.
Commons, `continuum-worker-springboot-starter`, `continuum-message-bridge`, `continuum-knime-base` and every feature
worker stay on protobuf-java 3.25.x and their previous guava.
The two consumers pin `protobuf-java-util` to 4.36.2 (version catalog entry `protobuf-java-util`, a Gradle
dependency constraint in their build files), so it matches the protobuf-java 4.36.2 that cel-java brings instead of
the 3.25.x that Temporal declares.

The module is not published to Maven Central and has no Spring dependency.

## The condition language

Any CEL expression over `vars` (`map(string, dyn)`, the loop's flow variables) whose type is `bool` or `dyn`: all
operators, the standard macros and functions, and the extensions strings, math, lists, sets, regex, encoders,
bindings (`cel.bind`), optional (`vars[?"x"]`, `vars.?x`) and comprehensions (two-variable macros). No protos, no
clock (`now()` does not exist). Limits: 2,048 characters, nesting 32, 1,000 comprehension iterations per check, every
list or map at most 10,000 elements, every string or bytes value at most 100,000, regex program size 1,000, and at
most 200 ms per evaluation. The evaluator enforces the 200 ms between values. The orchestration service enforces it
for a single long call (such as `sets.equivalent` over two 9,000-element lists, about a second): it waits at most
250 ms and then fails the check.

## What it contains

| Class | Role |
|-------|------|
| `LoopCelEnvironment` | the one cel-java setup for loops (options, macros, pinned extensions, `vars`), shared by validator and evaluator |
| `CelLoopConditionValidator` / `LoopConditionValidators.default` | the validator: pre-checks, parse, type-check, result type, literals, re-bound names, read variables; never throws |
| `LoopConditionTextPrecheck`, `LoopConditionNestingDepth` | empty text, 2,048-character cap, bracket nesting cap (32), before the parser runs |
| `ConditionResultTypeRule` | only `bool` or `dyn` results |
| `CelLiteralChecks` | invalid regex, timestamp and duration literals |
| `ReboundNameRule` | rejects re-bound macro / `cel.bind` names (works around a cel-java stack overflow) and binding `vars` |
| `ReadVariableNames` | the flow variables read with a literal key (`vars["x"]`, `vars.x`, `has(vars.x)`, `"x" in vars`, `vars[?"x"]`) |
| `CelLoopConditionEvaluator` | the evaluator: compile once, evaluate with `trace`, the size guard and the time budget, outcome true / false / non-boolean / failed; never throws |
| `LoopConditionSizeGuard` | aborts an evaluation when an intermediate list, map, string or bytes value is too large |
| `LoopConditionTimeBudget` | aborts an evaluation at the first value produced after 200 ms (a macro repeating an expensive call). It cannot cut short one function call that is already running; the orchestration service bounds the wait for that by running the evaluation on a separate thread |
| `EvaluationInterruptCheck` | stops an evaluation whose thread was interrupted (the orchestration service gave up waiting for it) at the next value |
| `FlowVariableCelValues` | JSON flow variables to CEL values (int64, double, key-sorted maps, CEL null), iterative |
| `CelEvaluationFailures`, `ValuePreview`, `LoopConditionIssues`, `CelAstWalk` | error mapping, value previews, message format, iterative AST walks |

## Usage

```kotlin
val result = LoopConditionValidators.default.check(text = "vars[\"page\"] < 10 && vars[\"hasMore\"]")

val evaluator = CelLoopConditionEvaluator()
val compiled = evaluator.compile(text = "vars[\"page\"] < 10 && vars[\"hasMore\"]")
val outcome = evaluator.evaluate(compiled = compiled, variables = mapOf("page" to 3, "hasMore" to true))
```

## Tests

```bash
./gradlew :continuum-loop-condition:test
```

`src/test/resources/loop-condition/cel-conditions.json` is the condition table: which texts are valid (with the result
type and the variables read) or invalid (with the issue code and column), and what the evaluator returns for given
variables. The validator and evaluator table tests run every row. The robustness tests run hostile conditions (each
must end within 100 ms with a cost limit and never throw; a macro repeating an expensive call stops at the time
budget), and `LoopCelEnvironmentTest` pins the environment's function
and macro names.
