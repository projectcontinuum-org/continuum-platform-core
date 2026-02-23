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

class ColumnJoinNodeModelTest {

    private lateinit var nodeModel: ColumnJoinNodeModel
    private lateinit var mockLeftReader: NodeInputReader
    private lateinit var mockRightReader: NodeInputReader
    private lateinit var mockOutputWriter: NodeOutputWriter
    private lateinit var mockPortWriter: NodeOutputWriter.OutputPortWriter

    @BeforeEach
    fun setUp() {
        nodeModel = ColumnJoinNodeModel()
        mockLeftReader = mock()
        mockRightReader = mock()
        mockOutputWriter = mock()
        mockPortWriter = mock()
        whenever(mockOutputWriter.createOutputPortWriter("output")).thenReturn(mockPortWriter)
    }

    // ===== Configuration Tests =====

    @Test
    fun `test node metadata is properly configured`() {
        val metadata = nodeModel.metadata
        assertEquals("com.continuum.base.node.ColumnJoinNodeModel", metadata.id)
        assertEquals("Joins two columns from left and right tables into one output column", metadata.description)
        assertEquals("Column Join Node", metadata.title)
        assertEquals("Join columns from two tables", metadata.subTitle)
        assertNotNull(metadata.icon)
        assertTrue(metadata.icon.toString().contains("svg"))
    }

    @Test
    fun `test input ports are correctly defined`() {
        val inputPorts = nodeModel.inputPorts
        assertEquals(2, inputPorts.size)
        assertNotNull(inputPorts["left"])
        assertNotNull(inputPorts["right"])
        assertEquals("left input table", inputPorts["left"]!!.name)
        assertEquals("right input table", inputPorts["right"]!!.name)
    }

    @Test
    fun `test output ports are correctly defined`() {
        val outputPorts = nodeModel.outputPorts
        assertEquals(1, outputPorts.size)
        assertNotNull(outputPorts["output"])
        assertEquals("output table", outputPorts["output"]!!.name)
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
    }

    // ===== Success Tests =====

    @Test
    fun `test execute joins columns from two tables successfully`() {
        // Arrange
        val leftRows = listOf(
            mapOf("name" to "Alice"),
            mapOf("name" to "Bob"),
            mapOf("name" to "Charlie")
        )
        val rightRows = listOf(
            mapOf("city" to "NYC"),
            mapOf("city" to "LA"),
            mapOf("city" to "SF")
        )
        mockSequentialReads(mockLeftReader, leftRows)
        mockSequentialReads(mockRightReader, rightRows)

        val properties = mapOf(
            "columnNameLeft" to "name",
            "columnNameRight" to "city",
            "outputColumnName" to "fullInfo"
        )
        val inputs = mapOf("left" to mockLeftReader, "right" to mockRightReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(3)).write(any(), rowCaptor.capture())
        assertEquals(3, rowCaptor.allValues.size)
        assertEquals("Alice NYC", rowCaptor.allValues[0]["fullInfo"])
        assertEquals("Bob LA", rowCaptor.allValues[1]["fullInfo"])
        assertEquals("Charlie SF", rowCaptor.allValues[2]["fullInfo"])
    }

