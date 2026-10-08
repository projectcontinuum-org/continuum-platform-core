package org.projectcontinuum.core.cluster.manager.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import freemarker.template.Configuration
import freemarker.template.Template
import jakarta.annotation.PostConstruct
import org.projectcontinuum.core.cluster.manager.config.OverlayProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.StringReader
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Resource types that can be customized via overlays.
 * Each maps to an expected filename suffix in the overlay directory.
 */
enum class ResourceType(val filename: String) {
  DEPLOYMENT("deployment.yaml"),
  SERVICE("service.yaml"),
  PVC("pvc.yaml"),
  INGRESS("ingress.yaml")
}

/**
 * Loads YAML overlay files from a mounted ConfigMap directory, renders them
 * as FreeMarker templates, and deep-merges the result onto rendered K8s
 * resource YAML before it is applied to the cluster.
 *
 * Overlay files follow the naming convention `{variant}--{resource}.yaml`
 * (e.g. `gpu-enabled--deployment.yaml`). When no variant is specified for
 * a workbench, no overlay is applied.
 *
 * Merge semantics:
 * - Object fields merge recursively (maps are merged key-by-key)
 * - Arrays and scalars replace entirely
 * - Critical identity fields (name, namespace, lifecycle labels) are protected
 *   and restored from the base after the merge
 *
 * Error handling is fail-open: overlay failures degrade to the un-overlaid
 * base YAML rather than blocking workbench operations.
 */
