package org.projectcontinuum.core.commons.utils

import org.apache.parquet.avro.AvroParquetWriter
import org.apache.parquet.conf.PlainParquetConfiguration
import org.apache.parquet.hadoop.ParquetFileWriter
import org.apache.parquet.hadoop.ParquetWriter
import org.apache.parquet.hadoop.metadata.CompressionCodecName
import org.apache.parquet.io.LocalOutputFile
import org.projectcontinuum.core.commons.activity.IContinuumNodeActivity.NodeOutputSystemPort
import org.projectcontinuum.core.commons.exception.NodeRuntimeException
import org.projectcontinuum.core.protocol.data.table.DataRow
import java.io.Closeable
import java.nio.file.Path

class NodeOutputWriter(
  private val outputDirectoryPath: Path
) {
  private val outputPortWriter = mutableMapOf<String, OutputPortWriter>()

  fun getTableSpec(
    portId: String
  ): List<Map<String, String>> {
    return outputPortWriter[portId]?.getTableSpec() ?: throw RuntimeException("No table spec for port $portId")
  }

  /**
   * Creates the writer for output port [portId], stored as `output.{portId}.parquet`.
   *
   * @throws NodeRuntimeException non-retriable, when [portId] is a reserved system key (starts with `$`).
   */
  fun createOutputPortWriter(
    portId: String
  ): OutputPortWriter {
    if (NodeOutputSystemPort.isSystemPort(portId)) {
      throw NodeRuntimeException(
        isRetriable = false,
        workflowId = "",
        nodeId = "",
        message = "Output port '$portId' is reserved: port ids starting with " +
          "'${NodeOutputSystemPort.SYSTEM_PORT_PREFIX}' are system keys such as \$error and \$variable"
      )
    }
    val writer = OutputPortWriter(outputDirectoryPath.resolve("output.$portId.parquet"))
    outputPortWriter[portId] = writer
    return writer
  }

  class OutputPortWriter(
    outputFilePath: Path
  ) : Closeable {
    private val spec = mutableSetOf<Map<String, String>>()

    private val dataRowToMapConverter = DataRowToMapConverter()

    private val parquetWriter: ParquetWriter<DataRow> =
        AvroParquetWriter.builder<DataRow>(LocalOutputFile(outputFilePath))
          .withConf(PlainParquetConfiguration())
          .withSchema(DataRow.getClassSchema())
          .withCompressionCodec(CompressionCodecName.SNAPPY)
          .enableDictionaryEncoding()
          .withWriteMode(ParquetFileWriter.Mode.OVERWRITE)
          .build()

    fun write(
      rowNumber: Long,
      row: Map<String, Any>
    ) {
      val dataRow = dataRowToMapConverter
        .toDataRow(
          rowNumber = rowNumber,
          data = row
        )
      dataRow.cells.forEach {
        spec.add(
          mapOf(
            "name" to it.name,
            "contentType" to it.contentType
          )
        )
      }
      parquetWriter.write(
        dataRowToMapConverter
          .toDataRow(
            rowNumber = rowNumber,
            data = row
          )
      )
    }

    override fun close() {
      parquetWriter.close()
    }

    fun getTableSpec(): List<Map<String, String>> {
      return spec.toList()
    }
  }
}