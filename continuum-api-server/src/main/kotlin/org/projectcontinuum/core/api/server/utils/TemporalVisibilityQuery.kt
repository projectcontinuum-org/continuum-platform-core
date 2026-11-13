package org.projectcontinuum.core.api.server.utils

import org.projectcontinuum.core.api.server.exception.InvalidRequestException

/**
 * Builds Temporal visibility (List/Count WorkflowExecutions) queries safely.
 *
 * - [quote] renders a value as a single-quoted visibility string literal, escaping `\` and `'`.
 * - [scope] AND-combines a client-supplied query with a server-built filter. The server filter comes first and
 *   the client query is wrapped in parentheses, after [validateClientQuery] has proven the client query cannot
 *   close that parenthesis, open an unterminated literal, or comment out the rest of the query.
 */
object TemporalVisibilityQuery {

  private val GROUP_BY_EXECUTION_STATUS = Regex("(?is)^(.*?)\\s*\\bGROUP\\s+BY\\s+ExecutionStatus\\s*$")

  fun quote(value: String): String =
    "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'"

  /** `WorkflowId IN ('a', 'b', ...)` with every id escaped via [quote]. */
  fun workflowIdIn(workflowIds: Collection<String>): String {
    require(workflowIds.isNotEmpty()) { "workflowIds must not be empty" }
    return workflowIds.joinToString(prefix = "WorkflowId IN (", postfix = ")", separator = ", ") { quote(it) }
  }

  /**
   * Returns `(<serverFilter>) AND (<clientQuery>)`, or `(<serverFilter>)` when [clientQuery] is blank.
   * When [allowGroupByExecutionStatus] is true a trailing `GROUP BY ExecutionStatus` (the only grouping Temporal
   * supports for counts) is moved outside the parentheses.
   */
  fun scope(serverFilter: String, clientQuery: String?, allowGroupByExecutionStatus: Boolean = false): String {
    val (where, groupBy) = checkClientQuery(clientQuery, allowGroupByExecutionStatus)
    val scoped = if (where.isEmpty()) "($serverFilter)" else "($serverFilter) AND ($where)"
    return scoped + groupBy
  }

  /**
   * Splits off an allowed trailing `GROUP BY ExecutionStatus`, validates the remaining filter with
   * [validateClientQuery] and returns `(filter, groupBySuffix)`. Throws [InvalidRequestException] when invalid.
   */
  fun checkClientQuery(clientQuery: String?, allowGroupByExecutionStatus: Boolean = false): Pair<String, String> {
    var where = clientQuery?.trim().orEmpty()
    var groupBy = ""
    if (allowGroupByExecutionStatus) {
      GROUP_BY_EXECUTION_STATUS.matchEntire(where)?.let {
        where = it.groupValues[1].trim()
        groupBy = " GROUP BY ExecutionStatus"
      }
    }
    validateClientQuery(where)
    return where to groupBy
  }

  /**
   * Rejects (HTTP 400) a client query that could escape the parentheses it is wrapped in:
   * unbalanced or prematurely closed parentheses, unterminated quotes, comment markers (`--`, `#`, slash-star),
   * statement separators (`;`), backslashes (to avoid any escape-handling ambiguity with Temporal's parser)
   * and control characters (including newlines).
   * Literals may use doubled quotes (`'it''s'`), which Temporal's parser treats the same way.
   */
  fun validateClientQuery(query: String) {
    var depth = 0
    var quoteChar: Char? = null
    var i = 0
    while (i < query.length) {
      val c = query[i]
      if (c == '\\') reject("backslashes are not allowed")
      if (c.isISOControl()) reject("control characters are not allowed")
      if (quoteChar != null) {
        if (c == quoteChar) {
          if (i + 1 < query.length && query[i + 1] == quoteChar) {
            i++ // doubled quote inside literal
          } else {
            quoteChar = null
          }
        }
      } else {
        when (c) {
          '\'', '"', '`' -> quoteChar = c
          '(' -> depth++
          ')' -> if (--depth < 0) reject("unbalanced parentheses")
          ';' -> reject("';' is not allowed")
          '#' -> reject("comments are not allowed")
          '-' -> if (query.getOrNull(i + 1) == '-') reject("comments are not allowed")
          '/' -> if (query.getOrNull(i + 1) == '*') reject("comments are not allowed")
        }
      }
      i++
    }
    if (quoteChar != null) reject("unterminated string literal")
    if (depth != 0) reject("unbalanced parentheses")
  }

  private fun reject(reason: String): Nothing =
    throw InvalidRequestException("Invalid visibility query: $reason")
}
