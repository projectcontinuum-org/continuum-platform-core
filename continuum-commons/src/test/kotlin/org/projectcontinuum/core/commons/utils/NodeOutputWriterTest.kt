package org.projectcontinuum.core.commons.utils

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class NodeOutputWriterTest {

  @TempDir
  lateinit var tempDir: Path

  @Test
  fun `createOutputPortWriter creates writer and file`() {
    val writer = NodeOutputWriter(tempDir)
    val portWriter = writer.createOutputPortWriter("port-0")

    portWriter.write(0L, mapOf("name" to "Alice", "age" to 30))
    portWriter.close()

    val outputFile = tempDir.resolve("output.port-0.parquet")
    assertTrue(outputFile.toFile().exists(), "Parquet output file should exist")
  }

  @Test
  fun `write single row and verify table spec`() {
    val writer = NodeOutputWriter(tempDir)
    val portWriter = writer.createOutputPortWriter("port-0")

    portWriter.write(0L, mapOf("name" to "Alice", "age" to 30))
    portWriter.close()

    val spec = writer.getTableSpec("port-0")
    assertEquals(2, spec.size)

    val specByName = spec.associateBy { it["name"] }
    assertEquals("application/vnd.continuum.x-string", specByName["name"]?.get("contentType"))
    assertEquals("application/vnd.continuum.x-int", specByName["age"]?.get("contentType"))
  }

  @Test
  fun `write multiple rows preserves all data via round-trip`() {
    val writer = NodeOutputWriter(tempDir)
    val portWriter = writer.createOutputPortWriter("port-0")

    val rows = listOf(
      mapOf("name" to "Alice", "score" to 95.5),
      mapOf("name" to "Bob", "score" to 87.3),
      mapOf("name" to "Charlie", "score" to 92.1)
    )

    rows.forEachIndexed { index, row ->
      portWriter.write(index.toLong(), row)
    }
    portWriter.close()

    // Read back using NodeInputReader to verify round-trip
    val reader = NodeInputReader(tempDir.resolve("output.port-0.parquet"))
    val readRows = mutableListOf<Map<String, Any>>()
    var row = reader.read()
    while (row != null) {
      readRows.add(row)
      row = reader.read()
    }
    reader.close()

    assertEquals(3, readRows.size)
    assertEquals("Alice", readRows[0]["name"])
    assertEquals(95.5, readRows[0]["score"])
    assertEquals("Bob", readRows[1]["name"])
    assertEquals("Charlie", readRows[2]["name"])
  }

  @Test
  fun `multiple output ports are independent`() {
    val writer = NodeOutputWriter(tempDir)

    val portWriter1 = writer.createOutputPortWriter("port-0")
    portWriter1.write(0L, mapOf("x" to 1))
    portWriter1.close()

    val portWriter2 = writer.createOutputPortWriter("port-1")
    portWriter2.write(0L, mapOf("y" to "hello", "z" to true))
    portWriter2.close()

    val spec1 = writer.getTableSpec("port-0")
    assertEquals(1, spec1.size)
    assertEquals("x", spec1[0]["name"])

    val spec2 = writer.getTableSpec("port-1")
    assertEquals(2, spec2.size)
    val spec2ByName = spec2.associateBy { it["name"] }
    assertEquals("application/vnd.continuum.x-string", spec2ByName["y"]?.get("contentType"))
    assertEquals("application/vnd.continuum.x-boolean", spec2ByName["z"]?.get("contentType"))
  }

  @Test
  fun `getTableSpec throws for unknown port`() {
    val writer = NodeOutputWriter(tempDir)

    val exception = assertThrows<RuntimeException> {
      writer.getTableSpec("nonexistent")
    }
    assertTrue(exception.message!!.contains("No table spec for port nonexistent"))
  }

  @Test
  fun `table spec deduplicates columns across rows`() {
    val writer = NodeOutputWriter(tempDir)
    val portWriter = writer.createOutputPortWriter("port-0")

    // Write multiple rows with the same columns — spec should not duplicate
    portWriter.write(0L, mapOf("name" to "Alice"))
    portWriter.write(1L, mapOf("name" to "Bob"))
    portWriter.close()

    val spec = writer.getTableSpec("port-0")
    assertEquals(1, spec.size)
    assertEquals("name", spec[0]["name"])
  }

  @Test
  fun `write various data types`() {
    val writer = NodeOutputWriter(tempDir)
    val portWriter = writer.createOutputPortWriter("port-0")

    val row = mapOf(
      "stringVal" to "hello",
      "intVal" to 42,
      "longVal" to 123456789L,
      "floatVal" to 3.14f,
      "doubleVal" to 2.718,
      "boolVal" to true
    )
    portWriter.write(0L, row)
    portWriter.close()

    val spec = writer.getTableSpec("port-0")
    val specByName = spec.associateBy { it["name"] }

    assertEquals("application/vnd.continuum.x-string", specByName["stringVal"]?.get("contentType"))
    assertEquals("application/vnd.continuum.x-int", specByName["intVal"]?.get("contentType"))
    assertEquals("application/vnd.continuum.x-long", specByName["longVal"]?.get("contentType"))
    assertEquals("application/vnd.continuum.x-float", specByName["floatVal"]?.get("contentType"))
    assertEquals("application/vnd.continuum.x-double", specByName["doubleVal"]?.get("contentType"))
    assertEquals("application/vnd.continuum.x-boolean", specByName["boolVal"]?.get("contentType"))
  }
}

