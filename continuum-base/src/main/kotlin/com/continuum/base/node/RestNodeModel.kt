package com.continuum.base.node

import com.continuum.core.commons.exception.NodeRuntimeException
import com.continuum.core.commons.model.ContinuumWorkflowModel
import com.continuum.core.commons.node.ProcessNodeModel
import com.continuum.core.commons.prototol.progress.NodeProgressCallback
import com.continuum.core.commons.utils.NodeInputReader
import com.continuum.core.commons.utils.NodeOutputWriter
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import freemarker.template.Configuration
import freemarker.template.Template
import freemarker.template.TemplateExceptionHandler
import io.temporal.activity.Activity
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType.TEXT_PLAIN_VALUE
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.io.StringReader
import java.io.StringWriter

@Component
class RestNodeModel(
  private val restTemplate: RestTemplate
) : ProcessNodeModel() {
  companion object {
    private val LOGGER = LoggerFactory.getLogger(RestNodeModel::class.java)
    private val objectMapper = ObjectMapper()

    private val freemarkerConfig = Configuration(Configuration.VERSION_2_3_32).apply {
      defaultEncoding = "UTF-8"
      templateExceptionHandler = TemplateExceptionHandler.RETHROW_HANDLER
      logTemplateExceptions = false
      wrapUncheckedExceptions = true
      fallbackOnNullLoopVariable = false
      numberFormat = "computer"  // Outputs: 1000, 100, not 1,000, 1,00
    }

    // Progress reporting interval in milliseconds (report every 5 seconds)
    private const val PROGRESS_REPORT_INTERVAL_MS = 500L
  }

  final override val inputPorts = mapOf(
    "data" to ContinuumWorkflowModel.NodePort(
      name = "input table",
      contentType = TEXT_PLAIN_VALUE
    )
  )

  final override val outputPorts = mapOf(
    "data" to ContinuumWorkflowModel.NodePort(
      name = "table with responses",
      contentType = TEXT_PLAIN_VALUE
    )
  )

  override val categories = listOf(
    "Integration & API"
  )

  val propertiesSchema: Map<String, Any> = objectMapper.readValue(
    """
        {
          "type": "object",
          "properties": {
            "method": {
              "type": "string",
              "title": "HTTP Method",
              "description": "HTTP method to use",
              "enum": ["GET", "POST", "PUT", "DELETE"],
              "default": "GET"
            },
            "url": {
              "type": "string",
              "title": "URL Template",
              "description": "FreeMarker template for URL (e.g., https://api.example.com?id=${'$'}{row.id})"
            },
            "payload": {
              "type": "string",
              "title": "Payload Template",
              "description": "FreeMarker template for request body (e.g., {\"name\": \"${'$'}{row.name}\"})"
            }
          },
          "required": ["method", "url"]
        }
        """.trimIndent(),
    object : TypeReference<Map<String, Any>>() {}
  )

  val propertiesUiSchema: Map<String, Any> = objectMapper.readValue(
    """
        {
          "type": "VerticalLayout",
          "elements": [
            {
              "type": "Control",
              "scope": "#/properties/method"
            },
            {
              "type": "Control",
              "scope": "#/properties/url",
              "options": {
                "format": "code",
                "language": "freemarker2",
                "rows": 1
              }
            },
            {
              "type": "Control",
              "scope": "#/properties/payload",
              "options": {
                "format": "code",
                "language": "freemarker2",
                "rows": 10
              },
              "rule": {
                "effect": "HIDE",
                "condition": {
                  "scope": "#/properties/method",
                  "schema": {
                    "enum": ["GET", "DELETE"]
                  }
                }
              }
            }
          ]
        }
        """.trimIndent(),
    object : TypeReference<Map<String, Any>>() {}
  )

  override val metadata = ContinuumWorkflowModel.NodeData(
    id = this.javaClass.name,
    description = "Makes HTTP requests for each row using FreeMarker templated URLs and payloads",
    title = "REST Node",
    subTitle = "HTTP client with FreeMarker templates",
    nodeModel = this.javaClass.name,
    icon = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" strokeWidth={1.5} viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 21a9.004 9.004 0 008.716-6.747M12 21a9.004 9.004 0 01-8.716-6.747M12 21c2.485 0 4.5-4.03 4.5-9S14.485 3 12 3m0 18c-2.485 0-4.5-4.03-4.5-9S9.515 3 12 3m0 0a8.997 8.997 0 017.843 4.582M12 3a8.997 8.997 0 00-7.843 4.582m15.686 0A11.953 11.953 0 0112 10.5c-2.998 0-5.74-1.1-7.843-2.918m15.686 0A8.959 8.959 0 0121 12c0 .778-.099 1.533-.284 2.253m0 0A17.919 17.919 0 0112 16.5c-3.162 0-6.133-.815-8.716-2.247m0 0A9.015 9.015 0 013 12c0-1.605.42-3.113 1.157-4.418" />
            </svg>
        """.trimIndent(),
    inputs = inputPorts,
    outputs = outputPorts,
    properties = mapOf(
      "method" to "GET",
      "url" to "https://api.example.com/data?id=${'$'}{row.id}",
      "payload" to ""
    ),
    propertiesSchema = propertiesSchema,
    propertiesUISchema = propertiesUiSchema
  )

  private fun renderTemplate(templateString: String, row: Map<String, Any>): String {
    return try {
      val template = Template("template", StringReader(templateString), freemarkerConfig)
      val dataModel = mapOf("row" to row)
      val writer = StringWriter()
      template.process(dataModel, writer)
      writer.toString()
    } catch (e: Exception) {
      LOGGER.error("Failed to render template: ${e.message}")
      throw NodeRuntimeException(
        workflowId = "",
        nodeId = "",
        message = "Template rendering failed: ${e.message}"
      )
    }
  }

  /**
   * Calculates the progress percentage based on the number of rows processed.
   *
   * @param rowsProcessed The number of rows that have been processed
   * @param totalRows The total number of rows to process
   * @return Progress percentage (0-99), capped at 99 during processing to ensure 100% is only reported at completion
   */
  private fun calculateProgressPercentage(rowsProcessed: Long, totalRows: Long?): Int {
    return if (totalRows != null && totalRows > 0) {
      (rowsProcessed * 100 / totalRows).toInt().coerceAtMost(99)
    } else {
      0
    }
  }

  /**
   * This method is required by the base class but is not used in this implementation.
   * The main logic is implemented in the overloaded execute method that includes the NodeProgressCallback.
   * This is to maintain compatibility with the base class while allowing for progress reporting in the main execute method.
   *
   * If the base class calls this method, it will simply do nothing, as the actual execution logic is in the other execute method.
   */
  override fun execute(
    properties: Map<String, Any>?,
    inputs: Map<String, NodeInputReader>,
    nodeOutputWriter: NodeOutputWriter
  ) {}

  override fun execute(
    properties: Map<String, Any>?,
    inputs: Map<String, NodeInputReader>,
    nodeOutputWriter: NodeOutputWriter,
    nodeProgressCallback: NodeProgressCallback?
  ) {
    // nodeProgressCallback must not be null for this node, as we want to report progress
    if(nodeProgressCallback == null) {
      throw NodeRuntimeException(
        isRetriable = true,
        workflowId = Activity.getExecutionContext().info.workflowId,
        nodeId = this.metadata.id ?: "",
        message = "NodeProgressCallback is required for RestNodeModel"
      )
    }
    val method = properties?.get("method") as String? ?: throw NodeRuntimeException(
      workflowId = "",
      nodeId = "",
      message = "method is not provided"
    )
    val urlTemplate = properties["url"] as String? ?: throw NodeRuntimeException(
      workflowId = "",
      nodeId = "",
      message = "url is not provided"
    )
    val payloadTemplate = properties["payload"] as String? ?: ""

    var lastProgressReportTime = System.currentTimeMillis()

    LOGGER.info("REST Node: method=$method, urlTemplate=$urlTemplate")
    val totalRowCount = inputs["data"]?.getRowCount()
    nodeOutputWriter.createOutputPortWriter("data").use { writer ->
      inputs["data"]?.use { reader ->
        var row = reader.read()
        var rowNumber = 0L

        while (row != null) {
          // Fake random delay
          Thread.sleep((2000..5000).random().toLong())
          try {
            // Render templates
            val url = renderTemplate(urlTemplate, row)
            val payload = renderTemplate(payloadTemplate, row)

            // Validate URL
            require(url.startsWith("http://") || url.startsWith("https://")) {
              "URL must use HTTP or HTTPS protocol: $url"
            }

            LOGGER.debug("Making $method request to: $url")

            // Build request headers
            val headers = HttpHeaders().apply {
              set("Content-Type", "application/json")
            }

            // Build request entity with payload (if applicable)
            val requestEntity = if (payload.isNotEmpty() && method.uppercase() in listOf("POST", "PUT")) {
              HttpEntity(payload, headers)
            } else {
              HttpEntity<String>(headers)
            }

            // Convert method string to HttpMethod enum
            val httpMethod = when (method.uppercase()) {
              "GET" -> HttpMethod.GET
              "POST" -> HttpMethod.POST
              "PUT" -> HttpMethod.PUT
              "DELETE" -> HttpMethod.DELETE
              else -> throw NodeRuntimeException(
                workflowId = "",
                nodeId = "",
                message = "Unsupported HTTP method: $method"
              )
            }

            // Execute request
            val response: ResponseEntity<String> = restTemplate.exchange(
              url,
              httpMethod,
              requestEntity,
              String::class.java
            )

            // Add response to row
            val newRow = row.toMutableMap().apply {
              this["response"] = mapOf(
                "status" to response.statusCodeValue,
                "body" to response.body
              )
            }

            writer.write(rowNumber, newRow)

          } catch (e: Exception) {
            LOGGER.error("Failed to make HTTP request for row $rowNumber: ${e.message}")

            // Add error response to row with error type
            val errorType = when (e) {
              is org.springframework.web.client.ResourceAccessException -> {
                when (e.cause) {
                  is java.net.ConnectException -> "connection_refused"
                  is java.net.UnknownHostException -> "unknown_host"
                  is java.net.SocketTimeoutException -> "timeout"
                  else -> "connection_error"
                }
              }
              is org.springframework.web.client.HttpClientErrorException -> "client_error"
              is org.springframework.web.client.HttpServerErrorException -> "server_error"
              is NodeRuntimeException -> "validation_error"
              else -> "unknown_error"
            }

            val newRow = row.toMutableMap().apply {
              this["response"] = mapOf(
                "status" to -1,
                "body" to "Error: ${e.message}",
                "errorType" to errorType
              )
            }

            writer.write(rowNumber, newRow)
          }

          rowNumber++

          // Report progress only every X seconds
          val currentTime = System.currentTimeMillis()
          if (currentTime - lastProgressReportTime >= PROGRESS_REPORT_INTERVAL_MS) {
            val progressPercentage = calculateProgressPercentage(rowNumber, totalRowCount)
            nodeProgressCallback.report(progressPercentage)
            lastProgressReportTime = currentTime
            LOGGER.debug("Progress: $progressPercentage% ($rowNumber/$totalRowCount rows processed)")
          }

          row = reader.read()
        }

        // Report final progress (100%)
        nodeProgressCallback.report(100)
        LOGGER.info("Processed $rowNumber HTTP requests")
      }
    }
  }
}
