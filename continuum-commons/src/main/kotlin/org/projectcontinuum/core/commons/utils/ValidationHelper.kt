package org.projectcontinuum.core.commons.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.InputFormat
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SpecificationVersion

class ValidationHelper {
  companion object {

    val mapper = ObjectMapper()

    fun validateJsonWithSchema(
      properties: Map<String, Any>,
      propertiesSchema: Map<String, Any>
    ) {

      val registry = SchemaRegistry.withDefaultDialect(
        SpecificationVersion.DRAFT_4
      )
      val schema = registry.getSchema(
        mapper.writeValueAsString(propertiesSchema),
        InputFormat.JSON
      )

      val errorMessages = schema.validate(
        mapper.writeValueAsString(properties),
        InputFormat.JSON
      )
      assert(errorMessages.isEmpty()) { "Validation failed: $errorMessages" }
    }
  }
}