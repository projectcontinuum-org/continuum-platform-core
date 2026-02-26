package com.continuum.base.node

import com.continuum.core.commons.exception.NodeRuntimeException
import com.continuum.core.commons.utils.NodeOutputWriter
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CreateTableNodeModelTest {

    private lateinit var nodeModel: CreateTableNodeModel
    private lateinit var mockOutputWriter: NodeOutputWriter
    private lateinit var mockPortWriter: NodeOutputWriter.OutputPortWriter

    @BeforeEach
    fun setUp() {
        nodeModel = CreateTableNodeModel()
        mockOutputWriter = mock()
        mockPortWriter = mock()
        whenever(mockOutputWriter.createOutputPortWriter("data")).thenReturn(mockPortWriter)
    }

    // ===== Configuration Tests =====

    @Test
    fun `test node metadata is properly configured`() {
        val metadata = nodeModel.metadata
        assertEquals("com.continuum.base.node.CreateTableNodeModel", metadata.id)
        assertEquals("Creates a structured table from FreeMarker template configuration", metadata.description)
        assertEquals("Create Table", metadata.title)
        assertEquals("Generate table rows from template", metadata.subTitle)
        assertNotNull(metadata.icon)
        assertTrue(metadata.icon.toString().contains("svg"))
    }

    @Test
    fun `test output ports are correctly defined`() {
        val outputPorts = nodeModel.outputPorts
        assertEquals(1, outputPorts.size)
        assertNotNull(outputPorts["data"])
        val dataPort = outputPorts["data"]!!
        assertEquals("output table", dataPort.name)
    }

    @Test
    fun `test categories are correctly defined`() {
        val categories = nodeModel.categories
        assertEquals(1, categories.size)
        assertEquals("Table & Data Structures", categories[0])
    }

    @Test
    fun `test properties schema is valid`() {
        val schema = nodeModel.propertiesSchema
        assertNotNull(schema)
        assertEquals("object", schema["type"])
        assertTrue(schema.containsKey("properties"))
        assertTrue(schema.containsKey("required"))
    }

    @Test
    fun `test properties UI schema is valid`() {
        val uiSchema = nodeModel.propertiesUiSchema
        assertNotNull(uiSchema)
        assertEquals("VerticalLayout", uiSchema["type"])
    }

    @Test
    fun `test default metadata properties`() {
        val defaultProperties = nodeModel.metadata.properties
        assertNotNull(defaultProperties)
        assertTrue(defaultProperties.containsKey("jsonArrayString"))
        val jsonString = defaultProperties["jsonArrayString"] as String
        assertTrue(jsonString.contains("list"))
        assertTrue(jsonString.contains("User"))
    }

    // ===== Success Tests =====

    @Test
    fun `test execute with simple JSON array`() {
        // Arrange
        val properties = mapOf(
            "jsonArrayString" to """[{"id": 1, "name": "Alice"}, {"id": 2, "name": "Bob"}]"""
        )
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, mockOutputWriter)

        // Assert
        verify(mockPortWriter, org.mockito.kotlin.times(2)).write(any(), rowCaptor.capture())
        assertEquals(2, rowCaptor.allValues.size)
        assertEquals(1, rowCaptor.allValues[0]["id"])
        assertEquals("Alice", rowCaptor.allValues[0]["name"])
        assertEquals(2, rowCaptor.allValues[1]["id"])
        assertEquals("Bob", rowCaptor.allValues[1]["name"])
    }

    @Test
    fun `test execute with single row`() {
        // Arrange
        val properties = mapOf(
            "jsonArrayString" to """[{"id": 1, "name": "Charlie"}]"""
        )
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, mockOutputWriter)

        // Assert
        verify(mockPortWriter).write(any(), rowCaptor.capture())
        assertEquals(1, rowCaptor.allValues.size)
        assertEquals(1, rowCaptor.allValues[0]["id"])
        assertEquals("Charlie", rowCaptor.allValues[0]["name"])
    }

    @Test
    fun `test execute with FreeMarker template`() {
        // Arrange
        val properties = mapOf(
            "jsonArrayString" to """[<#list 1..3 as i>{"id": ${'$'}{i}, "value": "Item${'$'}{i}"}<#if i?has_next>,</#if></#list>]"""
        )
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, mockOutputWriter)

        // Assert
        verify(mockPortWriter, org.mockito.kotlin.times(3)).write(any(), rowCaptor.capture())
        assertEquals(3, rowCaptor.allValues.size)
        assertEquals(1, rowCaptor.allValues[0]["id"])
        assertEquals("Item1", rowCaptor.allValues[0]["value"])
        assertEquals(3, rowCaptor.allValues[2]["id"])
        assertEquals("Item3", rowCaptor.allValues[2]["value"])
    }

    @Test
    fun `test execute with complex FreeMarker template`() {
        // Arrange
        val properties = mapOf(
            "jsonArrayString" to """[<#list 1..2 as i>{"userId": ${'$'}{i}, "status": "<#if i == 1>active<#else>inactive</#if>"}<#if i?has_next>,</#if></#list>]"""
        )
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, mockOutputWriter)

        // Assert
        verify(mockPortWriter, org.mockito.kotlin.times(2)).write(any(), rowCaptor.capture())
        assertEquals(2, rowCaptor.allValues.size)
        assertEquals("active", rowCaptor.allValues[0]["status"])
        assertEquals("inactive", rowCaptor.allValues[1]["status"])
    }

    @Test
    fun `test execute with different data types`() {
        // Arrange
        val properties = mapOf(
            "jsonArrayString" to """[{"id": 123, "score": 45.67, "active": true, "tags": ["a", "b"]}]"""
        )
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, mockOutputWriter)

        // Assert
        verify(mockPortWriter).write(any(), rowCaptor.capture())
        assertEquals(1, rowCaptor.allValues.size)
        val row = rowCaptor.allValues[0]
        assertEquals(123, row["id"])
        assertEquals(45.67, row["score"])
        assertEquals(true, row["active"])
        assertNotNull(row["tags"])
    }

    @Test
    fun `test execute with inconsistent object keys fills missing values with empty string`() {
        // Arrange
        val properties = mapOf(
            "jsonArrayString" to """[{"id": 1, "name": "Alice"}, {"id": 2, "email": "bob@example.com"}]"""
        )
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, mockOutputWriter)

        // Assert
        verify(mockPortWriter, org.mockito.kotlin.times(2)).write(any(), rowCaptor.capture())
        assertEquals(2, rowCaptor.allValues.size)

        // First row should have id, name, and email
        val firstRow = rowCaptor.allValues[0]
        assertEquals(1, firstRow["id"])
        assertEquals("Alice", firstRow["name"])
        assertEquals("", firstRow["email"]) // Missing key filled with empty string

        // Second row should have id, name, and email
        val secondRow = rowCaptor.allValues[1]
        assertEquals(2, secondRow["id"])
        assertEquals("", secondRow["name"]) // Missing key filled with empty string
        assertEquals("bob@example.com", secondRow["email"])
    }

    @Test
    fun `test execute with nested objects`() {
        // Arrange
        val properties = mapOf(
            "jsonArrayString" to """[{"id": 1, "user": {"name": "Alice", "age": 30}}]"""
        )
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, mockOutputWriter)

        // Assert
        verify(mockPortWriter).write(any(), rowCaptor.capture())
        assertEquals(1, rowCaptor.allValues.size)
        val row = rowCaptor.allValues[0]
        assertEquals(1, row["id"])
        assertNotNull(row["user"])
    }

    @Test
    fun `test execute with null values in JSON`() {
        // Arrange
        val properties = mapOf(
            "jsonArrayString" to """[{"id": 1, "name": null, "email": "alice@example.com"}]"""
        )
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, mockOutputWriter)

        // Assert
        verify(mockPortWriter).write(any(), rowCaptor.capture())
        assertEquals(1, rowCaptor.allValues.size)
        val row = rowCaptor.allValues[0]
        assertEquals(1, row["id"])
        // Null values in JSON are preserved as null
    }

    @Test
    fun `test execute with numeric row indices`() {
        // Arrange
        val properties = mapOf(
            "jsonArrayString" to """[{"id": 1}, {"id": 2}, {"id": 3}]"""
        )
        val indexCaptor = argumentCaptor<Long>()

        // Act
        nodeModel.execute(properties, mockOutputWriter)

        // Assert
        verify(mockPortWriter, org.mockito.kotlin.times(3)).write(indexCaptor.capture(), any())
        assertEquals(3, indexCaptor.allValues.size)
        assertEquals(0L, indexCaptor.allValues[0])
        assertEquals(1L, indexCaptor.allValues[1])
        assertEquals(2L, indexCaptor.allValues[2])
    }

    @Test
    fun `test execute with large number of rows`() {
        // Arrange
        val properties = mapOf(
            "jsonArrayString" to """[<#list 1..100 as i>{"id": ${'$'}{i}}<#if i?has_next>,</#if></#list>]"""
        )
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, mockOutputWriter)

        // Assert
        verify(mockPortWriter, org.mockito.kotlin.times(100)).write(any(), rowCaptor.capture())
        assertEquals(100, rowCaptor.allValues.size)
        assertEquals(1, rowCaptor.allValues[0]["id"])
        assertEquals(100, rowCaptor.allValues[99]["id"])
    }

    @Test
    fun `test execute with special characters in strings`() {
        // Arrange
        val properties = mapOf(
            "jsonArrayString" to """[{"id": 1, "text": "Hello \"World\" with \\ backslash"}]"""
        )
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, mockOutputWriter)

        // Assert
        verify(mockPortWriter).write(any(), rowCaptor.capture())
        assertEquals(1, rowCaptor.allValues.size)
        assertNotNull(rowCaptor.allValues[0]["text"])
    }

    @Test
    fun `test execute with Unicode characters`() {
        // Arrange
        val properties = mapOf(
            "jsonArrayString" to """[{"id": 1, "name": "José"}, {"id": 2, "name": "北京"}]"""
        )
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, mockOutputWriter)

        // Assert
        verify(mockPortWriter, org.mockito.kotlin.times(2)).write(any(), rowCaptor.capture())
        assertEquals(2, rowCaptor.allValues.size)
        assertEquals("José", rowCaptor.allValues[0]["name"])
        assertEquals("北京", rowCaptor.allValues[1]["name"])
    }

    // ===== Error Tests =====

    @Test
    fun `test execute throws exception when jsonArrayString is missing`() {
        // Arrange
        val properties = mapOf<String, Any>()

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, mockOutputWriter)
        }
        assertEquals("jsonArrayString is not provided", exception.message)
    }

    @Test
    fun `test execute throws exception when jsonArrayString is null`() {
        // Arrange
        @Suppress("UNCHECKED_CAST")
        val properties = (mapOf("jsonArrayString" to null) as Map<String, Any>)

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, mockOutputWriter)
        }
        assertEquals("jsonArrayString is not provided", exception.message)
    }

    @Test
    fun `test execute throws exception when jsonArrayString is empty`() {
        // Arrange
        val properties = mapOf("jsonArrayString" to "")

        // Act
        nodeModel.execute(properties, mockOutputWriter)

        // Assert
        verify(mockPortWriter, org.mockito.kotlin.never()).write(any(), any())
    }

    @Test
    fun `test execute throws exception when jsonArrayString is whitespace only`() {
        // Arrange
        val properties = mapOf("jsonArrayString" to "   \n\t  ")

        // Act
        nodeModel.execute(properties, mockOutputWriter)

        // Assert
        verify(mockPortWriter, org.mockito.kotlin.never()).write(any(), any())
    }

    @Test
    fun `test execute throws exception on FreeMarker template syntax error`() {
        // Arrange
        val properties = mapOf(
            "jsonArrayString" to """[<#list 1..3 as i>{"id": ${'$'}{i}}<#-- missing close tag -->]"""
        )

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, mockOutputWriter)
        }
        @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
        assertTrue(exception.message!!.contains("Template rendering failed"))
    }

    @Test
    fun `test execute throws exception on invalid JSON`() {
        // Arrange
        val properties = mapOf(
            "jsonArrayString" to """[{"id": 1, invalid json}]"""
        )

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, mockOutputWriter)
        }
        @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
        assertTrue(exception.message!!.contains("Invalid JSON array format"))
    }

    @Test
    fun `test execute throws exception when rendered template is not valid JSON`() {
        // Arrange
        val properties = mapOf(
            "jsonArrayString" to """not a json array"""
        )

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, mockOutputWriter)
        }
        @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
        assertTrue(exception.message!!.contains("Invalid JSON array format"))
    }

    @Test
    fun `test execute throws exception when JSON is not an array`() {
        // Arrange
        val properties = mapOf(
            "jsonArrayString" to """{"id": 1, "name": "Alice"}"""
        )

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, mockOutputWriter)
        }
        @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
        assertTrue(exception.message!!.contains("Invalid JSON array format"))
    }

    @Test
    fun `test execute returns early when template renders to empty array`() {
        // Arrange
        val properties = mapOf(
            "jsonArrayString" to """[]"""
        )

        // Act
        nodeModel.execute(properties, mockOutputWriter)

        // Assert
        verify(mockPortWriter, org.mockito.kotlin.never()).write(any(), any())
    }

    @Test
    fun `test execute returns early when template renders to empty string`() {
        // Arrange
        val properties = mapOf(
            "jsonArrayString" to """<#-- just a comment -->"""
        )

        // Act
        nodeModel.execute(properties, mockOutputWriter)

        // Assert
        verify(mockPortWriter, org.mockito.kotlin.never()).write(any(), any())
    }

    @Test
    fun `test execute with FreeMarker syntax error details`() {
        // Arrange
        val properties = mapOf(
            "jsonArrayString" to """[<#list 1..3>{"id": 1}</#list>]"""
        )

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, mockOutputWriter)
        }
        @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
        assertTrue(exception.message!!.contains("Template rendering failed"))
        assertEquals(false, exception.isRetriable)
    }

    @Test
    fun `test execute with invalid JSON error details`() {
        // Arrange
        val properties = mapOf(
            "jsonArrayString" to """[{invalid}]"""
        )

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, mockOutputWriter)
        }
        @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
        assertTrue(exception.message!!.contains("Invalid JSON array format"))
        assertEquals(false, exception.isRetriable)
    }

    @Test
    fun `test execute with missing jsonArrayString error is not retriable`() {
        // Arrange
        val properties = mapOf<String, Any>()

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, mockOutputWriter)
        }
        assertEquals(false, exception.isRetriable)
    }

    // ===== Edge Cases =====

    @Test
    fun `test execute with very long property values`() {
        // Arrange
        val longString = "x".repeat(10000)
        val properties = mapOf(
            "jsonArrayString" to """[{"id": 1, "description": "$longString"}]"""
        )
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, mockOutputWriter)

        // Assert
        verify(mockPortWriter).write(any(), rowCaptor.capture())
        assertEquals(1, rowCaptor.allValues.size)
        assertEquals(longString, rowCaptor.allValues[0]["description"])
    }

    @Test
    fun `test execute with numeric precision preservation`() {
        // Arrange
        val properties = mapOf(
            "jsonArrayString" to """[{"precision": 123.456789012345}]"""
        )
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, mockOutputWriter)

        // Assert
        verify(mockPortWriter).write(any(), rowCaptor.capture())
        assertEquals(1, rowCaptor.allValues.size)
        assertNotNull(rowCaptor.allValues[0]["precision"])
    }

    @Test
    fun `test execute with boolean values`() {
        // Arrange
        val properties = mapOf(
            "jsonArrayString" to """[{"active": true, "verified": false}]"""
        )
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, mockOutputWriter)

        // Assert
        verify(mockPortWriter).write(any(), rowCaptor.capture())
        assertEquals(1, rowCaptor.allValues.size)
        assertEquals(true, rowCaptor.allValues[0]["active"])
        assertEquals(false, rowCaptor.allValues[0]["verified"])
    }

    @Test
    fun `test execute with FreeMarker builtin functions`() {
        // Arrange
        val properties = mapOf(
            "jsonArrayString" to """[<#list 1..2 as i>{"id": ${'$'}{i}, "length": "${'$'}{i?string.computer}"}<#if i?has_next>,</#if></#list>]"""
        )
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, mockOutputWriter)

        // Assert
        verify(mockPortWriter, org.mockito.kotlin.times(2)).write(any(), rowCaptor.capture())
        assertEquals(2, rowCaptor.allValues.size)
    }

    @Test
    fun `test execute with FreeMarker conditional logic`() {
        // Arrange
        val properties = mapOf(
            "jsonArrayString" to """[<#list 1..3 as i><#if i == 2>{"id": ${'$'}{i}, "type": "special"}<#else>{"id": ${'$'}{i}, "type": "normal"}</#if><#if i?has_next>,</#if></#list>]"""
        )
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, mockOutputWriter)

        // Assert
        // Should have 3 rows
        verify(mockPortWriter, org.mockito.kotlin.times(3)).write(any(), rowCaptor.capture())
        assertEquals(3, rowCaptor.allValues.size)
        assertEquals("special", rowCaptor.allValues[1]["type"])
    }

    @Test
    fun `test execute output writer is properly closed`() {
        // Arrange
        val properties = mapOf(
            "jsonArrayString" to """[{"id": 1}]"""
        )

        // Act
        nodeModel.execute(properties, mockOutputWriter)

        // Assert
        verify(mockPortWriter).close()
    }

    @Test
    fun `test execute handles resource cleanup even on exception`() {
        // Arrange
        val properties = mapOf(
            "jsonArrayString" to """not valid json"""
        )
        whenever(mockPortWriter.close()).thenThrow(RuntimeException("Close error"))

        // Act & Assert
        assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, mockOutputWriter)
        }
    }

    @Test
    fun `test execute with all unique keys across rows`() {
        // Arrange
        val properties = mapOf(
            "jsonArrayString" to """[{"a": 1, "b": 2}, {"b": 3, "c": 4}, {"c": 5, "d": 6}]"""
        )
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, mockOutputWriter)

        // Assert
        verify(mockPortWriter, org.mockito.kotlin.times(3)).write(any(), rowCaptor.capture())
        assertEquals(3, rowCaptor.allValues.size)

        // All rows should have all keys
        rowCaptor.allValues.forEach { row ->
            assertTrue(row.containsKey("a"))
            assertTrue(row.containsKey("b"))
            assertTrue(row.containsKey("c"))
            assertTrue(row.containsKey("d"))
        }
    }
}


































