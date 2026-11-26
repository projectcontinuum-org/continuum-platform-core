package org.projectcontinuum.core.commons.node.builtin

import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.model.LoopNode
import org.projectcontinuum.core.commons.validation.loop.LoopConditionLimits

/**
 * The registered definition of the built-in Loop node: its [nodeData] (properties schema, UI schema, defaults, icon),
 * [documentationMarkdown] and [categories]. The orchestration service publishes it as the Loop's registration; tests
 * use it as the registry entry of the loop.
 *
 * The schemas are what the workbench dialog renders: change them only together with its condition renderer. The `condition` control carries
 * `options.format = "query"` and `options.language = "CEL"`, which route it to the workbench's condition renderer.
 */
object LoopNodeDescriptor {

  const val CONDITION_PROPERTY = "condition"
  const val MAX_ITERATIONS_PROPERTY = "maxIterations"
  const val ON_LIMIT_PROPERTY = "onLimit"
  const val ON_NON_BOOLEAN_RESULT_PROPERTY = "onNonBooleanResult"
  const val CHECK_PROPERTY = "check"

  const val DEFAULT_CONDITION = "vars[\"hasMore\"] == true"
  const val DEFAULT_MAX_ITERATIONS = 50
  const val MIN_MAX_ITERATIONS = 1
  const val MAX_MAX_ITERATIONS = 10_000

  /** `onLimit` values: fail the run, or stop the loop with a warning and continue downstream. */
  val ON_LIMIT_VALUES: List<String> = listOf("fail", "stop-with-warning")

  /**
   * `onNonBooleanResult` values, for a condition whose result type is only known at run time (`vars["hasMore"]`):
   * fail the loop when it returns something other than true or false, or treat that value as false.
   */
  val ON_NON_BOOLEAN_RESULT_VALUES: List<String> = listOf("fail", "treat-as-false")

  /** `check` values: evaluate the condition before or after each iteration. */
  val CHECK_VALUES: List<String> = listOf("before", "after")

  private const val DOCUMENTATION_RESOURCE = "org/projectcontinuum/core/commons/node/builtin/LoopNode.doc.md"

  val categories: List<String> = listOf(LoopNode.CATEGORY)

  /**
   * Property defaults: repeat while `hasMore` is true, at most 50 times, fail at the limit, fail on a result that is not
   * true or false, check after each iteration.
   */
  val defaultProperties: Map<String, Any> = mapOf(
    CONDITION_PROPERTY to DEFAULT_CONDITION,
    MAX_ITERATIONS_PROPERTY to DEFAULT_MAX_ITERATIONS,
    ON_LIMIT_PROPERTY to ON_LIMIT_VALUES.first(),
    ON_NON_BOOLEAN_RESULT_PROPERTY to ON_NON_BOOLEAN_RESULT_VALUES.first(),
    CHECK_PROPERTY to CHECK_VALUES.last()
  )

  /** JSON Schema (draft 7, no `$schema`) of the loop properties. */
  val propertiesSchema: Map<String, Any> = mapOf(
    "type" to "object",
    "additionalProperties" to false,
    "required" to listOf(
      CONDITION_PROPERTY,
      MAX_ITERATIONS_PROPERTY,
      ON_LIMIT_PROPERTY,
      ON_NON_BOOLEAN_RESULT_PROPERTY,
      CHECK_PROPERTY
    ),
    "properties" to mapOf(
      CONDITION_PROPERTY to conditionSchema(),
      MAX_ITERATIONS_PROPERTY to maxIterationsSchema(),
      ON_LIMIT_PROPERTY to onLimitSchema(),
      ON_NON_BOOLEAN_RESULT_PROPERTY to onNonBooleanResultSchema(),
      CHECK_PROPERTY to checkSchema()
    )
  )

  /** JSONForms UI schema: the condition first (query renderer), then the limit, the non-boolean rule and the check timing. */
  val propertiesUISchema: Map<String, Any> = mapOf(
    "type" to "VerticalLayout",
    "elements" to listOf(
      mapOf(
        "type" to "Control",
        "scope" to "#/properties/$CONDITION_PROPERTY",
        "options" to mapOf("format" to "query", "language" to "CEL")
      ),
      mapOf("type" to "Control", "scope" to "#/properties/$MAX_ITERATIONS_PROPERTY"),
      mapOf("type" to "Control", "scope" to "#/properties/$ON_LIMIT_PROPERTY"),
      mapOf("type" to "Control", "scope" to "#/properties/$ON_NON_BOOLEAN_RESULT_PROPERTY"),
      mapOf("type" to "Control", "scope" to "#/properties/$CHECK_PROPERTY")
    )
  )

