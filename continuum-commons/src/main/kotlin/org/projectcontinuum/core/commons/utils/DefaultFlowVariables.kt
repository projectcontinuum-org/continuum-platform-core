package org.projectcontinuum.core.commons.utils

/**
 * Flow variables the worker framework exports for every node run, next to the variables the node sets itself.
 *
 * Every name is `<prefix>.<variable>`, where the prefix is the node type in camelCase plus the node's instance id
 * (see [prefix]), e.g. `restNode_3.durationMs` for node `3` of type `...RestNodeModel`. The instance id keeps the names
 * unique, so two nodes of the same type never override each other or conflict where branches meet.
 *
 * | Variable            | Type               | Present                                                      |
 * |---------------------|--------------------|--------------------------------------------------------------|
 * | `executionStatus`   | String             | always: `SUCCESS` or `FAILED`                                |
 * | `errorMessage`      | String             | only when the node failed                                    |
 * | `startTimestamp`    | Long (epoch ms)    | always: when the activity started (before inputs download)   |
 * | `endTimestamp`      | Long (epoch ms)    | always: when the activity finished (after outputs upload)    |
 * | `durationMs`        | Long               | always: `endTimestamp - startTimestamp`                      |
 * | `inputCount`        | Map<portId, Long>  | process nodes, once their inputs were downloaded             |
 * | `outputCount`       | Map<portId, Long>  | successful nodes: rows written per output port               |
 *
 * A failed node's variables are visible in the run (events, node output viewer) but never reach downstream nodes,
 * because those are skipped.
 */
object DefaultFlowVariables {

  const val EXECUTION_STATUS = "executionStatus"
  const val ERROR_MESSAGE = "errorMessage"
  const val START_TIMESTAMP = "startTimestamp"
  const val END_TIMESTAMP = "endTimestamp"
  const val DURATION_MS = "durationMs"
  const val INPUT_COUNT = "inputCount"
  const val OUTPUT_COUNT = "outputCount"

  const val STATUS_SUCCESS = "SUCCESS"
  const val STATUS_FAILED = "FAILED"

  private const val NODE_MODEL_SUFFIX = "Model"
  private val NON_IDENTIFIER_CHARACTER = Regex("[^A-Za-z0-9_]")

  /**
   * The name prefix of [nodeId]'s default variables: the node type in camelCase, `_`, then the instance id with every
   * character other than letters, digits and `_` replaced by `_`.
   *
   * The node type is the simple class name of [nodeModel] without a trailing `Model`, with its leading capitals
   * lowercased as one word: `RestNodeModel` → `restNode`, `KNIMEWorkflowExecutorNodeModel` →
   * `knimeWorkflowExecutorNode`, `RDKit2SVGNodeModel` → `rdKit2SVGNode`.
   */
  fun prefix(nodeModel: String, nodeId: String): String {
    // A nested class has a binary name like `pkg.Outer$InnerNodeModel`: use the innermost class name.
    val simpleName = nodeModel.substringAfterLast('.').substringAfterLast('$')
    val typeName = if (simpleName.length > NODE_MODEL_SUFFIX.length && simpleName.endsWith(NODE_MODEL_SUFFIX)) {
      simpleName.removeSuffix(NODE_MODEL_SUFFIX)
    } else {
      simpleName
    }
    val instanceId = nodeId.replace(NON_IDENTIFIER_CHARACTER, "_")
    return "${decapitalize(typeName.replace(NON_IDENTIFIER_CHARACTER, "_"))}_$instanceId"
  }

  /** The full variable name, `<prefix>.<variable>`. */
  fun name(prefix: String, variable: String): String = "$prefix.$variable"

  /**
   * Lowercases the leading run of capitals as one word. When the run is followed by a lowercase letter, its last
   * capital starts the next word and stays (`RDKit` → `rdKit`); otherwise the whole run is lowercased (`SVG2` → `svg2`).
   */
  private fun decapitalize(typeName: String): String {
    val leadingCapitals = typeName.takeWhile { it.isUpperCase() }.length
    if (leadingCapitals == 0) return typeName
    val nextIsLowercase = typeName.getOrNull(leadingCapitals)?.isLowerCase() == true
    val lowercasedLength = if (leadingCapitals > 1 && nextIsLowercase) leadingCapitals - 1 else leadingCapitals
    return typeName.substring(0, lowercasedLength).lowercase() + typeName.substring(lowercasedLength)
  }

  /**
   * Builds one node run's default variables, named under [prefix]. [inputRowCounts] and [outputRowCounts] are left
   * out when null; [errorMessage] only when [failed].
   */
  fun build(
    prefix: String,
    failed: Boolean,
    errorMessage: String?,
    startTimestamp: Long,
    endTimestamp: Long,
    inputRowCounts: Map<String, Long>?,
    outputRowCounts: Map<String, Long>?
  ): Map<String, Any> {
    val variables = LinkedHashMap<String, Any>()
    variables[name(prefix, EXECUTION_STATUS)] = if (failed) STATUS_FAILED else STATUS_SUCCESS
    if (failed && errorMessage != null) {
      variables[name(prefix, ERROR_MESSAGE)] = errorMessage
    }
    variables[name(prefix, START_TIMESTAMP)] = startTimestamp
    variables[name(prefix, END_TIMESTAMP)] = endTimestamp
    variables[name(prefix, DURATION_MS)] = endTimestamp - startTimestamp
    inputRowCounts?.let { variables[name(prefix, INPUT_COUNT)] = it }
    outputRowCounts?.let { variables[name(prefix, OUTPUT_COUNT)] = it }
    return variables
  }
}
