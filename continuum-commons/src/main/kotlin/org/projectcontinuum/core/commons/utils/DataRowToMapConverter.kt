package org.projectcontinuum.core.commons.utils

import org.projectcontinuum.core.protocol.data.table.DataCell
import org.projectcontinuum.core.protocol.data.table.DataRow
import com.fasterxml.jackson.databind.ObjectMapper
import org.w3c.dom.Document
import org.xml.sax.InputSource
import java.io.StringReader
import java.io.StringWriter
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

class DataRowToMapConverter {
  private val objectMapper = ObjectMapper()

  companion object {
    const val MIME_SVG = "image/svg+xml"
  }

  fun toDataRow(
    rowNumber: Long,
    data: Map<String, Any>
  ): DataRow {
    val dataCells = data.entries.map {
      createDataCell(it.key, it.value)
    }
    return DataRow.newBuilder()
      .setRowNumber(rowNumber)
      .setCells(dataCells)
      .build()
  }

  fun toMap(
    dataRow: DataRow
  ): Map<String, Any> {
    return dataRow.cells.associate { dataCell ->
      createMapEntry(dataCell)
    }
  }

  private fun createMapEntry(
    dataCell: DataCell
  ): Pair<String, Any> {
    val name = dataCell.name
    val valueString = String(dataCell.value.array(), StandardCharsets.UTF_8)
    val value = when (dataCell.contentType) {
      "application/vnd.continuum.x-string" -> valueString
      "application/vnd.continuum.x-int" -> valueString.toInt()
      "application/vnd.continuum.x-long" -> valueString.toLong()
      "application/vnd.continuum.x-float" -> valueString.toFloat()
      "application/vnd.continuum.x-double" -> valueString.toDouble()
      "application/vnd.continuum.x-boolean" -> valueString.toBoolean()
      "application/json" -> objectMapper.readValue(valueString, Any::class.java)
      MIME_SVG -> parseSvgDocument(valueString)
      else -> throw IllegalArgumentException("Unsupported content type: ${dataCell.contentType}")
    }
    return Pair(name, value)
  }

  private fun createDataCell(
    cellName: String,
    cellValue: Any
  ): DataCell {
    val mimeType = when {
      cellValue is Document && isSvgDocument(cellValue) -> MIME_SVG
      cellValue is String -> "application/vnd.continuum.x-string"
      cellValue is Int -> "application/vnd.continuum.x-int"
      cellValue is Long -> "application/vnd.continuum.x-long"
      cellValue is Float -> "application/vnd.continuum.x-float"
      cellValue is Double -> "application/vnd.continuum.x-double"
      cellValue is Boolean -> "application/vnd.continuum.x-boolean"
      cellValue is List<*> -> "application/json"
      cellValue is Map<*, *> -> "application/json"
      else -> throw IllegalArgumentException("Unsupported type: ${cellValue::class.java.name}")
    }
    val value = when {
      cellValue is Document && isSvgDocument(cellValue) -> serializeSvgDocument(cellValue)
      cellValue is String -> cellValue
      cellValue is Int -> cellValue.toString()
      cellValue is Long -> "$cellValue"
      cellValue is Float -> "$cellValue"
      cellValue is Double -> cellValue.toString()
      cellValue is Boolean -> cellValue.toString()
      cellValue is List<*> -> objectMapper.writeValueAsString(cellValue)
      cellValue is Map<*, *> -> objectMapper.writeValueAsString(cellValue)
      else -> throw IllegalArgumentException("Unsupported type: ${cellValue::class.java.name}")
    }
    return DataCell.newBuilder()
      .setName(cellName)
      .setValue(ByteBuffer.wrap(value.toString().toByteArray()))
      .setContentType(mimeType)
      .build()
  }

  /**
   * Checks whether a DOM Document is an SVG document by inspecting the root element.
   */
  private fun isSvgDocument(document: Document): Boolean {
    val root = document.documentElement ?: return false
    val localName = root.localName?.lowercase() ?: root.tagName?.lowercase() ?: return false
    return localName == "svg" || root.namespaceURI == "http://www.w3.org/2000/svg"
  }

  /**
   * Serializes a DOM Document to an SVG string.
   */
  private fun serializeSvgDocument(document: Document): String {
    val transformer = TransformerFactory.newInstance().newTransformer()
    transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
    transformer.setOutputProperty(OutputKeys.INDENT, "no")
    val writer = StringWriter()
    transformer.transform(DOMSource(document), StreamResult(writer))
    return writer.toString()
  }

  /**
   * Parses an SVG string into a DOM Document.
   */
  private fun parseSvgDocument(svgString: String): Document {
    val factory = DocumentBuilderFactory.newInstance()
    factory.isNamespaceAware = true
    val builder = factory.newDocumentBuilder()
    return builder.parse(InputSource(StringReader(svgString)))
  }
}