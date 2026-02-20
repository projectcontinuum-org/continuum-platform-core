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

class BatchAccumulatorNodeModelTest {

    private lateinit var nodeModel: BatchAccumulatorNodeModel
    private lateinit var mockInputReader: NodeInputReader
    private lateinit var mockOutputWriter: NodeOutputWriter
    private lateinit var mockPortWriter: NodeOutputWriter.OutputPortWriter

    @BeforeEach
    fun setUp() {
        nodeModel = BatchAccumulatorNodeModel()
        mockInputReader = mock()
        mockOutputWriter = mock()
        mockPortWriter = mock()
        whenever(mockOutputWriter.createOutputPortWriter("data")).thenReturn(mockPortWriter)
    }

    // ===== Configuration Tests =====

    @Test
    fun `test node metadata is properly configured`() {
        val metadata = nodeModel.metadata
        assertEquals("com.continuum.base.node.BatchAccumulatorNodeModel", metadata.id)
        assertEquals("Groups rows into batches and adds batch_id and row_count columns", metadata.description)
        assertEquals("Batch Accumulator", metadata.title)
        assertEquals("Batch and label rows", metadata.subTitle)
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
        assertEquals("batched table", dataPort.name)
    }

    @Test
    fun `test categories are correctly defined`() {
        val categories = nodeModel.categories
        assertEquals(1, categories.size)
        assertEquals("Aggregation & Grouping", categories[0])
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
        assertEquals(2, defaultProperties["batchSize"])
    }

    // ===== Success Tests =====

    @Test
    fun `test execute with 5 rows and batch size 2`() {
        // Arrange - 5 rows should create 3 batches: [2, 2, 1]
        val rows = listOf(
            mapOf("id" to 1, "value" to "a"),
            mapOf("id" to 2, "value" to "b"),
            mapOf("id" to 3, "value" to "c"),
            mapOf("id" to 4, "value" to "d"),
            mapOf("id" to 5, "value" to "e")
        )
        mockSequentialReads(rows, totalCount = 5L)

        val properties = mapOf("batchSize" to 2)
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(5)).write(any(), rowCaptor.capture())
        assertEquals(5, rowCaptor.allValues.size)

        // Batch 1: rows 0-1 (batch_id=1, row_count=2)
        assertEquals(1, rowCaptor.allValues[0]["batch_id"])
        assertEquals(2, rowCaptor.allValues[0]["row_count"])
        assertEquals(1, rowCaptor.allValues[1]["batch_id"])
        assertEquals(2, rowCaptor.allValues[1]["row_count"])

        // Batch 2: rows 2-3 (batch_id=2, row_count=2)
        assertEquals(2, rowCaptor.allValues[2]["batch_id"])
        assertEquals(2, rowCaptor.allValues[2]["row_count"])
        assertEquals(2, rowCaptor.allValues[3]["batch_id"])
        assertEquals(2, rowCaptor.allValues[3]["row_count"])

