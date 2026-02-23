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

class TimeWindowAggregatorNodeModelTest {

    private lateinit var nodeModel: TimeWindowAggregatorNodeModel
    private lateinit var mockInputReader: NodeInputReader
    private lateinit var mockOutputWriter: NodeOutputWriter
    private lateinit var mockPortWriter: NodeOutputWriter.OutputPortWriter

    @BeforeEach
    fun setUp() {
        nodeModel = TimeWindowAggregatorNodeModel()
        mockInputReader = mock()
        mockOutputWriter = mock()
        mockPortWriter = mock()
        whenever(mockOutputWriter.createOutputPortWriter("data")).thenReturn(mockPortWriter)
    }

    // ===== Configuration Tests =====

    @Test
    fun `test node metadata is properly configured`() {
        val metadata = nodeModel.metadata
        assertEquals("com.continuum.base.node.TimeWindowAggregatorNodeModel", metadata.id)
        assertEquals("Aggregates values into time windows, summing by window buckets", metadata.description)
        assertEquals("Time Window Aggregator", metadata.title)
        assertEquals("Group and sum by time windows", metadata.subTitle)
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
        assertEquals("aggregated table", dataPort.name)
    }

    @Test
    fun `test categories are correctly defined`() {
        val categories = nodeModel.categories
        assertEquals(1, categories.size)
        assertEquals("Aggregation & Time Series", categories[0])
    }

    @Test
    fun `test properties schema is valid`() {
        val schema = nodeModel.propertiesSchema
        assertNotNull(schema)
        assertEquals("object", schema["type"])
        assertTrue(schema.containsKey("properties"))
        assertTrue(schema.containsKey("required"))

        val properties = schema["properties"] as Map<*, *>
        assertTrue(properties.containsKey("timeCol"))
        assertTrue(properties.containsKey("valueCol"))
        assertTrue(properties.containsKey("windowSize"))

        val required = schema["required"] as List<*>
        assertTrue(required.contains("timeCol"))
        assertTrue(required.contains("valueCol"))
        assertTrue(required.contains("windowSize"))
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
        assertEquals("time", defaultProperties["timeCol"])
        assertEquals("value", defaultProperties["valueCol"])
        assertEquals(5, defaultProperties["windowSize"])
    }

    // ===== Success Tests =====

    @Test
    fun `test basic 5-minute window aggregation`() {
        // Arrange - 3 rows in 2 different windows
        val rows = listOf(
            mapOf("time" to "2026-02-21 14:03:00", "value" to 10),
            mapOf("time" to "2026-02-21 14:04:00", "value" to 15),
            mapOf("time" to "2026-02-21 14:07:00", "value" to 20)
        )
        mockSequentialReads(rows)

        val properties = mapOf(
            "timeCol" to "time",
            "valueCol" to "value",
            "windowSize" to 5
        )
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(2)).write(any(), rowCaptor.capture())
        val writtenRows = rowCaptor.allValues

        // First window: 14:00:00 (10 + 15 = 25)
        assertEquals("2026-02-21 14:00:00", writtenRows[0]["window_start"])
        assertEquals(25.0, writtenRows[0]["sum_value"])

