package org.projectcontinuum.core.orchestration.registration

import com.fasterxml.jackson.databind.ObjectMapper
import org.projectcontinuum.core.protocol.event.FeatureRegistrationRequest
import java.time.Instant

/**
 * Builds the [FeatureRegistrationRequest] of a [BuiltinNodeDefinition]. Pure apart from JSON serialization, so the
 * payload can be checked field by field.
 *
 * The manifest is the node's `NodeData` serialized the way `continuum-worker-springboot-starter`'s
 * `FeatureRegistrationPublisher` serializes a worker node's metadata (a plain Jackson 2 mapper), so the message bridge
 * and the api-server read it exactly like any other registration.
 */
object BuiltinNodeRegistrationRequests {

  private val manifestMapper = ObjectMapper()

  fun build(
    definition: BuiltinNodeDefinition,
    workerId: String,
    registeredAt: Instant
  ): FeatureRegistrationRequest {
    val nodeManifestJson = manifestMapper.writeValueAsString(definition.nodeData)
    return FeatureRegistrationRequest.newBuilder()
      .setNodeId(definition.nodeId)
      .setWorkerId(workerId)
      .setFeatureId(BuiltinNodes.FEATURE_ID)
      .setTaskQueue(BuiltinNodes.TASK_QUEUE)
      .setNodeManifest(nodeManifestJson)
      .setDocumentationMarkdown(definition.documentationMarkdown)
      .setRegisteredAtTimestampUtc(registeredAt)
      .setCategories(definition.categories)
      .setExtensions(emptyMap())
      .build()
  }
}
