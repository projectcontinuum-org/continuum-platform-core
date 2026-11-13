package org.projectcontinuum.core.api.server.repository

import org.projectcontinuum.core.api.server.exception.InvalidRequestException
import org.projectcontinuum.core.api.server.model.CellDto
import org.projectcontinuum.core.api.server.model.Page
import org.duckdb.DuckDBArray
import org.duckdb.DuckDBConnection
import org.duckdb.DuckDBResultSet
import org.duckdb.DuckDBStruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Repository
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.transfer.s3.S3TransferManager
import software.amazon.awssdk.transfer.s3.model.DownloadFileRequest
import java.io.File

@Repository
class NodeOutputRepository(
  private val duckDBConnection: DuckDBConnection,
  private val s3TransferManager: S3TransferManager,
  @param:Value("\${continuum.core.api-server.storage.bucket-name}")
  val s3BucketName: String,
  @param:Value("\${continuum.core.api-server.storage.bucket-base-path}")
  val s3BucketBasePath: String,
  @param:Value("\${continuum.core.api-server.dataFileExtension:parquet}")
  val dataFileExtension: String,
  @param:Value("\${continuum.core.api-server.cache-directory-base-path}")
  val cacheDirectoryBasePath: String
) {

  companion object {
    private val LOGGER = LoggerFactory.getLogger(NodeOutputRepository::class.java)

    /** Largest page a client may request; larger values are rejected with 400. */
    const val MAX_PAGE_SIZE = 1000

    /**
     * Allow-list for the run id (Temporal run UUID), node id (React Flow / MCP node key) and output port id.
     * These become path segments of both the local cache file and the S3 key, so path separators, quotes,
     * whitespace and shell/SQL metacharacters are all rejected.
     */
    private val ID_PATTERN = Regex("^[A-Za-z0-9._-]{1,255}$")

    private const val COUNT_SQL = "SELECT COUNT(*) AS total FROM read_parquet(?)"
    private const val PAGE_SQL = "SELECT * FROM read_parquet(?) LIMIT ? OFFSET ?"

    fun validateId(name: String, value: String) {
      if (!ID_PATTERN.matches(value) || value == "." || value.contains("..")) {
        throw InvalidRequestException("Invalid $name: must match ${ID_PATTERN.pattern} and must not contain '..'")
      }
    }

    fun validatePaging(page: Int, pageSize: Int) {
      if (page < 0) {
        throw InvalidRequestException("Invalid page: must be >= 0")
      }
      if (pageSize !in 1..MAX_PAGE_SIZE) {
        throw InvalidRequestException("Invalid pageSize: must be between 1 and $MAX_PAGE_SIZE")
      }
    }
  }

  fun getOutput(
    workflowId: String,
    nodeId: String,
    outputId: String,
    page: Int = 0,
    pageSize: Int = 100
  ): Page<List<CellDto>> {
    validatePaging(page, pageSize)
    val localFile = getCachedOutputFile(
      workflowId = workflowId,
      nodeId = nodeId,
      outputId = outputId
    )
    // The path is passed as a bound parameter (DuckDB JDBC supports `read_parquet(?)`), never interpolated
    // into SQL: the connection has httpfs loaded, so SQL injection here would reach the network and filesystem.
    val filePath = localFile.path
    val offset = page.toLong() * pageSize

    val totalRows = duckDBConnection.prepareStatement(COUNT_SQL).use { statement ->
      statement.setString(1, filePath)
      statement.executeQuery().use { resultSet ->
        resultSet.next()
        resultSet.getLong("total")
      }
    }

    val rows = mutableListOf<List<CellDto>>()
    duckDBConnection.prepareStatement(PAGE_SQL).use { statement ->
      statement.setString(1, filePath)
      statement.setLong(2, pageSize.toLong())
      statement.setLong(3, offset)
      statement.executeQuery().use { resultSet ->
        while (resultSet.next()) {
          // map dataset to avro object
          val cells = resultSet.getArray("cells") as DuckDBArray
          @Suppress("UNCHECKED_CAST")
          val cellsDtos = (cells.array as Array<Object>).mapNotNull {
            if (it is DuckDBStruct) {
              val value = (it.map["value"] as DuckDBResultSet.DuckDBBlobResult)
              CellDto(
                name = it.map["name"] as String,
                contentType = it.map["contentType"] as String,
                value = value.getBytes(
                  1,
                  value.length().toInt()
                )
              )
            } else {
              null
            }
          }
          rows.add(cellsDtos)
        }
      }
    }
    // Calculate total pages
    val totalPages = if (totalRows % pageSize == 0L) {
      totalRows / pageSize
    } else {
      (totalRows / pageSize) + 1
    }
    // Return the Page object
    return Page(
      data = rows,
      currentPage = page,
      currentPageSize = pageSize,
      totalPages = totalPages.toInt(),
      totalElements = totalRows,
      hasNext = page < totalPages - 1,
      hasPrevious = page > 0
    )
  }

  /**
   * Validates the ids, resolves the local cache file for the output (downloading it from S3 on a cache miss)
   * and returns it. Throws [InvalidRequestException] for ids that fail the allow-list or would resolve
   * outside [cacheDirectoryBasePath].
   */
  fun getCachedOutputFile(
    workflowId: String,
    nodeId: String,
    outputId: String
  ): File {
    validateId("workflowId", workflowId)
    validateId("nodeId", nodeId)
    validateId("outputId", outputId)

    val fileName = "output.$outputId.$dataFileExtension"
    val bucketKey = "$s3BucketBasePath/$workflowId/$nodeId/$fileName"
    val cacheBaseDir = File(cacheDirectoryBasePath).canonicalFile
    val localCacheFile = File(cacheBaseDir, listOf(workflowId, nodeId, fileName).joinToString(File.separator))
      .canonicalFile
    // Defence in depth on top of the allow-list: the resolved file must stay under the cache base dir.
    if (!localCacheFile.toPath().startsWith(cacheBaseDir.toPath())) {
      throw InvalidRequestException("Invalid output path")
    }

    // Check if the file exists locally
    if (!localCacheFile.exists()) {

      // Ensure parent directories exist
      val parentDir = localCacheFile.parentFile
      if (parentDir != null && !parentDir.exists()) {
        parentDir.mkdirs()
      }

      LOGGER.debug("Downloading file from S3: {} to {}", bucketKey, localCacheFile)
      s3TransferManager
        .downloadFile(
          DownloadFileRequest.builder()
            .getObjectRequest(
              GetObjectRequest.builder()
                .bucket(s3BucketName)
                .key(bucketKey)
                .build()
            )
            .destination(localCacheFile.toPath())
            .build()
        )
        .completionFuture()
        .join()
    }

    LOGGER.debug("Using local cache file: {} for S3 object: {}", localCacheFile, bucketKey)
    return localCacheFile
  }
}
