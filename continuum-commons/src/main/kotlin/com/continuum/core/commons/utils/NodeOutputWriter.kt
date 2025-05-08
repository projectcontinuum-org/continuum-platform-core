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
        private val objectMapper = ObjectMapper()

        private val parquetWriter: ParquetWriter<DataRow> = AvroParquetWriter.builder<DataRow>(LocalOutputFile(outputFilePath))
            .withSchema(DataRow.getClassSchema())
            .withConf(Configuration())
            .withCompressionCodec(CompressionCodecName.SNAPPY)
            .withPageSize(1024 * 1024)
            .enableDictionaryEncoding()
            .withWriteMode(ParquetFileWriter.Mode.OVERWRITE)
            .withRowGroupSize(1024 * 1024 * 128)
            .build()

        fun write(data: DataRow) {
            parquetWriter.write(data)
        }

        fun write(
            rowNumber: Long,
            row: Map<String, Any>
        ) {
            val dataCells = row.entries.map {
                val dataCell = createDataCell(
                    cellName = it.key,
                    cellValue = it.value
                )
                spec.add(
                    mapOf(
                        "name" to it.key,
                        "type" to dataCell.contentType.toString()
                    )
                )
                dataCell
            }
            parquetWriter.write(
                DataRow(
                    rowNumber,
                    dataCells
                )
            )
        }

        override fun close() {
            parquetWriter.close()
        }

        fun getTableSpec(): List<Map<String, String>> {
            return spec.toList()
        }

        fun createDataCell(
            cellName: String,
            cellValue: Any
        ): DataCell {
            val mimeType =  when (cellValue) {
                is String -> "application/vnd.continuum.x-string"
                is ByteArray -> "application/vnd.continuum.x-byte-array"
                is ByteBuffer -> "application/vnd.continuum.x-byte-buffer"
                is Int -> "application/vnd.continuum.x-int"
                is Long -> "application/vnd.continuum.x-long"
                is Float -> "application/vnd.continuum.x-float"
                is Double -> "application/vnd.continuum.x-double"
                is Boolean -> "application/vnd.continuum.x-boolean"
                is List<*> -> "application/json"
                is Map<*, *> -> "application/json"
                else -> throw IllegalArgumentException("Unsupported type: ${cellValue::class.java.name}")
            }
            val value = when (cellValue) {
                is String -> cellValue
                is ByteArray -> cellValue
                is ByteBuffer -> cellValue
                is Int -> cellValue.toString()
                is Long -> cellValue.toString()
                is Float -> cellValue.toString()
                is Double -> cellValue.toString()
                is Boolean -> cellValue.toString()
                is List<*> -> objectMapper.writeValueAsString(cellValue)
                is Map<*, *> -> objectMapper.writeValueAsString(cellValue)
                else -> throw IllegalArgumentException("Unsupported type: ${cellValue::class.java.name}")
            }
            return DataCell.newBuilder()
                .setName(cellName)
                .setValue(ByteBuffer.wrap(value.toString().toByteArray()))
                .setContentType(mimeType)
                .build()
        }
    }
}