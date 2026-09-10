package org.projectcontinuum.core.api.server.config

import org.duckdb.DuckDBConnection
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.sql.DriverManager
import java.util.logging.Logger

private fun currentDuckDbPlatform(): String {
  val osName = System.getProperty("os.name").lowercase()
  val arch = if (System.getProperty("os.arch").lowercase().contains("aarch64")) "arm64" else "amd64"
  return when {
    osName.contains("win") -> "windows_amd64"
    osName.contains("mac") -> "osx_$arch"
    else -> "linux_$arch"
  }
}

private fun currentDuckDbPlatform(): String {
  val osName = System.getProperty("os.name").lowercase()
  val arch = if (System.getProperty("os.arch").lowercase().contains("aarch64")) "arm64" else "amd64"
  return when {
    osName.contains("win") -> "windows_amd64"
    osName.contains("mac") -> "osx_$arch"
    else -> "linux_$arch"
  }
}

@Component
class DuckDbConfig {

  private final val LOGGER = LoggerFactory.getLogger(DuckDbConfig::class.java)

  @Bean
  fun getDuckDbConnection(): DuckDBConnection {
    val connection = DriverManager.getConnection("jdbc:duckdb:")
    val statement = connection.createStatement()

    // Prefer the extension bundled into the jar at build time so pods don't need
    // outbound internet access to extensions.duckdb.org (blocked in many k8s clusters).
    // Falls back to a network INSTALL for platforms without a bundled resource.
    val resourcePath = "/duckdb-extensions/${currentDuckDbPlatform()}/httpfs.duckdb_extension"
    val bundledExtension = javaClass.getResourceAsStream(resourcePath)
    LOGGER.info("Loading DuckDB httpfs extension from bundled resource: $resourcePath")
    if (bundledExtension != null) {
      // DuckDB derives the extension's expected entrypoint symbol (e.g. "httpfs_init") from the
      // file's basename, so it must be named exactly "httpfs.duckdb_extension" — not a
      // Files.createTempFile() name, which inserts a random string into the basename.
      val extensionDir = Files.createTempDirectory("duckdb-extensions")
      extensionDir.toFile().deleteOnExit()
      val extensionFile = extensionDir.resolve("httpfs.duckdb_extension").toFile()
      extensionFile.deleteOnExit()
      bundledExtension.use { input -> extensionFile.outputStream().use { input.copyTo(it) } }
      check(extensionFile.length() >= 512) {
        "Bundled DuckDB extension at $resourcePath is only ${extensionFile.length()} bytes — " +
          "this is almost certainly a Git LFS pointer file, not the real binary. " +
          "Make sure the checkout step fetches LFS content (actions/checkout with lfs: true, or `git lfs pull`)."
      }
      statement.execute("LOAD '${extensionFile.absolutePath}';")
      LOGGER.info("Loaded DuckDB extension from $resourcePath")
    } else {
      statement.execute(
        """
                INSTALL httpfs;
                LOAD httpfs;
            """.trimIndent()
      )
    }

    return connection as DuckDBConnection
  }
}