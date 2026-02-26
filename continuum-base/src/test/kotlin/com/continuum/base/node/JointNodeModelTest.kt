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

class JointNodeModelTest {

    private lateinit var nodeModel: JointNodeModel
    private lateinit var mockInputReader1: NodeInputReader
    private lateinit var mockInputReader2: NodeInputReader
    private lateinit var mockOutputWriter: NodeOutputWriter
    private lateinit var mockPortWriter: NodeOutputWriter.OutputPortWriter

    @BeforeEach
    fun setUp() {
        nodeModel = JointNodeModel()
        mockInputReader1 = mock()
        mockInputReader2 = mock()
        mockOutputWriter = mock()
        mockPortWriter = mock()
        whenever(mockOutputWriter.createOutputPortWriter("output-1")).thenReturn(mockPortWriter)
    }

    // ===== Configuration Tests =====

    @Test
    fun `test node metadata is properly configured`() {
        val metadata = nodeModel.metadata
        assertEquals("com.continuum.base.node.JointNodeModel", metadata.id)
        assertEquals("Joint the input strings into one", metadata.description)
        assertEquals("Joint Node", metadata.title)
        assertEquals("Joint the input strings", metadata.subTitle)
        assertNotNull(metadata.icon)
        assertTrue(metadata.icon.toString().contains("svg"))
    }

    @Test
    fun `test input ports are correctly defined`() {
        val inputPorts = nodeModel.inputPorts
        assertEquals(2, inputPorts.size)
        assertNotNull(inputPorts["input-1"])
        assertNotNull(inputPorts["input-2"])
        assertEquals("first input string", inputPorts["input-1"]!!.name)
        assertEquals("second input string", inputPorts["input-2"]!!.name)
    }

    @Test
    fun `test output ports are correctly defined`() {
        val outputPorts = nodeModel.outputPorts
        assertEquals(1, outputPorts.size)
        assertNotNull(outputPorts["output-1"])
        val outputPort = outputPorts["output-1"]!!
        assertEquals("part 1", outputPort.name)
    }

    @Test
    fun `test categories are correctly defined`() {
        val categories = nodeModel.categories
        assertEquals(1, categories.size)
        assertEquals("Processing", categories[0])
    }

    @Test
    fun `test properties schema is valid`() {
        val schema = nodeModel.propertiesSchema
        assertNotNull(schema)
        assertEquals("object", schema["type"])
        assertTrue(schema.containsKey("properties"))
        assertTrue(schema.containsKey("required"))

        val properties = schema["properties"] as Map<*, *>
        assertTrue(properties.containsKey("inputs"))
        assertTrue(properties.containsKey("outputsColumnName"))

        val required = schema["required"] as List<*>
        assertTrue(required.contains("outputsColumnName"))
        assertTrue(required.contains("inputs"))
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

        val inputs = defaultProperties["inputs"] as List<*>
        assertEquals(2, inputs.size)

        assertEquals("message", defaultProperties["outputsColumnName"])
    }

    // ===== Success Tests =====

    @Test
    fun `test basic two-column join`() {
        // Arrange
        val rows1 = listOf(mapOf("msg-1" to "Hello"))
        val rows2 = listOf(mapOf("msg-2" to "World"))
        mockSequentialReads(mockInputReader1, rows1)
        mockSequentialReads(mockInputReader2, rows2)

        val properties = mapOf(
            "inputs" to listOf(
                mapOf("columnName" to "msg-1"),
                mapOf("columnName" to "msg-2")
            ),
            "outputsColumnName" to "message"
        )
        val inputs = mapOf("input-1" to mockInputReader1, "input-2" to mockInputReader2)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val writtenRows = rowCaptor.allValues

        assertEquals(1, writtenRows.size)
        assertEquals("Hello World", writtenRows[0]["message"])
    }

    @Test
    fun `test with multiple rows`() {
        // Arrange
        val rows1 = listOf(
            mapOf("msg-1" to "Hello"),
            mapOf("msg-1" to "Good"),
            mapOf("msg-1" to "See")
        )
        val rows2 = listOf(
            mapOf("msg-2" to "World"),
            mapOf("msg-2" to "Morning"),
            mapOf("msg-2" to "You")
        )
        mockSequentialReads(mockInputReader1, rows1)
        mockSequentialReads(mockInputReader2, rows2)

        val properties = mapOf(
            "inputs" to listOf(
                mapOf("columnName" to "msg-1"),
                mapOf("columnName" to "msg-2")
            ),
            "outputsColumnName" to "message"
        )
        val inputs = mapOf("input-1" to mockInputReader1, "input-2" to mockInputReader2)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(3)).write(any(), rowCaptor.capture())
        val writtenRows = rowCaptor.allValues

