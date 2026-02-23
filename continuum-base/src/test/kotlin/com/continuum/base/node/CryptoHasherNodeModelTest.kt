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

class CryptoHasherNodeModelTest {

    private lateinit var nodeModel: CryptoHasherNodeModel
    private lateinit var mockInputReader: NodeInputReader
    private lateinit var mockOutputWriter: NodeOutputWriter
    private lateinit var mockPortWriter: NodeOutputWriter.OutputPortWriter

    @BeforeEach
    fun setUp() {
        nodeModel = CryptoHasherNodeModel()
        mockInputReader = mock()
        mockOutputWriter = mock()
        mockPortWriter = mock()
        whenever(mockOutputWriter.createOutputPortWriter("data")).thenReturn(mockPortWriter)
    }

    // ===== Configuration Tests =====

    @Test
    fun `test node metadata is properly configured`() {
        val metadata = nodeModel.metadata
        assertEquals("com.continuum.base.node.CryptoHasherNodeModel", metadata.id)
        assertEquals("Generates SHA-256 hash of column values", metadata.description)
        assertEquals("Crypto Hasher", metadata.title)
        assertEquals("SHA-256 hashing", metadata.subTitle)
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
        assertEquals("hashed table", outputPorts["data"]!!.name)
    }

    @Test
    fun `test categories are correctly defined`() {
        val categories = nodeModel.categories
        assertEquals(1, categories.size)
        assertEquals("Security & Encryption", categories[0])
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
    fun `test execute hashes simple text`() {
        // Arrange
        val rows = listOf(
            mapOf("text" to "hello")
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("inputCol" to "text", "outputCol" to "hash")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val expectedHash = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
        assertEquals(expectedHash, rowCaptor.firstValue["hash"])
    }

    @Test
    fun `test execute hashes empty string`() {
        // Arrange
        val rows = listOf(
            mapOf("text" to "")
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("inputCol" to "text", "outputCol" to "hash")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val expectedHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        assertEquals(expectedHash, rowCaptor.firstValue["hash"])
    }

    @Test
    fun `test execute hashes multiple rows`() {
        // Arrange
        val rows = listOf(
            mapOf("text" to "hello"),
            mapOf("text" to "world"),
            mapOf("text" to "test")
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("inputCol" to "text", "outputCol" to "hash")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(3)).write(any(), rowCaptor.capture())
        assertEquals(3, rowCaptor.allValues.size)

        val hash1 = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
        val hash2 = "486ea46224d1bb4fb680f34f7c9ad96a8f24ec88be73ea8e5a6c65260e9cb8a7"
        val hash3 = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"

        assertEquals(hash1, rowCaptor.allValues[0]["hash"])
        assertEquals(hash2, rowCaptor.allValues[1]["hash"])
        assertEquals(hash3, rowCaptor.allValues[2]["hash"])
    }

    @Test
    fun `test execute preserves original columns`() {
        // Arrange
        val rows = listOf(
            mapOf("text" to "hello", "id" to 1, "name" to "Alice")
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("inputCol" to "text", "outputCol" to "hash")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val result = rowCaptor.firstValue
        assertEquals("hello", result["text"])
        assertEquals(1, result["id"])
        assertEquals("Alice", result["name"])
        assertNotNull(result["hash"])
    }

    @Test
    fun `test execute with missing input column defaults to empty string`() {
        // Arrange
        val rows = listOf(
            mapOf("otherColumn" to "value")
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("inputCol" to "missingColumn", "outputCol" to "hash")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val expectedHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855" // hash of empty string
        assertEquals(expectedHash, rowCaptor.firstValue["hash"])
    }

    @Test
    fun `test execute converts numbers to strings before hashing`() {
        // Arrange
        val rows = listOf(
            mapOf("text" to 12345)
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("inputCol" to "text", "outputCol" to "hash")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val expectedHash = "5994471abb01112afcc18159f6cc74b4f511b99806da59b3caf5a9c173cacfc5"
        assertEquals(expectedHash, rowCaptor.firstValue["hash"])
    }

    @Test
    fun `test execute with special characters`() {
        // Arrange
        val rows = listOf(
            mapOf("text" to "!@#\$%^&*()")
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("inputCol" to "text", "outputCol" to "hash")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val result = rowCaptor.firstValue["hash"] as String
        assertTrue(result.length == 64) // SHA-256 produces 64 hex characters
        assertTrue(result.matches(Regex("[a-f0-9]{64}")))
    }

    @Test
    fun `test execute with unicode characters`() {
        // Arrange
        val rows = listOf(
            mapOf("text" to "你好世界")
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("inputCol" to "text", "outputCol" to "hash")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val result = rowCaptor.firstValue["hash"] as String
        assertTrue(result.length == 64)
        assertTrue(result.matches(Regex("[a-f0-9]{64}")))
    }

    @Test
    fun `test execute produces lowercase hex output`() {
        // Arrange
        val rows = listOf(
            mapOf("text" to "ABC")
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("inputCol" to "text", "outputCol" to "hash")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val hash = rowCaptor.firstValue["hash"] as String
        assertEquals(hash, hash.lowercase())
    }

    @Test
    fun `test execute hash consistency for same input`() {
        // Arrange - same text should produce same hash
        val rows = listOf(
            mapOf("text" to "test"),
            mapOf("text" to "test")
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("inputCol" to "text", "outputCol" to "hash")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(2)).write(any(), rowCaptor.capture())
        val hash1 = rowCaptor.allValues[0]["hash"]
        val hash2 = rowCaptor.allValues[1]["hash"]
        assertEquals(hash1, hash2)
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

        val properties = mapOf("inputCol" to "text", "outputCol" to "hash")
        val inputs = mapOf("data" to mockInputReader)
        val indexCaptor = argumentCaptor<Long>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(3)).write(indexCaptor.capture(), any())
        assertEquals(listOf(0L, 1L, 2L), indexCaptor.allValues)
    }

    @Test
    fun `test execute with long text`() {
        // Arrange
        val longText = "a".repeat(10000)
        val rows = listOf(
            mapOf("text" to longText)
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("inputCol" to "text", "outputCol" to "hash")
        val inputs = mapOf("data" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val hash = rowCaptor.firstValue["hash"] as String
        assertTrue(hash.length == 64)
    }

    // ===== Error Tests =====

    @Test
    fun `test execute throws exception when inputCol is missing`() {
        // Arrange
        val properties = mapOf("outputCol" to "hash")
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

        val properties = mapOf("inputCol" to "text", "outputCol" to "hash")
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

        val properties = mapOf("inputCol" to "text", "outputCol" to "hash")
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

        val properties = mapOf("inputCol" to "text", "outputCol" to "hash")
        val inputs = mapOf("data" to mockInputReader)

        // Act
        nodeModel.execute(properties, inputs, mockOutputWriter)

        // Assert
        verify(mockInputReader).close()
    }

    @Test
    fun `test execute with large dataset`() {
        // Arrange
        val rows = (1..100).map { mapOf("text" to "value$it") }
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf("inputCol" to "text", "outputCol" to "hash")
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
