package com.continuum.core.commons.utils

import com.continuum.data.table.DataRow
import org.apache.hadoop.conf.Configuration
import org.apache.parquet.avro.AvroParquetReader
import org.apache.parquet.hadoop.ParquetFileReader
import org.apache.parquet.io.LocalInputFile
import java.io.Closeable
import java.nio.file.Path

/**
 * Reader for input data in Parquet format.
 *
 * Provides streaming access to rows in a Parquet file and metadata operations
 * such as retrieving the total row count without reading all data.
 *
 * @param inputFilePath Path to the Parquet file to read
 */
class NodeInputReader(
  private val inputFilePath: Path
) : Closeable {
  private val parquetReader = AvroParquetReader.builder<DataRow>(LocalInputFile(inputFilePath))
    .withConf(Configuration())
    .build()
  private val dataRowToMapConverter = DataRowToMapConverter()

  // Cached row count to avoid repeatedly opening the file for metadata
  private var cachedRowCount: Long? = null

  /**
   * Reads the next row from the Parquet file.
   *
   * @return A map representation of the next row, or null if end of file is reached
   */
  fun read(): Map<String, Any>? {
    val dataRow = parquetReader.read()
    return dataRow?.let {
      dataRowToMapConverter.toMap(it)
    }
  }

  /**
   * Gets the total number of rows in the Parquet file without reading all the data.
   *
   * This method reads only the Parquet file metadata (footer) to determine the row count,
   * which is much more efficient than streaming through all rows.
   *
   * The row count is cached after the first call, so subsequent calls return immediately
   * without re-opening the file.
   *
   * This is useful for:
   * - Pre-allocating buffers or data structures
   * - Calculating statistics before processing
   * - Progress tracking and reporting
   * - Optimizing multi-pass algorithms
   *
   * @return The total number of rows in the Parquet file
   */
  fun getRowCount(): Long {
    // Return cached value if available
    cachedRowCount?.let { return it }

    // Read metadata once and cache the result
    val rowCount = ParquetFileReader.open(LocalInputFile(inputFilePath)).use { reader ->
      reader.recordCount
    }

    cachedRowCount = rowCount
    return rowCount
  }

  override fun close() {
    parquetReader.close()
  }
}