        assertEquals(3, writtenRows.size)
        assertEquals("Hello World", writtenRows[0]["message"])
        assertEquals("Good Morning", writtenRows[1]["message"])
        assertEquals("See You", writtenRows[2]["message"])
    }

    @Test
    fun `test with custom column names`() {
        // Arrange
        val rows1 = listOf(mapOf("firstName" to "John"))
        val rows2 = listOf(mapOf("lastName" to "Doe"))
        mockSequentialReads(mockInputReader1, rows1)
        mockSequentialReads(mockInputReader2, rows2)

        val properties = mapOf(
            "inputs" to listOf(
                mapOf("columnName" to "firstName"),
                mapOf("columnName" to "lastName")
            ),
            "outputsColumnName" to "fullName"
        )
        val inputs = mapOf("input-1" to mockInputReader1, "input-2" to mockInputReader2)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val writtenRows = rowCaptor.allValues

        assertEquals(1, writtenRows.size)
        assertEquals("John Doe", writtenRows[0]["fullName"])
    }

    @Test
    fun `test with default output column name`() {
        // Arrange
        val rows1 = listOf(mapOf("msg-1" to "Hello"))
        val rows2 = listOf(mapOf("msg-2" to "World"))
        mockSequentialReads(mockInputReader1, rows1)
        mockSequentialReads(mockInputReader2, rows2)

        val properties = mapOf(
            "inputs" to listOf(
                mapOf("columnName" to "msg-1"),
                mapOf("columnName" to "msg-2")
            )
            // No outputsColumnName provided, should default to "message"
        )
        val inputs = mapOf("input-1" to mockInputReader1, "input-2" to mockInputReader2)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val writtenRows = rowCaptor.allValues

        assertEquals(1, writtenRows.size)
        assertEquals("Hello World", writtenRows[0]["message"])
    }

    @Test
    fun `test row indices are sequential`() {
        // Arrange
        val rows1 = listOf(
            mapOf("msg-1" to "A"),
            mapOf("msg-1" to "B")
        )
        val rows2 = listOf(
            mapOf("msg-2" to "1"),
            mapOf("msg-2" to "2")
        )
        mockSequentialReads(mockInputReader1, rows1)
        mockSequentialReads(mockInputReader2, rows2)

        val properties = mapOf(
            "inputs" to listOf(
                mapOf("columnName" to "msg-1"),
                mapOf("columnName" to "msg-2")
            ),
            "outputsColumnName" to "message"
        )
        val inputs = mapOf("input-1" to mockInputReader1, "input-2" to mockInputReader2)
        val indexCaptor = argumentCaptor<Long>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(2)).write(indexCaptor.capture(), any())
        assertEquals(2, indexCaptor.allValues.size)
        assertEquals(0L, indexCaptor.allValues[0])
        assertEquals(1L, indexCaptor.allValues[1])
    }

    // ===== Type Conversion Tests =====

    @Test
    fun `test type conversion - numbers to strings`() {
        // Arrange
        val rows1 = listOf(mapOf("value1" to 42))
        val rows2 = listOf(mapOf("value2" to 3.14))
        mockSequentialReads(mockInputReader1, rows1)
        mockSequentialReads(mockInputReader2, rows2)

        val properties = mapOf(
            "inputs" to listOf(
                mapOf("columnName" to "value1"),
                mapOf("columnName" to "value2")
            ),
            "outputsColumnName" to "result"
        )
        val inputs = mapOf("input-1" to mockInputReader1, "input-2" to mockInputReader2)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val writtenRows = rowCaptor.allValues

        assertEquals(1, writtenRows.size)
        assertEquals("42 3.14", writtenRows[0]["result"])
    }

    @Test
    fun `test type conversion - booleans to strings`() {
        // Arrange
        val rows1 = listOf(mapOf("flag1" to true))
        val rows2 = listOf(mapOf("flag2" to false))
        mockSequentialReads(mockInputReader1, rows1)
        mockSequentialReads(mockInputReader2, rows2)

        val properties = mapOf(
            "inputs" to listOf(
                mapOf("columnName" to "flag1"),
                mapOf("columnName" to "flag2")
            ),
            "outputsColumnName" to "flags"
        )
        val inputs = mapOf("input-1" to mockInputReader1, "input-2" to mockInputReader2)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val writtenRows = rowCaptor.allValues

        assertEquals(1, writtenRows.size)
        assertEquals("true false", writtenRows[0]["flags"])
    }

    @Test
    fun `test type conversion - mixed types`() {
        // Arrange
        val rows1 = listOf(mapOf("mixed1" to "text"))
        val rows2 = listOf(mapOf("mixed2" to 123))
        mockSequentialReads(mockInputReader1, rows1)
        mockSequentialReads(mockInputReader2, rows2)

        val properties = mapOf(
            "inputs" to listOf(
                mapOf("columnName" to "mixed1"),
                mapOf("columnName" to "mixed2")
            ),
            "outputsColumnName" to "result"
        )
        val inputs = mapOf("input-1" to mockInputReader1, "input-2" to mockInputReader2)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val writtenRows = rowCaptor.allValues

        assertEquals(1, writtenRows.size)
        assertEquals("text 123", writtenRows[0]["result"])
    }

    // ===== Null Value Tests =====

    @Test
    fun `test with null value in first column`() {
        // Arrange
        val rows1 = listOf(mapOf("msg-1" to null))
        val rows2 = listOf(mapOf("msg-2" to "World"))
        mockSequentialReads(mockInputReader1, rows1)
        mockSequentialReads(mockInputReader2, rows2)

        val properties = mapOf(
            "inputs" to listOf(
                mapOf("columnName" to "msg-1"),
                mapOf("columnName" to "msg-2")
            ),
            "outputsColumnName" to "message"
        )
        val inputs = mapOf("input-1" to mockInputReader1, "input-2" to mockInputReader2)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val writtenRows = rowCaptor.allValues

        assertEquals(1, writtenRows.size)
        assertEquals(" World", writtenRows[0]["message"])
    }

    @Test
    fun `test with null value in second column`() {
        // Arrange
        val rows1 = listOf(mapOf("msg-1" to "Hello"))
        val rows2 = listOf(mapOf("msg-2" to null))
        mockSequentialReads(mockInputReader1, rows1)
        mockSequentialReads(mockInputReader2, rows2)

        val properties = mapOf(
            "inputs" to listOf(
                mapOf("columnName" to "msg-1"),
                mapOf("columnName" to "msg-2")
            ),
            "outputsColumnName" to "message"
        )
        val inputs = mapOf("input-1" to mockInputReader1, "input-2" to mockInputReader2)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val writtenRows = rowCaptor.allValues

        assertEquals(1, writtenRows.size)
        assertEquals("Hello ", writtenRows[0]["message"])
    }

    @Test
    fun `test with null values in both columns`() {
        // Arrange
        val rows1 = listOf(mapOf("msg-1" to null))
        val rows2 = listOf(mapOf("msg-2" to null))
        mockSequentialReads(mockInputReader1, rows1)
        mockSequentialReads(mockInputReader2, rows2)

        val properties = mapOf(
            "inputs" to listOf(
                mapOf("columnName" to "msg-1"),
                mapOf("columnName" to "msg-2")
            ),
            "outputsColumnName" to "message"
        )
        val inputs = mapOf("input-1" to mockInputReader1, "input-2" to mockInputReader2)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val writtenRows = rowCaptor.allValues

        assertEquals(1, writtenRows.size)
        assertEquals(" ", writtenRows[0]["message"])
    }

    @Test
    fun `test with missing column in first input`() {
        // Arrange - row doesn't have msg-1 column
        val rows1 = listOf(mapOf("other" to "something"))
        val rows2 = listOf(mapOf("msg-2" to "World"))
        mockSequentialReads(mockInputReader1, rows1)
        mockSequentialReads(mockInputReader2, rows2)

        val properties = mapOf(
            "inputs" to listOf(
                mapOf("columnName" to "msg-1"),
                mapOf("columnName" to "msg-2")
            ),
            "outputsColumnName" to "message"
        )
        val inputs = mapOf("input-1" to mockInputReader1, "input-2" to mockInputReader2)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val writtenRows = rowCaptor.allValues

        assertEquals(1, writtenRows.size)
        assertEquals(" World", writtenRows[0]["message"])
    }

    @Test
    fun `test with missing column in second input`() {
        // Arrange - row doesn't have msg-2 column
        val rows1 = listOf(mapOf("msg-1" to "Hello"))
        val rows2 = listOf(mapOf("other" to "something"))
        mockSequentialReads(mockInputReader1, rows1)
        mockSequentialReads(mockInputReader2, rows2)

        val properties = mapOf(
            "inputs" to listOf(
                mapOf("columnName" to "msg-1"),
                mapOf("columnName" to "msg-2")
            ),
            "outputsColumnName" to "message"
        )
        val inputs = mapOf("input-1" to mockInputReader1, "input-2" to mockInputReader2)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val writtenRows = rowCaptor.allValues

        assertEquals(1, writtenRows.size)
        assertEquals("Hello ", writtenRows[0]["message"])
    }

    // ===== Different Input Lengths =====

    @Test
    fun `test when first input has fewer rows - stops at shorter stream`() {
        // Arrange
        val rows1 = listOf(
            mapOf("msg-1" to "Hello")
        )
        val rows2 = listOf(
            mapOf("msg-2" to "World"),
            mapOf("msg-2" to "Universe")
        )
        mockSequentialReads(mockInputReader1, rows1)
        mockSequentialReads(mockInputReader2, rows2)

        val properties = mapOf(
            "inputs" to listOf(
                mapOf("columnName" to "msg-1"),
                mapOf("columnName" to "msg-2")
            ),
            "outputsColumnName" to "message"
        )
        val inputs = mapOf("input-1" to mockInputReader1, "input-2" to mockInputReader2)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert - only 1 row written (stopped when input-1 exhausted)
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val writtenRows = rowCaptor.allValues

        assertEquals(1, writtenRows.size)
        assertEquals("Hello World", writtenRows[0]["message"])
    }

    @Test
    fun `test when second input has fewer rows - stops at shorter stream`() {
        // Arrange
        val rows1 = listOf(
            mapOf("msg-1" to "Hello"),
            mapOf("msg-1" to "Goodbye")
        )
        val rows2 = listOf(
            mapOf("msg-2" to "World")
        )
        mockSequentialReads(mockInputReader1, rows1)
        mockSequentialReads(mockInputReader2, rows2)

        val properties = mapOf(
            "inputs" to listOf(
                mapOf("columnName" to "msg-1"),
                mapOf("columnName" to "msg-2")
            ),
            "outputsColumnName" to "message"
        )
        val inputs = mapOf("input-1" to mockInputReader1, "input-2" to mockInputReader2)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert - only 1 row written (stopped when input-2 exhausted)
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val writtenRows = rowCaptor.allValues

        assertEquals(1, writtenRows.size)
        assertEquals("Hello World", writtenRows[0]["message"])
    }

    // ===== Empty Input Tests =====

    @Test
    fun `test with empty first input`() {
        // Arrange
        whenever(mockInputReader1.read()).thenReturn(null)
        whenever(mockInputReader2.read()).thenReturn(mapOf("msg-2" to "World"))

        val properties = mapOf(
            "inputs" to listOf(
                mapOf("columnName" to "msg-1"),
                mapOf("columnName" to "msg-2")
            ),
            "outputsColumnName" to "message"
        )
        val inputs = mapOf("input-1" to mockInputReader1, "input-2" to mockInputReader2)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert - no rows written
        verify(mockPortWriter, org.mockito.kotlin.never()).write(any(), any())
    }

    @Test
    fun `test with empty second input`() {
        // Arrange
        whenever(mockInputReader1.read()).thenReturn(mapOf("msg-1" to "Hello"))
        whenever(mockInputReader2.read()).thenReturn(null)

        val properties = mapOf(
            "inputs" to listOf(
                mapOf("columnName" to "msg-1"),
                mapOf("columnName" to "msg-2")
            ),
            "outputsColumnName" to "message"
        )
        val inputs = mapOf("input-1" to mockInputReader1, "input-2" to mockInputReader2)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert - no rows written
        verify(mockPortWriter, org.mockito.kotlin.never()).write(any(), any())
    }

    @Test
    fun `test with both inputs empty`() {
        // Arrange
        whenever(mockInputReader1.read()).thenReturn(null)
        whenever(mockInputReader2.read()).thenReturn(null)

        val properties = mapOf(
            "inputs" to listOf(
                mapOf("columnName" to "msg-1"),
                mapOf("columnName" to "msg-2")
            ),
            "outputsColumnName" to "message"
        )
        val inputs = mapOf("input-1" to mockInputReader1, "input-2" to mockInputReader2)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert - no rows written
        verify(mockPortWriter, org.mockito.kotlin.never()).write(any(), any())
    }

    // ===== Error Tests =====

    @Test
    fun `test execute throws exception when inputs property is missing`() {
        // Arrange
        val properties = mapOf(
            "outputsColumnName" to "message"
        )
        val inputs = mapOf("input-1" to mockInputReader1, "input-2" to mockInputReader2)

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter)
        }
        assertEquals("inputs is not provided", exception.message)
    }

    @Test
    fun `test execute throws exception when inputs property is null`() {
        // Arrange
        @Suppress("UNCHECKED_CAST")
        val properties = mapOf("inputs" to null) as Map<String, Any>
        val inputs = mapOf("input-1" to mockInputReader1, "input-2" to mockInputReader2)

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter)
        }
        assertEquals("inputs is not provided", exception.message)
    }

    @Test
    fun `test execute throws exception when inputs has less than 2 elements`() {
        // Arrange
        val properties = mapOf(
            "inputs" to listOf(
                mapOf("columnName" to "msg-1")
            ),
            "outputsColumnName" to "message"
        )
        val inputs = mapOf("input-1" to mockInputReader1, "input-2" to mockInputReader2)

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter)
        }
        assertTrue(exception.message?.contains("inputs must contain at least 2 elements") ?: false)
    }

    @Test
    fun `test execute throws exception when inputs is empty array`() {
        // Arrange
        val properties = mapOf(
            "inputs" to listOf<Map<String, String>>(),
            "outputsColumnName" to "message"
        )
        val inputs = mapOf("input-1" to mockInputReader1, "input-2" to mockInputReader2)

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter)
        }
        assertTrue(exception.message?.contains("inputs must contain at least 2 elements") ?: false)
    }

    @Test
    fun `test execute throws exception when first input columnName is missing`() {
        // Arrange
        val properties = mapOf(
            "inputs" to listOf(
                mapOf<String, String>(), // Missing columnName
                mapOf("columnName" to "msg-2")
            ),
            "outputsColumnName" to "message"
        )
        val inputs = mapOf("input-1" to mockInputReader1, "input-2" to mockInputReader2)

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter)
        }
        assertEquals("Input column name 1 is not provided", exception.message)
    }

    @Test
    fun `test execute throws exception when second input columnName is missing`() {
        // Arrange
        val properties = mapOf(
            "inputs" to listOf(
                mapOf("columnName" to "msg-1"),
                mapOf<String, String>() // Missing columnName
            ),
            "outputsColumnName" to "message"
        )
        val inputs = mapOf("input-1" to mockInputReader1, "input-2" to mockInputReader2)

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter)
        }
        assertEquals("Input column name 2 is not provided", exception.message)
    }

    @Test
    fun `test execute throws exception when properties map is null`() {
        // Arrange
        val inputs = mapOf("input-1" to mockInputReader1, "input-2" to mockInputReader2)

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(null, inputs, mockOutputWriter)
        }
        assertEquals("inputs is not provided", exception.message)
    }

    // ===== Resource Management Tests =====

    @Test
    fun `test output writer is properly closed`() {
        // Arrange
        val rows1 = listOf(mapOf("msg-1" to "Hello"))
        val rows2 = listOf(mapOf("msg-2" to "World"))
        mockSequentialReads(mockInputReader1, rows1)
        mockSequentialReads(mockInputReader2, rows2)

        val properties = mapOf(
            "inputs" to listOf(
                mapOf("columnName" to "msg-1"),
                mapOf("columnName" to "msg-2")
            ),
            "outputsColumnName" to "message"
        )
        val inputs = mapOf("input-1" to mockInputReader1, "input-2" to mockInputReader2)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter).close()
    }

    @Test
    fun `test input readers are properly closed`() {
        // Arrange
        val rows1 = listOf(mapOf("msg-1" to "Hello"))
        val rows2 = listOf(mapOf("msg-2" to "World"))
        mockSequentialReads(mockInputReader1, rows1)
        mockSequentialReads(mockInputReader2, rows2)

        val properties = mapOf(
            "inputs" to listOf(
                mapOf("columnName" to "msg-1"),
                mapOf("columnName" to "msg-2")
            ),
            "outputsColumnName" to "message"
        )
        val inputs = mapOf("input-1" to mockInputReader1, "input-2" to mockInputReader2)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockInputReader1).close()
        verify(mockInputReader2).close()
    }

    // ===== Helper Methods =====

    /**
     * Mocks the input reader to support streaming through data once.
     *
     * @param reader The reader to mock
     * @param rows The rows to return when read() is called
     */
    private fun mockSequentialReads(reader: NodeInputReader, rows: List<Map<String, Any?>>) {
        val rowsWithNull = rows + null
        var callCount = 0

        whenever(reader.read()).thenAnswer {
            val result = rowsWithNull[callCount]
            callCount++
            result
        }
    }
}