  /** The loop documentation shown by the workbench and the MCP documentation tool. */
  val documentationMarkdown: String by lazy {
    val resource = LoopNodeDescriptor::class.java.classLoader.getResource(DOCUMENTATION_RESOURCE)
      ?: error("Loop documentation resource $DOCUMENTATION_RESOURCE is missing")
    resource.readText(charset = Charsets.UTF_8)
  }

  /** Inline SVG (arrows in a circle), drawn with the current text colour. */
  private val LOOP_ICON_SVG = """
    <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
      <path stroke-linecap="round" stroke-linejoin="round" d="M16.023 9.348h4.992v-.001M2.985 19.644v-4.992m0 0h4.992m-4.993 0 3.181 3.183a8.25 8.25 0 0 0 13.803-3.7M4.031 9.865a8.25 8.25 0 0 1 13.803-3.7l3.181 3.182m0-4.991v4.99" />
    </svg>
  """.trimIndent()

  /** The registered definition. A loop has no ports: edges always connect the nodes inside it. */
  val nodeData: ContinuumWorkflowModel.NodeData = ContinuumWorkflowModel.NodeData(
    id = LoopNode.NODE_MODEL,
    description = "Repeats the nodes inside the box while a CEL condition over flow variables holds.",
    title = "Loop",
    subTitle = "Repeat the nodes inside",
    icon = LOOP_ICON_SVG,
    nodeModel = LoopNode.NODE_MODEL,
    inputs = null,
    outputs = null,
    properties = defaultProperties,
    propertiesSchema = propertiesSchema,
    propertiesUISchema = propertiesUISchema
  )

  private fun conditionSchema(): Map<String, Any> = mapOf(
    "type" to "string",
    "title" to "Condition",
    "description" to "Repeat while this CEL expression is true. Read flow variables as vars[\"name\"]; for example " +
      "vars[\"hasMore\"] == true or vars[\"page\"] < 10 && vars[\"hasMore\"].",
    "minLength" to 1,
    "maxLength" to LoopConditionLimits.MAX_EXPRESSION_LENGTH,
    "default" to DEFAULT_CONDITION
  )

  private fun maxIterationsSchema(): Map<String, Any> = mapOf(
    "type" to "integer",
    "title" to "Maximum iterations",
    "description" to "The loop never runs more often than this. What happens when the condition still holds at the " +
      "limit is set by 'When the limit is reached'.",
    "minimum" to MIN_MAX_ITERATIONS,
    "maximum" to MAX_MAX_ITERATIONS,
    "default" to DEFAULT_MAX_ITERATIONS
  )

  private fun onLimitSchema(): Map<String, Any> = mapOf(
    "type" to "string",
    "title" to "When the limit is reached",
    "description" to "fail: the loop and the run fail. stop-with-warning: the loop stops with a warning and the " +
      "nodes after it run with the last iteration's outputs.",
    "enum" to ON_LIMIT_VALUES,
    "default" to ON_LIMIT_VALUES.first()
  )

  private fun onNonBooleanResultSchema(): Map<String, Any> = mapOf(
    "type" to "string",
    "title" to "When the condition is not true or false",
    "description" to "fail: the loop and the run fail when the condition returns another value (for example a string). " +
      "treat-as-false: the loop stops as if the condition were false and ends with a warning; the nodes after it run " +
      "with the last iteration's outputs.",
    "enum" to ON_NON_BOOLEAN_RESULT_VALUES,
    "default" to ON_NON_BOOLEAN_RESULT_VALUES.first()
  )

  private fun checkSchema(): Map<String, Any> = mapOf(
    "type" to "string",
    "title" to "Check the condition",
    "description" to "after: run the nodes inside at least once, then check after each iteration. before: check " +
      "before each iteration, so the loop may run zero times.",
    "enum" to CHECK_VALUES,
    "default" to CHECK_VALUES.last()
  )
}

