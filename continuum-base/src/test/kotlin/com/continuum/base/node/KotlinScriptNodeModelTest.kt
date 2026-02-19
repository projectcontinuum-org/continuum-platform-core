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
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class KotlinScriptNodeModelTest {

    private lateinit var nodeModel: KotlinScriptNodeModel
    private lateinit var mockInputReader: NodeInputReader
    private lateinit var mockOutputWriter: NodeOutputWriter
    private lateinit var mockPortWriter: NodeOutputWriter.OutputPortWriter

    @BeforeEach
    fun setUp() {
        nodeModel = KotlinScriptNodeModel()
        mockInputReader = mock()
        mockOutputWriter = mock()
        mockPortWriter = mock()
    }

    // ===== Configuration Tests =====

    @Test
    fun `test node metadata is properly configured`() {
        val metadata = nodeModel.metadata
        assertEquals("com.continuum.base.node.KotlinScriptNodeModel", metadata.id)
        assertEquals("Run a Kotlin script for each row, adding script_result column", metadata.description)
        assertEquals("Kotlin Script", metadata.title)
        assertEquals("Evaluate Kotlin script per row", metadata.subTitle)
        assertEquals("Transform", metadata.categories[0])
    }

    @Test
    fun `test input ports are correctly defined`() {
        val inputPorts = nodeModel.inputPorts
        assertEquals(1, inputPorts.size)
        assertNotNull(inputPorts["data"])
        assertEquals("input table", inputPorts["data"]?.name)
    }

    @Test
    fun `test output ports are correctly defined`() {
        val outputPorts = nodeModel.outputPorts
        assertEquals(1, outputPorts.size)
        assertNotNull(outputPorts["data"])
        assertEquals("enriched table", outputPorts["data"]?.name)
    }

    @Test
    fun `test properties schema is valid JSON structure`() {
        val schema = nodeModel.propertiesSchema
        assertEquals("object", schema["type"])
        val properties = schema["properties"] as Map<*, *>
        assertNotNull(properties["script"])
    }

    @Test
    fun `test UI schema is valid JSON structure`() {
        val uiSchema = nodeModel.uiSchema
        assertEquals("VerticalLayout", uiSchema["type"])
        val elements = uiSchema["elements"] as List<*>
        assertEquals(1, elements.size)
    }

    // ===== Success Cases =====

    @Test
    fun `test execute with single row and simple script`() {
        // Arrange
        val inputRow = mapOf("message" to "hello")
        whenever(mockInputReader.read())
            .thenReturn(inputRow)
            .thenReturn(null)

        whenever(mockOutputWriter.createOutputPortWriter("data")).thenReturn(mockPortWriter)

        val properties = mapOf("script" to "(row[\"message\"]?.toString() ?: \"\") + \"_result\"")
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        val rowCaptor = argumentCaptor<Map<String, Any>>()
        verify(mockPortWriter).write(any(), rowCaptor.capture())
        assertEquals("hello_result", rowCaptor.firstValue["script_result"])
        verify(mockInputReader).close()
    }

    @Test
    fun `test execute with multiple rows`() {
        // Arrange
        val rows = listOf(
            mapOf("message" to "first"),
            mapOf("message" to "second"),
            mapOf("message" to "third")
        )
        var callCount = 0
        whenever(mockInputReader.read()).thenAnswer {
            if (callCount < rows.size) {
                rows[callCount++]
            } else {
                null
            }
        }

        whenever(mockOutputWriter.createOutputPortWriter("data")).thenReturn(mockPortWriter)

        val properties = mapOf("script" to "row[\"message\"]")
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        val rowCaptor = argumentCaptor<Map<String, Any>>()
        verify(mockPortWriter, times(3)).write(any(), rowCaptor.capture())
        assertEquals("first", rowCaptor.allValues[0]["script_result"])
        assertEquals("second", rowCaptor.allValues[1]["script_result"])
        assertEquals("third", rowCaptor.allValues[2]["script_result"])
    }

    @Test
    fun `test execute with arithmetic expression`() {
        // Arrange
        val inputRow = mapOf("value" to 10)
        whenever(mockInputReader.read())
            .thenReturn(inputRow)
            .thenReturn(null)

        whenever(mockOutputWriter.createOutputPortWriter("data")).thenReturn(mockPortWriter)

        val properties = mapOf("script" to "(row[\"value\"] as? Int ?: 0) * 2 + 5")
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        val rowCaptor = argumentCaptor<Map<String, Any>>()
        verify(mockPortWriter).write(any(), rowCaptor.capture())
        assertEquals(25, rowCaptor.firstValue["script_result"])
    }

    @Test
    fun `test execute with string manipulation`() {
        // Arrange
        val inputRow = mapOf("text" to "kotlin")
        whenever(mockInputReader.read())
            .thenReturn(inputRow)
            .thenReturn(null)

        whenever(mockOutputWriter.createOutputPortWriter("data")).thenReturn(mockPortWriter)

        val properties = mapOf("script" to "(row[\"text\"]?.toString() ?: \"\").uppercase()")
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        val rowCaptor = argumentCaptor<Map<String, Any>>()
        verify(mockPortWriter).write(any(), rowCaptor.capture())
        assertEquals("KOTLIN", rowCaptor.firstValue["script_result"])
    }

    @Test
    fun `test execute with conditional expression`() {
        // Arrange
        val rows = listOf(
            mapOf("age" to 25),
            mapOf("age" to 17),
            mapOf("age" to 30)
        )
        var callCount = 0
        whenever(mockInputReader.read()).thenAnswer {
            if (callCount < rows.size) {
                rows[callCount++]
            } else {
                null
            }
        }

        whenever(mockOutputWriter.createOutputPortWriter("data")).thenReturn(mockPortWriter)

        val properties = mapOf("script" to "if ((row[\"age\"] as? Int ?: 0) >= 18) \"adult\" else \"minor\"")
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        val rowCaptor = argumentCaptor<Map<String, Any>>()
        verify(mockPortWriter, times(3)).write(any(), rowCaptor.capture())
        assertEquals("adult", rowCaptor.allValues[0]["script_result"])
        assertEquals("minor", rowCaptor.allValues[1]["script_result"])
        assertEquals("adult", rowCaptor.allValues[2]["script_result"])
    }

    @Test
    fun `test execute with null values in row`() {
        // Arrange
        val inputRow = mapOf<String, Any?>("message" to null)
        whenever(mockInputReader.read())
            .thenReturn(inputRow)
            .thenReturn(null)

        whenever(mockOutputWriter.createOutputPortWriter("data")).thenReturn(mockPortWriter)

        val properties = mapOf("script" to "row[\"message\"]?.toString() ?: \"default\"")
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        val rowCaptor = argumentCaptor<Map<String, Any>>()
        verify(mockPortWriter).write(any(), rowCaptor.capture())
        assertEquals("default", rowCaptor.firstValue["script_result"])
    }

    @Test
    fun `test execute with missing row property`() {
        // Arrange
        val inputRow = mapOf("name" to "Alice")
        whenever(mockInputReader.read())
            .thenReturn(inputRow)
            .thenReturn(null)

        whenever(mockOutputWriter.createOutputPortWriter("data")).thenReturn(mockPortWriter)

        val properties = mapOf("script" to "row[\"missing\"]?.toString() ?: \"not found\"")
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        val rowCaptor = argumentCaptor<Map<String, Any>>()
        verify(mockPortWriter).write(any(), rowCaptor.capture())
        assertEquals("not found", rowCaptor.firstValue["script_result"])
    }

    @Test
    fun `test execute with empty input rows`() {
        // Arrange
        whenever(mockInputReader.read()).thenReturn(null)
        whenever(mockOutputWriter.createOutputPortWriter("data")).thenReturn(mockPortWriter)

        val properties = mapOf("script" to "row[\"test\"]")
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, never()).write(any(), any())
    }

    @Test
    fun `test execute with complex nested access`() {
        // Arrange
        val inputRow = mapOf(
            "user" to mapOf(
                "id" to 123,
                "name" to "Alice"
            )
        )
        whenever(mockInputReader.read())
            .thenReturn(inputRow)
            .thenReturn(null)

        whenever(mockOutputWriter.createOutputPortWriter("data")).thenReturn(mockPortWriter)

        val properties = mapOf("script" to "row[\"user\"]")
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        val rowCaptor = argumentCaptor<Map<String, Any>>()
        verify(mockPortWriter).write(any(), rowCaptor.capture())
        val result = rowCaptor.firstValue["script_result"] as Map<*, *>
        assertEquals(123, result["id"])
        assertEquals("Alice", result["name"])
    }

    // ===== Error Cases =====

    @Test
    fun `test execute throws exception when data input port is missing`() {
        // Arrange
        val properties = mapOf("script" to "row[\"value\"]")
        val inputs = emptyMap<String, NodeInputReader>()

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter)
        }
        assertEquals("Data port required", exception.message)
    }

    @Test
    fun `test execute throws exception when script property is missing`() {
        // Arrange
        val properties = emptyMap<String, Any>()
        val inputs = mapOf("data" to mockInputReader)

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter)
        }
        assertEquals("Script property is required and cannot be empty", exception.message)
    }

    @Test
    fun `test execute throws exception when script property is empty string`() {
        // Arrange
        val properties = mapOf("script" to "   ")
        val inputs = mapOf("data" to mockInputReader)

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter)
        }
        assertEquals("Script property is required and cannot be empty", exception.message)
    }

    @Test
    fun `test execute throws exception when script property is null`() {
        // Arrange
        val properties = mapOf<String, Any?>("script" to null)
        val inputs = mapOf("data" to mockInputReader)

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter)
        }
        assertEquals("Script property is required and cannot be empty", exception.message)
    }

    @Test
    fun `test execute throws exception on script syntax error`() {
        // Arrange
        val inputRow = mapOf("value" to 10)
        whenever(mockInputReader.read())
            .thenReturn(inputRow)
            .thenReturn(null)

        whenever(mockOutputWriter.createOutputPortWriter("data")).thenReturn(mockPortWriter)

        val properties = mapOf("script" to "invalid kotlin code !! @@")
        val inputs = mapOf("data" to mockInputReader)

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter)
        }
        assertEquals(true, exception.message?.contains("Script execution error at row 0"))
    }

    @Test
    fun `test execute throws exception on runtime error in script`() {
        // Arrange
        val inputRow = mapOf("value" to "abc")
        whenever(mockInputReader.read())
            .thenReturn(inputRow)
            .thenReturn(null)

        whenever(mockOutputWriter.createOutputPortWriter("data")).thenReturn(mockPortWriter)

        val properties = mapOf("script" to "(row[\"value\"] as Int) * 2")
        val inputs = mapOf("data" to mockInputReader)

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter)
        }
        assertEquals(true, exception.message?.contains("Script execution error at row 0"))
    }

    @Test
    fun `test execute throws exception on null reference in script`() {
        // Arrange
        val inputRow = mapOf("name" to null)
        whenever(mockInputReader.read())
            .thenReturn(inputRow)
            .thenReturn(null)

        whenever(mockOutputWriter.createOutputPortWriter("data")).thenReturn(mockPortWriter)

        val properties = mapOf("script" to "(row[\"name\"] as String).uppercase()")
        val inputs = mapOf("data" to mockInputReader)

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter)
        }
        assertEquals(true, exception.message?.contains("Script execution error at row 0"))
    }

    // ===== Row Number Tracking Tests =====

    @Test
    fun `test execute tracks row numbers correctly`() {
        // Arrange
        val rows = listOf(
            mapOf("id" to 1),
            mapOf("id" to 2),
            mapOf("id" to 3)
        )
        var callCount = 0
        whenever(mockInputReader.read()).thenAnswer {
            if (callCount < rows.size) {
                rows[callCount++]
            } else {
                null
            }
        }

        whenever(mockOutputWriter.createOutputPortWriter("data")).thenReturn(mockPortWriter)

        val properties = mapOf("script" to "row[\"id\"]")
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        val rowNumberCaptor = argumentCaptor<Long>()
        verify(mockPortWriter, times(3)).write(rowNumberCaptor.capture(), any())
        assertEquals(listOf(0L, 1L, 2L), rowNumberCaptor.allValues)
    }

    // ===== Input Row Preservation Tests =====

    @Test
    fun `test execute preserves original row data`() {
        // Arrange
        val inputRow = mapOf(
            "col1" to "value1",
            "col2" to 42,
            "col3" to true
        )
        whenever(mockInputReader.read())
            .thenReturn(inputRow)
            .thenReturn(null)

        whenever(mockOutputWriter.createOutputPortWriter("data")).thenReturn(mockPortWriter)

        val properties = mapOf("script" to "\"processed\"")
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        val rowCaptor = argumentCaptor<Map<String, Any>>()
        verify(mockPortWriter).write(any(), rowCaptor.capture())
        val result = rowCaptor.firstValue
        assertEquals("value1", result["col1"])
        assertEquals(42, result["col2"])
        assertEquals(true, result["col3"])
        assertEquals("processed", result["script_result"])
    }

    @Test
    fun `test execute adds script_result to every row`() {
        // Arrange
        val rows = listOf(
            mapOf("data" to "test1"),
            mapOf("data" to "test2")
        )
        var callCount = 0
        whenever(mockInputReader.read()).thenAnswer {
            if (callCount < rows.size) {
                rows[callCount++]
            } else {
                null
            }
        }

        whenever(mockOutputWriter.createOutputPortWriter("data")).thenReturn(mockPortWriter)

        val properties = mapOf("script" to "\"result\"")
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        val rowCaptor = argumentCaptor<Map<String, Any>>()
        verify(mockPortWriter, times(2)).write(any(), rowCaptor.capture())
        rowCaptor.allValues.forEach {
            assertEquals(true, it.containsKey("script_result"))
            assertEquals("result", it["script_result"])
        }
    }

    // ===== Resource Management Tests =====

    @Test
    fun `test execute closes input reader after processing`() {
        // Arrange
        whenever(mockInputReader.read()).thenReturn(null)
        whenever(mockOutputWriter.createOutputPortWriter("data")).thenReturn(mockPortWriter)

        val properties = mapOf("script" to "\"test\"")
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockInputReader).close()
    }

    @Test
    fun `test execute closes output writer after processing`() {
        // Arrange
        whenever(mockInputReader.read()).thenReturn(null)
        whenever(mockOutputWriter.createOutputPortWriter("data")).thenReturn(mockPortWriter)

        val properties = mapOf("script" to "\"test\"")
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter).close()
    }

    // ===== Properties Handling Tests =====

    @Test
    fun `test execute with properties as null`() {
        // Arrange
        whenever(mockInputReader.read()).thenReturn(null)
        whenever(mockOutputWriter.createOutputPortWriter("data")).thenReturn(mockPortWriter)

        val inputs = mapOf("data" to mockInputReader)

        // Act & Assert
        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(null, inputs, mockOutputWriter)
        }
        assertEquals("Script property is required and cannot be empty", exception.message)
    }

    @Test
    fun `test execute with additional properties beyond script`() {
        // Arrange
        val inputRow = mapOf("value" to 10)
        whenever(mockInputReader.read())
            .thenReturn(inputRow)
            .thenReturn(null)

        whenever(mockOutputWriter.createOutputPortWriter("data")).thenReturn(mockPortWriter)

        val properties = mapOf(
            "script" to "row[\"value\"]",
            "extraProperty" to "should be ignored",
            "anotherProperty" to 123
        )
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        val rowCaptor = argumentCaptor<Map<String, Any>>()
        verify(mockPortWriter).write(any(), rowCaptor.capture())
        assertEquals(10, rowCaptor.firstValue["script_result"])
    }

    @Test
    fun `test execute with script containing multiline expression`() {
        // Arrange
        val inputRow = mapOf("x" to 5, "y" to 3)
        whenever(mockInputReader.read())
            .thenReturn(inputRow)
            .thenReturn(null)

        whenever(mockOutputWriter.createOutputPortWriter("data")).thenReturn(mockPortWriter)

        val script = """
            val x = row["x"] as? Int ?: 0
            val y = row["y"] as? Int ?: 0
            x + y
        """.trimIndent()

        val properties = mapOf("script" to script)
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        val rowCaptor = argumentCaptor<Map<String, Any>>()
        verify(mockPortWriter).write(any(), rowCaptor.capture())
        assertEquals(8, rowCaptor.firstValue["script_result"])
    }
}
