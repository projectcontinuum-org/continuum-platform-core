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
import java.security.MessageDigest

@Component
class CryptoHasherNodeModel: ProcessNodeModel() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(CryptoHasherNodeModel::class.java)
        private val objectMapper = ObjectMapper()
    }

    final override val inputPorts = mapOf(
        "data" to ContinuumWorkflowModel.NodePort(
            name = "input table",
            contentType = TEXT_PLAIN_VALUE
        )
    )

    final override val outputPorts = mapOf(
        "data" to ContinuumWorkflowModel.NodePort(
            name = "hashed table",
            contentType = TEXT_PLAIN_VALUE
        )
    )

    override val categories = listOf(
        "Security & Encryption"
    )

    override val documentationMarkdown = """
        # Crypto Hasher
        
        Generates SHA-256 cryptographic hashes of text values, useful for data integrity verification, deduplication, and security workflows.
        
        ## Input Ports
        - **data**: Input table with text column to hash
        
        ## Output Ports
        - **data**: Input table with added hash column
        
        ## Properties
        - **inputCol** (string, required): Column containing text to hash
        - **outputCol** (string, required): Column name for the hash output
        
        ## Behavior
        For each row:
        1. Extracts text from `inputCol` (empty string if null)
        2. Converts to UTF-8 bytes
        3. Computes SHA-256 hash using `java.security.MessageDigest`
        4. Formats as lowercase hexadecimal string (64 characters)
        5. Adds hash to new column `outputCol`
        6. Preserves original column
        
        **Properties:**
        - Algorithm: SHA-256 (256-bit)
        - Output: 64-character lowercase hex string
        - Encoding: UTF-8
        - Deterministic: Same input always produces same hash
        
        ## Use Cases
        - Data integrity verification
        - Deduplication (hash as unique key)
        - Password hashing (though SHA-256 alone not recommended for passwords)
        - Data anonymization
        - Change detection
        
        ## Security Note
        SHA-256 is cryptographically secure but not suitable for password storage without additional measures (salt, key stretching). Use bcrypt, PBKDF2, or Argon2 for passwords.
        
        ## Example
        
        **Input:**
        ```json
        [
          {"id": 1, "text": "hello"},
          {"id": 2, "text": "world"}
        ]
        ```
        
        **Properties:**
        ```json
        {
          "inputCol": "text",
          "outputCol": "hash"
        }
        ```
        
        **Output:**
        ```json
        [
          {
            "id": 1,
            "text": "hello",
            "hash": "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
          },
          {
            "id": 2,
            "text": "world",
            "hash": "486ea46224d1bb4fb680f34f7c9ad96a8f24ec88be73ea8e5a6c65260e9cb8a7"
          }
        ]
        ```
        
        The hash values are deterministic: hashing "hello" always produces the same 64-character hash.
    """.trimIndent()

    val propertiesSchema: Map<String, Any> = objectMapper.readValue(
        """
        {
          "type": "object",
          "properties": {
            "inputCol": {
              "type": "string",
              "title": "Input Column",
              "description": "The column containing text to hash"
            },
            "outputCol": {
              "type": "string",
              "title": "Output Column",
              "description": "The column to write SHA-256 hash to"
            }
          },
          "required": ["inputCol", "outputCol"]
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
              "scope": "#/properties/inputCol"
            },
            {
              "type": "Control",
              "scope": "#/properties/outputCol"
            }
          ]
        }
        """.trimIndent(),
        object: TypeReference<Map<String, Any>>() {}
    )

    override val metadata = ContinuumWorkflowModel.NodeData(
        id = this.javaClass.name,
        description = "Generates SHA-256 hash of column values",
        title = "Crypto Hasher",
        subTitle = "SHA-256 hashing",
        nodeModel = this.javaClass.name,
        icon = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" strokeWidth={1.5} viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" d="M16.5 10.5V6.75a4.5 4.5 0 10-9 0v3.75m-.75 11.25h10.5a2.25 2.25 0 002.25-2.25v-6.75a2.25 2.25 0 00-2.25-2.25H6.75a2.25 2.25 0 00-2.25 2.25v6.75a2.25 2.25 0 002.25 2.25z" />
            </svg>
        """.trimIndent(),
        inputs = inputPorts,
        outputs = outputPorts,
        properties = mapOf(
            "inputCol" to "text",
            "outputCol" to "hash"
        ),
        propertiesSchema = propertiesSchema,
        propertiesUISchema = propertiesUiSchema
    )

    override fun execute(
        properties: Map<String, Any>?,
        inputs: Map<String, NodeInputReader>,
        nodeOutputWriter: NodeOutputWriter
    ) {
        val inputCol = properties?.get("inputCol") as String? ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "inputCol is not provided"
        )
        val outputCol = properties["outputCol"] as String? ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "outputCol is not provided"
        )
        
        LOGGER.info("Hashing column '$inputCol' to '$outputCol' using SHA-256")
        
        val digest = try {
            MessageDigest.getInstance("SHA-256")
        } catch (e: Exception) {
            throw NodeRuntimeException(
                workflowId = "",
                nodeId = "",
                message = "Failed to initialize SHA-256: ${e.message}"
            )
        }
        
        nodeOutputWriter.createOutputPortWriter("data").use { writer ->
            inputs["data"]?.use { reader ->
                var row = reader.read()
                var rowNumber = 0L
                
                while (row != null) {
                    try {
                        val text = row[inputCol]?.toString() ?: ""
                        val hashBytes = digest.digest(text.toByteArray(Charsets.UTF_8))
                        val hash = hashBytes.joinToString("") { "%02x".format(it) }
                        
                        val newRow = row.toMutableMap().apply {
                            this[outputCol] = hash
                        }
                        
                        writer.write(rowNumber, newRow)
                        rowNumber++
                    } catch (e: Exception) {
                        LOGGER.error("Failed to hash row $rowNumber: ${e.message}")
                        throw NodeRuntimeException(
                            workflowId = "",
                            nodeId = "",
                            message = "Failed to hash row $rowNumber: ${e.message}"
                        )
                    }
                    
                    row = reader.read()
                }
                
                LOGGER.info("Hashed $rowNumber rows")
            }
        }
    }
}