        // Batch 3: row 4 (batch_id=3, row_count=1)
        assertEquals(3, rowCaptor.allValues[4]["batch_id"])
        assertEquals(1, rowCaptor.allValues[4]["row_count"])
    }

    @Test
    fun `test execute with 6 rows and batch size 3 - evenly divisible`() {
        // Arrange - 6 rows should create 2 batches: [3, 3]
        val rows = listOf(
            mapOf("id" to 1),
            mapOf("id" to 2),
            mapOf("id" to 3),
            mapOf("id" to 4),
            mapOf("id" to 5),
            mapOf("id" to 6)
        )
        mockSequentialReads(rows, totalCount = 6L)

        val properties = mapOf("batchSize" to 3)
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(6)).write(any(), rowCaptor.capture())
        assertEquals(6, rowCaptor.allValues.size)

        // Batch 1: rows 0-2 (batch_id=1, row_count=3)
        assertEquals(1, rowCaptor.allValues[0]["batch_id"])
        assertEquals(3, rowCaptor.allValues[0]["row_count"])
        assertEquals(1, rowCaptor.allValues[1]["batch_id"])
        assertEquals(3, rowCaptor.allValues[1]["row_count"])
        assertEquals(1, rowCaptor.allValues[2]["batch_id"])
        assertEquals(3, rowCaptor.allValues[2]["row_count"])

        // Batch 2: rows 3-5 (batch_id=2, row_count=3)
        assertEquals(2, rowCaptor.allValues[3]["batch_id"])
        assertEquals(3, rowCaptor.allValues[3]["row_count"])
        assertEquals(2, rowCaptor.allValues[4]["batch_id"])
        assertEquals(3, rowCaptor.allValues[4]["row_count"])
        assertEquals(2, rowCaptor.allValues[5]["batch_id"])
        assertEquals(3, rowCaptor.allValues[5]["row_count"])
    }

    @Test
    fun `test execute with single row`() {
        // Arrange
        val rows = listOf(mapOf("id" to 1, "value" to "only"))
        mockSequentialReads(rows, totalCount = 1L)

        val properties = mapOf("batchSize" to 5)
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        assertEquals(1, rowCaptor.allValues.size)
        assertEquals(1, rowCaptor.allValues[0]["batch_id"])
        assertEquals(1, rowCaptor.allValues[0]["row_count"])
    }

    @Test
    fun `test execute with batch size 1`() {
        // Arrange - each row is its own batch
        val rows = listOf(
            mapOf("id" to 1),
            mapOf("id" to 2),
            mapOf("id" to 3)
        )
        mockSequentialReads(rows, totalCount = 3L)

        val properties = mapOf("batchSize" to 1)
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(3)).write(any(), rowCaptor.capture())
        assertEquals(3, rowCaptor.allValues.size)

        // Each row should be in its own batch
        assertEquals(1, rowCaptor.allValues[0]["batch_id"])
        assertEquals(1, rowCaptor.allValues[0]["row_count"])
        assertEquals(2, rowCaptor.allValues[1]["batch_id"])
        assertEquals(1, rowCaptor.allValues[1]["row_count"])
        assertEquals(3, rowCaptor.allValues[2]["batch_id"])
        assertEquals(1, rowCaptor.allValues[2]["row_count"])
    }

    @Test
    fun `test execute with large batch size - all rows in one batch`() {
        // Arrange
        val rows = listOf(
            mapOf("id" to 1),
            mapOf("id" to 2),
            mapOf("id" to 3)
        )
        mockSequentialReads(rows, totalCount = 3L)

        val properties = mapOf("batchSize" to 100)
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(3)).write(any(), rowCaptor.capture())
        assertEquals(3, rowCaptor.allValues.size)

        // All rows should be in batch 1
        rowCaptor.allValues.forEach { row ->
            assertEquals(1, row["batch_id"])
            assertEquals(3, row["row_count"])
        }
    }

    @Test
    fun `test execute with 10 rows and batch size 3`() {
        // Arrange - 10 rows should create 4 batches: [3, 3, 3, 1]
        val rows = (1..10).map { mapOf("id" to it) }
        mockSequentialReads(rows, totalCount = 10L)

        val properties = mapOf("batchSize" to 3)
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(10)).write(any(), rowCaptor.capture())
        assertEquals(10, rowCaptor.allValues.size)

        // Verify batch assignments
        (0..2).forEach { i ->
            assertEquals(1, rowCaptor.allValues[i]["batch_id"], "Row $i should be in batch 1")
            assertEquals(3, rowCaptor.allValues[i]["row_count"])
        }
        (3..5).forEach { i ->
            assertEquals(2, rowCaptor.allValues[i]["batch_id"], "Row $i should be in batch 2")
            assertEquals(3, rowCaptor.allValues[i]["row_count"])
        }
        (6..8).forEach { i ->
            assertEquals(3, rowCaptor.allValues[i]["batch_id"], "Row $i should be in batch 3")
            assertEquals(3, rowCaptor.allValues[i]["row_count"])
        }
        assertEquals(4, rowCaptor.allValues[9]["batch_id"])
        assertEquals(1, rowCaptor.allValues[9]["row_count"])
    }

    @Test
    fun `test execute preserves original columns`() {
        // Arrange
        val rows = listOf(
            mapOf("id" to 1, "name" to "Alice", "age" to 30, "city" to "NYC"),
            mapOf("id" to 2, "name" to "Bob", "age" to 25, "city" to "LA")
        )
        mockSequentialReads(rows, totalCount = 2L)

        val properties = mapOf("batchSize" to 1)
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(2)).write(any(), rowCaptor.capture())
        assertEquals(2, rowCaptor.allValues.size)

        // Check first row
        val row1 = rowCaptor.allValues[0]
        assertEquals(1, row1["id"])
        assertEquals("Alice", row1["name"])
        assertEquals(30, row1["age"])
        assertEquals("NYC", row1["city"])
        assertEquals(1, row1["batch_id"])
        assertEquals(1, row1["row_count"])

        // Check second row
        val row2 = rowCaptor.allValues[1]
        assertEquals(2, row2["id"])
        assertEquals("Bob", row2["name"])
        assertEquals(25, row2["age"])
        assertEquals("LA", row2["city"])
        assertEquals(2, row2["batch_id"])
        assertEquals(1, row2["row_count"])
    }

    @Test
    fun `test execute with row indices are sequential`() {
        // Arrange
        val rows = listOf(
            mapOf("value" to 1),
            mapOf("value" to 2),
            mapOf("value" to 3),
            mapOf("value" to 4)
        )
        mockSequentialReads(rows, totalCount = 4L)

        val properties = mapOf("batchSize" to 2)
        val inputs = mapOf("data" to mockInputReader)
        val indexCaptor = argumentCaptor<Long>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(4)).write(indexCaptor.capture(), any())
        assertEquals(4, indexCaptor.allValues.size)
        assertEquals(0L, indexCaptor.allValues[0])
        assertEquals(1L, indexCaptor.allValues[1])
        assertEquals(2L, indexCaptor.allValues[2])
        assertEquals(3L, indexCaptor.allValues[3])
    }

    @Test
    fun `test execute with 100 rows and batch size 10`() {
        // Arrange - 100 rows should create 10 batches of 10 rows each
        val rows = (1..100).map { mapOf("id" to it) }
        mockSequentialReads(rows, totalCount = 100L)

        val properties = mapOf("batchSize" to 10)
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(100)).write(any(), rowCaptor.capture())
        assertEquals(100, rowCaptor.allValues.size)

        // Verify all batches have correct size
        for (i in 0 until 100) {
            val expectedBatchId = (i / 10) + 1
            assertEquals(expectedBatchId, rowCaptor.allValues[i]["batch_id"], "Row $i should be in batch $expectedBatchId")
            assertEquals(10, rowCaptor.allValues[i]["row_count"])
        }
    }

    @Test
    fun `test execute with complex data types`() {
        // Arrange
        val rows = listOf(
            mapOf("int" to 42, "double" to 3.14, "string" to "hello", "bool" to true),
            mapOf("int" to 99, "double" to 2.71, "string" to "world", "bool" to false)
        )
        mockSequentialReads(rows, totalCount = 2L)

        val properties = mapOf("batchSize" to 2)
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(2)).write(any(), rowCaptor.capture())
        assertEquals(2, rowCaptor.allValues.size)

        // Verify data types are preserved
        val row1 = rowCaptor.allValues[0]
        assertEquals(42, row1["int"])
        assertEquals(3.14, row1["double"])
        assertEquals("hello", row1["string"])
        assertEquals(true, row1["bool"])
    }

    // ===== Error Tests =====

    @Test
    fun `test execute throws exception when batchSize property is missing`() {
        // Arrange
        whenever(mockInputReader.read()).thenReturn(null)

        val properties = mapOf<String, Any>()
        val inputs = mapOf("data" to mockInputReader)

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter)
        }
        assertEquals("batchSize is not provided", exception.message)
    }

    @Test
    fun `test execute throws exception when batchSize property is null`() {
        // Arrange
        whenever(mockInputReader.read()).thenReturn(null)

        @Suppress("UNCHECKED_CAST")
        val properties = (mapOf("batchSize" to null) as Map<String, Any>)
        val inputs = mapOf("data" to mockInputReader)

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter)
        }
        assertEquals("batchSize is not provided", exception.message)
    }

    @Test
    fun `test execute throws exception when batchSize is less than 1`() {
        // Arrange
        whenever(mockInputReader.read()).thenReturn(null)

        val properties = mapOf("batchSize" to 0)
        val inputs = mapOf("data" to mockInputReader)

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter)
        }
        assertEquals("batchSize must be at least 1", exception.message)
    }

    @Test
    fun `test execute throws exception when batchSize is negative`() {
        // Arrange
        whenever(mockInputReader.read()).thenReturn(null)

        val properties = mapOf("batchSize" to -5)
        val inputs = mapOf("data" to mockInputReader)

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter)
        }
        assertEquals("batchSize must be at least 1", exception.message)
    }

    // ===== Edge Cases =====

    @Test
    fun `test execute with empty input stream`() {
        // Arrange
        whenever(mockInputReader.getRowCount()).thenReturn(0L)
        whenever(mockInputReader.read()).thenReturn(null)

        val properties = mapOf("batchSize" to 2)
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, org.mockito.kotlin.never()).write(any(), any())
    }

    @Test
    fun `test execute with no input data provided`() {
        // Arrange
        val properties = mapOf("batchSize" to 2)
        val inputs = mapOf<String, NodeInputReader>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, org.mockito.kotlin.never()).write(any(), any())
    }

    @Test
    fun `test execute with batchSize as Long type`() {
        // Arrange
        val rows = listOf(mapOf("id" to 1), mapOf("id" to 2))
        mockSequentialReads(rows, totalCount = 2L)

        val properties = mapOf("batchSize" to 2L)
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(2)).write(any(), rowCaptor.capture())
        assertEquals(2, rowCaptor.allValues.size)
    }

    @Test
    fun `test execute with batchSize as Double type`() {
        // Arrange
        val rows = listOf(mapOf("id" to 1), mapOf("id" to 2))
        mockSequentialReads(rows, totalCount = 2L)

        val properties = mapOf("batchSize" to 2.0)
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(2)).write(any(), rowCaptor.capture())
        assertEquals(2, rowCaptor.allValues.size)
    }

    @Test
    fun `test output writer is properly closed`() {
        // Arrange
        val rows = listOf(mapOf("value" to 1))
        mockSequentialReads(rows, totalCount = 1L)

        val properties = mapOf("batchSize" to 1)
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter).close()
    }

    @Test
    fun `test input reader is properly closed`() {
        // Arrange
        val rows = listOf(mapOf("value" to 1))
        mockSequentialReads(rows, totalCount = 1L)

        val properties = mapOf("batchSize" to 1)
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockInputReader).close()
    }

    @Test
    fun `test execute with very large batch size uses correct batch_id`() {
        // Arrange
        val rows = (1..5).map { mapOf("id" to it) }
        mockSequentialReads(rows, totalCount = 5L)

        val properties = mapOf("batchSize" to Int.MAX_VALUE)
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(5)).write(any(), rowCaptor.capture())
        rowCaptor.allValues.forEach { row ->
            assertEquals(1, row["batch_id"])
            assertEquals(5, row["row_count"])
        }
    }

    @Test
    fun `test execute with exactly batch size rows`() {
        // Arrange - exactly 5 rows with batch size 5
        val rows = (1..5).map { mapOf("id" to it) }
        mockSequentialReads(rows, totalCount = 5L)

        val properties = mapOf("batchSize" to 5)
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(5)).write(any(), rowCaptor.capture())
        rowCaptor.allValues.forEach { row ->
            assertEquals(1, row["batch_id"])
            assertEquals(5, row["row_count"])
        }
    }

    // ===== Helper Methods =====

    /**
     * Mocks the input reader to support streaming through data once.
     * Also mocks the getRowCount() method to return the total count.
     *
     * @param rows The rows to return when read() is called
     * @param totalCount The total row count to return from getRowCount()
     */
    private fun mockSequentialReads(rows: List<Map<String, Any>>, totalCount: Long) {
        // Mock getRowCount() to return the total count
        whenever(mockInputReader.getRowCount()).thenReturn(totalCount)

        // Mock read() to return rows sequentially
        val rowsWithNull = rows + null
        var callCount = 0

        whenever(mockInputReader.read()).thenAnswer {
            val result = rowsWithNull[callCount]
            callCount++
            result
        }
    }
}
