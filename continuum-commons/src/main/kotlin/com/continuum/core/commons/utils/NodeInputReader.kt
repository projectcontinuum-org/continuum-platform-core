package com.continuum.core.commons.utils

import com.continuum.data.table.DataRow
import org.apache.hadoop.conf.Configuration
import org.apache.parquet.avro.AvroParquetReader
import org.apache.parquet.io.LocalInputFile
import java.io.Closeable
import java.nio.file.Path

class NodeInputReader(
    inputFilePath: Path
): Closeable {
    private val parquetReader = AvroParquetReader.builder<DataRow>(LocalInputFile(inputFilePath))
        .withConf(Configuration())
        .build()
    private val dataRowToMapConvertor = DataRowToMapConvertor()

    fun read(): Map<String, Any>? {
        val dataRow = parquetReader.read()
        return dataRow?.let {
            dataRowToMapConvertor.toMap(it)
        }
    }

    override fun close() {
        parquetReader.close()
    }
}