package org.projectcontinuum.core.orchestration.workflow.fixtures

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.temporal.common.converter.JacksonJsonPayloadConverter
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel

object WorkflowFixtures {

  val objectMapper: ObjectMapper = JacksonJsonPayloadConverter.newDefaultObjectMapper()
    .apply { registerModule(KotlinModule.Builder().build()) }

  fun loadWorkflow(classpathResource: String): ContinuumWorkflowModel {
    val stream = WorkflowFixtures::class.java.getResourceAsStream(classpathResource)
      ?: error("Fixture not found on classpath: $classpathResource")
    return stream.use { objectMapper.readValue(it, ContinuumWorkflowModel::class.java) }
  }
}
