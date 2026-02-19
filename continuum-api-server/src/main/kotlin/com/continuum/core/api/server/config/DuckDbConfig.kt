package com.continuum.core.api.server.config

import org.duckdb.DuckDBConnection
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component
import java.sql.DriverManager

@Component
class DuckDbConfig {
  @Bean
  fun getDuckDbConnection(): DuckDBConnection {
    // Create a new DuckDB connection
    val connection = DriverManager.getConnection("jdbc:duckdb:")
    connection.createStatement()
      .execute(
        """
                INSTALL httpfs;
                LOAD httpfs;
            """.trimIndent()
      )
    return connection as DuckDBConnection
  }
}