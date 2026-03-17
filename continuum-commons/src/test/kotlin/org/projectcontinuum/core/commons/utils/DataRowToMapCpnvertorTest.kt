package org.projectcontinuum.core.commons.utils

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.w3c.dom.Document
import org.xml.sax.InputSource
import java.io.StringReader
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult


class DataRowToMapConverterTest {

  private val converter = DataRowToMapConverter()

  @Test
  fun stringConverterTest() {
    val dataRow = converter.toDataRow(
      rowNumber = 1,
      data = mapOf(
        "name" to "John Doe"
      )
    )

    assertEquals(
      1L,
      dataRow.rowNumber
    )
    assertEquals(
      "application/vnd.continuum.x-string",
      dataRow.cells[0].contentType
    )
    assertEquals(
      "name",
      dataRow.cells[0].name
    )
    assertEquals(
      "John Doe",
      String(dataRow.cells[0].value.array())
    )

    val rowMap = converter.toMap(dataRow)

    assert(
      rowMap.containsKey("name")
    )

    assertEquals(
      "John Doe",
      rowMap["name"]
    )
  }

  @Test
  fun intConverterTest() {
    val dataRow = converter.toDataRow(
      rowNumber = 1,
      data = mapOf(
        "age" to 30
      )
    )

    assertEquals(
      1L,
      dataRow.rowNumber
    )
    assertEquals(
      "application/vnd.continuum.x-int",
      dataRow.cells[0].contentType
    )
    assertEquals(
      "age",
      dataRow.cells[0].name
    )
    assertEquals(
      "30",
      String(dataRow.cells[0].value.array())
    )

    val rowMap = converter.toMap(dataRow)

    assert(
      rowMap.containsKey("age")
    )

    assert(
      rowMap["age"] is Int
    )

    assertEquals(
      30,
      rowMap["age"]
    )
  }

  @Test
  fun longConverterTest() {
    val dataRow = converter.toDataRow(
      rowNumber = 1,
      data = mapOf(
        "id" to 1234567890123L
      )
    )

    assertEquals(
      1L,
      dataRow.rowNumber
    )
    assertEquals(
      "application/vnd.continuum.x-long",
      dataRow.cells[0].contentType
    )
    assertEquals(
      "id",
      dataRow.cells[0].name
    )
    assertEquals(
      "1234567890123",
      String(dataRow.cells[0].value.array())
    )

    val rowMap = converter.toMap(dataRow)

    assert(
      rowMap.containsKey("id")
    )

    assert(
      rowMap["id"] is Long
    )

    assertEquals(
      1234567890123L,
      rowMap["id"]
    )
  }

  @Test
  fun floatConverterTest() {
    val dataRow = converter.toDataRow(
      rowNumber = 1,
      data = mapOf(
        "height" to 5.9f
      )
    )

    assertEquals(
      1L,
      dataRow.rowNumber
    )
    assertEquals(
      "application/vnd.continuum.x-float",
      dataRow.cells[0].contentType
    )
    assertEquals(
      "height",
      dataRow.cells[0].name
    )
    assertEquals(
      "5.9",
      String(dataRow.cells[0].value.array())
    )

    val rowMap = converter.toMap(dataRow)

    assert(
      rowMap.containsKey("height")
    )

    assert(
      rowMap["height"] is Float
    )

    assertEquals(
      5.9f,
      rowMap["height"]
    )
  }

  @Test
  fun doubleConverterTest() {
    val dataRow = converter.toDataRow(
      rowNumber = 1,
      data = mapOf(
        "weight" to 70.5
      )
    )

    assertEquals(
      1L,
      dataRow.rowNumber
    )
    assertEquals(
      "application/vnd.continuum.x-double",
      dataRow.cells[0].contentType
    )
    assertEquals(
      "weight",
      dataRow.cells[0].name
    )
    assertEquals(
      "70.5",
      String(dataRow.cells[0].value.array())
    )

    val rowMap = converter.toMap(dataRow)

    assert(
      rowMap.containsKey("weight")
    )

    assert(
      rowMap["weight"] is Double
    )

    assertEquals(
      70.5,
      rowMap["weight"]
    )
  }

