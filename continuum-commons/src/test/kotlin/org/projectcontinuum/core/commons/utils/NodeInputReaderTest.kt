package org.projectcontinuum.core.commons.utils

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class NodeInputReaderTest {

  @TempDir
  lateinit var tempDir: Path

  /**
   * Helper: writes rows to a Parquet file using NodeOutputWriter and returns the file path.
   */
  private fun writeParquetFile(
    rows: List<Map<String, Any>>,
    portId: String = "port-0"
  ): Path {
    val writer = NodeOutputWriter(tempDir)
    val portWriter = writer.createOutputPortWriter(portId)
    rows.forEachIndexed { index, row ->
      portWriter.write(index.toLong(), row)
    }
    portWriter.close()
    return tempDir.resolve("output.$portId.parquet")
  }

  @Test
  fun `read returns rows in order`() {
    val inputRows = listOf(
      mapOf("name" to "Alice", "age" to 25),
      mapOf("name" to "Bob", "age" to 30),
      mapOf("name" to "Charlie", "age" to 35)
    )
    val filePath = writeParquetFile(inputRows)

    NodeInputReader(filePath).use { reader ->
      val row1 = reader.read()
      assertNotNull(row1)
      assertEquals("Alice", row1!!["name"])
      assertEquals(25, row1["age"])

      val row2 = reader.read()
      assertNotNull(row2)
      assertEquals("Bob", row2!!["name"])
      assertEquals(30, row2["age"])

      val row3 = reader.read()
      assertNotNull(row3)
      assertEquals("Charlie", row3!!["name"])
      assertEquals(35, row3["age"])

      // No more rows
      assertNull(reader.read())
    }
  }

  @Test
  fun `read returns null for empty file`() {
    val filePath = writeParquetFile(emptyList())

    NodeInputReader(filePath).use { reader ->
      assertNull(reader.read())
    }
  }

  @Test
  fun `getFilePath returns the input file path`() {
    val filePath = writeParquetFile(listOf(mapOf("x" to 1)))

    NodeInputReader(filePath).use { reader ->
      assertEquals(filePath, reader.getFilePath())
    }
  }

  @Test
  fun `getRowCount returns correct count`() {
    val inputRows = listOf(
      mapOf("a" to 1),
      mapOf("a" to 2),
      mapOf("a" to 3),
      mapOf("a" to 4),
      mapOf("a" to 5)
    )
    val filePath = writeParquetFile(inputRows)

    NodeInputReader(filePath).use { reader ->
      assertEquals(5L, reader.getRowCount())
    }
  }

  @Test
  fun `getRowCount returns zero for empty file`() {
    val filePath = writeParquetFile(emptyList())

    NodeInputReader(filePath).use { reader ->
      assertEquals(0L, reader.getRowCount())
    }
  }

  @Test
  fun `getRowCount is cached across calls`() {
    val filePath = writeParquetFile(listOf(mapOf("a" to 1), mapOf("a" to 2)))

    NodeInputReader(filePath).use { reader ->
      val count1 = reader.getRowCount()
      val count2 = reader.getRowCount()
      assertEquals(count1, count2)
      assertEquals(2L, count1)
    }
  }

  @Test
  fun `getRowCount does not affect read position`() {
    val filePath = writeParquetFile(listOf(mapOf("val" to "first"), mapOf("val" to "second")))

    NodeInputReader(filePath).use { reader ->
      // Read first row
      val row1 = reader.read()
      assertEquals("first", row1!!["val"])

      // Call getRowCount mid-stream
      assertEquals(2L, reader.getRowCount())

      // Continue reading — should get second row, not first
      val row2 = reader.read()
      assertEquals("second", row2!!["val"])

      assertNull(reader.read())
    }
  }

  @Test
  fun `reset allows re-reading from the beginning`() {
    val filePath = writeParquetFile(listOf(
      mapOf("name" to "Alice"),
      mapOf("name" to "Bob")
    ))

    NodeInputReader(filePath).use { reader ->
      // First pass
      assertEquals("Alice", reader.read()!!["name"])
      assertEquals("Bob", reader.read()!!["name"])
      assertNull(reader.read())

      // Reset and re-read
      reader.reset()

      assertEquals("Alice", reader.read()!!["name"])
      assertEquals("Bob", reader.read()!!["name"])
      assertNull(reader.read())
    }
  }

  @Test
  fun `reset can be called multiple times`() {
    val filePath = writeParquetFile(listOf(mapOf("x" to 10)))

    NodeInputReader(filePath).use { reader ->
      assertEquals(10, reader.read()!!["x"])
      assertNull(reader.read())

      reader.reset()
      assertEquals(10, reader.read()!!["x"])
      assertNull(reader.read())

      reader.reset()
      assertEquals(10, reader.read()!!["x"])
      assertNull(reader.read())
    }
  }

  @Test
  fun `reset mid-stream restarts from beginning`() {
    val filePath = writeParquetFile(listOf(
      mapOf("i" to 1),
      mapOf("i" to 2),
      mapOf("i" to 3)
    ))

    NodeInputReader(filePath).use { reader ->
      // Read only first row, then reset
      assertEquals(1, reader.read()!!["i"])
      reader.reset()

      // Should start from the beginning
      assertEquals(1, reader.read()!!["i"])
      assertEquals(2, reader.read()!!["i"])
      assertEquals(3, reader.read()!!["i"])
      assertNull(reader.read())
    }
  }

  @Test
  fun `close prevents further reads`() {
    val filePath = writeParquetFile(listOf(mapOf("a" to 1)))

    val reader = NodeInputReader(filePath)
    reader.close()

    assertThrows<IllegalStateException> {
      reader.read()
    }
  }

  @Test
  fun `close prevents reset`() {
    val filePath = writeParquetFile(listOf(mapOf("a" to 1)))

    val reader = NodeInputReader(filePath)
    reader.close()

    assertThrows<IllegalStateException> {
      reader.reset()
    }
  }

  @Test
  fun `close is idempotent`() {
    val filePath = writeParquetFile(listOf(mapOf("a" to 1)))

    val reader = NodeInputReader(filePath)
    reader.close()
    // Second close should not throw
    assertDoesNotThrow { reader.close() }
  }

  @Test
  fun `read preserves various data types`() {
    val inputRow = mapOf(
      "stringVal" to "hello",
      "intVal" to 42,
      "longVal" to 9876543210L,
      "floatVal" to 1.5f,
      "doubleVal" to 3.14,
      "boolVal" to false
    )
    val filePath = writeParquetFile(listOf(inputRow))

    NodeInputReader(filePath).use { reader ->
      val row = reader.read()!!
      assertEquals("hello", row["stringVal"])
      assertEquals(42, row["intVal"])
      assertTrue(row["intVal"] is Int)
      assertEquals(9876543210L, row["longVal"])
      assertTrue(row["longVal"] is Long)
      assertEquals(1.5f, row["floatVal"])
      assertTrue(row["floatVal"] is Float)
      assertEquals(3.14, row["doubleVal"])
      assertTrue(row["doubleVal"] is Double)
      assertEquals(false, row["boolVal"])
      assertTrue(row["boolVal"] is Boolean)
    }
  }

  @Test
  fun `read large number of rows`() {
    val rows = (0 until 1000).map { i ->
      mapOf("index" to i, "value" to "row-$i")
    }
    val filePath = writeParquetFile(rows)

    NodeInputReader(filePath).use { reader ->
      assertEquals(1000L, reader.getRowCount())

      var count = 0
      while (reader.read() != null) {
        count++
      }
      assertEquals(1000, count)
    }
  }
}

