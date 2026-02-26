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

class TextNormalizerNodeModelTest {

    private lateinit var nodeModel: TextNormalizerNodeModel
    private lateinit var mockInputReader: NodeInputReader
    private lateinit var mockOutputWriter: NodeOutputWriter
    private lateinit var mockPortWriter: NodeOutputWriter.OutputPortWriter

    @BeforeEach
    fun setUp() {
        nodeModel = TextNormalizerNodeModel()
        mockInputReader = mock()
        mockOutputWriter = mock()
        mockPortWriter = mock()
        whenever(mockOutputWriter.createOutputPortWriter("data")).thenReturn(mockPortWriter)
    }

    // ===== Configuration Tests =====

    @Test
    fun `test node metadata is properly configured`() {
        val metadata = nodeModel.metadata
        assertEquals("com.continuum.base.node.TextNormalizerNodeModel", metadata.id)
        assertEquals("Normalizes text by trimming, lowercasing, and removing non-alphanumeric characters", metadata.description)
        assertEquals("Text Normalizer", metadata.title)
        assertEquals("Clean and normalize text", metadata.subTitle)
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
        assertEquals("normalized table", outputPorts["data"]!!.name)
    }

    @Test
    fun `test categories are correctly defined`() {
        val categories = nodeModel.categories
        assertEquals(1, categories.size)
        assertEquals("String & Text", categories[0])
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
    fun `test execute normalizes simple text`() {
        // Arrange
        val rows = listOf(
            mapOf("text" to "Hello World")
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("inputCol" to "text", "outputCol" to "clean")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        assertEquals("hello world", rowCaptor.firstValue["clean"])
    }

    @Test
    fun `test execute removes special characters`() {
        // Arrange
        val rows = listOf(
            mapOf("text" to "Hello! @World# \$123%")
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("inputCol" to "text", "outputCol" to "clean")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        assertEquals("hello world 123", rowCaptor.firstValue["clean"])
    }

    @Test
    fun `test execute trims whitespace`() {
        // Arrange
        val rows = listOf(
            mapOf("text" to "   Hello World   ")
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("inputCol" to "text", "outputCol" to "clean")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        assertEquals("hello world", rowCaptor.firstValue["clean"])
    }

    @Test
    fun `test execute converts to lowercase`() {
        // Arrange
        val rows = listOf(
            mapOf("text" to "HELLO WORLD")
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("inputCol" to "text", "outputCol" to "clean")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        assertEquals("hello world", rowCaptor.firstValue["clean"])
    }

    @Test
    fun `test execute preserves numbers`() {
        // Arrange
        val rows = listOf(
            mapOf("text" to "Test123")
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("inputCol" to "text", "outputCol" to "clean")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        assertEquals("test123", rowCaptor.firstValue["clean"])
    }

    @Test
    fun `test execute preserves spaces between words`() {
        // Arrange
        val rows = listOf(
            mapOf("text" to "Hello Beautiful World")
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("inputCol" to "text", "outputCol" to "clean")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        assertEquals("hello beautiful world", rowCaptor.firstValue["clean"])
    }

    @Test
    fun `test execute handles empty string`() {
        // Arrange
        val rows = listOf(
            mapOf("text" to "")
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("inputCol" to "text", "outputCol" to "clean")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        assertEquals("", rowCaptor.firstValue["clean"])
    }

    @Test
    fun `test execute handles only special characters`() {
        // Arrange
        val rows = listOf(
            mapOf("text" to "!@#\$%^&*()")
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("inputCol" to "text", "outputCol" to "clean")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        assertEquals("", rowCaptor.firstValue["clean"])
    }

    @Test
    fun `test execute handles only whitespace`() {
        // Arrange
        val rows = listOf(
            mapOf("text" to "     ")
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("inputCol" to "text", "outputCol" to "clean")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        assertEquals("", rowCaptor.firstValue["clean"])
    }

    @Test
    fun `test execute handles missing input column`() {
        // Arrange
        val rows = listOf(
            mapOf("otherColumn" to "value")
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("inputCol" to "missingColumn", "outputCol" to "clean")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        assertEquals("", rowCaptor.firstValue["clean"])
    }

    @Test
    fun `test execute normalizes multiple rows`() {
        // Arrange
        val rows = listOf(
            mapOf("text" to "Hello!"),
            mapOf("text" to "World?"),
            mapOf("text" to "Test123!")
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("inputCol" to "text", "outputCol" to "clean")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(3)).write(any(), rowCaptor.capture())
        assertEquals("hello", rowCaptor.allValues[0]["clean"])
        assertEquals("world", rowCaptor.allValues[1]["clean"])
        assertEquals("test123", rowCaptor.allValues[2]["clean"])
    }

    @Test
    fun `test execute preserves original columns`() {
        // Arrange
        val rows = listOf(
            mapOf("text" to "Hello!", "id" to 1, "name" to "Alice")
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("inputCol" to "text", "outputCol" to "clean")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val result = rowCaptor.firstValue
        assertEquals("Hello!", result["text"])
        assertEquals(1, result["id"])
        assertEquals("Alice", result["name"])
        assertEquals("hello", result["clean"])
    }

    @Test
    fun `test execute handles numbers in input column`() {
        // Arrange
        val rows = listOf(
            mapOf("text" to 12345)
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("inputCol" to "text", "outputCol" to "clean")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        assertEquals("12345", rowCaptor.firstValue["clean"])
    }

    @Test
    fun `test execute with row indices are sequential`() {
        // Arrange
        val rows = listOf(
            mapOf("text" to "a"),
            mapOf("text" to "b"),
            mapOf("text" to "c")
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("inputCol" to "text", "outputCol" to "clean")
        val inputs = mapOf("data" to mockInputReader)
        val indexCaptor = argumentCaptor<Long>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(3)).write(indexCaptor.capture(), any())
        assertEquals(listOf(0L, 1L, 2L), indexCaptor.allValues)
    }

    @Test
    fun `test execute handles accented characters`() {
        // Arrange
        val rows = listOf(
            mapOf("text" to "Café")
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("inputCol" to "text", "outputCol" to "clean")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        // Accented characters should be removed (not alphanumeric)
        assertEquals("caf", rowCaptor.firstValue["clean"])
    }

    @Test
    fun `test execute with mixed case and punctuation`() {
        // Arrange
        val rows = listOf(
            mapOf("text" to "The Quick BROWN Fox!!! Jumps Over...the Lazy DOG??")
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("inputCol" to "text", "outputCol" to "clean")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        assertEquals("the quick brown fox jumps overthe lazy dog", rowCaptor.firstValue["clean"])
    }

    // ===== Error Tests =====

    @Test
    fun `test execute throws exception when inputCol is missing`() {
        // Arrange
        val properties = mapOf("outputCol" to "clean")
        val inputs = mapOf("data" to mockInputReader)

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter)
        }
        assertEquals("inputCol is not provided", exception.message)
    }

    @Test
    fun `test execute throws exception when outputCol is missing`() {
        // Arrange
        val properties = mapOf("inputCol" to "text")
        val inputs = mapOf("data" to mockInputReader)

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter)
        }
        assertEquals("outputCol is not provided", exception.message)
    }

    // ===== Edge Cases =====

    @Test
    fun `test execute with empty input`() {
        // Arrange
        mockSequentialReads(mockInputReader, emptyList())

        val properties = mapOf("inputCol" to "text", "outputCol" to "clean")
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, org.mockito.kotlin.never()).write(any(), any())
    }

    @Test
    fun `test execute properly closes writer`() {
        // Arrange
        val rows = listOf(mapOf("text" to "test"))
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("inputCol" to "text", "outputCol" to "clean")
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter).close()
    }

    @Test
    fun `test execute properly closes input reader`() {
        // Arrange
        val rows = listOf(mapOf("text" to "test"))
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("inputCol" to "text", "outputCol" to "clean")
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockInputReader).close()
    }

    @Test
    fun `test execute with large dataset`() {
        // Arrange
        val rows = (1..100).map { mapOf("text" to "Value$it!") }
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("inputCol" to "text", "outputCol" to "clean")
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(100)).write(any(), any())
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