    @Test
    fun `test execute with single row from each table`() {
        // Arrange
        val leftRows = listOf(mapOf("first" to "John"))
        val rightRows = listOf(mapOf("last" to "Doe"))
        mockSequentialReads(mockLeftReader, leftRows)
        mockSequentialReads(mockRightReader, rightRows)

        val properties = mapOf(
            "columnNameLeft" to "first",
            "columnNameRight" to "last",
            "outputColumnName" to "fullName"
        )
        val inputs = mapOf("left" to mockLeftReader, "right" to mockRightReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        assertEquals("John Doe", rowCaptor.allValues[0]["fullName"])
    }

    @Test
    fun `test execute stops when left table has fewer rows`() {
        // Arrange
        val leftRows = listOf(mapOf("a" to "1"), mapOf("a" to "2"))
        val rightRows = listOf(mapOf("b" to "x"), mapOf("b" to "y"), mapOf("b" to "z"))
        mockSequentialReads(mockLeftReader, leftRows)
        mockSequentialReads(mockRightReader, rightRows)

        val properties = mapOf(
            "columnNameLeft" to "a",
            "columnNameRight" to "b",
            "outputColumnName" to "result"
        )
        val inputs = mapOf("left" to mockLeftReader, "right" to mockRightReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(2)).write(any(), any())
    }

    @Test
    fun `test execute stops when right table has fewer rows`() {
        // Arrange
        val leftRows = listOf(mapOf("a" to "1"), mapOf("a" to "2"), mapOf("a" to "3"))
        val rightRows = listOf(mapOf("b" to "x"), mapOf("b" to "y"))
        mockSequentialReads(mockLeftReader, leftRows)
        mockSequentialReads(mockRightReader, rightRows)

        val properties = mapOf(
            "columnNameLeft" to "a",
            "columnNameRight" to "b",
            "outputColumnName" to "result"
        )
        val inputs = mapOf("left" to mockLeftReader, "right" to mockRightReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(2)).write(any(), any())
    }

    @Test
    fun `test execute handles empty string values`() {
        // Arrange
        val leftRows = listOf(mapOf("col1" to ""))
        val rightRows = listOf(mapOf("col2" to ""))
        mockSequentialReads(mockLeftReader, leftRows)
        mockSequentialReads(mockRightReader, rightRows)

        val properties = mapOf(
            "columnNameLeft" to "col1",
            "columnNameRight" to "col2",
            "outputColumnName" to "joined"
        )
        val inputs = mapOf("left" to mockLeftReader, "right" to mockRightReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        assertEquals("", rowCaptor.allValues[0]["joined"])
    }

    @Test
    fun `test execute handles missing columns as empty strings`() {
        // Arrange
        val leftRows = listOf(mapOf("other" to "value"))
        val rightRows = listOf(mapOf("other2" to "value2"))
        mockSequentialReads(mockLeftReader, leftRows)
        mockSequentialReads(mockRightReader, rightRows)

        val properties = mapOf(
            "columnNameLeft" to "missingCol",
            "columnNameRight" to "alsoMissing",
            "outputColumnName" to "result"
        )
        val inputs = mapOf("left" to mockLeftReader, "right" to mockRightReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        assertEquals("", rowCaptor.allValues[0]["result"])
    }

    @Test
    fun `test execute with numbers converted to strings`() {
        // Arrange
        val leftRows = listOf(mapOf("num" to 42))
        val rightRows = listOf(mapOf("num2" to 100))
        mockSequentialReads(mockLeftReader, leftRows)
        mockSequentialReads(mockRightReader, rightRows)

        val properties = mapOf(
            "columnNameLeft" to "num",
            "columnNameRight" to "num2",
            "outputColumnName" to "joined"
        )
        val inputs = mapOf("left" to mockLeftReader, "right" to mockRightReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        assertEquals("42 100", rowCaptor.allValues[0]["joined"])
    }

    @Test
    fun `test execute trims trailing and leading spaces in joined output`() {
        // Arrange
        val leftRows = listOf(mapOf("col1" to " "))
        val rightRows = listOf(mapOf("col2" to "value"))
        mockSequentialReads(mockLeftReader, leftRows)
        mockSequentialReads(mockRightReader, rightRows)

        val properties = mapOf(
            "columnNameLeft" to "col1",
            "columnNameRight" to "col2",
            "outputColumnName" to "result"
        )
        val inputs = mapOf("left" to mockLeftReader, "right" to mockRightReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        assertEquals("value", rowCaptor.allValues[0]["result"])
    }

    @Test
    fun `test execute with row indices are sequential`() {
        // Arrange
        val leftRows = listOf(mapOf("a" to "1"), mapOf("a" to "2"))
        val rightRows = listOf(mapOf("b" to "x"), mapOf("b" to "y"))
        mockSequentialReads(mockLeftReader, leftRows)
        mockSequentialReads(mockRightReader, rightRows)

        val properties = mapOf(
            "columnNameLeft" to "a",
            "columnNameRight" to "b",
            "outputColumnName" to "result"
        )
        val inputs = mapOf("left" to mockLeftReader, "right" to mockRightReader)
        val indexCaptor = argumentCaptor<Long>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(2)).write(indexCaptor.capture(), any())
        assertEquals(2, indexCaptor.allValues.size)
        assertEquals(0L, indexCaptor.allValues[0])
        assertEquals(1L, indexCaptor.allValues[1])
    }

    // ===== Error Tests =====

    @Test
    fun `test execute throws exception when columnNameLeft is missing`() {
        // Arrange
        val properties = mapOf(
            "columnNameRight" to "city",
            "outputColumnName" to "result"
        )
        val inputs = mapOf("left" to mockLeftReader, "right" to mockRightReader)

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter)
        }
        assertEquals("columnNameLeft is not provided", exception.message)
    }

    @Test
    fun `test execute throws exception when columnNameRight is missing`() {
        // Arrange
        val properties = mapOf(
            "columnNameLeft" to "name",
            "outputColumnName" to "result"
        )
        val inputs = mapOf("left" to mockLeftReader, "right" to mockRightReader)

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter)
        }
        assertEquals("columnNameRight is not provided", exception.message)
    }

