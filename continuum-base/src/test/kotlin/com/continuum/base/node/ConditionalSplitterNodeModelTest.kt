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

class ConditionalSplitterNodeModelTest {

    private lateinit var nodeModel: ConditionalSplitterNodeModel
    private lateinit var mockInputReader: NodeInputReader
    private lateinit var mockOutputWriter: NodeOutputWriter
    private lateinit var mockHighWriter: NodeOutputWriter.OutputPortWriter
    private lateinit var mockLowWriter: NodeOutputWriter.OutputPortWriter

    @BeforeEach
    fun setUp() {
        nodeModel = ConditionalSplitterNodeModel()
        mockInputReader = mock()
        mockOutputWriter = mock()
        mockHighWriter = mock()
        mockLowWriter = mock()
        whenever(mockOutputWriter.createOutputPortWriter("high")).thenReturn(mockHighWriter)
        whenever(mockOutputWriter.createOutputPortWriter("low")).thenReturn(mockLowWriter)
    }

    // ===== Configuration Tests =====

    @Test
    fun `test node metadata is properly configured`() {
        val metadata = nodeModel.metadata
        assertEquals("com.continuum.base.node.ConditionalSplitterNodeModel", metadata.id)
        assertEquals("Splits rows into two outputs based on threshold comparison", metadata.description)
        assertEquals("Conditional Splitter", metadata.title)
        assertEquals("Split by threshold", metadata.subTitle)
        assertNotNull(metadata.icon)
        assertTrue(metadata.icon.toString().contains("svg"))
    }

    @Test
    fun `test input ports are correctly defined`() {
        val inputPorts = nodeModel.inputPorts
        assertEquals(1, inputPorts.size)
        assertNotNull(inputPorts["data"])
        assertEquals("input table", inputPorts["data"]!!.name)
    }

    @Test
    fun `test output ports are correctly defined`() {
        val outputPorts = nodeModel.outputPorts
        assertEquals(2, outputPorts.size)
        assertNotNull(outputPorts["high"])
        assertNotNull(outputPorts["low"])
        assertEquals("high values (>= threshold)", outputPorts["high"]!!.name)
        assertEquals("low values (< threshold)", outputPorts["low"]!!.name)
    }

    @Test
    fun `test categories are correctly defined`() {
        val categories = nodeModel.categories
        assertEquals(1, categories.size)
        assertEquals("Flow Control", categories[0])
    }

    @Test
    fun `test properties schema is valid`() {
        val schema = nodeModel.propertiesSchema
        assertNotNull(schema)
        assertEquals("object", schema["type"])
        assertTrue(schema.containsKey("properties"))
        assertTrue(schema.containsKey("required"))
    }

    // ===== Success Tests =====

    @Test
    fun `test execute splits rows correctly with threshold 50`() {
        // Arrange
        val rows = listOf(
            mapOf("value" to 100, "name" to "high1"),
            mapOf("value" to 25, "name" to "low1"),
            mapOf("value" to 50, "name" to "high2"),
            mapOf("value" to 49, "name" to "low2"),
            mapOf("value" to 75, "name" to "high3")
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("column" to "value", "threshold" to 50)
        val inputs = mapOf("data" to mockInputReader)
        val highCaptor = argumentCaptor<Map<String, Any>>()
        val lowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockHighWriter, times(3)).write(any(), highCaptor.capture())
        verify(mockLowWriter, times(2)).write(any(), lowCaptor.capture())

        // Verify high values
        assertEquals("high1", highCaptor.allValues[0]["name"])
        assertEquals("high2", highCaptor.allValues[1]["name"])
        assertEquals("high3", highCaptor.allValues[2]["name"])

        // Verify low values
        assertEquals("low1", lowCaptor.allValues[0]["name"])
        assertEquals("low2", lowCaptor.allValues[1]["name"])
    }

    @Test
    fun `test execute with all values above threshold`() {
        // Arrange
        val rows = listOf(
            mapOf("value" to 100),
            mapOf("value" to 200),
            mapOf("value" to 150)
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("column" to "value", "threshold" to 50)
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockHighWriter, times(3)).write(any(), any())
        verify(mockLowWriter, org.mockito.kotlin.never()).write(any(), any())
    }

    @Test
    fun `test execute with all values below threshold`() {
        // Arrange
        val rows = listOf(
            mapOf("value" to 10),
            mapOf("value" to 20),
            mapOf("value" to 30)
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("column" to "value", "threshold" to 50)
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockHighWriter, org.mockito.kotlin.never()).write(any(), any())
        verify(mockLowWriter, times(3)).write(any(), any())
    }

    @Test
    fun `test execute with threshold zero`() {
        // Arrange
        val rows = listOf(
            mapOf("value" to 0),
            mapOf("value" to -5),
            mapOf("value" to 5)
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("column" to "value", "threshold" to 0)
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockHighWriter, times(2)).write(any(), any()) // 0 and 5
        verify(mockLowWriter, times(1)).write(any(), any()) // -5
    }

    @Test
    fun `test execute with negative threshold`() {
        // Arrange
        val rows = listOf(
            mapOf("value" to -10),
            mapOf("value" to -5),
            mapOf("value" to 0)
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("column" to "value", "threshold" to -5)
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockHighWriter, times(2)).write(any(), any()) // -5 and 0
        verify(mockLowWriter, times(1)).write(any(), any()) // -10
    }

    @Test
    fun `test execute with decimal threshold`() {
        // Arrange
        val rows = listOf(
            mapOf("value" to 15.5),
            mapOf("value" to 15.4),
            mapOf("value" to 15.6)
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("column" to "value", "threshold" to 15.5)
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockHighWriter, times(2)).write(any(), any()) // 15.5 and 15.6
        verify(mockLowWriter, times(1)).write(any(), any()) // 15.4
    }

