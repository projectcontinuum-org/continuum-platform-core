package com.continuum.core.commons.utils

import com.continuum.data.table.DataCell
import com.continuum.data.table.DataRow
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.hadoop.conf.Configuration
import org.apache.parquet.avro.AvroParquetWriter
import org.apache.parquet.hadoop.ParquetFileWriter
import org.apache.parquet.hadoop.ParquetWriter
import org.apache.parquet.hadoop.metadata.CompressionCodecName
import org.apache.parquet.io.LocalOutputFile
import java.io.Closeable
import java.nio.ByteBuffer
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

    fun createOutputPortWriter(
        portId: String
    ): OutputPortWriter {
        val writer = OutputPortWriter(outputDirectoryPath.resolve("output.$portId.parquet"))
        outputPortWriter[portId] = writer
        return writer
    }

    class OutputPortWriter(
        outputFilePath: Path
    ): Closeable {
        private val spec = mutableSetOf<Map<String, String>>()
//        private val objectMapper = ObjectMapper()
        private val dataRowToMapConvertor = DataRowToMapConvertor()

        private val parquetWriter: ParquetWriter<DataRow> = AvroParquetWriter.builder<DataRow>(LocalOutputFile(outputFilePath))
            .withSchema(DataRow.getClassSchema())
            .withConf(Configuration())
            .withCompressionCodec(CompressionCodecName.SNAPPY)
            .withPageSize(1024 * 1024)
            .enableDictionaryEncoding()
            .withWriteMode(ParquetFileWriter.Mode.OVERWRITE)
            .withRowGroupSize(1024 * 1024 * 128)
            .build()

//        private fun write(data: DataRow) {
//            parquetWriter.write(data)
//        }

        fun write(
            rowNumber: Long,
            row: Map<String, Any>
        ) {
            val dataRow = dataRowToMapConvertor
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
                dataRowToMapConvertor
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