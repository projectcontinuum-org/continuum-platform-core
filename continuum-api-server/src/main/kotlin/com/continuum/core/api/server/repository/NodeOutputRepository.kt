package com.continuum.core.api.server.repository

import com.continuum.core.api.server.model.CellDto
import com.continuum.core.api.server.model.Page
import org.duckdb.DuckDBArray
import org.duckdb.DuckDBConnection
import org.duckdb.DuckDBResultSet
import org.duckdb.DuckDBStruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Repository
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.transfer.s3.S3TransferManager
import software.amazon.awssdk.transfer.s3.model.DownloadFileRequest
import java.io.File
import java.net.URI
import java.time.Duration

@Repository
class NodeOutputRepository(
    private val duckDBConnection: DuckDBConnection,
    private val s3TransferManager: S3TransferManager,
    private val s3Presigner: S3Presigner,
    @Value("\${continuum.core.api-server.cache-bucket-name}")
    val s3BucketName: String = "continuum-data",
    @Value("\${continuum.core.api-server.cache-bucket-base-path}")
    val s3BucketBasePath: String,
    @Value("\${continuum.core.api-server.dataFileExtension:parquet}")
    val dataFileExtension: String,
    @Value("\${continuum.core.api-server.cache-directory-base-path}")
    val cacheDirectoryBasePath: String
) {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(NodeOutputRepository::class.java)
    }

    fun getOutput(
        workflowId: String,
        nodeId: String,
        outputId: String,
        page: Int = 0,
        pageSize: Int = 100
    ): Page<List<CellDto>> {
        val objectPreSignedUri = getPreSignedUrl(
            workflowId = workflowId,
            nodeId = nodeId,
            outputId = outputId
        )
        val offset = page * pageSize
        val statement = duckDBConnection.createStatement()

        // Get the total number of rows
        val countResultSet = statement.executeQuery(
            """
                SELECT COUNT(*) AS total FROM read_parquet('$objectPreSignedUri');
            """.trimIndent()
        )
        countResultSet.next()
        val totalRows = countResultSet.getLong("total")

        // Get the rows for the current page
        val resultSet = statement.executeQuery(
            """
                SELECT * FROM read_parquet('$objectPreSignedUri')
                LIMIT $pageSize OFFSET $offset;
            """.trimIndent()
        )
        val rows = mutableListOf<List<CellDto>>()
        while (resultSet.next()) {
            // map dataset to avro object
            val cells = resultSet.getArray("cells") as DuckDBArray
            val cellsDtos = (cells.array as Array<Object>).mapNotNull {
                if (it is DuckDBStruct) {
                    val value = (it.map["value"] as DuckDBResultSet.DuckDBBlobResult)
                    CellDto(
                        name = it.map["name"] as String,
                        contentType = it.map["contentType"] as String,
                        value = value.getBytes(1, value.length().toInt())
                    )
                } else {
                    null
                }
            }
            rows.add(cellsDtos)
        }
        resultSet.close()
        statement.close()
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

    fun getPreSignedUrl(
        workflowId: String,
        nodeId: String,
        outputId: String
    ): URI {
        val relativeKey = "$workflowId/$nodeId/output.$outputId.$dataFileExtension"
        val bucketKey = "$s3BucketBasePath/$relativeKey"
        val localCachePath = listOf(
            cacheDirectoryBasePath,
            workflowId,
            nodeId,
            "output.$outputId.$dataFileExtension"
        ).joinToString(File.separator)
        val localCacheFile = File(localCachePath)

        // Check if the file exists locally
        if (!localCacheFile.exists()) {
            LOGGER.debug("Downloading file from S3: $bucketKey to $localCachePath")
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
        }

        return URI("$localCachePath").also {
            LOGGER.debug("Using local cache file: $localCachePath for S3 object: $bucketKey")
        }
    }
}