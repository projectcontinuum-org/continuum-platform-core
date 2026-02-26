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

class SplitNodeModelTest {

    private lateinit var nodeModel: SplitNodeModel
    private lateinit var mockInputReader: NodeInputReader
    private lateinit var mockOutputWriter: NodeOutputWriter
    private lateinit var mockPortWriter1: NodeOutputWriter.OutputPortWriter
    private lateinit var mockPortWriter2: NodeOutputWriter.OutputPortWriter

    @BeforeEach
    fun setUp() {
        nodeModel = SplitNodeModel()
        mockInputReader = mock()
        mockOutputWriter = mock()
        mockPortWriter1 = mock()
        mockPortWriter2 = mock()
        whenever(mockOutputWriter.createOutputPortWriter("output-1")).thenReturn(mockPortWriter1)
        whenever(mockOutputWriter.createOutputPortWriter("output-2")).thenReturn(mockPortWriter2)
    }

    // ===== Configuration Tests =====

    @Test
    fun `test node metadata is properly configured`() {
        val metadata = nodeModel.metadata
        assertEquals("com.continuum.base.node.SplitNodeModel", metadata.id)
        assertEquals("Split a column into two parts", metadata.description)
        assertEquals("Column Splitter", metadata.title)
        assertEquals("Split a column", metadata.subTitle)
        assertNotNull(metadata.icon)
        assertTrue(metadata.icon.toString().contains("svg"))
    }

    @Test
    fun `test input ports are correctly defined`() {
        val inputPorts = nodeModel.inputPorts
        assertEquals(1, inputPorts.size)
        assertNotNull(inputPorts["input-1"])
        val inputPort = inputPorts["input-1"]!!
        assertEquals("input string", inputPort.name)
    }

    @Test
    fun `test output ports are correctly defined`() {
        val outputPorts = nodeModel.outputPorts
        assertEquals(2, outputPorts.size)
        assertNotNull(outputPorts["output-1"])
        assertNotNull(outputPorts["output-2"])
        assertEquals("part 1", outputPorts["output-1"]!!.name)
        assertEquals("part 2", outputPorts["output-2"]!!.name)
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
        assertTrue(properties.containsKey("columnName"))
        assertTrue(properties.containsKey("outputs"))

        val required = schema["required"] as List<*>
        assertTrue(required.contains("columnName"))
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
        assertEquals("message", defaultProperties["columnName"])

        val outputs = defaultProperties["outputs"] as List<*>
        assertEquals(2, outputs.size)
    }

    // ===== Success Tests =====

    @Test
    fun `test split single-word text - one part`() {
        // Arrange
        val rows = listOf(
            mapOf("message" to "Hello")
        )
        mockSequentialReads(rows)

        val properties = mapOf(
            "columnName" to "message",
            "outputs" to listOf(
                mapOf("columnName" to "part-1"),
                mapOf("columnName" to "part-2")
            )
        )
        val inputs = mapOf("input-1" to mockInputReader)
        val captor1 = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert - only output-1 is written for single word
        verify(mockPortWriter1, times(1)).write(any(), captor1.capture())
        val written1 = captor1.allValues

        assertEquals(1, written1.size)
        assertEquals("Hello", written1[0]["part-1"])
    }

    @Test
    fun `test split two-word text - two parts`() {
        // Arrange
        val rows = listOf(
            mapOf("message" to "Hello World")
        )
        mockSequentialReads(rows)

        val properties = mapOf(
            "columnName" to "message",
            "outputs" to listOf(
                mapOf("columnName" to "part-1"),
                mapOf("columnName" to "part-2")
            )
        )
        val inputs = mapOf("input-1" to mockInputReader)
        val captor1 = argumentCaptor<Map<String, Any>>()
        val captor2 = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter1, times(1)).write(any(), captor1.capture())
        verify(mockPortWriter2, times(1)).write(any(), captor2.capture())
        val written1 = captor1.allValues
        val written2 = captor2.allValues

        assertEquals(1, written1.size)
        assertEquals("Hello", written1[0]["part-1"])

