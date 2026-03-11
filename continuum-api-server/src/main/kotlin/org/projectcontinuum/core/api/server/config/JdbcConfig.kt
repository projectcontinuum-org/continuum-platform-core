package org.projectcontinuum.core.api.server.config

import org.postgresql.util.PGobject
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration

@Configuration
class JdbcConfig : AbstractJdbcConfiguration() {

  override fun userConverters(): List<Any> {
    return listOf(PGobjectToStringConverter())
  }

  class PGobjectToStringConverter : Converter<PGobject, String> {
    override fun convert(source: PGobject): String {
      return source.value ?: ""
    }
  }
}
