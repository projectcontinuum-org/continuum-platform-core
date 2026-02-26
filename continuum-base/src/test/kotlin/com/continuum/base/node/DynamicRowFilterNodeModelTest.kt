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

class DynamicRowFilterNodeModelTest {

    private lateinit var nodeModel: DynamicRowFilterNodeModel
    private lateinit var mockInputReader: NodeInputReader
    private lateinit var mockOutputWriter: NodeOutputWriter
    private lateinit var mockPortWriter: NodeOutputWriter.OutputPortWriter

    @BeforeEach
    fun setUp() {
        nodeModel = DynamicRowFilterNodeModel()
        mockInputReader = mock()
        mockOutputWriter = mock()
        mockPortWriter = mock()
        whenever(mockOutputWriter.createOutputPortWriter("data")).thenReturn(mockPortWriter)
    }

    // ===== Configuration Tests =====

    @Test
    fun `test node metadata is properly configured`() {
        val metadata = nodeModel.metadata
        assertEquals("com.continuum.base.node.DynamicRowFilterNodeModel", metadata.id)
        assertEquals("Filters rows where the specified column value is greater than the threshold", metadata.description)
        assertEquals("Dynamic Row Filter", metadata.title)
        assertEquals("Filter rows by threshold", metadata.subTitle)
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
        assertEquals(1, outputPorts.size)
        assertNotNull(outputPorts["data"])
        assertEquals("filtered table", outputPorts["data"]!!.name)
    }

