package org.projectcontinuum.core.loopcondition

import tools.jackson.databind.JsonNode
import tools.jackson.module.kotlin.jacksonObjectMapper

/** Typed rows of `loop-condition/cel-conditions.json`, the condition table both the validator and evaluator tests run. */
object CelConditionTable {

  private const val RESOURCE = "/loop-condition/cel-conditions.json"

  data class Evaluation(
    val variables: Map<String, Any?>,
    val onNonBooleanResult: String,
    val expect: String,
    val messageContains: String?
  )

  data class Row(
    val id: String,
    val input: String,
    val valid: Boolean,
    val code: String?,
    val column: Int?,
    val resultType: String?,
    val readsVariables: Set<String>,
    val evaluations: List<Evaluation>
  )

  private val mapper = jacksonObjectMapper()

  val rows: List<Row> by lazy {
    val resource = CelConditionTable::class.java.getResource(RESOURCE) ?: error("$RESOURCE is missing from the test classpath")
    val root = mapper.readTree(resource.readText(charset = Charsets.UTF_8))
    val loaded = elementsOf(node = root.get("rows")).map { node -> rowOf(node = node) }
    val duplicateIds = loaded.groupBy { row -> row.id }.filterValues { group -> group.size > 1 }.keys
    require(duplicateIds.isEmpty()) { "duplicate row ids in $RESOURCE: $duplicateIds" }
    loaded
  }

  private fun rowOf(node: JsonNode): Row = Row(
    id = node.get("id").asString(),
    input = node.get("input").asString(),
    valid = node.get("valid").asBoolean(),
    code = node.get("code")?.asString(),
    column = node.get("column")?.asInt(),
    resultType = node.get("resultType")?.asString(),
    readsVariables = elementsOf(node = node.get("readsVariables")).map { name -> name.asString() }.toSet(),
    evaluations = elementsOf(node = node.get("evaluations")).map { evaluation -> evaluationOf(node = evaluation) }
  )

  private fun elementsOf(node: JsonNode?): List<JsonNode> =
    if (node == null) emptyList() else (0 until node.size()).map { index -> node.get(index) }

  private fun evaluationOf(node: JsonNode): Evaluation {
    @Suppress("UNCHECKED_CAST")
    val variables = mapper.convertValue(node.get("vars"), Map::class.java) as Map<String, Any?>
    val expectNode = node.get("expect")
    val expect = if (expectNode.isBoolean) expectNode.asBoolean().toString() else expectNode.asString()
    return Evaluation(
      variables = variables,
      onNonBooleanResult = node.get("onNonBooleanResult")?.asString() ?: "fail",
      expect = expect,
      messageContains = node.get("messageContains")?.asString()
    )
  }
}
