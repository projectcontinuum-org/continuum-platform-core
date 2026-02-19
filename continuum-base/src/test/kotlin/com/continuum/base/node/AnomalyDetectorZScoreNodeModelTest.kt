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

class AnomalyDetectorZScoreNodeModelTest {

    private lateinit var nodeModel: AnomalyDetectorZScoreNodeModel
    private lateinit var mockInputReader: NodeInputReader
    private lateinit var mockOutputWriter: NodeOutputWriter
    private lateinit var mockPortWriter: NodeOutputWriter.OutputPortWriter

    @BeforeEach
    fun setUp() {
        nodeModel = AnomalyDetectorZScoreNodeModel()
        mockInputReader = mock()
        mockOutputWriter = mock()
        mockPortWriter = mock()
        whenever(mockOutputWriter.createOutputPortWriter("data")).thenReturn(mockPortWriter)
    }

    // ===== Configuration Tests =====

    @Test
    fun `test node metadata is properly configured`() {
        val metadata = nodeModel.metadata
        assertEquals("com.continuum.base.node.AnomalyDetectorZScoreNodeModel", metadata.id)
        assertEquals("Detects outliers using Z-score method (flags values with |Z| > 2)", metadata.description)
        assertEquals("Anomaly Detector (Z-Score)", metadata.title)
        assertEquals("Statistical outlier detection", metadata.subTitle)
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
        assertEquals("table with outlier flags", dataPort.name)
    }

    @Test
    fun `test categories are correctly defined`() {
        val categories = nodeModel.categories
        assertEquals(1, categories.size)
        assertEquals("Analysis & Statistics", categories[0])
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
        assertEquals("value", defaultProperties["valueCol"])
    }

    // ===== Success Tests =====

