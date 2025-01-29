package com.continuum.core.commons.utils

import com.continuum.data.table.DataCell
import com.continuum.data.table.DataRow
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

    fun createOutputPortWriter(
        portId: String
    ): OutputPortWriter {
        return OutputPortWriter(outputDirectoryPath.resolve("output.$portId.parquet"))
    }

    class OutputPortWriter(
        outputFilePath: Path
    ): Closeable {
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
            row: Map<String, String>
        ) {
            val dataCells = row.entries.map {
                DataCell.newBuilder()
                    .setName(it.key)
                    .setValue(ByteBuffer.wrap(it.value.toByteArray()))
                    .setContentType("text/plain")
                    .build()
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
    }
}