@Service
class OverlayService(
  private val overlayProperties: OverlayProperties,
  private val freemarkerConfig: Configuration
) {

  private val logger = LoggerFactory.getLogger(OverlayService::class.java)

  private val yamlMapper: ObjectMapper = ObjectMapper(
    YAMLFactory()
      .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
  )

  /** Labels that must never be changed by an overlay */
  private val protectedLabels = listOf("instance-id", "app", "managed-by")

  @PostConstruct
  fun init() {
    if (!overlayProperties.enabled) {
      logger.info("K8s resource overlays are disabled")
      return
    }

    val overlayDir = Paths.get(overlayProperties.path)
    if (!Files.isDirectory(overlayDir)) {
      logger.warn("Overlay directory does not exist: {}", overlayProperties.path)
      return
    }

    val presentFiles = Files.list(overlayDir)
      .map { it.fileName.toString() }
      .filter { it.contains("--") && it.endsWith(".yaml") }
      .toList()

    val variants = presentFiles
      .map { it.substringBefore("--") }
      .distinct()
      .sorted()

    logger.info(
      "K8s resource overlays enabled — directory: {}, variants: {}, files: {}",
      overlayProperties.path,
      if (variants.isEmpty()) "none" else variants.joinToString(", "),
      if (presentFiles.isEmpty()) "none" else presentFiles.joinToString(", ")
    )
  }

  /**
   * Returns the list of available overlay variant names by scanning the
   * overlay directory for files matching `{variant}--*.yaml`.
   * Returns an empty list if overlays are disabled or the directory is missing.
   */
  fun listVariants(): List<String> {
    if (!overlayProperties.enabled) {
      return emptyList()
    }

    val overlayDir = Paths.get(overlayProperties.path)
    if (!Files.isDirectory(overlayDir)) {
      return emptyList()
    }

    return try {
      Files.list(overlayDir)
        .map { it.fileName.toString() }
        .filter { it.contains("--") && it.endsWith(".yaml") }
        .map { it.substringBefore("--") }
        .distinct()
        .sorted()
        .toList()
    } catch (ex: Exception) {
      logger.warn("Failed to list overlay variants from {}: {}", overlayProperties.path, ex.message)
      emptyList()
    }
  }

  /**
   * Applies the overlay for [resourceType] and [variant] onto [baseYaml].
   *
   * The overlay file is first rendered as a FreeMarker template with the
   * given [model] (same variables available to base templates: instanceId,
   * namespace, userId, etc.), then deep-merged onto the base YAML.
   *
   * Returns the merged YAML string, or [baseYaml] unchanged if overlays are
   * disabled, no variant is specified, the overlay file is missing, or an
   * error occurs.
   */
  fun applyOverlay(
    baseYaml: String,
    resourceType: ResourceType,
    model: Map<String, Any?> = emptyMap(),
    variant: String? = null
  ): String {
    if (!overlayProperties.enabled || variant == null) {
      return baseYaml
    }

    val overlayFile = Paths.get(overlayProperties.path, "${variant}--${resourceType.filename}")
    if (!Files.exists(overlayFile)) {
      return baseYaml
    }

    return try {
      val rawOverlayContent = Files.readString(overlayFile).trim()
      if (rawOverlayContent.isEmpty()) {
        return baseYaml
      }

      // Render overlay as FreeMarker template
      val renderedOverlay = renderOverlayTemplate(rawOverlayContent, model, variant, resourceType)
        ?: return baseYaml

      val baseTree = yamlMapper.readTree(baseYaml) as? ObjectNode
        ?: return baseYaml
      val overlayTree = yamlMapper.readTree(renderedOverlay) as? ObjectNode
        ?: return baseYaml

      // Snapshot protected fields before merge
      val protectedSnapshot = snapshotProtectedFields(baseTree, resourceType)

      // Deep merge overlay onto base
      deepMerge(baseTree, overlayTree)

      // Restore protected fields
      restoreProtectedFields(baseTree, protectedSnapshot, resourceType)

      val mergedYaml = yamlMapper.writeValueAsString(baseTree)
      logger.info("Applied {} overlay (variant={}) from {}", resourceType.name, variant, overlayFile)
      mergedYaml
    } catch (ex: Exception) {
      logger.error("Failed to apply {} overlay (variant={}) from {}: {}", resourceType.name, variant, overlayFile, ex.message, ex)
      baseYaml
    }
  }

  /**
   * Renders the overlay content as a FreeMarker template with the given model.
   * Returns null on failure (fail-open).
   */
  private fun renderOverlayTemplate(
    overlayContent: String,
    model: Map<String, Any?>,
    variant: String,
    resourceType: ResourceType
  ): String? {
    return try {
      val templateName = "${variant}--${resourceType.filename}"
      val template = Template(templateName, StringReader(overlayContent), freemarkerConfig)
      val writer = StringWriter()
      template.process(model, writer)
      writer.toString().trim().ifEmpty { null }
    } catch (ex: Exception) {
      logger.error(
        "Failed to render FreeMarker overlay template (variant={}, resource={}): {}",
        variant, resourceType.name, ex.message, ex
      )
      null
    }
  }

  /**
   * Recursively deep-merges [overlay] into [base].
   * - Object nodes: merge recursively
   * - Array nodes and scalars: overlay replaces base
   */
  private fun deepMerge(base: ObjectNode, overlay: ObjectNode): ObjectNode {
    val fields = overlay.fieldNames()
    while (fields.hasNext()) {
      val fieldName = fields.next()
      val baseVal = base.get(fieldName)
      val overlayVal = overlay.get(fieldName)
      if (baseVal != null && baseVal.isObject && overlayVal != null && overlayVal.isObject) {
        deepMerge(baseVal as ObjectNode, overlayVal as ObjectNode)
      } else {
        base.set<JsonNode>(fieldName, overlayVal)
      }
    }
    return base
  }

  /**
   * Snapshots the fields that must be preserved through the merge.
   */
  private fun snapshotProtectedFields(
    base: ObjectNode,
    resourceType: ResourceType
  ): ProtectedFieldSnapshot {
    val metadata = base.path("metadata")
    val name = metadata.path("name").takeUnless { it.isMissingNode }
    val namespace = metadata.path("namespace").takeUnless { it.isMissingNode }
    val metadataLabels = snapshotLabels(metadata.path("labels"))

    val selectorMatchLabels = when (resourceType) {
      ResourceType.DEPLOYMENT -> snapshotLabels(base.at("/spec/selector/matchLabels"))
      ResourceType.SERVICE -> snapshotLabels(base.at("/spec/selector"))
      ResourceType.PVC, ResourceType.INGRESS -> emptyMap()
    }

    val templateLabels = when (resourceType) {
      ResourceType.DEPLOYMENT -> snapshotLabels(base.at("/spec/template/metadata/labels"))
      else -> emptyMap()
    }

    return ProtectedFieldSnapshot(
      name = name,
      namespace = namespace,
      metadataLabels = metadataLabels,
      selectorMatchLabels = selectorMatchLabels,
      templateLabels = templateLabels
    )
  }

  private fun snapshotLabels(labelsNode: JsonNode): Map<String, JsonNode> {
    if (labelsNode.isMissingNode || !labelsNode.isObject) return emptyMap()
    val result = mutableMapOf<String, JsonNode>()
    for (label in protectedLabels) {
      val value = labelsNode.get(label)
      if (value != null) {
        result[label] = value.deepCopy<JsonNode>()
      }
    }
    return result
  }

  /**
   * Restores protected fields from the snapshot onto the (already merged) tree.
   */
  private fun restoreProtectedFields(
    merged: ObjectNode,
    snapshot: ProtectedFieldSnapshot,
    resourceType: ResourceType
  ) {
    val mergedMeta = merged.path("metadata")
    if (mergedMeta is ObjectNode) {
      snapshot.name?.let { mergedMeta.set<JsonNode>("name", it) }
      snapshot.namespace?.let { mergedMeta.set<JsonNode>("namespace", it) }
      restoreLabelsOnNode(mergedMeta.path("labels"), snapshot.metadataLabels)
    }

    when (resourceType) {
      ResourceType.DEPLOYMENT -> {
        restoreLabelsOnNode(merged.at("/spec/selector/matchLabels"), snapshot.selectorMatchLabels)
        restoreLabelsOnNode(merged.at("/spec/template/metadata/labels"), snapshot.templateLabels)
      }
      ResourceType.SERVICE -> {
        restoreLabelsOnNode(merged.at("/spec/selector"), snapshot.selectorMatchLabels)
      }
      ResourceType.PVC, ResourceType.INGRESS -> { /* no additional protected fields */ }
    }
  }

  private fun restoreLabelsOnNode(node: JsonNode, labels: Map<String, JsonNode>) {
    if (node !is ObjectNode || labels.isEmpty()) return
    labels.forEach { (key, value) ->
      node.set<JsonNode>(key, value)
    }
  }

  private data class ProtectedFieldSnapshot(
    val name: JsonNode?,
    val namespace: JsonNode?,
    val metadataLabels: Map<String, JsonNode>,
    val selectorMatchLabels: Map<String, JsonNode>,
    val templateLabels: Map<String, JsonNode>
  )
}