  @Test
  fun booleanConverterTest() {
    val dataRow = converter.toDataRow(
      rowNumber = 1,
      data = mapOf(
        "isActive" to true
      )
    )

    assertEquals(
      1L,
      dataRow.rowNumber
    )
    assertEquals(
      "application/vnd.continuum.x-boolean",
      dataRow.cells[0].contentType
    )
    assertEquals(
      "isActive",
      dataRow.cells[0].name
    )
    assertEquals(
      "true",
      String(dataRow.cells[0].value.array())
    )

    val rowMap = converter.toMap(dataRow)

    assert(
      rowMap.containsKey("isActive")
    )

    assert(
      rowMap["isActive"] is Boolean
    )

    assertEquals(
      true,
      rowMap["isActive"]
    )
  }

  @Test
  fun listConverterTest() {
    val objectMapper = jacksonObjectMapper()
    val dataRow = converter.toDataRow(
      rowNumber = 1,
      data = mapOf(
        "tags" to listOf("tag1", "tag2", "tag3")
      )
    )

    assertEquals(
      1L,
      dataRow.rowNumber
    )
    assertEquals(
      "application/json",
      dataRow.cells[0].contentType
    )
    assertEquals(
      "tags",
      dataRow.cells[0].name
    )
    assertEquals(
      objectMapper.readTree("[\"tag1\", \"tag2\", \"tag3\"]"),
      objectMapper.readTree(String(dataRow.cells[0].value.array()))
    )

    val rowMap = converter.toMap(dataRow)

    assert(
      rowMap.containsKey("tags")
    )

    assert(
      rowMap["tags"] is List<*>
    )

    assertEquals(
      listOf("tag1", "tag2", "tag3"),
      rowMap["tags"]
    )
  }

  @Test
  fun mapConverterTest() {
    val objectMapper = jacksonObjectMapper()
    val dataRow = converter.toDataRow(
      rowNumber = 1,
      data = mapOf(
        "address" to mapOf("city" to "New York", "zip" to "10001")
      )
    )

    assertEquals(
      1L,
      dataRow.rowNumber
    )
    assertEquals(
      "application/json",
      dataRow.cells[0].contentType
    )
    assertEquals(
      "address",
      dataRow.cells[0].name
    )
    assertEquals(
      objectMapper.readTree("{\"city\":\"New York\",\"zip\":\"10001\"}"),
      objectMapper.readTree(String(dataRow.cells[0].value.array()))
    )

    val rowMap = converter.toMap(dataRow)

    assert(
      rowMap.containsKey("address")
    )

    assert(
      rowMap["address"] is Map<*, *>
    )

    assertEquals(
      mapOf("city" to "New York", "zip" to "10001"),
      rowMap["address"]
    )
  }

  @Test
  fun unsupportedTypeTest() {
    assertThrows<IllegalArgumentException> {
      converter.toDataRow(
        rowNumber = 1,
        data = mapOf(
          "unsupported" to object {}
        )
      )
    }
  }

  @Test
  fun svgDocumentConverterTest() {
    val svgString = """<svg xmlns="http://www.w3.org/2000/svg" width="300" height="300"><circle cx="150" cy="150" r="100"/></svg>"""
    val svgDocument = parseSvg(svgString)

    val dataRow = converter.toDataRow(
      rowNumber = 1,
      data = mapOf(
        "molecule_svg" to svgDocument
      )
    )

    assertEquals(
      1L,
      dataRow.rowNumber
    )
    assertEquals(
      "image/svg+xml",
      dataRow.cells[0].contentType
    )
    assertEquals(
      "molecule_svg",
      dataRow.cells[0].name
    )

    // The serialized value should contain the SVG content
    val serialized = String(dataRow.cells[0].value.array())
    assert(serialized.contains("<svg"))
    assert(serialized.contains("<circle"))
    assert(serialized.contains("</svg>"))

    // Round-trip: deserialize back to Document
    val rowMap = converter.toMap(dataRow)

    assert(
      rowMap.containsKey("molecule_svg")
    )

    assert(
      rowMap["molecule_svg"] is Document
    )

    // Verify the Document still has the SVG content
    val roundTripped = rowMap["molecule_svg"] as Document
    val roundTrippedString = documentToString(roundTripped)
    assert(roundTrippedString.contains("<svg"))
    assert(roundTrippedString.contains("<circle"))
    assert(roundTrippedString.contains("</svg>"))
  }

  private fun parseSvg(svgString: String): Document {
    val factory = DocumentBuilderFactory.newInstance()
    factory.isNamespaceAware = true
    val builder = factory.newDocumentBuilder()
    return builder.parse(InputSource(StringReader(svgString)))
  }

  private fun documentToString(doc: Document): String {
    val transformer = TransformerFactory.newInstance().newTransformer()
    val writer = StringWriter()
    transformer.transform(DOMSource(doc), StreamResult(writer))
    return writer.toString()
  }

}