    @Test
    fun `test execute handles integer column values`() {
        // Arrange
        val rows = listOf(
            mapOf("value" to 100),
            mapOf("value" to 50)
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("column" to "value", "threshold" to 75)
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockHighWriter, times(1)).write(any(), any())
        verify(mockLowWriter, times(1)).write(any(), any())
    }

    @Test
    fun `test execute handles long column values`() {
        // Arrange
        val rows = listOf(
            mapOf("value" to 1000L),
            mapOf("value" to 500L)
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("column" to "value", "threshold" to 750)
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockHighWriter, times(1)).write(any(), any())
        verify(mockLowWriter, times(1)).write(any(), any())
    }

    @Test
    fun `test execute with missing column defaults to zero`() {
        // Arrange
        val rows = listOf(
            mapOf("otherColumn" to "value"),
            mapOf("otherColumn" to "value2")
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("column" to "missingColumn", "threshold" to 0)
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert - all should go to high since 0.0 >= 0
        verify(mockHighWriter, times(2)).write(any(), any())
        verify(mockLowWriter, org.mockito.kotlin.never()).write(any(), any())
    }

    @Test
    fun `test execute preserves all row data`() {
        // Arrange
        val rows = listOf(
            mapOf("value" to 100, "name" to "Alice", "age" to 30, "city" to "NYC"),
            mapOf("value" to 25, "name" to "Bob", "age" to 25, "city" to "LA")
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("column" to "value", "threshold" to 50)
        val inputs = mapOf("data" to mockInputReader)
        val highCaptor = argumentCaptor<Map<String, Any>>()
        val lowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockHighWriter, times(1)).write(any(), highCaptor.capture())
        verify(mockLowWriter, times(1)).write(any(), lowCaptor.capture())

        // Check high row
        val highRow = highCaptor.firstValue
        assertEquals(100, highRow["value"])
        assertEquals("Alice", highRow["name"])
        assertEquals(30, highRow["age"])
        assertEquals("NYC", highRow["city"])

        // Check low row
        val lowRow = lowCaptor.firstValue
        assertEquals(25, lowRow["value"])
        assertEquals("Bob", lowRow["name"])
        assertEquals(25, lowRow["age"])
        assertEquals("LA", lowRow["city"])
    }

    @Test
    fun `test execute with row indices are correct`() {
        // Arrange
        val rows = listOf(
            mapOf("value" to 100),
            mapOf("value" to 25),
            mapOf("value" to 75),
            mapOf("value" to 10)
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("column" to "value", "threshold" to 50)
        val inputs = mapOf("data" to mockInputReader)
        val highIndexCaptor = argumentCaptor<Long>()
        val lowIndexCaptor = argumentCaptor<Long>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockHighWriter, times(2)).write(highIndexCaptor.capture(), any())
        verify(mockLowWriter, times(2)).write(lowIndexCaptor.capture(), any())

        assertEquals(listOf(0L, 1L), highIndexCaptor.allValues)
        assertEquals(listOf(0L, 1L), lowIndexCaptor.allValues)
    }

    // ===== Error Tests =====

    @Test
    fun `test execute throws exception when column is missing`() {
        // Arrange
        val properties = mapOf("threshold" to 50)
        val inputs = mapOf("data" to mockInputReader)

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter)
        }
        assertEquals("column is not provided", exception.message)
    }

    @Test
    fun `test execute throws exception when threshold is missing`() {
        // Arrange
        val properties = mapOf("column" to "value")
        val inputs = mapOf("data" to mockInputReader)

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter)
        }
        assertEquals("threshold is not provided", exception.message)
    }

    @Test
    fun `test execute throws exception when threshold is not a number`() {
        // Arrange
        val properties = mapOf("column" to "value", "threshold" to "not a number")
        val inputs = mapOf("data" to mockInputReader)

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter)
        }
        assertEquals("threshold is not provided", exception.message)
    }

    // ===== Edge Cases =====

    @Test
    fun `test execute with empty input`() {
        // Arrange
        mockSequentialReads(mockInputReader, emptyList())

        val properties = mapOf("column" to "value", "threshold" to 50)
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockHighWriter, org.mockito.kotlin.never()).write(any(), any())
        verify(mockLowWriter, org.mockito.kotlin.never()).write(any(), any())
    }

    @Test
    fun `test execute with single row`() {
        // Arrange
        val rows = listOf(mapOf("value" to 100))
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("column" to "value", "threshold" to 50)
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockHighWriter, times(1)).write(any(), any())
        verify(mockLowWriter, org.mockito.kotlin.never()).write(any(), any())
    }

    @Test
    fun `test execute properly closes writers`() {
        // Arrange
        val rows = listOf(mapOf("value" to 100))
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("column" to "value", "threshold" to 50)
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockHighWriter).close()
        verify(mockLowWriter).close()
    }

    @Test
    fun `test execute properly closes input reader`() {
        // Arrange
        val rows = listOf(mapOf("value" to 100))
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("column" to "value", "threshold" to 50)
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockInputReader).close()
    }

    @Test
    fun `test execute with very large dataset`() {
        // Arrange
        val rows = (1..1000).map { mapOf("value" to it) }
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("column" to "value", "threshold" to 500)
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockHighWriter, times(501)).write(any(), any()) // 500-1000 inclusive
        verify(mockLowWriter, times(499)).write(any(), any()) // 1-499
    }

    // ===== Helper Methods =====

    private fun mockSequentialReads(reader: NodeInputReader, rows: List<Map<String, Any>>) {
        val rowsWithNull = rows + null
        var callCount = 0

        whenever(reader.read()).thenAnswer {
            val result = rowsWithNull[callCount]
            callCount++
            result
        }
    }
}
