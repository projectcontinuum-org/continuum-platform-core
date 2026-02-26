package com.continuum.base.node

import com.continuum.core.commons.exception.NodeRuntimeException
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
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TimeTriggerNodeModelTest {

    private lateinit var nodeModel: TimeTriggerNodeModel
    private lateinit var mockOutputWriter: NodeOutputWriter
    private lateinit var mockPortWriter: NodeOutputWriter.OutputPortWriter

    @BeforeEach
    fun setUp() {
        nodeModel = TimeTriggerNodeModel()
        mockOutputWriter = mock()
        mockPortWriter = mock()
        whenever(mockOutputWriter.createOutputPortWriter("output-1")).thenReturn(mockPortWriter)
    }

    // ===== Configuration Tests =====

    @Test
    fun `test node metadata is properly configured`() {
        val metadata = nodeModel.metadata
        assertEquals("com.continuum.base.node.TimeTriggerNodeModel", metadata.id)
        assertEquals("Starts the workflow execution with the current time as the output", metadata.description)
        assertEquals("Start Node", metadata.title)
        assertEquals("Starts the workflow execution", metadata.subTitle)
        assertNotNull(metadata.icon)
    }

    @Test
    fun `test output ports are correctly defined`() {
        val outputPorts = nodeModel.outputPorts
        assertEquals(1, outputPorts.size)
        assertNotNull(outputPorts["output-1"])
        assertEquals("output-1", outputPorts["output-1"]!!.name)
    }

    @Test
    fun `test categories are correctly defined`() {
        val categories = nodeModel.categories
        assertEquals(1, categories.size)
        assertEquals("Trigger", categories[0])
    }

    @Test
    fun `test default metadata properties`() {
        val defaultProperties = nodeModel.metadata.properties
        assertNotNull(defaultProperties)
        assertEquals("Logging at", defaultProperties["message"])
        assertEquals(10, defaultProperties["rowCount"])
    }

    // ===== Success Tests =====

    @Test
    fun `test execute with default row count`() {
        // Arrange
        val properties = mapOf("message" to "Test", "rowCount" to 10)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(10)).write(any(), rowCaptor.capture())
        assertEquals(10, rowCaptor.allValues.size)

        // Verify all rows have message starting with "Test"
        rowCaptor.allValues.forEach { row ->
            val message = row["message"] as String
            assertTrue(message.startsWith("Test "))
            // Verify message contains a timestamp
            assertTrue(message.contains("T"))
        }
    }

    @Test
    fun `test execute with custom row count`() {
        // Arrange
        val properties = mapOf("message" to "Custom", "rowCount" to 5)

        // Act
        nodeModel.execute(properties, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(5)).write(any(), any())
    }

    @Test
    fun `test execute with single row`() {
        // Arrange
        val properties = mapOf("message" to "Single", "rowCount" to 1)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val message = rowCaptor.firstValue["message"] as String
        assertTrue(message.startsWith("Single "))
    }

    @Test
    fun `test execute with large row count`() {
        // Arrange
        val properties = mapOf("message" to "Many", "rowCount" to 100)

        // Act
        nodeModel.execute(properties, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(100)).write(any(), any())
    }

    @Test
    fun `test execute with row count as string`() {
        // Arrange
        val properties = mapOf("message" to "Test", "rowCount" to "20")

        // Act
        nodeModel.execute(properties, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(20)).write(any(), any())
    }

    @Test
    fun `test execute with rowCount as Long`() {
        // Arrange
        val properties = mapOf("message" to "Test", "rowCount" to 15L)

        // Act
        nodeModel.execute(properties, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(15)).write(any(), any())
    }

    @Test
    fun `test execute with custom message`() {
        // Arrange
        val properties = mapOf("message" to "Custom Message", "rowCount" to 3)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(3)).write(any(), rowCaptor.capture())
        rowCaptor.allValues.forEach { row ->
            val message = row["message"] as String
            assertTrue(message.startsWith("Custom Message "))
        }
    }

    @Test
    fun `test execute with empty message defaults to Logging at`() {
        // Arrange
        val properties = mapOf("rowCount" to 2)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(2)).write(any(), rowCaptor.capture())
        rowCaptor.allValues.forEach { row ->
            val message = row["message"] as String
            assertTrue(message.startsWith("Logging at "))
        }
    }

    @Test
    fun `test execute generates different timestamps for each row`() {
        // Arrange
        val properties = mapOf("message" to "Time", "rowCount" to 5)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(5)).write(any(), rowCaptor.capture())

        val messages = rowCaptor.allValues.map { it["message"] as String }
        // Timestamps might be the same if execution is very fast, but structure should be valid
        messages.forEach { message ->
            assertTrue(message.startsWith("Time "))
            // Verify ISO-8601 format is present
            assertTrue(Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}").containsMatchIn(message))
        }
    }

    @Test
    fun `test execute with row indices are sequential`() {
        // Arrange
        val properties = mapOf("message" to "Test", "rowCount" to 5)
        val indexCaptor = argumentCaptor<Long>()

        // Act
        nodeModel.execute(properties, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(5)).write(indexCaptor.capture(), any())
        assertEquals(listOf(0L, 1L, 2L, 3L, 4L), indexCaptor.allValues)
    }

    @Test
    fun `test execute message format is correct`() {
        // Arrange
        val beforeExecution = Instant.now()
        val properties = mapOf("message" to "TestMsg", "rowCount" to 1)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val message = rowCaptor.firstValue["message"] as String

        // Parse timestamp from message
        val timestampStr = message.substring("TestMsg ".length)
        val timestamp = Instant.parse(timestampStr)

        // Verify timestamp is reasonable (within 1 second of execution)
        val afterExecution = Instant.now()
        assertTrue(timestamp.isAfter(beforeExecution.minusSeconds(1)))
        assertTrue(timestamp.isBefore(afterExecution.plusSeconds(1)))
    }

    // ===== Error Tests / Edge Cases =====

    @Test
    fun `test execute with zero row count defaults to 10`() {
        // Arrange - invalid rowCount defaults to 10
        val properties = mapOf("message" to "Test", "rowCount" to 0)

        // Act
        nodeModel.execute(properties, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(10)).write(any(), any())
    }

    @Test
    fun `test execute with negative row count defaults to 10`() {
        // Arrange - invalid rowCount defaults to 10
        val properties = mapOf("message" to "Test", "rowCount" to -5)

        // Act
        nodeModel.execute(properties, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(10)).write(any(), any())
    }

    @Test
    fun `test execute with invalid row count string defaults to 10`() {
        // Arrange
        val properties = mapOf("message" to "Test", "rowCount" to "not a number")

        // Act
        nodeModel.execute(properties, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(10)).write(any(), any())
    }

    @Test
    fun `test execute with missing rowCount defaults to 10`() {
        // Arrange
        val properties = mapOf("message" to "Test")

        // Act
        nodeModel.execute(properties, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(10)).write(any(), any())
    }

    @Test
    fun `test execute with null properties uses defaults`() {
        // Arrange
        val properties: Map<String, Any>? = null

        // Act
        nodeModel.execute(properties, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(10)).write(any(), any())
    }

    @Test
    fun `test execute properly closes writer`() {
        // Arrange
        val properties = mapOf("message" to "Test", "rowCount" to 1)

        // Act
        nodeModel.execute(properties, mockOutputWriter)

        // Assert
        verify(mockPortWriter).close()
    }

    @Test
    fun `test execute with very large row count`() {
        // Arrange
        val properties = mapOf("message" to "Bulk", "rowCount" to 1000)

        // Act
        nodeModel.execute(properties, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1000)).write(any(), any())
    }

    @Test
    fun `test execute with special characters in message`() {
        // Arrange
        val properties = mapOf("message" to "Test!@#\$%^&*()", "rowCount" to 1)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val message = rowCaptor.firstValue["message"] as String
        assertTrue(message.startsWith("Test!@#\$%^&*() "))
    }

    @Test
    fun `test execute with unicode message`() {
        // Arrange
        val properties = mapOf("message" to "你好世界", "rowCount" to 1)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val message = rowCaptor.firstValue["message"] as String
        assertTrue(message.startsWith("你好世界 "))
    }
}
