package com.continuum.base.node

import com.continuum.core.commons.exception.NodeRuntimeException
import com.continuum.core.commons.model.ContinuumWorkflowModel
import com.continuum.core.commons.node.ProcessNodeModel
import com.continuum.core.commons.utils.NodeInputReader
import com.continuum.core.commons.utils.NodeOutputWriter
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType.TEXT_PLAIN_VALUE
import org.springframework.stereotype.Component
import freemarker.template.Configuration
import freemarker.template.Template
import freemarker.template.TemplateExceptionHandler
import java.io.StringReader
import java.io.StringWriter
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@Component
class RestNodeModel: ProcessNodeModel() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(RestNodeModel::class.java)
        private val objectMapper = ObjectMapper()
        private val httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
        
        private val freemarkerConfig = Configuration(Configuration.VERSION_2_3_32).apply {
            defaultEncoding = "UTF-8"
            templateExceptionHandler = TemplateExceptionHandler.RETHROW_HANDLER
            logTemplateExceptions = false
            wrapUncheckedExceptions = true
            fallbackOnNullLoopVariable = false
            numberFormat = "computer"  // Outputs: 1000, 100, not 1,000, 1,00
        }
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

    override val documentationMarkdown = """
        # REST Node
        
        Makes HTTP requests for each row using FreeMarker templates to dynamically construct URLs and payloads from row data.
        
        ## Input Ports
        | Port | Type | Format | Description |
        |------|------|--------|-------------|
        | data | Table | List<Map<String, Any>> | Input table where each row triggers an HTTP request |
        
        ## Output Ports
        | Port | Type | Format | Description |
        |------|------|--------|-------------|
        | data | Table | List<Map<String, Any>> | Input table plus response column with {status, body} |
        
        ## Properties
        - **method** (string, required): HTTP method - GET, POST, PUT, or DELETE
        - **url** (string, required): FreeMarker template for URL (e.g., `https://api.example.com/user/${'$'}{row.id}`)
        - **payload** (string, optional): FreeMarker template for request body
        
        ## Behavior
        For each row:
        1. Renders `url` template with row data using FreeMarker
        2. Renders `payload` template (if provided)
        3. Builds HTTP request with:
           - Method: GET, POST, PUT, or DELETE
           - URL: Rendered from template
           - Body: Rendered payload (POST/PUT only)
           - Header: Content-Type set to application/json
        4. Executes synchronous HTTP request
        5. Adds `response` object to row:
           - `status` (integer): HTTP status code
           - `body` (string): Response body
        
        **Error Handling:**
        - On failure: Returns status=-1 with error message in body
        - Template errors throw NodeRuntimeException
        
        ## FreeMarker Template Syntax
        
        Access row fields using `${'$'}{row.fieldName}`:
        
        **Simple field access:**
        ```
        https://api.example.com/user/${'$'}{row.userId}
        ```
        
        **Multiple fields:**
        ```
        https://api.example.com/search?q=${'$'}{row.query}&page=${'$'}{row.page}
        ```
        
        **Conditionals:**
        ```
        <#if row.premium>https://api.premium.com<#else>https://api.free.com</#if>
        ```
        
        **JSON payload:**
        ```json
        {
          "name": "${'$'}{row.name}",
          "age": ${'$'}{row.age},
          "active": <#if row.active>true<#else>false</#if>
        }
        ```
        
        ## Example 1: Simple GET Request
        
        **Input:**
        ```json
        [
          {"userId": 1, "query": "hello"},
          {"userId": 2, "query": "world"}
        ]
        ```
        
        **Properties:**
        ```json
        {
          "method": "GET",
          "url": "https://api.example.com/search?user=${'$'}{row.userId}&q=${'$'}{row.query}",
          "payload": ""
        }
        ```
        
        **Output:**
        ```json
        [
          {
            "userId": 1,
            "query": "hello",
            "response": {
              "status": 200,
              "body": "{\"results\": [...]}"
            }
          },
          {
            "userId": 2,
            "query": "world",
            "response": {
              "status": 200,
              "body": "{\"results\": [...]}"
            }
          }
        ]
        ```
        
        ## Example 2: POST with Payload
        
        **Properties:**
        ```json
        {
          "method": "POST",
          "url": "https://api.example.com/users",
          "payload": "{\"name\": \"${'$'}{row.name}\", \"email\": \"${'$'}{row.email}\"}"
        }
        ```
        
        This creates a new user via POST for each row.
    """.trimIndent()

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
        object: TypeReference<Map<String, Any>>() {}
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
        object: TypeReference<Map<String, Any>>() {}
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

    override fun execute(
        properties: Map<String, Any>?,
        inputs: Map<String, NodeInputReader>,
        nodeOutputWriter: NodeOutputWriter
    ) {
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
        
        LOGGER.info("REST Node: method=$method, urlTemplate=$urlTemplate")
        
        nodeOutputWriter.createOutputPortWriter("data").use { writer ->
            inputs["data"]?.use { reader ->
                var row = reader.read()
                var rowNumber = 0L
                
                while (row != null) {
                    try {
                        // Render templates
                        val url = renderTemplate(urlTemplate, row)
                        val payload = renderTemplate(payloadTemplate, row)
                        
                        LOGGER.debug("Making $method request to: $url")
                        
                        // Build request
                        val requestBuilder = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .header("Content-Type", "application/json")
                        
                        val request = when (method.uppercase()) {
                            "GET" -> requestBuilder.GET().build()
                            "POST" -> requestBuilder.POST(HttpRequest.BodyPublishers.ofString(payload)).build()
                            "PUT" -> requestBuilder.PUT(HttpRequest.BodyPublishers.ofString(payload)).build()
                            "DELETE" -> requestBuilder.DELETE().build()
                            else -> throw NodeRuntimeException(
                                workflowId = "",
                                nodeId = "",
                                message = "Unsupported HTTP method: $method"
                            )
                        }
                        
                        // Execute request
                        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                        
                        // Add response to row
                        val newRow = row.toMutableMap().apply {
                            this["response"] = mapOf(
                                "status" to response.statusCode(),
                                "body" to response.body()
                            )
                        }
                        
                        writer.write(rowNumber, newRow)
                        rowNumber++
                        
                    } catch (e: Exception) {
                        LOGGER.error("Failed to make HTTP request for row $rowNumber: ${e.message}")
                        
                        // Add error response to row
                        val newRow = row.toMutableMap().apply {
                            this["response"] = mapOf(
                                "status" to -1,
                                "body" to "Error: ${e.message}"
                            )
                        }
                        
                        writer.write(rowNumber, newRow)
                        rowNumber++
                    }
                    
                    row = reader.read()
                }
                
                LOGGER.info("Processed $rowNumber HTTP requests")
            }
        }
    }
}
