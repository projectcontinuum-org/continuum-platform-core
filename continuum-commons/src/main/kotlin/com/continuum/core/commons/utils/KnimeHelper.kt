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

        fun javaTypeToKnimeType(
            value: Any
        ): String {
            return when (value) {
                is String -> "string"
                is Int -> "long"
                is Long -> "long"
                is Float -> "double"
                is Double -> "double"
                is Boolean -> "boolean"
                is List<*> -> "string"
                is Map<*, *> -> "string"
                else -> throw IllegalArgumentException("Unsupported java type: ${value::class.java}")
            }
        }

        fun knimeTypeToJavaType(
            knimeType: String,
            knimeValue: Any
        ): Any {
            val value = knimeValue.toString()
            return when (knimeType.lowercase()) {
                "string" -> {
                    if (value.startsWith("{") && value.endsWith("}")) {
                        objectMapper.readValue<Map<String, Any>>(value)
                    } else if (value.startsWith("[") && value.endsWith("]")) {
                        objectMapper.readValue<List<Any>>(value)
                    } else {
                        value
                    }
                }
                "long" -> value.toLong()
                "double" -> value.toDouble()
                "boolean" -> value.toBoolean()
                else -> throw IllegalArgumentException("Unsupported knime type: $knimeType")
            }
        }

        fun dataCellToKnimeType(
            value: Any
        ): Any {
            return when (value) {
                is String -> value
                is Int -> value
                is Long -> value
                is Float -> value
                is Double -> value
                is Boolean -> value
                is List<*> -> objectMapper.writeValueAsString(value)
                is Map<*, *> -> objectMapper.writeValueAsString(value)
                else -> throw IllegalArgumentException("Unsupported data cell type: ${value::class.java}")
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
            while (input != null) {
                if (isFirst) {
                    val columnNames: Map<String, String> = input.entries.associate { entry ->
                        entry.key to javaTypeToKnimeType(entry.value)
                    }
                    (knimeTable["table-spec"] as MutableList<Any>).add(columnNames)
                    isFirst = false
                }
                val dataCells = input.entries.map { entry ->
                    dataCellToKnimeType(entry.value)
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
            knimeTableData.forEachIndexed { index, cells ->
                val row = mutableMapOf<String, Any>()
                knimeTableSpec.forEachIndexed { collIndex, coll ->
                    val cellValue = cells[collIndex]
                    val cellType = coll.values.first()
                    val cellName = coll.keys.first()
                    row[cellName] = knimeTypeToJavaType(
                        knimeType = cellType,
                        knimeValue = cellValue
                    )
                }
                outputPortWriter.write(
                    rowNumber = index.toLong(),
                    row = row
                )
            }
        }
    }
}