        // Second window: 14:05:00 (20)
        assertEquals("2026-02-21 14:05:00", writtenRows[1]["window_start"])
        assertEquals(20.0, writtenRows[1]["sum_value"])
    }

    @Test
    fun `test window flooring algorithm correctness - CRITICAL BUG TEST`() {
        // This tests the FIXED algorithm (was broken before)
        // 14:03:45 with 5-min window should → 14:00:00
        // 14:07:12 with 5-min window should → 14:05:00
        // 14:59:59 with 5-min window should → 14:55:00

        val rows = listOf(
            mapOf("time" to "2026-02-21 14:03:45", "value" to 1),
            mapOf("time" to "2026-02-21 14:07:12", "value" to 2),
            mapOf("time" to "2026-02-21 14:59:59", "value" to 3)
        )
        mockSequentialReads(rows)

        val properties = mapOf(
            "timeCol" to "time",
            "valueCol" to "value",
            "windowSize" to 5
        )
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(3)).write(any(), rowCaptor.capture())
        val writtenRows = rowCaptor.allValues

        assertEquals("2026-02-21 14:00:00", writtenRows[0]["window_start"])
        assertEquals(1.0, writtenRows[0]["sum_value"])

        assertEquals("2026-02-21 14:05:00", writtenRows[1]["window_start"])
        assertEquals(2.0, writtenRows[1]["sum_value"])

        assertEquals("2026-02-21 14:55:00", writtenRows[2]["window_start"])
        assertEquals(3.0, writtenRows[2]["sum_value"])
    }

    @Test
    fun `test hour boundary behavior`() {
        // Arrange - values at the exact hour boundary
        val rows = listOf(
            mapOf("time" to "2026-02-21 14:00:00", "value" to 10),
            mapOf("time" to "2026-02-21 15:00:00", "value" to 20),
            mapOf("time" to "2026-02-21 16:00:00", "value" to 30)
        )
        mockSequentialReads(rows)

        val properties = mapOf(
            "timeCol" to "time",
            "valueCol" to "value",
            "windowSize" to 5
        )
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(3)).write(any(), rowCaptor.capture())
        val writtenRows = rowCaptor.allValues

        assertEquals("2026-02-21 14:00:00", writtenRows[0]["window_start"])
        assertEquals(10.0, writtenRows[0]["sum_value"])

        assertEquals("2026-02-21 15:00:00", writtenRows[1]["window_start"])
        assertEquals(20.0, writtenRows[1]["sum_value"])

        assertEquals("2026-02-21 16:00:00", writtenRows[2]["window_start"])
        assertEquals(30.0, writtenRows[2]["sum_value"])
    }

    @Test
    fun `test multiple values in same window`() {
        // Arrange - 5 rows all in the same 5-minute window
        val rows = listOf(
            mapOf("time" to "2026-02-21 14:00:00", "value" to 1),
            mapOf("time" to "2026-02-21 14:01:00", "value" to 2),
            mapOf("time" to "2026-02-21 14:02:00", "value" to 3),
            mapOf("time" to "2026-02-21 14:03:00", "value" to 4),
            mapOf("time" to "2026-02-21 14:04:59", "value" to 5)
        )
        mockSequentialReads(rows)

        val properties = mapOf(
            "timeCol" to "time",
            "valueCol" to "value",
            "windowSize" to 5
        )
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val writtenRows = rowCaptor.allValues

        assertEquals("2026-02-21 14:00:00", writtenRows[0]["window_start"])
        assertEquals(15.0, writtenRows[0]["sum_value"]) // 1+2+3+4+5 = 15
    }

    @Test
    fun `test 10-minute window size`() {
        // Arrange
        val rows = listOf(
            mapOf("time" to "2026-02-21 14:05:00", "value" to 10),
            mapOf("time" to "2026-02-21 14:08:00", "value" to 20),
            mapOf("time" to "2026-02-21 14:15:00", "value" to 30)
        )
        mockSequentialReads(rows)

        val properties = mapOf(
            "timeCol" to "time",
            "valueCol" to "value",
            "windowSize" to 10
        )
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(2)).write(any(), rowCaptor.capture())
        val writtenRows = rowCaptor.allValues

        // First window: 14:00:00 (10 + 20 = 30)
        assertEquals("2026-02-21 14:00:00", writtenRows[0]["window_start"])
        assertEquals(30.0, writtenRows[0]["sum_value"])

        // Second window: 14:10:00 (30)
        assertEquals("2026-02-21 14:10:00", writtenRows[1]["window_start"])
        assertEquals(30.0, writtenRows[1]["sum_value"])
    }

    @Test
    fun `test 1-minute window size`() {
        // Arrange
        val rows = listOf(
            mapOf("time" to "2026-02-21 14:00:00", "value" to 10),
            mapOf("time" to "2026-02-21 14:00:30", "value" to 5),
            mapOf("time" to "2026-02-21 14:01:00", "value" to 20),
            mapOf("time" to "2026-02-21 14:02:00", "value" to 30)
        )
        mockSequentialReads(rows)

        val properties = mapOf(
            "timeCol" to "time",
            "valueCol" to "value",
            "windowSize" to 1
        )
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(3)).write(any(), rowCaptor.capture())
        val writtenRows = rowCaptor.allValues

        // 14:00 window (10 + 5 = 15)
        assertEquals("2026-02-21 14:00:00", writtenRows[0]["window_start"])
        assertEquals(15.0, writtenRows[0]["sum_value"])

        // 14:01 window (20)
        assertEquals("2026-02-21 14:01:00", writtenRows[1]["window_start"])
        assertEquals(20.0, writtenRows[1]["sum_value"])

        // 14:02 window (30)
        assertEquals("2026-02-21 14:02:00", writtenRows[2]["window_start"])
        assertEquals(30.0, writtenRows[2]["sum_value"])
    }

    @Test
    fun `test sorted output verification`() {
        // Arrange - rows in reverse chronological order
        val rows = listOf(
            mapOf("time" to "2026-02-21 14:20:00", "value" to 30),
            mapOf("time" to "2026-02-21 14:10:00", "value" to 20),
            mapOf("time" to "2026-02-21 14:00:00", "value" to 10)
        )
        mockSequentialReads(rows)

        val properties = mapOf(
            "timeCol" to "time",
            "valueCol" to "value",
            "windowSize" to 5
        )
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(3)).write(any(), rowCaptor.capture())
        val writtenRows = rowCaptor.allValues

        // Output should be sorted by window_start
        assertEquals("2026-02-21 14:00:00", writtenRows[0]["window_start"])
        assertEquals("2026-02-21 14:10:00", writtenRows[1]["window_start"])
        assertEquals("2026-02-21 14:20:00", writtenRows[2]["window_start"])
    }

    @Test
    fun `test with decimal values`() {
        // Arrange
        val rows = listOf(
            mapOf("time" to "2026-02-21 14:00:00", "value" to 10.5),
            mapOf("time" to "2026-02-21 14:01:00", "value" to 20.7),
            mapOf("time" to "2026-02-21 14:06:00", "value" to 15.3)
        )
        mockSequentialReads(rows)

        val properties = mapOf(
            "timeCol" to "time",
            "valueCol" to "value",
            "windowSize" to 5
        )
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(2)).write(any(), rowCaptor.capture())
        val writtenRows = rowCaptor.allValues

        assertEquals("2026-02-21 14:00:00", writtenRows[0]["window_start"])
        assertEquals(31.2, writtenRows[0]["sum_value"])

        assertEquals("2026-02-21 14:05:00", writtenRows[1]["window_start"])
        assertEquals(15.3, writtenRows[1]["sum_value"])
    }

    @Test
    fun `test with integer values`() {
        // Arrange
        val rows = listOf(
            mapOf("time" to "2026-02-21 14:00:00", "value" to 100),
            mapOf("time" to "2026-02-21 14:02:00", "value" to 200)
        )
        mockSequentialReads(rows)

        val properties = mapOf(
            "timeCol" to "time",
            "valueCol" to "value",
            "windowSize" to 5
        )
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val writtenRows = rowCaptor.allValues

        assertEquals("2026-02-21 14:00:00", writtenRows[0]["window_start"])
        assertEquals(300.0, writtenRows[0]["sum_value"])
    }

    @Test
    fun `test with custom column names`() {
        // Arrange
        val rows = listOf(
            mapOf("timestamp" to "2026-02-21 14:00:00", "temperature" to 25.5),
            mapOf("timestamp" to "2026-02-21 14:03:00", "temperature" to 26.0)
        )
        mockSequentialReads(rows)

        val properties = mapOf(
            "timeCol" to "timestamp",
            "valueCol" to "temperature",
            "windowSize" to 5
        )
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val writtenRows = rowCaptor.allValues

        assertEquals("2026-02-21 14:00:00", writtenRows[0]["window_start"])
        assertEquals(51.5, writtenRows[0]["sum_value"])
    }

    @Test
    fun `test row indices are sequential`() {
        // Arrange
        val rows = listOf(
            mapOf("time" to "2026-02-21 14:00:00", "value" to 1),
            mapOf("time" to "2026-02-21 14:05:00", "value" to 2),
            mapOf("time" to "2026-02-21 14:10:00", "value" to 3)
        )
        mockSequentialReads(rows)

        val properties = mapOf(
            "timeCol" to "time",
            "valueCol" to "value",
            "windowSize" to 5
        )
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

    // ===== Edge Cases =====

    @Test
    fun `test with empty input stream`() {
        // Arrange
        whenever(mockInputReader.read()).thenReturn(null)

        val properties = mapOf(
            "timeCol" to "time",
            "valueCol" to "value",
            "windowSize" to 5
        )
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, org.mockito.kotlin.never()).write(any(), any())
    }

    @Test
    fun `test with single row`() {
        // Arrange
        val rows = listOf(
            mapOf("time" to "2026-02-21 14:03:00", "value" to 42)
        )
        mockSequentialReads(rows)

        val properties = mapOf(
            "timeCol" to "time",
            "valueCol" to "value",
            "windowSize" to 5
        )
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val writtenRows = rowCaptor.allValues

        assertEquals("2026-02-21 14:00:00", writtenRows[0]["window_start"])
        assertEquals(42.0, writtenRows[0]["sum_value"])
    }

    @Test
    fun `test with zero value`() {
        // Arrange
        val rows = listOf(
            mapOf("time" to "2026-02-21 14:00:00", "value" to 0),
            mapOf("time" to "2026-02-21 14:01:00", "value" to 10)
        )
        mockSequentialReads(rows)

        val properties = mapOf(
            "timeCol" to "time",
            "valueCol" to "value",
            "windowSize" to 5
        )
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val writtenRows = rowCaptor.allValues

        assertEquals("2026-02-21 14:00:00", writtenRows[0]["window_start"])
        assertEquals(10.0, writtenRows[0]["sum_value"])
    }

    @Test
    fun `test with negative values`() {
        // Arrange
        val rows = listOf(
            mapOf("time" to "2026-02-21 14:00:00", "value" to 100),
            mapOf("time" to "2026-02-21 14:01:00", "value" to -30)
        )
        mockSequentialReads(rows)

        val properties = mapOf(
            "timeCol" to "time",
            "valueCol" to "value",
            "windowSize" to 5
        )
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val writtenRows = rowCaptor.allValues

        assertEquals("2026-02-21 14:00:00", writtenRows[0]["window_start"])
        assertEquals(70.0, writtenRows[0]["sum_value"])
    }

    @Test
    fun `test with empty timestamp string - row skipped`() {
        // Arrange
        val rows = listOf(
            mapOf("time" to "", "value" to 10),
            mapOf("time" to "2026-02-21 14:00:00", "value" to 20)
        )
        mockSequentialReads(rows)

        val properties = mapOf(
            "timeCol" to "time",
            "valueCol" to "value",
            "windowSize" to 5
        )
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert - only 1 row written (empty timestamp skipped)
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val writtenRows = rowCaptor.allValues

        assertEquals("2026-02-21 14:00:00", writtenRows[0]["window_start"])
        assertEquals(20.0, writtenRows[0]["sum_value"])
    }

    @Test
    fun `test with null value - defaults to zero`() {
        // Arrange
        val rows = listOf(
            mapOf("time" to "2026-02-21 14:00:00", "value" to null),
            mapOf("time" to "2026-02-21 14:01:00", "value" to 20)
        )
        mockSequentialReads(rows)

        val properties = mapOf(
            "timeCol" to "time",
            "valueCol" to "value",
            "windowSize" to 5
        )
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val writtenRows = rowCaptor.allValues

        assertEquals("2026-02-21 14:00:00", writtenRows[0]["window_start"])
        assertEquals(20.0, writtenRows[0]["sum_value"]) // null treated as 0
    }

    // ===== Error Tests =====

    @Test
    fun `test execute throws exception when timeCol property is missing`() {
        // Arrange
        whenever(mockInputReader.read()).thenReturn(null)

        val properties = mapOf(
            "valueCol" to "value",
            "windowSize" to 5
        )
        val inputs = mapOf("data" to mockInputReader)

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter)
        }
        assertEquals("timeCol is not provided", exception.message)
    }

    @Test
    fun `test execute throws exception when valueCol property is missing`() {
        // Arrange
        whenever(mockInputReader.read()).thenReturn(null)

        val properties = mapOf(
            "timeCol" to "time",
            "windowSize" to 5
        )
        val inputs = mapOf("data" to mockInputReader)

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter)
        }
        assertEquals("valueCol is not provided", exception.message)
    }

    @Test
    fun `test execute throws exception when windowSize property is missing`() {
        // Arrange
        whenever(mockInputReader.read()).thenReturn(null)

        val properties = mapOf(
            "timeCol" to "time",
            "valueCol" to "value"
        )
        val inputs = mapOf("data" to mockInputReader)

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter)
        }
        assertEquals("windowSize is not provided", exception.message)
    }

    @Test
    fun `test execute throws exception when properties map is null`() {
        // Arrange
        whenever(mockInputReader.read()).thenReturn(null)

        val inputs = mapOf("data" to mockInputReader)

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(null, inputs, mockOutputWriter)
        }
        assertTrue(exception.message?.contains("is not provided") ?: false)
    }

    @Test
    fun `test with invalid timestamp format - row skipped with warning`() {
        // Arrange
        val rows = listOf(
            mapOf("time" to "invalid-timestamp", "value" to 10),
            mapOf("time" to "2026-02-21 14:00:00", "value" to 20)
        )
        mockSequentialReads(rows)

        val properties = mapOf(
            "timeCol" to "time",
            "valueCol" to "value",
            "windowSize" to 5
        )
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert - only 1 row written (invalid timestamp skipped)
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val writtenRows = rowCaptor.allValues

        assertEquals("2026-02-21 14:00:00", writtenRows[0]["window_start"])
        assertEquals(20.0, writtenRows[0]["sum_value"])
    }

    @Test
    fun `test with missing time column in row`() {
        // Arrange - row missing the time column
        val rows = listOf(
            mapOf("value" to 10),
            mapOf("time" to "2026-02-21 14:00:00", "value" to 20)
        )
        mockSequentialReads(rows)

        val properties = mapOf(
            "timeCol" to "time",
            "valueCol" to "value",
            "windowSize" to 5
        )
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert - only 1 row written (missing column treated as empty string)
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val writtenRows = rowCaptor.allValues

        assertEquals("2026-02-21 14:00:00", writtenRows[0]["window_start"])
        assertEquals(20.0, writtenRows[0]["sum_value"])
    }

    @Test
    fun `test with missing value column in row - defaults to zero`() {
        // Arrange - row missing the value column
        val rows = listOf(
            mapOf("time" to "2026-02-21 14:00:00"),
            mapOf("time" to "2026-02-21 14:01:00", "value" to 30)
        )
        mockSequentialReads(rows)

        val properties = mapOf(
            "timeCol" to "time",
            "valueCol" to "value",
            "windowSize" to 5
        )
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val writtenRows = rowCaptor.allValues

        assertEquals("2026-02-21 14:00:00", writtenRows[0]["window_start"])
        assertEquals(30.0, writtenRows[0]["sum_value"]) // missing value treated as 0
    }

    @Test
    fun `test with non-numeric value - defaults to zero`() {
        // Arrange
        val rows = listOf(
            mapOf("time" to "2026-02-21 14:00:00", "value" to "not-a-number"),
            mapOf("time" to "2026-02-21 14:01:00", "value" to 20)
        )
        mockSequentialReads(rows)

        val properties = mapOf(
            "timeCol" to "time",
            "valueCol" to "value",
            "windowSize" to 5
        )
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val writtenRows = rowCaptor.allValues

        assertEquals("2026-02-21 14:00:00", writtenRows[0]["window_start"])
        assertEquals(20.0, writtenRows[0]["sum_value"]) // non-numeric treated as 0
    }

    @Test
    fun `test output writer is properly closed`() {
        // Arrange
        val rows = listOf(
            mapOf("time" to "2026-02-21 14:00:00", "value" to 10)
        )
        mockSequentialReads(rows)

        val properties = mapOf(
            "timeCol" to "time",
            "valueCol" to "value",
            "windowSize" to 5
        )
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter).close()
    }

    @Test
    fun `test input reader is properly closed`() {
        // Arrange
        val rows = listOf(
            mapOf("time" to "2026-02-21 14:00:00", "value" to 10)
        )
        mockSequentialReads(rows)

        val properties = mapOf(
            "timeCol" to "time",
            "valueCol" to "value",
            "windowSize" to 5
        )
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockInputReader).close()
    }

    @Test
    fun `test windowSize as Long type`() {
        // Arrange
        val rows = listOf(
            mapOf("time" to "2026-02-21 14:00:00", "value" to 10)
        )
        mockSequentialReads(rows)

        val properties = mapOf(
            "timeCol" to "time",
            "valueCol" to "value",
            "windowSize" to 5L
        )
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        assertEquals(1, rowCaptor.allValues.size)
    }

    @Test
    fun `test windowSize as Double type`() {
        // Arrange
        val rows = listOf(
            mapOf("time" to "2026-02-21 14:00:00", "value" to 10)
        )
        mockSequentialReads(rows)

        val properties = mapOf(
            "timeCol" to "time",
            "valueCol" to "value",
            "windowSize" to 5.0
        )
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        assertEquals(1, rowCaptor.allValues.size)
    }

    // ===== Helper Methods =====

    /**
     * Mocks the input reader to support streaming through data once.
     *
     * @param rows The rows to return when read() is called
     */
    private fun mockSequentialReads(rows: List<Map<String, Any?>>) {
        val rowsWithNull = rows + null
        var callCount = 0

        whenever(mockInputReader.read()).thenAnswer {
            val result = rowsWithNull[callCount]
            callCount++
            result
        }
    }
}
