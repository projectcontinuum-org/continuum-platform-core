package com.continuum.core.commons.utils

import com.continuum.data.table.DataCell
import com.continuum.data.table.DataRow
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File
import java.nio.ByteBuffer

class KnimeHelper {
    companion object {
        private val objectMapper = ObjectMapper()

        fun knimeTypeToMimeType(
            knimeType: String,
        ): String {
            return when (knimeType.lowercase()) {
                "string" -> "text/plain"
                "long" -> "application/x-number"
                else -> throw IllegalArgumentException("Unsupported knime type: $knimeType")
            }
        }

        fun mimeTypeToKnimeType(
            mimeType: String,
        ): String {
            return when (mimeType.lowercase()) {
                "text/plain" -> "string"
                "application/x-number" -> "long"
                else -> throw IllegalArgumentException("Unsupported mime type: $mimeType")
            }
        }

        fun dataCellToKnimeType(
            dataCell: DataCell
        ): Any {
            // convert bytebuffer to string
            val bytes = getBytesFromByteBuffer(dataCell.value)
            return when (dataCell.contentType) {
                "application/json" -> String(bytes)
                "text/plain" -> String(bytes)
                "application/x-number" -> dataCell.value.toString().toLong()
                else -> throw IllegalArgumentException("Unsupported mime type: ${dataCell.contentType}")
            }
        }

        fun continuumTableToKnimeContainerInputTable(
            reader: NodeInputReader
        ): Map<String, Any> {
            val knimeTable = mutableMapOf<String, Any>(
                "table-spec" to mutableListOf<Map<String, String>>(),
                "table-data" to mutableListOf<List<Any>>()
            )
            var isFirst = true
            var input = reader.read()
            if (isFirst) {
                val columnNames: Map<String, String> = input.cells.associate { cell ->
                    cell.name to mimeTypeToKnimeType(cell.contentType)
                }
                (knimeTable["table-spec"] as MutableList<Any>).add(columnNames)
                isFirst = false
            }
            while (input != null) {
                val dataCells = input.cells.map { cell ->
                    dataCellToKnimeType(cell)
                }
                (knimeTable["table-data"] as MutableList<Any>).add(dataCells)
                input = reader.read()
            }
            return knimeTable
        }

        fun knimeContainerOutputTableToPortOutput(
            outputFile: File,
            outputPortWriter: NodeOutputWriter.OutputPortWriter
        ) {
            val knimeContainerOutputTable: Map<String, Any> = objectMapper
                .readValue(outputFile)
            val knimeTableSpec = knimeContainerOutputTable["table-spec"] as List<Map<String, String>>
            val knimeTableData = knimeContainerOutputTable["table-data"] as List<List<Any>>
            knimeTableData.forEachIndexed { index, row ->
                outputPortWriter.write(
                    DataRow.newBuilder()
                        .setRowNumber(index.toLong())
                        .setCells(
                            row.mapIndexed { cellIndex, cell ->
                                DataCell.newBuilder()
                                    .setName(knimeTableSpec[cellIndex].keys.first())
                                    .setValue(
                                        ByteBuffer.wrap(
                                            cell.toString().toByteArray()
                                        )
                                    )
                                    .setContentType(
                                        knimeTypeToMimeType(
                                            knimeTableSpec[cellIndex].values.first()
                                        )
                                    )
                                    .build()
                            }.toList()
                        )
                        .build()
                )
            }
        }

        private fun getBytesFromByteBuffer(buffer: ByteBuffer): ByteArray {
            val bytes = ByteArray(buffer.remaining()) // Create a byte array of the remaining size
            buffer.get(bytes) // Transfer the bytes from the buffer to the array
            return bytes
        }
    }
}