    @Test
    fun `test categories are correctly defined`() {
        val categories = nodeModel.categories
        assertEquals(1, categories.size)
        assertEquals("Filter & Select", categories[0])
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
    fun `test execute filters rows correctly`() {
        // Arrange
        val rows = listOf(
            mapOf("age" to 35, "name" to "Alice"),
            mapOf("age" to 25, "name" to "Bob"),
            mapOf("age" to 40, "name" to "Charlie"),
            mapOf("age" to 30, "name" to "David"),
            mapOf("age" to 28, "name" to "Eve")
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("columnName" to "age", "threshold" to 30)
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert - only Alice(35) and Charlie(40) pass since filter is > 30 (strictly greater)
        verify(mockPortWriter, times(2)).write(any(), rowCaptor.capture())
        assertEquals("Alice", rowCaptor.allValues[0]["name"])
        assertEquals("Charlie", rowCaptor.allValues[1]["name"])
    }

    @Test
    fun `test execute with threshold zero`() {
        // Arrange
        val rows = listOf(
            mapOf("value" to 5),
            mapOf("value" to 0),
            mapOf("value" to -5)
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("columnName" to "value", "threshold" to 0)
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert - only value > 0 should pass
        verify(mockPortWriter, times(1)).write(any(), any())
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

        val properties = mapOf("columnName" to "value", "threshold" to -5)
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert - only values > -5 should pass
        verify(mockPortWriter, times(1)).write(any(), any())
    }

    @Test
    fun `test execute with decimal threshold`() {
        // Arrange
        val rows = listOf(
            mapOf("value" to 10.5),
            mapOf("value" to 10.6),
            mapOf("value" to 10.4)
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("columnName" to "value", "threshold" to 10.5)
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert - only value > 10.5 should pass (10.6)
        verify(mockPortWriter, times(1)).write(any(), any())
    }

    @Test
    fun `test execute with all rows passing filter`() {
        // Arrange
        val rows = listOf(
            mapOf("value" to 100),
            mapOf("value" to 200),
            mapOf("value" to 150)
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("columnName" to "value", "threshold" to 50)
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(3)).write(any(), any())
    }

    @Test
    fun `test execute with no rows passing filter`() {
        // Arrange
        val rows = listOf(
            mapOf("value" to 10),
            mapOf("value" to 20),
            mapOf("value" to 30)
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("columnName" to "value", "threshold" to 50)
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, org.mockito.kotlin.never()).write(any(), any())
    }

    @Test
    fun `test execute handles integer values`() {
        // Arrange
        val rows = listOf(
            mapOf("value" to 100),
            mapOf("value" to 50)
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("columnName" to "value", "threshold" to 75)
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), any())
    }

    @Test
    fun `test execute handles long values`() {
        // Arrange
        val rows = listOf(
            mapOf("value" to 1000L),
            mapOf("value" to 500L)
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("columnName" to "value", "threshold" to 750)
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), any())
    }

    @Test
    fun `test execute handles double values`() {
        // Arrange
        val rows = listOf(
            mapOf("value" to 99.9),
            mapOf("value" to 100.1)
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("columnName" to "value", "threshold" to 100)
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), any())
    }

    @Test
    fun `test execute with missing column defaults to zero`() {
        // Arrange
        val rows = listOf(
            mapOf("otherColumn" to "value"),
            mapOf("otherColumn" to "value2")
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("columnName" to "missingColumn", "threshold" to -1)
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert - 0.0 > -1, so both should pass
        verify(mockPortWriter, times(2)).write(any(), any())
    }

    @Test
    fun `test execute preserves all row data`() {
        // Arrange
        val rows = listOf(
            mapOf("age" to 35, "name" to "Alice", "city" to "NYC", "salary" to 100000)
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("columnName" to "age", "threshold" to 30)
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val result = rowCaptor.firstValue
        assertEquals(35, result["age"])
        assertEquals("Alice", result["name"])
        assertEquals("NYC", result["city"])
        assertEquals(100000, result["salary"])
    }

    @Test
    fun `test execute with row indices are sequential`() {
        // Arrange
        val rows = listOf(
            mapOf("value" to 100),
            mapOf("value" to 25),
            mapOf("value" to 75),
            mapOf("value" to 10),
            mapOf("value" to 50)
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("columnName" to "value", "threshold" to 30)
        val inputs = mapOf("data" to mockInputReader)
        val indexCaptor = argumentCaptor<Long>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert - values 100, 75, 50 should pass
        verify(mockPortWriter, times(3)).write(indexCaptor.capture(), any())
        assertEquals(listOf(0L, 1L, 2L), indexCaptor.allValues)
    }

    @Test
    fun `test execute filters based on strictly greater than`() {
        // Arrange - value must be > threshold, not >=
        val rows = listOf(
            mapOf("value" to 31),
            mapOf("value" to 30),
            mapOf("value" to 29)
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("columnName" to "value", "threshold" to 30)
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert - only 31 should pass (> 30, not >= 30)
        verify(mockPortWriter, times(1)).write(any(), any())
    }

    // ===== Error Tests =====

    @Test
    fun `test execute throws exception when columnName is missing`() {
        // Arrange
        val properties = mapOf("threshold" to 50)
        val inputs = mapOf("data" to mockInputReader)

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter)
        }
        assertEquals("columnName is not provided", exception.message)
    }

    @Test
    fun `test execute throws exception when threshold is missing`() {
        // Arrange
        val properties = mapOf("columnName" to "value")
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
        val properties = mapOf("columnName" to "value", "threshold" to "not a number")
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

        val properties = mapOf("columnName" to "value", "threshold" to 50)
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, org.mockito.kotlin.never()).write(any(), any())
    }

    @Test
    fun `test execute with single row`() {
        // Arrange
        val rows = listOf(mapOf("value" to 100))
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("columnName" to "value", "threshold" to 50)
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), any())
    }

    @Test
    fun `test execute properly closes writer`() {
        // Arrange
        val rows = listOf(mapOf("value" to 100))
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("columnName" to "value", "threshold" to 50)
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter).close()
    }

    @Test
    fun `test execute properly closes input reader`() {
        // Arrange
        val rows = listOf(mapOf("value" to 100))
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("columnName" to "value", "threshold" to 50)
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockInputReader).close()
    }

    @Test
    fun `test execute with large dataset`() {
        // Arrange
        val rows = (1..1000).map { mapOf("value" to it) }
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("columnName" to "value", "threshold" to 500)
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert - values 501-1000 should pass (strictly > 500)
        verify(mockPortWriter, times(500)).write(any(), any())
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
