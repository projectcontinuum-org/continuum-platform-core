package com.continuum.core.commons.utils

import com.continuum.data.table.DataCell
import com.continuum.data.table.DataRow
import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

class DataRowToMapConverter {
    private val objectMapper = ObjectMapper()

    fun toDataRow(
        rowNumber: Long,
        data: Map<String, Any>
    ): DataRow {
        val dataCells = data.entries.map {
            createDataCell(it.key, it.value)
        }
        return DataRow.newBuilder()
            .setRowNumber(rowNumber)
            .setCells(dataCells)
            .build()
    }

    fun toMap(
        dataRow: DataRow
    ): Map<String, Any> {
        return dataRow.cells.associate { dataCell ->
            createMapEntry(dataCell)
        }
    }

    private fun createMapEntry(
        dataCell: DataCell
    ): Pair<String, Any> {
        val name = dataCell.name
        val valueString = String(dataCell.value.array(), StandardCharsets.UTF_8)
        val value = when (dataCell.contentType) {
            "application/vnd.continuum.x-string" -> valueString
            "application/vnd.continuum.x-int" -> valueString.toInt()
            "application/vnd.continuum.x-long" -> valueString.toLong()
            "application/vnd.continuum.x-float" -> valueString.toFloat()
            "application/vnd.continuum.x-double" -> valueString.toDouble()
            "application/vnd.continuum.x-boolean" -> valueString.toBoolean()
            "application/json" -> objectMapper.readValue(valueString, Any::class.java)
            else -> throw IllegalArgumentException("Unsupported content type: ${dataCell.contentType}")
        }
        return Pair(name, value)
    }

    private fun createDataCell(
        cellName: String,
        cellValue: Any
    ): DataCell {
        val mimeType =  when (cellValue) {
            is String -> "application/vnd.continuum.x-string"
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
            is Int -> cellValue.toString()
            is Long -> "$cellValue"
            is Float -> "$cellValue"
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