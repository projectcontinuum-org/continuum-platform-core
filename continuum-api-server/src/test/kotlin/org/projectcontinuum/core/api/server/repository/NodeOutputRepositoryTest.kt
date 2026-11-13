package org.projectcontinuum.core.api.server.repository

import org.duckdb.DuckDBConnection
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.projectcontinuum.core.api.server.exception.InvalidRequestException
import org.projectcontinuum.core.commons.utils.NodeOutputWriter
import software.amazon.awssdk.transfer.s3.S3TransferManager
import java.io.File
import java.nio.file.Path
import java.sql.DriverManager
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NodeOutputRepositoryTest {

  @TempDir
  lateinit var cacheDir: Path

  private lateinit var connection: DuckDBConnection
  private lateinit var s3TransferManager: S3TransferManager
  private lateinit var repository: NodeOutputRepository

  @BeforeEach
  fun setUp() {
    connection = DriverManager.getConnection("jdbc:duckdb:") as DuckDBConnection
    s3TransferManager = mock()
    repository = NodeOutputRepository(
      duckDBConnection = connection,
      s3TransferManager = s3TransferManager,
      s3BucketName = "bucket",
      s3BucketBasePath = "workflow-data",
      dataFileExtension = "parquet",
      cacheDirectoryBasePath = cacheDir.toString()
    )
  }

  @AfterEach
  fun tearDown() {
    connection.close()
  }

  /** Writes a cached output file for the ids so no S3 download is attempted. */
  private fun writeCachedOutput(workflowId: String, nodeId: String, outputId: String, rows: Int) {
    val dir = cacheDir.resolve(workflowId).resolve(nodeId)
    dir.toFile().mkdirs()
    NodeOutputWriter(dir).createOutputPortWriter(outputId).use { writer ->
      repeat(rows) { writer.write(it.toLong(), mapOf("n" to it)) }
    }
  }

  @Test
  fun `reads a page of a cached output with realistic ids`() {
    val runId = "0195c2a1-7d1e-7b3c-9a4f-1c2d3e4f5a6b"
    writeCachedOutput(runId, "reactflow_node.12", "output-1", rows = 7)

    val page = repository.getOutput(runId, "reactflow_node.12", "output-1", page = 1, pageSize = 3)

    assertEquals(7, page.totalElements)
    assertEquals(3, page.totalPages)
    assertEquals(3, page.data.size)
    assertTrue(page.hasNext)
    assertTrue(page.hasPrevious)
    verifyNoInteractions(s3TransferManager)
  }

  @ParameterizedTest
  @ValueSource(
    strings = [
      "'",
      "x'); SELECT 1; --",
      "'); SELECT 1; --",
      "../",
      "..",
      ".",
      "a/../../etc",
      "a\\b",
      "\$(id)",
      "`id`",
      "a\nb",
      "a b",
      "",
      "s3://bucket/key",
      "https://evil.example/x",
    ]
  )
  fun `rejects hostile ids in every position without touching S3 or DuckDB`(hostile: String) {
    assertThrows<InvalidRequestException> { repository.getOutput(hostile, "1", "output") }
    assertThrows<InvalidRequestException> { repository.getOutput("run-1", hostile, "output") }
    assertThrows<InvalidRequestException> { repository.getOutput("run-1", "1", hostile) }
    verify(s3TransferManager, never()).downloadFile(any<software.amazon.awssdk.transfer.s3.model.DownloadFileRequest>())
  }

  @Test
  fun `cache file always resolves under the cache base dir`() {
    writeCachedOutput("run-1", "node-1", "output", rows = 1)

    val file = repository.getCachedOutputFile("run-1", "node-1", "output")

    assertTrue(file.toPath().startsWith(File(cacheDir.toString()).canonicalFile.toPath()))
    assertEquals("output.output.parquet", file.name)
  }

  @Test
  fun `a quote in the cache base path is bound as a literal, not parsed as SQL`() {
    // The ids are allow-listed, but the configured base dir is not; it is bound via read_parquet(?), so a quote
    // in it is treated as part of the file name.
    val quotedBase = cacheDir.resolve("it's'); SELECT 1; --").toFile().apply { mkdirs() }
    val quotedRepository = NodeOutputRepository(
      duckDBConnection = connection,
      s3TransferManager = s3TransferManager,
      s3BucketName = "bucket",
      s3BucketBasePath = "workflow-data",
      dataFileExtension = "parquet",
      cacheDirectoryBasePath = quotedBase.path
    )
    val dir = quotedBase.toPath().resolve("run-1").resolve("node-1")
    dir.toFile().mkdirs()
    NodeOutputWriter(dir).createOutputPortWriter("output").use { it.write(0, mapOf("n" to 1)) }

    val page = quotedRepository.getOutput("run-1", "node-1", "output", page = 0, pageSize = 10)

    assertEquals(1, page.totalElements)
    assertEquals(1, page.data.size)
  }

  @ParameterizedTest
  @ValueSource(ints = [0, -1, NodeOutputRepository.MAX_PAGE_SIZE + 1, Int.MAX_VALUE])
  fun `rejects out-of-range page sizes`(pageSize: Int) {
    assertThrows<InvalidRequestException> { repository.getOutput("run-1", "1", "output", page = 0, pageSize = pageSize) }
  }

  @Test
  fun `rejects negative pages`() {
    assertThrows<InvalidRequestException> { repository.getOutput("run-1", "1", "output", page = -1, pageSize = 10) }
  }

  @Test
  fun `large page numbers do not overflow the offset`() {
    writeCachedOutput("run-1", "node-1", "output", rows = 2)

    val page = repository.getOutput("run-1", "node-1", "output", page = Int.MAX_VALUE, pageSize = NodeOutputRepository.MAX_PAGE_SIZE)

    assertEquals(0, page.data.size)
    assertEquals(2, page.totalElements)
  }
}
