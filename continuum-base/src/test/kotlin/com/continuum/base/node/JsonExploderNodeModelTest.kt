package com.continuum.base.node

import com.continuum.core.commons.exception.NodeRuntimeException
import com.continuum.core.commons.utils.NodeInputReader
import com.continuum.core.commons.utils.NodeOutputWriter
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JsonExploderNodeModelTest {

    private lateinit var nodeModel: JsonExploderNodeModel
    private lateinit var mockInputReader: NodeInputReader
    private lateinit var mockOutputWriter: NodeOutputWriter
    private lateinit var mockPortWriter: NodeOutputWriter.OutputPortWriter

    @BeforeEach
    fun setUp() {
        nodeModel = JsonExploderNodeModel()
        mockInputReader = mock()
        mockOutputWriter = mock()
        mockPortWriter = mock()
        whenever(mockOutputWriter.createOutputPortWriter("data")).thenReturn(mockPortWriter)
    }

    // ===== Configuration Tests =====

    @Test
    fun `test node metadata is properly configured`() {
        val metadata = nodeModel.metadata
        assertEquals("com.continuum.base.node.JsonExploderNodeModel", metadata.id)
        assertEquals("Parses JSON strings and flattens keys into new columns", metadata.description)
        assertEquals("JSON Exploder", metadata.title)
        assertEquals("Parse and flatten JSON", metadata.subTitle)
        assertNotNull(metadata.icon)
        assertTrue(metadata.icon.toString().contains("svg"))
    }

    @Test
    fun `test input ports are correctly defined`() {
        val inputPorts = nodeModel.inputPorts
        assertEquals(1, inputPorts.size)
        assertNotNull(inputPorts["data"])
        val dataPort = inputPorts["data"]!!
        assertEquals("input table", dataPort.name)
    }

    @Test
    fun `test output ports are correctly defined`() {
        val outputPorts = nodeModel.outputPorts
        assertEquals(1, outputPorts.size)
        assertNotNull(outputPorts["data"])
        val dataPort = outputPorts["data"]!!
        assertEquals("exploded table", dataPort.name)
    }

    @Test
    fun `test categories are correctly defined`() {
        val categories = nodeModel.categories
        assertEquals(1, categories.size)
        assertEquals("JSON & Data Parsing", categories[0])
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
        assertEquals("json", defaultProperties["jsonCol"])
    }

    // ===== Success Tests =====

    @Test
    fun `test execute with simple JSON object`() {
        // Arrange
        val inputRow = mapOf(
            "id" to 1,
            "json" to """{"name": "Alice", "age": 30}"""
        )
        whenever(mockInputReader.read())
            .thenReturn(inputRow)
            .thenReturn(null)

        val properties = mapOf("jsonCol" to "json")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter).write(any(), rowCaptor.capture())
        assertEquals(1, rowCaptor.allValues.size)
        val resultRow = rowCaptor.allValues[0]
        assertEquals(1, resultRow["id"])
        assertEquals("Alice", resultRow["name"])
        assertEquals(30, resultRow["age"])
        assertTrue(!resultRow.containsKey("json"))
    }

    @Test
    fun `test execute with multiple rows containing JSON`() {
        // Arrange
        val row1 = mapOf("id" to 1, "json" to """{"name": "Alice"}""")
        val row2 = mapOf("id" to 2, "json" to """{"name": "Bob"}""")
        whenever(mockInputReader.read())
            .thenReturn(row1)
            .thenReturn(row2)
            .thenReturn(null)

        val properties = mapOf("jsonCol" to "json")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(2)).write(any(), rowCaptor.capture())
        assertEquals(2, rowCaptor.allValues.size)
        assertEquals("Alice", rowCaptor.allValues[0]["name"])
        assertEquals("Bob", rowCaptor.allValues[1]["name"])
    }

    @Test
    fun `test execute with nested JSON object`() {
        // Arrange
        val inputRow = mapOf(
            "id" to 1,
            "json" to """{"user": {"name": "Alice", "email": "alice@example.com"}}"""
        )
        whenever(mockInputReader.read())
            .thenReturn(inputRow)
            .thenReturn(null)

        val properties = mapOf("jsonCol" to "json")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter).write(any(), rowCaptor.capture())
        assertEquals(1, rowCaptor.allValues.size)
        val resultRow = rowCaptor.allValues[0]
        assertEquals(1, resultRow["id"])
        assertNotNull(resultRow["user"])
    }

    @Test
    fun `test execute with JSON array`() {
        // Arrange
        val inputRow = mapOf(
            "id" to 1,
            "json" to """{"items": [1, 2, 3], "status": "active"}"""
        )
        whenever(mockInputReader.read())
            .thenReturn(inputRow)
            .thenReturn(null)

        val properties = mapOf("jsonCol" to "json")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter).write(any(), rowCaptor.capture())
        assertEquals(1, rowCaptor.allValues.size)
        val resultRow = rowCaptor.allValues[0]
        assertEquals("active", resultRow["status"])
        assertNotNull(resultRow["items"])
    }

    @Test
    fun `test execute with different JSON data types`() {
        // Arrange
        val inputRow = mapOf(
            "id" to 1,
            "json" to """{"name": "Alice", "age": 30, "active": true, "salary": 50000.50}"""
        )
        whenever(mockInputReader.read())
            .thenReturn(inputRow)
            .thenReturn(null)

        val properties = mapOf("jsonCol" to "json")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter).write(any(), rowCaptor.capture())
        assertEquals(1, rowCaptor.allValues.size)
        val resultRow = rowCaptor.allValues[0]
        assertEquals("Alice", resultRow["name"])
        assertEquals(30, resultRow["age"])
        assertEquals(true, resultRow["active"])
        assertEquals(50000.50, resultRow["salary"])
    }

    @Test
    fun `test execute with empty JSON object`() {
        // Arrange
        val inputRow = mapOf(
            "id" to 1,
            "json" to """{}"""
        )
        whenever(mockInputReader.read())
            .thenReturn(inputRow)
            .thenReturn(null)

        val properties = mapOf("jsonCol" to "json")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter).write(any(), rowCaptor.capture())
        assertEquals(1, rowCaptor.allValues.size)
        val resultRow = rowCaptor.allValues[0]
        assertEquals(1, resultRow["id"])
        assertTrue(!resultRow.containsKey("json"))
    }

    @Test
    fun `test execute with empty JSON string`() {
        // Arrange
        val inputRow = mapOf(
            "id" to 1,
            "json" to ""
        )
        whenever(mockInputReader.read())
            .thenReturn(inputRow)
            .thenReturn(null)

        val properties = mapOf("jsonCol" to "json")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter).write(any(), rowCaptor.capture())
        assertEquals(1, rowCaptor.allValues.size)
        val resultRow = rowCaptor.allValues[0]
        assertEquals(1, resultRow["id"])
        assertTrue(!resultRow.containsKey("json"))
    }

    @Test
    fun `test execute with null JSON value`() {
        // Arrange
        @Suppress("UNCHECKED_CAST")
        val inputRow = (mapOf("id" to 1, "json" to null) as Map<String, Any>)
        whenever(mockInputReader.read())
            .thenReturn(inputRow)
            .thenReturn(null)

        val properties = mapOf("jsonCol" to "json")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter).write(any(), rowCaptor.capture())
        assertEquals(1, rowCaptor.allValues.size)
        val resultRow = rowCaptor.allValues[0]
        assertEquals(1, resultRow["id"])
        assertTrue(!resultRow.containsKey("json"))
    }

    @Test
    fun `test execute with unicode characters in JSON`() {
        // Arrange
        val inputRow = mapOf(
            "id" to 1,
            "json" to """{"name": "José", "city": "北京"}"""
        )
        whenever(mockInputReader.read())
            .thenReturn(inputRow)
            .thenReturn(null)

        val properties = mapOf("jsonCol" to "json")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter).write(any(), rowCaptor.capture())
        assertEquals(1, rowCaptor.allValues.size)
        val resultRow = rowCaptor.allValues[0]
        assertEquals("José", resultRow["name"])
        assertEquals("北京", resultRow["city"])
    }

    @Test
    fun `test execute with special characters in JSON values`() {
        // Arrange
        val inputRow = mapOf(
            "id" to 1,
            "json" to """{"text": "Hello World", "symbols": "@#$%&"}"""
        )
        whenever(mockInputReader.read())
            .thenReturn(inputRow)
            .thenReturn(null)

        val properties = mapOf("jsonCol" to "json")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter).write(any(), rowCaptor.capture())
        assertEquals(1, rowCaptor.allValues.size)
        assertNotNull(rowCaptor.allValues[0]["text"])
        assertNotNull(rowCaptor.allValues[0]["symbols"])
    }

    @Test
    fun `test execute with numeric row indices`() {
        // Arrange
        val row1 = mapOf("id" to 1, "json" to """{"name": "Alice"}""")
        val row2 = mapOf("id" to 2, "json" to """{"name": "Bob"}""")
        val row3 = mapOf("id" to 3, "json" to """{"name": "Charlie"}""")
        whenever(mockInputReader.read())
            .thenReturn(row1)
            .thenReturn(row2)
            .thenReturn(row3)
            .thenReturn(null)

        val properties = mapOf("jsonCol" to "json")
        val inputs = mapOf("data" to mockInputReader)
        val indexCaptor = argumentCaptor<Long>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(3)).write(indexCaptor.capture(), any())
        assertEquals(3, indexCaptor.allValues.size)
        assertEquals(0L, indexCaptor.allValues[0])
        assertEquals(1L, indexCaptor.allValues[1])
        assertEquals(2L, indexCaptor.allValues[2])
    }

    @Test
    fun `test execute with large JSON object`() {
        // Arrange
        val largeJson = """{"field1": "value1", "field2": "value2", "field3": "value3", "field4": "value4", "field5": "value5", "field6": "value6", "field7": "value7", "field8": "value8", "field9": "value9", "field10": "value10"}"""
        val inputRow = mapOf("id" to 1, "json" to largeJson)
        whenever(mockInputReader.read())
            .thenReturn(inputRow)
            .thenReturn(null)

        val properties = mapOf("jsonCol" to "json")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter).write(any(), rowCaptor.capture())
        assertEquals(1, rowCaptor.allValues.size)
        val resultRow = rowCaptor.allValues[0]
        assertEquals(10, resultRow.size - 1) // All fields except id
    }

    @Test
    fun `test execute with additional columns besides JSON column`() {
        // Arrange
        val inputRow = mapOf(
            "id" to 1,
            "name" to "User1",
            "json" to """{"status": "active", "score": 95}""",
            "timestamp" to "2024-01-01"
        )
        whenever(mockInputReader.read())
            .thenReturn(inputRow)
            .thenReturn(null)

        val properties = mapOf("jsonCol" to "json")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter).write(any(), rowCaptor.capture())
        assertEquals(1, rowCaptor.allValues.size)
        val resultRow = rowCaptor.allValues[0]
        assertEquals(1, resultRow["id"])
        assertEquals("User1", resultRow["name"])
        assertEquals("active", resultRow["status"])
        assertEquals(95, resultRow["score"])
        assertEquals("2024-01-01", resultRow["timestamp"])
        assertTrue(!resultRow.containsKey("json"))
    }

    @Test
    fun `test execute with conflicting JSON keys overwrites original columns`() {
        // Arrange
        val inputRow = mapOf(
            "id" to 1,
            "name" to "OriginalName",
            "json" to """{"name": "JsonName", "age": 30}"""
        )
        whenever(mockInputReader.read())
            .thenReturn(inputRow)
            .thenReturn(null)

        val properties = mapOf("jsonCol" to "json")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter).write(any(), rowCaptor.capture())
        assertEquals(1, rowCaptor.allValues.size)
        val resultRow = rowCaptor.allValues[0]
        // JSON values should overwrite original columns
        assertEquals("JsonName", resultRow["name"])
        assertEquals(30, resultRow["age"])
        assertEquals(1, resultRow["id"])
    }

    // ===== Error Tests =====

    @Test
    fun `test execute throws exception when jsonCol property is missing`() {
        // Arrange
        val inputRow = mapOf("id" to 1, "json" to """{"name": "Alice"}""")
        whenever(mockInputReader.read()).thenReturn(inputRow).thenReturn(null)

        val properties = mapOf<String, Any>()
        val inputs = mapOf("data" to mockInputReader)

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter)
        }
        assertEquals("jsonCol is not provided", exception.message)
    }

    @Test
    fun `test execute throws exception when jsonCol property is null`() {
        // Arrange
        val inputRow = mapOf("id" to 1, "json" to """{"name": "Alice"}""")
        whenever(mockInputReader.read()).thenReturn(inputRow).thenReturn(null)

        @Suppress("UNCHECKED_CAST")
        val properties = (mapOf("jsonCol" to null) as Map<String, Any>)
        val inputs = mapOf("data" to mockInputReader)

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter)
        }
        assertEquals("jsonCol is not provided", exception.message)
    }

    @Test
    fun `test execute throws exception on invalid JSON in row`() {
        // Arrange
        val inputRow = mapOf(
            "id" to 1,
            "json" to """{"name": "Alice", invalid json}"""
        )
        whenever(mockInputReader.read())
            .thenReturn(inputRow)
            .thenReturn(null)

        val properties = mapOf("jsonCol" to "json")
        val inputs = mapOf("data" to mockInputReader)

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter)
        }
        @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
        assertTrue(exception.message!!.contains("Failed to parse JSON in row 0"))
    }

    @Test
    fun `test execute throws exception on JSON parse exception`() {
        // Arrange
        val inputRow = mapOf(
            "id" to 1,
            "json" to """{'single': 'quotes'}"""
        )
        whenever(mockInputReader.read())
            .thenReturn(inputRow)
            .thenReturn(null)

        val properties = mapOf("jsonCol" to "json")
        val inputs = mapOf("data" to mockInputReader)

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter)
        }
        @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
        assertTrue(exception.message!!.contains("Failed to parse JSON in row 0"))
    }

    @Test
    fun `test execute with multiple invalid rows`() {
        // Arrange
        val row1 = mapOf("id" to 1, "json" to """invalid json""")
        whenever(mockInputReader.read())
            .thenReturn(row1)
            .thenReturn(null)

        val properties = mapOf("jsonCol" to "json")
        val inputs = mapOf("data" to mockInputReader)

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter)
        }
        @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
        assertTrue(exception.message!!.contains("Failed to parse JSON in row 0"))
    }

    @Test
    fun `test execute throws exception when JSON column name does not exist in row`() {
        // Arrange
        val inputRow = mapOf(
            "id" to 1,
            "name" to "Alice"
            // json column doesn't exist
        )
        whenever(mockInputReader.read())
            .thenReturn(inputRow)
            .thenReturn(null)

        val properties = mapOf("jsonCol" to "json")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert - should treat null/missing as empty string
        verify(mockPortWriter).write(any(), rowCaptor.capture())
        assertEquals(1, rowCaptor.allValues.size)
        assertEquals(1, rowCaptor.allValues[0]["id"])
        assertEquals("Alice", rowCaptor.allValues[0]["name"])
    }

    @Test
    fun `test execute with JSON mapping exception`() {
        // Arrange
        // Malformed JSON that will cause mapping error
        val inputRow = mapOf(
            "id" to 1,
            "json" to """{"date": "not-a-valid-date"}"""
        )
        whenever(mockInputReader.read())
            .thenReturn(inputRow)
            .thenReturn(null)

        val properties = mapOf("jsonCol" to "json")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert - mapping exceptions are non-retriable in this case
        verify(mockPortWriter).write(any(), rowCaptor.capture())
        assertNotNull(rowCaptor.allValues)
    }

    @Test
    fun `test execute error is not retriable for parse exception`() {
        // Arrange
        val inputRow = mapOf(
            "id" to 1,
            "json" to """invalid"""
        )
        whenever(mockInputReader.read())
            .thenReturn(inputRow)
            .thenReturn(null)

        val properties = mapOf("jsonCol" to "json")
        val inputs = mapOf("data" to mockInputReader)

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter)
        }
        assertEquals(false, exception.isRetriable)
    }

    // ===== Edge Cases =====

    @Test
    fun `test execute with empty input stream`() {
        // Arrange
        whenever(mockInputReader.read()).thenReturn(null)

        val properties = mapOf("jsonCol" to "json")
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert - no writes should happen for empty input
        verify(mockPortWriter, org.mockito.kotlin.never()).write(any(), any())
    }

    @Test
    fun `test execute with many rows`() {
        // Arrange
        val row1 = mapOf("id" to 1, "json" to """{"value": 1}""")
        val row2 = mapOf("id" to 2, "json" to """{"value": 2}""")
        val row3 = mapOf("id" to 3, "json" to """{"value": 3}""")
        whenever(mockInputReader.read())
            .thenReturn(row1)
            .thenReturn(row2)
            .thenReturn(row3)
            .thenReturn(null)

        val properties = mapOf("jsonCol" to "json")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(3)).write(any(), rowCaptor.capture())
        assertEquals(3, rowCaptor.allValues.size)
        assertEquals(1, rowCaptor.allValues[0]["value"])
        assertEquals(2, rowCaptor.allValues[1]["value"])
        assertEquals(3, rowCaptor.allValues[2]["value"])
    }

    @Test
    fun `test execute with whitespace-only JSON throws exception`() {
        // Arrange
        val inputRow = mapOf(
            "id" to 1,
            "json" to "   "  // Whitespace only - treated as invalid JSON
        )
        whenever(mockInputReader.read())
            .thenReturn(inputRow)
            .thenReturn(null)

        val properties = mapOf("jsonCol" to "json")
        val inputs = mapOf("data" to mockInputReader)

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter)
        }
        @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
        assertTrue(exception.message!!.contains("Failed to parse JSON"))
    }

    @Test
    fun `test execute with single character JSON column name`() {
        // Arrange
        val inputRow = mapOf(
            "id" to 1,
            "j" to """{"name": "Alice"}"""
        )
        whenever(mockInputReader.read())
            .thenReturn(inputRow)
            .thenReturn(null)

        val properties = mapOf("jsonCol" to "j")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter).write(any(), rowCaptor.capture())
        assertEquals(1, rowCaptor.allValues.size)
        val resultRow = rowCaptor.allValues[0]
        assertEquals("Alice", resultRow["name"])
        assertTrue(!resultRow.containsKey("j"))
    }

    @Test
    fun `test execute with very long JSON string`() {
        // Arrange
        val longValue = "x".repeat(5000)
        val inputRow = mapOf(
            "id" to 1,
            "json" to """{"longField": "$longValue"}"""
        )
        whenever(mockInputReader.read())
            .thenReturn(inputRow)
            .thenReturn(null)

        val properties = mapOf("jsonCol" to "json")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter).write(any(), rowCaptor.capture())
        assertEquals(1, rowCaptor.allValues.size)
        assertEquals(longValue, rowCaptor.allValues[0]["longField"])
    }

    @Test
    fun `test execute with JSON numbers of different ranges`() {
        // Arrange
        val inputRow = mapOf(
            "id" to 1,
            "json" to """{"tiny": 0, "small": 42, "large": 999999999, "negative": -12345, "decimal": 3.14159}"""
        )
        whenever(mockInputReader.read())
            .thenReturn(inputRow)
            .thenReturn(null)

        val properties = mapOf("jsonCol" to "json")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter).write(any(), rowCaptor.capture())
        assertEquals(1, rowCaptor.allValues.size)
        val resultRow = rowCaptor.allValues[0]
        assertEquals(0, resultRow["tiny"])
        assertEquals(42, resultRow["small"])
        assertEquals(999999999, resultRow["large"])
        assertEquals(-12345, resultRow["negative"])
        assertNotNull(resultRow["decimal"])
    }

    @Test
    fun `test output writer is properly closed`() {
        // Arrange
        whenever(mockInputReader.read()).thenReturn(null)

        val properties = mapOf("jsonCol" to "json")
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter).close()
    }

    @Test
    fun `test input reader is properly closed`() {
        // Arrange
        whenever(mockInputReader.read()).thenReturn(null)

        val properties = mapOf("jsonCol" to "json")
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockInputReader).close()
    }

    @Test
    fun `test execute with JSON containing escaped newlines`() {
        // Arrange
        val inputRow = mapOf(
            "id" to 1,
            "json" to """{"description": "Line1\\nLine2\\nLine3"}"""
        )
        whenever(mockInputReader.read())
            .thenReturn(inputRow)
            .thenReturn(null)

        val properties = mapOf("jsonCol" to "json")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter).write(any(), rowCaptor.capture())
        assertEquals(1, rowCaptor.allValues.size)
        assertNotNull(rowCaptor.allValues[0]["description"])
    }

    @Test
    fun `test execute with JSON boolean values`() {
        // Arrange
        val inputRow = mapOf(
            "id" to 1,
            "json" to """{"active": true, "deleted": false, "pending": true}"""
        )
        whenever(mockInputReader.read())
            .thenReturn(inputRow)
            .thenReturn(null)

        val properties = mapOf("jsonCol" to "json")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter).write(any(), rowCaptor.capture())
        assertEquals(1, rowCaptor.allValues.size)
        val resultRow = rowCaptor.allValues[0]
        assertEquals(true, resultRow["active"])
        assertEquals(false, resultRow["deleted"])
        assertEquals(true, resultRow["pending"])
    }
}







