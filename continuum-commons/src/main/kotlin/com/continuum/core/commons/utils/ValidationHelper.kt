package com.continuum.core.commons.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.InputFormat
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion

class ValidationHelper {
  companion object {

    val mapper = ObjectMapper()

    fun validateJsonWithSchema(
      properties: Map<String, Any>,
      propertiesSchema: Map<String, Any>
    ) {

      val factory = JsonSchemaFactory.getInstance(
        SpecVersion.VersionFlag.V4
      )
      val schema = factory.getSchema(
        mapper.writeValueAsString(propertiesSchema)
      )

      val errorMessages = schema.validate(
        mapper.writeValueAsString(properties),
        InputFormat.JSON
      )
      assert(errorMessages.isEmpty()) { "Validation failed: $errorMessages" }
    }
  }
}