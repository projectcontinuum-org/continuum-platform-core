package org.projectcontinuum.core.api.server.config

import org.postgresql.util.PGobject
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories

@Configuration
@EnableJdbcRepositories(basePackages = ["org.projectcontinuum.core.api.server.repository.jdbc"])
class JdbcConfig {

  @Bean
  fun jdbcCustomConversions(): JdbcCustomConversions {
    return JdbcCustomConversions(listOf(PGobjectToStringConverter()))
  }

  class PGobjectToStringConverter : Converter<PGobject, String> {
    override fun convert(source: PGobject): String {
      return source.value ?: ""
    }
  }
}
