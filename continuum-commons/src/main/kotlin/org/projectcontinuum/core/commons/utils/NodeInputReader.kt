package org.projectcontinuum.core.commons.utils

import org.projectcontinuum.core.protocol.data.table.DataRow
import org.apache.hadoop.conf.Configuration
import org.apache.parquet.avro.AvroParquetReader
import org.apache.parquet.hadoop.ParquetFileReader
import org.apache.parquet.hadoop.ParquetReader
import org.apache.parquet.io.LocalInputFile
import java.io.Closeable
import java.nio.file.Path

/**
 * Reader for input data in Parquet format.
 *
 * Provides streaming access to rows in a Parquet file and metadata operations
 * such as retrieving the total row count without reading all data.
 *
 * Supports multi-pass reading through the reset() method, which allows
 * re-reading the file from the beginning by creating a new underlying reader.
 *
 * @param inputFilePath Path to the Parquet file to read
 */
class NodeInputReader(
  private val inputFilePath: Path
) : Closeable {
  private var parquetReader: ParquetReader<DataRow>? = createReader()
  private val dataRowToMapConverter = DataRowToMapConverter()

  // Cached row count to avoid repeatedly opening the file for metadata
  private var cachedRowCount: Long? = null

  // Track whether this reader has been closed
  private var closed = false

  /**
   * Returns the path to the underlying Parquet file.
   *
   * This method is useful when the file path needs to be passed to external tools
   * or scripts that process the Parquet file directly (e.g., Python scripts for
   * machine learning training).
   *
   * **Use Cases:**
   * - Passing the file path to external Python scripts for ML training
   * - Copying or moving the file for archival purposes
   * - Logging the file location for debugging
   *
   * @return The [Path] to the input Parquet file
   */
  fun getFilePath(): Path = inputFilePath

  /**
   * Creates a new AvroParquetReader for the input file.
   *
   * @return A new ParquetReader instance
   */
  private fun createReader(): ParquetReader<DataRow> = AvroParquetReader.builder<DataRow>(LocalInputFile(inputFilePath))
    .withConf(Configuration())
    .build()

  /**
   * Reads the next row from the Parquet file.
   *
   * @return A map representation of the next row, or null if end of file is reached
   * @throws IllegalStateException if the reader has been closed
   */
  fun read(): Map<String, Any>? {
    check(!closed) { "Cannot read from a closed NodeInputReader" }
    val dataRow = parquetReader?.read()
    return dataRow?.let {
      dataRowToMapConverter.toMap(it)
    }
  }

  /**
   * Resets the reader to the beginning of the file.
   *
   * Creates a new underlying ParquetReader instance, allowing the file to be read again
   * from the start. This is useful for multi-pass algorithms that need to iterate through
   * data multiple times (e.g., calculating statistics then processing based on those statistics).
   *
   * **Performance Note**: Reopening the Parquet file involves file I/O overhead. For small
   * to medium-sized files, this overhead is typically negligible compared to the memory
   * savings of avoiding buffering all data in RAM.
   *
   * **Example:**
   * ```kotlin
   * NodeInputReader(inputPath).use { reader ->
   *   // First pass: calculate statistics
   *   while (reader.read() != null) {
   *     // Calculate mean, std, etc.
   *   }
   *
   *   // Reset to beginning
   *   reader.reset()
   *
   *   // Second pass: process data using statistics
   *   while (reader.read() != null) {
   *     // Flag outliers, etc.
   *   }
   * }
   * ```
   *
   * @throws IllegalStateException if the reader has been closed
   */
  fun reset() {
    check(!closed) { "Cannot reset a closed NodeInputReader" }
  
    // Close existing reader if not null
    parquetReader?.close()

    // Create new reader starting from beginning
    parquetReader = createReader()
  }

  /**
   * Gets the total number of rows in the Parquet file without reading all the data.
   *
   * This method reads only the Parquet file metadata (footer) to determine the row count,
   * which is much more efficient than streaming through all rows. Use this method when you
   * only need to know the count without accessing the actual row data.
   *
   * **When to use getRowCount() vs read():**
   * - Use `getRowCount()` for metadata operations where only the count is needed (e.g.,
   *   pre-allocating arrays, progress bars, validation checks)
   * - Use `read()` when you need to access the actual row data (e.g., transformations,
   *   aggregations, filtering)
   * - Can combine both: call `getRowCount()` first for initialization, then `read()`
   *   to process rows
   *
   * **Performance consideration:**
   * Reading metadata is O(1) and only reads the file footer, while reading all rows
   * is O(n) and processes the entire file. For large files, this difference can be
   * significant (milliseconds vs seconds/minutes).
   *
   * The row count is cached after the first call, so subsequent calls return immediately
   * without re-opening the file.
   *
   * **Example use cases:**
   * - Pre-allocating buffers or data structures sized to row count
   * - Calculating statistics before processing (e.g., percentiles)
   * - Progress tracking and reporting (current row / total rows)
   * - Validation (ensuring file has expected number of records)
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
    if (!closed) {
      parquetReader?.close()
      parquetReader = null
      closed = true
    }
  }
}