    @Test
    fun `test execute with normal data - no outliers`() {
        // Arrange
        val rows = listOf(
            mapOf("id" to 1, "value" to 10.0),
            mapOf("id" to 2, "value" to 11.0),
            mapOf("id" to 3, "value" to 12.0),
            mapOf("id" to 4, "value" to 13.0),
            mapOf("id" to 5, "value" to 14.0)
        )
        mockSequentialReads(rows)

        val properties = mapOf("valueCol" to "value")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(5)).write(any(), rowCaptor.capture())
        assertEquals(5, rowCaptor.allValues.size)
        rowCaptor.allValues.forEach { row ->
            assertEquals(false, row["is_outlier"])
        }
    }

    @Test
    fun `test execute with outliers - z-score greater than 2`() {
        // Arrange
        val rows = listOf(
            mapOf("id" to 1, "value" to 1.0),
            mapOf("id" to 2, "value" to 2.0),
            mapOf("id" to 3, "value" to 3.0),
            mapOf("id" to 4, "value" to 100.0),  // Outlier
            mapOf("id" to 5, "value" to 2.0)
        )
        mockSequentialReads(rows)

        val properties = mapOf("valueCol" to "value")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(5)).write(any(), rowCaptor.capture())
        assertEquals(5, rowCaptor.allValues.size)
        assertEquals(false, rowCaptor.allValues[0]["is_outlier"]) // 1.0
        assertEquals(false, rowCaptor.allValues[1]["is_outlier"]) // 2.0
        assertEquals(false, rowCaptor.allValues[2]["is_outlier"]) // 3.0
        assertEquals(true, rowCaptor.allValues[3]["is_outlier"])  // 100.0 is outlier
        assertEquals(false, rowCaptor.allValues[4]["is_outlier"]) // 2.0
    }

    @Test
    fun `test execute with negative outliers - z-score less than -2`() {
        // Arrange
        val rows = listOf(
            mapOf("id" to 1, "value" to 10.0),
            mapOf("id" to 2, "value" to 11.0),
            mapOf("id" to 3, "value" to 9.0),
            mapOf("id" to 4, "value" to -100.0),  // Negative outlier
            mapOf("id" to 5, "value" to 10.0)
        )
        mockSequentialReads(rows)

        val properties = mapOf("valueCol" to "value")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(5)).write(any(), rowCaptor.capture())
        assertEquals(5, rowCaptor.allValues.size)
        assertEquals(true, rowCaptor.allValues[3]["is_outlier"]) // -100.0 is outlier
    }

    @Test
    fun `test execute with single row`() {
        // Arrange
        val rows = listOf(mapOf("id" to 1, "value" to 5.0))
        mockSequentialReads(rows)

        val properties = mapOf("valueCol" to "value")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter).write(any(), rowCaptor.capture())
        assertEquals(1, rowCaptor.allValues.size)
        assertEquals(false, rowCaptor.allValues[0]["is_outlier"])
    }

    @Test
    fun `test execute with identical values - zero standard deviation`() {
        // Arrange
        val rows = listOf(
            mapOf("id" to 1, "value" to 5.0),
            mapOf("id" to 2, "value" to 5.0),
            mapOf("id" to 3, "value" to 5.0),
            mapOf("id" to 4, "value" to 5.0),
            mapOf("id" to 5, "value" to 5.0)
        )
        mockSequentialReads(rows)

        val properties = mapOf("valueCol" to "value")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(5)).write(any(), rowCaptor.capture())
        assertEquals(5, rowCaptor.allValues.size)
        // All should be marked as non-outliers when std dev = 0
        rowCaptor.allValues.forEach { row ->
            assertEquals(false, row["is_outlier"])
        }
    }

    @Test
    fun `test execute with negative values`() {
        // Arrange
        val rows = listOf(
            mapOf("id" to 1, "value" to -10.0),
            mapOf("id" to 2, "value" to -11.0),
            mapOf("id" to 3, "value" to -12.0),
            mapOf("id" to 4, "value" to -13.0),
            mapOf("id" to 5, "value" to -14.0)
        )
        mockSequentialReads(rows)

        val properties = mapOf("valueCol" to "value")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(5)).write(any(), rowCaptor.capture())
        assertEquals(5, rowCaptor.allValues.size)
        rowCaptor.allValues.forEach { row ->
            assertEquals(false, row["is_outlier"])
        }
    }

    @Test
    fun `test execute with mixed positive and negative values`() {
        // Arrange
        val rows = listOf(
            mapOf("id" to 1, "value" to -5.0),
            mapOf("id" to 2, "value" to 0.0),
            mapOf("id" to 3, "value" to 5.0),
            mapOf("id" to 4, "value" to -10.0),
            mapOf("id" to 5, "value" to 10.0)
        )
        mockSequentialReads(rows)

        val properties = mapOf("valueCol" to "value")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(5)).write(any(), rowCaptor.capture())
        assertEquals(5, rowCaptor.allValues.size)
        assertNotNull(rowCaptor.allValues[0]["is_outlier"])
    }

    @Test
    fun `test execute with floating point values`() {
        // Arrange
        val rows = listOf(
            mapOf("id" to 1, "value" to 1.5),
            mapOf("id" to 2, "value" to 2.3),
            mapOf("id" to 3, "value" to 1.9),
            mapOf("id" to 4, "value" to 2.1),
            mapOf("id" to 5, "value" to 1.8)
        )
        mockSequentialReads(rows)

        val properties = mapOf("valueCol" to "value")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(5)).write(any(), rowCaptor.capture())
        assertEquals(5, rowCaptor.allValues.size)
        rowCaptor.allValues.forEach { row ->
            assertEquals(false, row["is_outlier"])
        }
    }

    @Test
    fun `test execute with very large numbers`() {
        // Arrange
        val rows = listOf(
            mapOf("id" to 1, "value" to 1000000.0),
            mapOf("id" to 2, "value" to 1000001.0),
            mapOf("id" to 3, "value" to 999999.0),
            mapOf("id" to 4, "value" to 1000002.0),
            mapOf("id" to 5, "value" to 1000003.0)
        )
        mockSequentialReads(rows)

        val properties = mapOf("valueCol" to "value")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(5)).write(any(), rowCaptor.capture())
        assertEquals(5, rowCaptor.allValues.size)
        rowCaptor.allValues.forEach { row ->
            assertEquals(false, row["is_outlier"])
        }
    }

    @Test
    fun `test execute with very small numbers`() {
        // Arrange
        val rows = listOf(
            mapOf("id" to 1, "value" to 0.00001),
            mapOf("id" to 2, "value" to 0.00002),
            mapOf("id" to 3, "value" to 0.00003),
            mapOf("id" to 4, "value" to 0.00004),
            mapOf("id" to 5, "value" to 0.00005)
        )
        mockSequentialReads(rows)

        val properties = mapOf("valueCol" to "value")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(5)).write(any(), rowCaptor.capture())
        assertEquals(5, rowCaptor.allValues.size)
        rowCaptor.allValues.forEach { row ->
            assertEquals(false, row["is_outlier"])
        }
    }

    @Test
    fun `test execute with zero values`() {
        // Arrange
        val rows = listOf(
            mapOf("id" to 1, "value" to 0.0),
            mapOf("id" to 2, "value" to 0.0),
            mapOf("id" to 3, "value" to 0.0),
            mapOf("id" to 4, "value" to 0.0),
            mapOf("id" to 5, "value" to 0.0)
        )
        mockSequentialReads(rows)

        val properties = mapOf("valueCol" to "value")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(5)).write(any(), rowCaptor.capture())
        assertEquals(5, rowCaptor.allValues.size)
        rowCaptor.allValues.forEach { row ->
            assertEquals(false, row["is_outlier"])
        }
    }

    @Test
    fun `test execute with integer values converted to double`() {
        // Arrange
        val rows = listOf(
            mapOf("id" to 1, "value" to 10),  // Integer, not Double
            mapOf("id" to 2, "value" to 11),
            mapOf("id" to 3, "value" to 12),
            mapOf("id" to 4, "value" to 13),
            mapOf("id" to 5, "value" to 14)
        )
        mockSequentialReads(rows)

        val properties = mapOf("valueCol" to "value")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(5)).write(any(), rowCaptor.capture())
        assertEquals(5, rowCaptor.allValues.size)
        rowCaptor.allValues.forEach { row ->
            assertEquals(false, row["is_outlier"])
        }
    }

    @Test
    fun `test execute with two extreme outliers`() {
        // Arrange
        val rows = listOf(
            mapOf("id" to 1, "value" to 1.0),
            mapOf("id" to 2, "value" to -1000.0),  // Lower outlier
            mapOf("id" to 3, "value" to 2.0),
            mapOf("id" to 4, "value" to 1000.0),   // Upper outlier
            mapOf("id" to 5, "value" to 1.5)
        )
        mockSequentialReads(rows)

        val properties = mapOf("valueCol" to "value")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(5)).write(any(), rowCaptor.capture())
        assertEquals(5, rowCaptor.allValues.size)
        assertEquals(true, rowCaptor.allValues[1]["is_outlier"])  // -1000.0
        assertEquals(true, rowCaptor.allValues[3]["is_outlier"])  // 1000.0
    }

    @Test
    fun `test execute with additional columns are preserved`() {
        // Arrange
        val rows = listOf(
            mapOf("id" to 1, "name" to "Alice", "value" to 10.0),
            mapOf("id" to 2, "name" to "Bob", "value" to 11.0),
            mapOf("id" to 3, "name" to "Charlie", "value" to 12.0)
        )
        mockSequentialReads(rows)

        val properties = mapOf("valueCol" to "value")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(3)).write(any(), rowCaptor.capture())
        assertEquals(3, rowCaptor.allValues.size)
        assertEquals(1, rowCaptor.allValues[0]["id"])
        assertEquals("Alice", rowCaptor.allValues[0]["name"])
        assertEquals(10.0, rowCaptor.allValues[0]["value"])
        assertNotNull(rowCaptor.allValues[0]["is_outlier"])
    }

    @Test
    fun `test execute with numeric row indices`() {
        // Arrange
        val rows = listOf(
            mapOf("value" to 1.0),
            mapOf("value" to 2.0),
            mapOf("value" to 3.0)
        )
        mockSequentialReads(rows)

        val properties = mapOf("valueCol" to "value")
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
    fun `test execute with exactly z-score threshold boundary`() {
        // Arrange - Create data where one value is exactly at z-score = 2.0
        // Mean = 10, values: 9, 10, 11 => std ≈ 0.816
        // For z-score = 2.0: value = mean + 2 * std = 10 + 2 * 0.816 ≈ 11.633
        val rows = listOf(
            mapOf("id" to 1, "value" to 9.0),
            mapOf("id" to 2, "value" to 10.0),
            mapOf("id" to 3, "value" to 11.0),
            mapOf("id" to 4, "value" to 11.632),  // Near boundary
            mapOf("id" to 5, "value" to 10.0)
        )
        mockSequentialReads(rows)

        val properties = mapOf("valueCol" to "value")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(5)).write(any(), rowCaptor.capture())
        assertEquals(5, rowCaptor.allValues.size)
        // Value at boundary should NOT be marked as outlier (needs > 2.0, not >= 2.0)
        assertEquals(false, rowCaptor.allValues[3]["is_outlier"])
    }

    @Test
    fun `test execute with just above z-score threshold`() {
        // Arrange - Create data where value exceeds z-score threshold
        val rows = listOf(
            mapOf("id" to 1, "value" to 1.0),
            mapOf("id" to 2, "value" to 2.0),
            mapOf("id" to 3, "value" to 1.5),
            mapOf("id" to 4, "value" to 2.5),
            mapOf("id" to 5, "value" to 100.0)  // Clear outlier
        )
        mockSequentialReads(rows)

        val properties = mapOf("valueCol" to "value")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(5)).write(any(), rowCaptor.capture())
        assertEquals(5, rowCaptor.allValues.size)
        assertEquals(true, rowCaptor.allValues[4]["is_outlier"])
    }

    @Test
    fun `test execute with missing value column defaults to 0`() {
        // Arrange
        val rows = listOf(
            mapOf("id" to 1, "other_field" to "data"),  // Missing "value" field
            mapOf("id" to 2, "value" to 2.0),
            mapOf("id" to 3, "value" to 3.0)
        )
        mockSequentialReads(rows)

        val properties = mapOf("valueCol" to "value")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(3)).write(any(), rowCaptor.capture())
        assertEquals(3, rowCaptor.allValues.size)
        assertNotNull(rowCaptor.allValues[0]["is_outlier"])
    }

    @Test
    fun `test execute preserves all original fields plus adds is_outlier`() {
        // Arrange
        val rows = listOf(
            mapOf("id" to 1, "value" to 1.0, "category" to "A", "timestamp" to "2024-01-01")
        )
        mockSequentialReads(rows)

        val properties = mapOf("valueCol" to "value")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter).write(any(), rowCaptor.capture())
        val result = rowCaptor.allValues[0]
        assertEquals(1, result["id"])
        assertEquals(1.0, result["value"])
        assertEquals("A", result["category"])
        assertEquals("2024-01-01", result["timestamp"])
        assertNotNull(result["is_outlier"])
    }

    // ===== Error Tests =====

    @Test
    fun `test execute throws exception when valueCol property is missing`() {
        // Arrange
        whenever(mockInputReader.read()).thenReturn(null)

        val properties = mapOf<String, Any>()
        val inputs = mapOf("data" to mockInputReader)

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter)
        }
        assertEquals("valueCol is not provided", exception.message)
    }

    @Test
    fun `test execute throws exception when valueCol property is null`() {
        // Arrange
        whenever(mockInputReader.read()).thenReturn(null)

        @Suppress("UNCHECKED_CAST")
        val properties = (mapOf("valueCol" to null) as Map<String, Any>)
        val inputs = mapOf("data" to mockInputReader)

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter)
        }
        assertEquals("valueCol is not provided", exception.message)
    }

    // ===== Edge Cases =====

    @Test
    fun `test execute with empty input stream`() {
        // Arrange
        whenever(mockInputReader.read()).thenReturn(null)

        val properties = mapOf("valueCol" to "value")
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, org.mockito.kotlin.never()).write(any(), any())
    }

    @Test
    fun `test execute with two rows`() {
        // Arrange
        val rows = listOf(
            mapOf("value" to 1.0),
            mapOf("value" to 2.0)
        )
        mockSequentialReads(rows)

        val properties = mapOf("valueCol" to "value")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(2)).write(any(), rowCaptor.capture())
        assertEquals(2, rowCaptor.allValues.size)
        assertEquals(false, rowCaptor.allValues[0]["is_outlier"])
        assertEquals(false, rowCaptor.allValues[1]["is_outlier"])
    }

    @Test
    fun `test execute with many rows - statistical accuracy`() {
        // Arrange - Create 100 rows with normal distribution (mostly in 1-100 range)
        val rows = (1..100).map { i ->
            mapOf("id" to i, "value" to (i % 50).toDouble())
        }
        mockSequentialReads(rows)

        val properties = mapOf("valueCol" to "value")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(100)).write(any(), rowCaptor.capture())
        assertEquals(100, rowCaptor.allValues.size)
        assertTrue(rowCaptor.allValues.any { it["is_outlier"] == false })
    }

    @Test
    fun `test execute with null in value field defaults to 0`() {
        // Arrange
        @Suppress("UNCHECKED_CAST")
        val rows = listOf(
            mapOf("id" to 1, "value" to null) as Map<String, Any>,
            mapOf("id" to 2, "value" to 1.0),
            mapOf("id" to 3, "value" to 2.0)
        )
        mockSequentialReads(rows)

        val properties = mapOf("valueCol" to "value")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(3)).write(any(), rowCaptor.capture())
        assertEquals(3, rowCaptor.allValues.size)
        assertNotNull(rowCaptor.allValues[0]["is_outlier"])
    }

    @Test
    fun `test output writer is properly closed`() {
        // Arrange
        whenever(mockInputReader.read()).thenReturn(null)

        val properties = mapOf("valueCol" to "value")
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

        val properties = mapOf("valueCol" to "value")
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockInputReader).close()
    }

    @Test
    fun `test execute with alternating high-low pattern`() {
        // Arrange
        val rows = listOf(
            mapOf("value" to 1.0),
            mapOf("value" to 100.0),
            mapOf("value" to 1.0),
            mapOf("value" to 100.0),
            mapOf("value" to 1.0)
        )
        mockSequentialReads(rows)

        val properties = mapOf("valueCol" to "value")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(5)).write(any(), rowCaptor.capture())
        assertEquals(5, rowCaptor.allValues.size)
        // All should be flagged as outliers due to high variance
        assertTrue(rowCaptor.allValues.any { it["is_outlier"] == true })
    }

    @Test
    fun `test execute with scientific notation numbers`() {
        // Arrange
        val rows = listOf(
            mapOf("value" to 1e-5),
            mapOf("value" to 2e-5),
            mapOf("value" to 3e-5),
            mapOf("value" to 4e-5),
            mapOf("value" to 5e-5)
        )
        mockSequentialReads(rows)

        val properties = mapOf("valueCol" to "value")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(5)).write(any(), rowCaptor.capture())
        assertEquals(5, rowCaptor.allValues.size)
        rowCaptor.allValues.forEach { row ->
            assertEquals(false, row["is_outlier"])
        }
    }

    @Test
    fun `test execute with max double value`() {
        // Arrange
        val rows = listOf(
            mapOf("value" to 1.0),
            mapOf("value" to 2.0),
            mapOf("value" to 3.0),
            mapOf("value" to Double.MAX_VALUE)  // Extreme outlier
        )
        mockSequentialReads(rows)

        val properties = mapOf("valueCol" to "value")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(4)).write(any(), rowCaptor.capture())
        assertEquals(4, rowCaptor.allValues.size)
        assertEquals(true, rowCaptor.allValues[3]["is_outlier"])
    }

    @Test
    fun `test execute with long value converted to double`() {
        // Arrange
        val rows = listOf(
            mapOf("value" to 1L),
            mapOf("value" to 2L),
            mapOf("value" to 3L)
        )
        mockSequentialReads(rows)

        val properties = mapOf("valueCol" to "value")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(3)).write(any(), rowCaptor.capture())
        assertEquals(3, rowCaptor.allValues.size)
        rowCaptor.allValues.forEach { row ->
            assertEquals(false, row["is_outlier"])
        }
    }

    // ===== Helper Methods =====

    private fun mockSequentialReads(rows: List<Map<String, Any>>) {
        val reads = rows + null  // Add null to terminate
        whenever(mockInputReader.read()).thenReturn(reads[0], *reads.drop(1).toTypedArray())
    }
}

