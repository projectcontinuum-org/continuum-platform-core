package org.projectcontinuum.core.credentials.config

import org.projectcontinuum.core.credentials.entity.JsonValue
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration

/**
 * Test-only JDBC configuration for H2. Overrides the main JdbcConfig so that
 * JsonValue is written as a plain String (H2 TEXT columns) instead of PGobject.
 */
@TestConfiguration
class H2JdbcConfig : AbstractJdbcConfiguration() {

  override fun userConverters(): List<Any> {
    return listOf(
      JsonValueToStringConverter(),
      StringToJsonValueConverter()
    )
  }

  @WritingConverter
  class JsonValueToStringConverter : Converter<JsonValue, String> {
    override fun convert(source: JsonValue): String {
      return source.value
    }
  }

  @ReadingConverter
  class StringToJsonValueConverter : Converter<String, JsonValue> {
    override fun convert(source: String): JsonValue {
      return JsonValue(source)
    }
  }
}
