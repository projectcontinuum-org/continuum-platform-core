package com.continuum.core.commons.utils

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows


class DataRowToMapConverterTest {

    private val converter = DataRowToMapConverter()

    @Test
    fun stringConverterTest() {
        val dataRow = converter.toDataRow(
            rowNumber = 1,
            data = mapOf(
                "name" to "John Doe"
            )
        )

        assertEquals(
            1L,
            dataRow.rowNumber
        )
        assertEquals(
            "application/vnd.continuum.x-string",
            dataRow.cells[0].contentType
        )
        assertEquals(
            "name",
            dataRow.cells[0].name
        )
        assertEquals(
            "John Doe",
            String(dataRow.cells[0].value.array())
        )

        val rowMap = converter.toMap(dataRow)

        assert(
            rowMap.containsKey("name")
        )

        assertEquals(
            "John Doe",
            rowMap["name"]
        )
    }

    @Test
    fun intConverterTest() {
        val dataRow = converter.toDataRow(
            rowNumber = 1,
            data = mapOf(
                "age" to 30
            )
        )

        assertEquals(
            1L,
            dataRow.rowNumber
        )
        assertEquals(
            "application/vnd.continuum.x-int",
            dataRow.cells[0].contentType
        )
        assertEquals(
            "age",
            dataRow.cells[0].name
        )
        assertEquals(
            "30",
            String(dataRow.cells[0].value.array())
        )

        val rowMap = converter.toMap(dataRow)

        assert(
            rowMap.containsKey("age")
        )

        assert(
            rowMap["age"] is Int
        )

        assertEquals(
            30,
            rowMap["age"]
        )
    }

    @Test
    fun longConverterTest() {
        val dataRow = converter.toDataRow(
            rowNumber = 1,
            data = mapOf(
                "id" to 1234567890123L
            )
        )

        assertEquals(
            1L,
            dataRow.rowNumber
        )
        assertEquals(
            "application/vnd.continuum.x-long",
            dataRow.cells[0].contentType
        )
        assertEquals(
            "id",
            dataRow.cells[0].name
        )
        assertEquals(
            "1234567890123",
            String(dataRow.cells[0].value.array())
        )

        val rowMap = converter.toMap(dataRow)

        assert(
            rowMap.containsKey("id")
        )

        assert(
            rowMap["id"] is Long
        )

        assertEquals(
            1234567890123L,
            rowMap["id"]
        )
    }

    @Test
    fun floatConverterTest() {
        val dataRow = converter.toDataRow(
            rowNumber = 1,
            data = mapOf(
                "height" to 5.9f
            )
        )

        assertEquals(
            1L,
            dataRow.rowNumber
        )
        assertEquals(
            "application/vnd.continuum.x-float",
            dataRow.cells[0].contentType
        )
        assertEquals(
            "height",
            dataRow.cells[0].name
        )
        assertEquals(
            "5.9",
            String(dataRow.cells[0].value.array())
        )

        val rowMap = converter.toMap(dataRow)

        assert(
            rowMap.containsKey("height")
        )

        assert(
            rowMap["height"] is Float
        )

        assertEquals(
            5.9f,
            rowMap["height"]
        )
    }

    @Test
    fun doubleConverterTest() {
        val dataRow = converter.toDataRow(
            rowNumber = 1,
            data = mapOf(
                "weight" to 70.5
            )
        )

        assertEquals(
            1L,
            dataRow.rowNumber
        )
        assertEquals(
            "application/vnd.continuum.x-double",
            dataRow.cells[0].contentType
        )
        assertEquals(
            "weight",
            dataRow.cells[0].name
        )
        assertEquals(
            "70.5",
            String(dataRow.cells[0].value.array())
        )

        val rowMap = converter.toMap(dataRow)

        assert(
            rowMap.containsKey("weight")
        )

        assert(
            rowMap["weight"] is Double
        )

        assertEquals(
            70.5,
            rowMap["weight"]
        )
    }

    @Test
    fun booleanConverterTest() {
        val dataRow = converter.toDataRow(
            rowNumber = 1,
            data = mapOf(
                "isActive" to true
            )
        )

        assertEquals(
            1L,
            dataRow.rowNumber
        )
        assertEquals(
            "application/vnd.continuum.x-boolean",
            dataRow.cells[0].contentType
        )
        assertEquals(
            "isActive",
            dataRow.cells[0].name
        )
        assertEquals(
            "true",
            String(dataRow.cells[0].value.array())
        )

        val rowMap = converter.toMap(dataRow)

        assert(
            rowMap.containsKey("isActive")
        )

        assert(
            rowMap["isActive"] is Boolean
        )

        assertEquals(
            true,
            rowMap["isActive"]
        )
    }

    @Test
    fun listConverterTest() {
        val objectMapper = jacksonObjectMapper()
        val dataRow = converter.toDataRow(
            rowNumber = 1,
            data = mapOf(
                "tags" to listOf("tag1", "tag2", "tag3")
            )
        )

        assertEquals(
            1L,
            dataRow.rowNumber
        )
        assertEquals(
            "application/json",
            dataRow.cells[0].contentType
        )
        assertEquals(
            "tags",
            dataRow.cells[0].name
        )
        assertEquals(
            objectMapper.readTree("[\"tag1\", \"tag2\", \"tag3\"]"),
            objectMapper.readTree(String(dataRow.cells[0].value.array()))
        )

        val rowMap = converter.toMap(dataRow)

        assert(
            rowMap.containsKey("tags")
        )

        assert(
            rowMap["tags"] is List<*>
        )

        assertEquals(
            listOf("tag1", "tag2", "tag3"),
            rowMap["tags"]
        )
    }

    @Test
    fun mapConverterTest() {
        val objectMapper = jacksonObjectMapper()
        val dataRow = converter.toDataRow(
            rowNumber = 1,
            data = mapOf(
                "address" to mapOf("city" to "New York", "zip" to "10001")
            )
        )

        assertEquals(
            1L,
            dataRow.rowNumber
        )
        assertEquals(
            "application/json",
            dataRow.cells[0].contentType
        )
        assertEquals(
            "address",
            dataRow.cells[0].name
        )
        assertEquals(
            objectMapper.readTree("{\"city\":\"New York\",\"zip\":\"10001\"}"),
            objectMapper.readTree(String(dataRow.cells[0].value.array()))
        )

        val rowMap = converter.toMap(dataRow)

        assert(
            rowMap.containsKey("address")
        )

        assert(
            rowMap["address"] is Map<*, *>
        )

        assertEquals(
            mapOf("city" to "New York", "zip" to "10001"),
            rowMap["address"]
        )
    }

    @Test
    fun unsupportedTypeTest() {
        assertThrows<IllegalArgumentException> {
            converter.toDataRow(
                rowNumber = 1,
                data = mapOf(
                    "unsupported" to object {}
                )
            )
        }
    }

}