    @Test
    fun `test execute throws exception when outputColumnName is missing`() {
        // Arrange
        val properties = mapOf(
            "columnNameLeft" to "name",
            "columnNameRight" to "city"
        )
        val inputs = mapOf("left" to mockLeftReader, "right" to mockRightReader)

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter)
        }
        assertEquals("outputColumnName is not provided", exception.message)
    }

    // ===== Edge Cases =====

    @Test
    fun `test execute with empty left input`() {
        // Arrange
        mockSequentialReads(mockLeftReader, emptyList())
        mockSequentialReads(mockRightReader, listOf(mapOf("col" to "value")))

        val properties = mapOf(
            "columnNameLeft" to "a",
            "columnNameRight" to "b",
            "outputColumnName" to "result"
        )
        val inputs = mapOf("left" to mockLeftReader, "right" to mockRightReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, org.mockito.kotlin.never()).write(any(), any())
    }

    @Test
    fun `test execute with empty right input`() {
        // Arrange
        mockSequentialReads(mockLeftReader, listOf(mapOf("col" to "value")))
        mockSequentialReads(mockRightReader, emptyList())

        val properties = mapOf(
            "columnNameLeft" to "a",
            "columnNameRight" to "b",
            "outputColumnName" to "result"
        )
        val inputs = mapOf("left" to mockLeftReader, "right" to mockRightReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, org.mockito.kotlin.never()).write(any(), any())
    }

    @Test
    fun `test execute with both inputs empty`() {
        // Arrange
        mockSequentialReads(mockLeftReader, emptyList())
        mockSequentialReads(mockRightReader, emptyList())

        val properties = mapOf(
            "columnNameLeft" to "a",
            "columnNameRight" to "b",
            "outputColumnName" to "result"
        )
        val inputs = mapOf("left" to mockLeftReader, "right" to mockRightReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, org.mockito.kotlin.never()).write(any(), any())
    }

    @Test
    fun `test execute properly closes output writer`() {
        // Arrange
        val leftRows = listOf(mapOf("a" to "1"))
        val rightRows = listOf(mapOf("b" to "x"))
        mockSequentialReads(mockLeftReader, leftRows)
        mockSequentialReads(mockRightReader, rightRows)

        val properties = mapOf(
            "columnNameLeft" to "a",
            "columnNameRight" to "b",
            "outputColumnName" to "result"
        )
        val inputs = mapOf("left" to mockLeftReader, "right" to mockRightReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter).close()
    }

    @Test
    fun `test execute properly closes input readers`() {
        // Arrange
        val leftRows = listOf(mapOf("a" to "1"))
        val rightRows = listOf(mapOf("b" to "x"))
        mockSequentialReads(mockLeftReader, leftRows)
        mockSequentialReads(mockRightReader, rightRows)

        val properties = mapOf(
            "columnNameLeft" to "a",
            "columnNameRight" to "b",
            "outputColumnName" to "result"
        )
        val inputs = mapOf("left" to mockLeftReader, "right" to mockRightReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockLeftReader).close()
        verify(mockRightReader).close()
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