        assertEquals(1, written2.size)
        assertEquals("World", written2[0]["part-2"])
    }

    @Test
    fun `test split multi-word text - limit of 2 behavior`() {
        // Arrange - should split into max 2 parts (limit=2)
        val rows = listOf(
            mapOf("message" to "Hello Beautiful World")
        )
        mockSequentialReads(rows)

        val properties = mapOf(
            "columnName" to "message",
            "outputs" to listOf(
                mapOf("columnName" to "first"),
                mapOf("columnName" to "rest")
            )
        )
        val inputs = mapOf("input-1" to mockInputReader)
        val captor1 = argumentCaptor<Map<String, Any>>()
        val captor2 = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter1, times(1)).write(any(), captor1.capture())
        verify(mockPortWriter2, times(1)).write(any(), captor2.capture())
        val written1 = captor1.allValues
        val written2 = captor2.allValues

        assertEquals(1, written1.size)
        assertEquals("Hello", written1[0]["first"])

        assertEquals(1, written2.size)
        assertEquals("Beautiful World", written2[0]["rest"]) // Remaining text after first space
    }

    @Test
    fun `test split multiple rows`() {
        // Arrange
        val rows = listOf(
            mapOf("message" to "Hello World"),
            mapOf("message" to "Good Morning"),
            mapOf("message" to "See You")
        )
        mockSequentialReads(rows)

        val properties = mapOf(
            "columnName" to "message",
            "outputs" to listOf(
                mapOf("columnName" to "first"),
                mapOf("columnName" to "second")
            )
        )
        val inputs = mapOf("input-1" to mockInputReader)
        val captor1 = argumentCaptor<Map<String, Any>>()
        val captor2 = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter1, times(3)).write(any(), captor1.capture())
        verify(mockPortWriter2, times(3)).write(any(), captor2.capture())
        val written1 = captor1.allValues
        val written2 = captor2.allValues

        assertEquals(3, written1.size)
        assertEquals("Hello", written1[0]["first"])
        assertEquals("Good", written1[1]["first"])
        assertEquals("See", written1[2]["first"])

        assertEquals(3, written2.size)
        assertEquals("World", written2[0]["second"])
        assertEquals("Morning", written2[1]["second"])
        assertEquals("You", written2[2]["second"])
    }

    @Test
    fun `test split with varying part counts across rows`() {
        // Arrange - different rows have different number of words
        val rows = listOf(
            mapOf("message" to "Single"),
            mapOf("message" to "Two Words"),
            mapOf("message" to "Three Word Text")
        )
        mockSequentialReads(rows)

        val properties = mapOf(
            "columnName" to "message",
            "outputs" to listOf(
                mapOf("columnName" to "part1"),
                mapOf("columnName" to "part2")
            )
        )
        val inputs = mapOf("input-1" to mockInputReader)
        val captor1 = argumentCaptor<Map<String, Any>>()
        val captor2 = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter1, times(3)).write(any(), captor1.capture())
        verify(mockPortWriter2, times(2)).write(any(), captor2.capture()) // Only 2 because first row has 1 word
        val written1 = captor1.allValues
        val written2 = captor2.allValues

        assertEquals(3, written1.size)
        assertEquals("Single", written1[0]["part1"])
        assertEquals("Two", written1[1]["part1"])
        assertEquals("Three", written1[2]["part1"])

        assertEquals(2, written2.size)
        assertEquals("Words", written2[0]["part2"])
        assertEquals("Word Text", written2[1]["part2"]) // limit=2 keeps rest together
    }

    @Test
    fun `test dynamic output port creation`() {
        // Arrange - start with 1 output configured, should create output-2 dynamically
        val rows = listOf(
            mapOf("message" to "Hello World")
        )
        mockSequentialReads(rows)

        val properties = mapOf(
            "columnName" to "message",
            "outputs" to listOf(
                mapOf("columnName" to "part1"),
                mapOf("columnName" to "part2")
            )
        )
        val inputs = mapOf("input-1" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert - verify output-2 writer was created
        verify(mockOutputWriter).createOutputPortWriter("output-1")
        verify(mockOutputWriter).createOutputPortWriter("output-2")
    }

    @Test
    fun `test output column naming`() {
        // Arrange
        val rows = listOf(
            mapOf("text" to "Alpha Beta")
        )
        mockSequentialReads(rows)

        val properties = mapOf(
            "columnName" to "text",
            "outputs" to listOf(
                mapOf("columnName" to "greek1"),
                mapOf("columnName" to "greek2")
            )
        )
        val inputs = mapOf("input-1" to mockInputReader)
        val captor1 = argumentCaptor<Map<String, Any>>()
        val captor2 = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter1, times(1)).write(any(), captor1.capture())
        verify(mockPortWriter2, times(1)).write(any(), captor2.capture())
        val written1 = captor1.allValues
        val written2 = captor2.allValues

        assertEquals("Alpha", written1[0]["greek1"])
        assertEquals("Beta", written2[0]["greek2"])
    }

    @Test
    fun `test row indices are sequential`() {
        // Arrange
        val rows = listOf(
            mapOf("message" to "A B"),
            mapOf("message" to "C D")
        )
        mockSequentialReads(rows)

        val properties = mapOf(
            "columnName" to "message",
            "outputs" to listOf(
                mapOf("columnName" to "part1"),
                mapOf("columnName" to "part2")
            )
        )
        val inputs = mapOf("input-1" to mockInputReader)
        val indexCaptor1 = argumentCaptor<Long>()
        val indexCaptor2 = argumentCaptor<Long>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter1, times(2)).write(indexCaptor1.capture(), any())
        verify(mockPortWriter2, times(2)).write(indexCaptor2.capture(), any())

        assertEquals(2, indexCaptor1.allValues.size)
        assertEquals(0L, indexCaptor1.allValues[0])
        assertEquals(1L, indexCaptor1.allValues[1])

        assertEquals(2, indexCaptor2.allValues.size)
        assertEquals(0L, indexCaptor2.allValues[0])
        assertEquals(1L, indexCaptor2.allValues[1])
    }

    // ===== Edge Cases =====

    @Test
    fun `test with empty input stream`() {
        // Arrange
        whenever(mockInputReader.read()).thenReturn(null)

        val properties = mapOf(
            "columnName" to "message",
            "outputs" to listOf(
                mapOf("columnName" to "part1"),
                mapOf("columnName" to "part2")
            )
        )
        val inputs = mapOf("input-1" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert - no writes should occur
        verify(mockPortWriter1, org.mockito.kotlin.never()).write(any(), any())
        verify(mockPortWriter2, org.mockito.kotlin.never()).write(any(), any())
    }

    @Test
    fun `test with text containing multiple consecutive spaces`() {
        // Arrange - split on first space, rest stays together
        val rows = listOf(
            mapOf("message" to "Hello   World")
        )
        mockSequentialReads(rows)

        val properties = mapOf(
            "columnName" to "message",
            "outputs" to listOf(
                mapOf("columnName" to "first"),
                mapOf("columnName" to "rest")
            )
        )
        val inputs = mapOf("input-1" to mockInputReader)
        val captor1 = argumentCaptor<Map<String, Any>>()
        val captor2 = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter1, times(1)).write(any(), captor1.capture())
        verify(mockPortWriter2, times(1)).write(any(), captor2.capture())
        val written1 = captor1.allValues
        val written2 = captor2.allValues

        assertEquals("Hello", written1[0]["first"])
        assertEquals("  World", written2[0]["rest"]) // Keeps the extra spaces
    }

    @Test
    fun `test with text starting with space`() {
        // Arrange
        val rows = listOf(
            mapOf("message" to " Hello World")
        )
        mockSequentialReads(rows)

        val properties = mapOf(
            "columnName" to "message",
            "outputs" to listOf(
                mapOf("columnName" to "first"),
                mapOf("columnName" to "second")
            )
        )
        val inputs = mapOf("input-1" to mockInputReader)
        val captor1 = argumentCaptor<Map<String, Any>>()
        val captor2 = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter1, times(1)).write(any(), captor1.capture())
        verify(mockPortWriter2, times(1)).write(any(), captor2.capture())
        val written1 = captor1.allValues
        val written2 = captor2.allValues

        assertEquals("", written1[0]["first"]) // Empty before first space
        assertEquals("Hello World", written2[0]["second"])
    }

    @Test
    fun `test with text ending with space`() {
        // Arrange
        val rows = listOf(
            mapOf("message" to "Hello World ")
        )
        mockSequentialReads(rows)

        val properties = mapOf(
            "columnName" to "message",
            "outputs" to listOf(
                mapOf("columnName" to "first"),
                mapOf("columnName" to "second")
            )
        )
        val inputs = mapOf("input-1" to mockInputReader)
        val captor1 = argumentCaptor<Map<String, Any>>()
        val captor2 = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter1, times(1)).write(any(), captor1.capture())
        verify(mockPortWriter2, times(1)).write(any(), captor2.capture())
        val written1 = captor1.allValues
        val written2 = captor2.allValues

        assertEquals("Hello", written1[0]["first"])
        assertEquals("World ", written2[0]["second"]) // Keeps trailing space
    }

    @Test
    fun `test with single space only`() {
        // Arrange
        val rows = listOf(
            mapOf("message" to " ")
        )
        mockSequentialReads(rows)

        val properties = mapOf(
            "columnName" to "message",
            "outputs" to listOf(
                mapOf("columnName" to "first"),
                mapOf("columnName" to "second")
            )
        )
        val inputs = mapOf("input-1" to mockInputReader)
        val captor1 = argumentCaptor<Map<String, Any>>()
        val captor2 = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter1, times(1)).write(any(), captor1.capture())
        verify(mockPortWriter2, times(1)).write(any(), captor2.capture())
        val written1 = captor1.allValues
        val written2 = captor2.allValues

        assertEquals("", written1[0]["first"])
        assertEquals("", written2[0]["second"])
    }

    // ===== Error Tests =====

    @Test
    fun `test execute throws exception when columnName property is missing`() {
        // Arrange - need at least one row to trigger validation
        val rows = listOf(
            mapOf("message" to "Hello World")
        )
        mockSequentialReads(rows)

        val properties = mapOf(
            "outputs" to listOf(
                mapOf("columnName" to "part1"),
                mapOf("columnName" to "part2")
            )
        )
        val inputs = mapOf("input-1" to mockInputReader)

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter)
        }
        assertEquals("Input column name is not provided", exception.message)
    }

    @Test
    fun `test execute throws exception when columnName property is null`() {
        // Arrange
        whenever(mockInputReader.read()).thenReturn(mapOf("message" to "Hello World"))

        @Suppress("UNCHECKED_CAST")
        val properties = mapOf(
            "columnName" to null,
            "outputs" to listOf(
                mapOf("columnName" to "part1"),
                mapOf("columnName" to "part2")
            )
        ) as Map<String, Any>
        val inputs = mapOf("input-1" to mockInputReader)

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter)
        }
        assertEquals("Input column name is not provided", exception.message)
    }

    @Test
    fun `test execute throws exception when row is missing the specified column`() {
        // Arrange - row doesn't have the "message" column
        val rows = listOf(
            mapOf("other" to "something")
        )
        mockSequentialReads(rows)

        val properties = mapOf(
            "columnName" to "message",
            "outputs" to listOf(
                mapOf("columnName" to "part1"),
                mapOf("columnName" to "part2")
            )
        )
        val inputs = mapOf("input-1" to mockInputReader)

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter)
        }
        assertEquals("Input column name is not provided", exception.message)
    }

    @Test
    fun `test execute throws exception when properties is null`() {
        // Arrange - need at least one row to trigger validation
        val rows = listOf(
            mapOf("message" to "Hello")
        )
        mockSequentialReads(rows)

        val inputs = mapOf("input-1" to mockInputReader)

        // Act & Assert
        // Implementation throws NullPointerException when accessing properties?.get("outputs")
        val exception = assertThrows<NullPointerException> {
            nodeModel.execute(null, inputs, mockOutputWriter)
        }
        assertNotNull(exception)
    }

    @Test
    fun `test execute throws exception when column value is null`() {
        // Arrange
        val rows = listOf(
            mapOf("message" to null)
        )
        mockSequentialReads(rows)

        val properties = mapOf(
            "columnName" to "message",
            "outputs" to listOf(
                mapOf("columnName" to "part1"),
                mapOf("columnName" to "part2")
            )
        )
        val inputs = mapOf("input-1" to mockInputReader)

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter)
        }
        assertEquals("Input column name is not provided", exception.message)
    }

    // ===== Resource Management Tests =====

    @Test
    fun `test output writers are properly closed`() {
        // Arrange
        val rows = listOf(
            mapOf("message" to "Hello World")
        )
        mockSequentialReads(rows)

        val properties = mapOf(
            "columnName" to "message",
            "outputs" to listOf(
                mapOf("columnName" to "part1"),
                mapOf("columnName" to "part2")
            )
        )
        val inputs = mapOf("input-1" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter1).close()
        verify(mockPortWriter2).close()
    }

    @Test
    fun `test input reader is properly closed`() {
        // Arrange
        val rows = listOf(
            mapOf("message" to "Hello World")
        )
        mockSequentialReads(rows)

        val properties = mapOf(
            "columnName" to "message",
            "outputs" to listOf(
                mapOf("columnName" to "part1"),
                mapOf("columnName" to "part2")
            )
        )
        val inputs = mapOf("input-1" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockInputReader).close()
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
