# Loop

Repeats the nodes inside the box while a CEL condition over flow variables holds.

## How it works

Drop nodes into the loop box to make them its members. The loop runs its members as one iteration, in their normal
order, then checks the condition. While the condition holds and the limit is not reached, it resets the members and
runs them again. Nodes after the loop start only when the loop has finished, and they receive the outputs of the last
iteration.

A loop has **no ports**. Never draw an edge to or from the loop box: connect the nodes inside it instead. An edge may
leave the loop and an edge may enter it, but a path may not leave the loop and come back into it through an outside
node (`X1 -> Y -> X2` with `X1` and `X2` inside and `Y` outside), because `Y` cannot run between iterations. Loops can
be nested.

## The condition

The condition is a [CEL](https://cel.dev) expression over `vars`, the map of the flow variables the loop sees after an
iteration. It must end in `true` or `false`.

- Read a flow variable as `vars["name"]`. Always write it this way, so names with dots work too:
  `vars["restNode_3.durationMs"]`. `vars.name` also works for plain names.
- Check that a variable is set with `has(vars.name)` (plain names), `"name" in vars` (any name), or read it with a
  default: `vars[?"name"].orValue(0)`.
- Things to remember about CEL:
  - reading a variable that is not set is an error, unless the result does not depend on it
    (`vars["missing"] || true` is true);
  - `"a" == 1` is `false`, but `"a" > 1` is an error;
  - integers and doubles compare by value (`vars["page"] < 2.5`).
- Available: all CEL operators (`&& || ! ?: + - * / % == != < <= > >= in`, index, field selection), the macros
  `has`, `all`, `exists`, `exists_one`, `map`, `filter`, the standard functions (`size`, `int`, `double`, `string`,
  `bool`, `bytes`, `type`, `dyn`, `matches`, `contains`, `startsWith`, `endsWith`, `timestamp`, `duration` and the
  timestamp accessors such as `getHours()`), and these libraries:
  - strings: `lowerAscii`, `upperAscii`, `trim`, `replace`, `split`, `substring`, `indexOf`, `join`, `format`, ...;
  - math: `math.greatest`, `math.least`, `math.abs`, `math.round`, `math.sqrt`, ...;
  - lists: `lists.range`, `slice`, `flatten`, `distinct`, `reverse`, `sort`, `sortBy`;
  - sets: `sets.contains`, `sets.equivalent`, `sets.intersects`;
  - regex: `regex.extract`, `regex.extractAll`, `regex.replace`;
  - encoders: `base64.encode`, `base64.decode`;
  - bindings: `cel.bind(name, value, expression)`;
  - optional values: `vars[?"x"]`, `vars.?x`, `orValue`, `hasValue`, `optional.of`, ...;
  - two-variable macros: `vars.all(k, v, ...)`, `exists`, `existsOne`, `transformList`, `transformMap`.
- Not available: `now()` or any clock, protobuf messages, and your own functions.
- Limits: at most 2,048 characters and brackets nested at most 32 deep; at most 1,000 iterations of all macros
  together per check; every list or map at most 10,000 elements, every string or bytes value at most 100,000
  characters, regular expressions at most program size 1,000; one check may take at most 200 ms (a macro that repeats
  an expensive function, such as `sets.contains` over two large lists, stops there; the orchestrator waits at most
  250 ms for one check, so a single long function call, such as `sets.equivalent` over two lists of thousands of
  elements, fails the same way). A macro or `cel.bind` must not re-use a name that an
  enclosing one already binds, and must not bind `vars`.
- The validator checks the condition when the workflow is validated or started and names the problem and its column.

Examples:

```
vars["hasMore"] == true
vars["page"] < 10 && vars["hasMore"]
"nextCursor" in vars && vars["nextCursor"] != ""
vars["items"].exists(i, i.status == "PENDING")
!vars["status"].matches("^(DONE|FAILED)$")
vars[?"retries"].orValue(0) < 3
```

A member of the loop must set the variables the condition reads.

## Settings

- **Condition** — default `vars["hasMore"] == true`.
- **Check the condition** — `after` (default): the members run at least once and the condition is checked after each
  iteration. `before`: checked before each iteration, so the loop may run zero times; then the members and the nodes
  that need their outputs are skipped.
- **Maximum iterations** — 1 to 10,000, default 50.
- **When the limit is reached** — `fail` (default): the loop and the run fail. `stop-with-warning`: the loop ends with
  a warning and the nodes after it run with the outputs of the last iteration.
- **When the condition is not true or false** (`onNonBooleanResult`) — only for a condition whose type is decided at
  run time, such as `vars["hasMore"]`. `fail` (default): the loop and the run fail, naming the type and the value.
  `treat-as-false`: the loop stops as if the condition were false and ends with a warning ("Condition returned string
  in iteration 2; treated as false."); the nodes after it run with that iteration's outputs. With `check: before` and
  such a value on the first check, the members are skipped and the loop ends with a warning.

## Flow variables in a loop

After each iteration, every flow variable set by any member is carried into the next iteration's first members (the
members with no input from another member), over the values that come from outside the loop. When two members set the
same variable, the one later in the run order wins. The condition reads this end-of-iteration set; a `before` check
on the first pass reads the variables that reach the loop from outside. Nodes after the loop inherit the variables of
the last iteration: what its first members received (a variable set only in an earlier iteration included) plus what
the members set in it. After a run, the loop shows the variables carried into its last iteration.

Every member and the condition also get a read-only flow variable `iteration`, the number of the running iteration
(1 in the first). In the condition it is the iteration that just ended (0 in a `before` check on the first pass), so
`vars["iteration"] < 5` works. Nested members see their innermost loop's count. A value a node sets under this name is ignored,
and nodes after the loop do not get it.

## When a run fails

The loop fails the run, with a message naming the loop and the iteration, when:

- the condition reads a flow variable that is not set (unless the result does not depend on it); the message names
  the variable and its column;
- the condition fails to evaluate (for example `vars["s"] > 1` with a string, a division by zero);
- the condition exceeds a cost limit (macro iterations, list, map, string or bytes size, regex size, or the 200 ms
  a check may take: "the condition took longer than 200 ms to evaluate");
- the condition returns something other than true or false and `onNonBooleanResult` is `fail`;
- the limit is reached with `fail`;
- the condition or a setting is not valid at loop start (only possible when the workflow was started without
  validation);
- the run history gets close to Temporal's limit.

A failing member stops the loop: there is no next iteration, and the nodes after the loop are skipped.

## Keep conditions cheap

A normal condition, such as comparing a few flow variables, takes well under a millisecond. The orchestrator
evaluates every condition on 4 threads shared by all runs, and waits at most about 250 ms for one check:

- A condition that is one long function call (for example `sets.equivalent` or `sets.intersects` over lists of
  thousands of elements) fails its loop at 200 ms, but the call keeps one of the 4 threads until it returns (about a
  second, or longer). While slow conditions hold all 4, other loop checks wait: they are tried again later and
  finish normally, so they are delayed, never failed. The first retry comes at once, the next ones at least 10
  seconds apart and further apart each time (10, 10, 15, 18, 27, 47, 90 seconds and more), so a delayed loop can
  wait from a few seconds to a few minutes (55 to 130 seconds were measured on a slow, busy orchestrator). Each
  retry after the first logs an error with a stack trace from the Temporal SDK on the orchestrator; that is
  expected and not a fault.
- A run with more loops side by side than there are threads (5 or more), each with such a slow condition, holds all
  4 threads itself. Its remaining check is then tried again and again, and every try evaluates the slow conditions
  again, so the run stays running, makes no progress and does not end on its own (5 such loops: still running after
  12 minutes). Until it is terminated, every try takes all 4 threads again and delays the loop checks of every other
  run, other users' included. Terminate the run; it is then shown as terminated.
- There is no limit for all the checks that come at once in a run. Many loops side by side whose members finish
  together, each with a slow condition, add up: about 30 such loops take 8 s, and at about 40 the run passes
  Temporal's 10-second limit for one step and stays running without making progress. Then terminate the run; it is
  then shown as terminated.

So compare flow variables in the condition and do the heavy work in a node inside the loop that sets a variable (for
example `vars["changed"] == true`), and avoid tens of parallel loops with heavy conditions.

## When nothing inside the loop can run

- A loop with no nodes inside it (the validator warns "Loop 'L' has no nodes inside it."), or with only empty loops
  inside, runs no iteration: it ends at once with a warning ("The loop has no nodes inside it to run; it ran no
  iteration") without checking the condition, and the nodes after it run.
- When no node inside the loop could run in an iteration (all of them are skipped, for example because an input is
  not connected), another iteration would do exactly the same, so the loop is skipped ("No node inside the loop could
  run in iteration 1") without checking the condition, like a node that cannot run. The nodes after it are skipped
  too; the run is not failed by this.

## How many iterations are possible

Every member run and every condition check adds to the run's history, which Temporal limits to 51,200 events. Each
run of a node inside the loop adds about 10 events and each condition check about 1; the loop stops with an error
when the history passes 40,000 events, whatever "When the limit is reached" says. That allows about:

| Nodes inside the loop | Iterations |
|---|---|
| 1 | 3,636 |
| 3 | 1,290 |
| 10 | 396 |

The validator warns when `maxIterations` times (nodes inside x 10 + 1) is over 40,000. Move work that does not need to
repeat out of the loop to allow more iterations.
