package com.continuum.core.commons.node

import com.continuum.core.commons.model.ContinuumWorkflowModel
import com.continuum.core.commons.utils.NodeInputReader
import com.continuum.core.commons.utils.NodeOutputWriter
import com.continuum.core.commons.utils.TemplateHelper
import io.temporal.activity.Activity
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolutePathString

abstract class KnimeNodeModel: ProcessNodeModel() {
    abstract val knimeWorkflowRootDir: String
    abstract val knimeNodeFactoryClass: String
    abstract val knimeNodeName: String
    private val templateHelper = TemplateHelper()

    companion object {
        private const val WORKFLOW_TEMPLATE_RESOURCE_ROOT = "knime-workflow-template"
        private const val WORKFLOW_SET_TEMPLATE_FILE_NAME = "workflowset.meta.template"
        private const val WORKFLOW_METADATA_XML_TEMPLATE_FILE_NAME = "workflow-metadata.xml.template"
        private const val WORKFLOW_KNIME_TEMPLATE_FILE_NAME = "workflow.knime.template"
        private const val WORKFLOW_INPUT_NODE_SETTINGS_TEMPLATE_FILE_NAME = "input-node-template/settings.xml.template"
        private const val WORKFLOW_OUTPUT_NODE_SETTINGS_TEMPLATE_FILE_NAME = "output-node-template/settings.xml.template"
        private const val WORKFLOW_PROCESSING_NODE_SETTINGS_TEMPLATE_FILE_NAME = "processing-node-template/settings.xml.template"
        private val LOGGER = LoggerFactory.getLogger(KnimeNodeModel::class.java)
    }

    override fun run(
        node: ContinuumWorkflowModel.Node,
        inputs: Map<String, NodeInputReader>,
        nodeOutputWriter: NodeOutputWriter
    ) {
        try {
            val workflowRunId = Activity.getExecutionContext().info.runId
            // Create temporary directory for the workflow
            val workflowDir = File(
                knimeWorkflowRootDir,
                "${workflowRunId}${File.separator}${node.id}"
            )

            LOGGER.info("Generating workflow at ${workflowDir.absolutePath}")

            // Render the workflow
            renderKnimeWorkflow(
                workflowDir.toPath()
            )

            execute(
                node.data.properties,
                inputs,
                nodeOutputWriter
            )
        } catch (ex: Exception) {
            LOGGER.error("Error while executing node ${node.id}", ex)
            throw ex
        }
    }

    fun renderKnimeWorkflow(
        workflowDir: Path
    ) {
        // create all parent directories if not exists
        if(!workflowDir.toFile().exists()) {
            LOGGER.info("Creating workflow directory at ${workflowDir.absolutePathString()}")
            workflowDir.toFile().mkdirs()
        }

        // Render workflowset.meta file
        renderWorkflowSet(
            workflowDir
        )

        // Render workflow.metadata.xml file
        renderWorkflowMetadataXml(
            workflowDir
        )

        // Render workflow.knime file
        renderWorkflowKnime(
            workflowDir,
            mapOf(
                "inputPorts" to inputPorts,
                "outputPorts" to outputPorts,
            )
        )

        // Render input node settings file
        inputPorts.keys.forEach {
            val inputNodeDir = workflowDir.resolve(it)
            if(!inputNodeDir.toFile().exists()) {
                LOGGER.info("Creating input node directory at ${inputNodeDir.absolutePathString()}")
                inputNodeDir.toFile().mkdirs()
            }
            renderInput(
                inputNodeDir
            )
        }

        // Render processing node settings file
        val processingNodeDir = workflowDir.resolve("processing-node")
        if(!processingNodeDir.toFile().exists()) {
            LOGGER.info("Creating processing node directory at ${processingNodeDir.absolutePathString()}")
            processingNodeDir.toFile().mkdirs()
        }
        renderProcessingNode(
            processingNodeDir,
            mapOf(
                "knimeNodeFactoryClass" to knimeNodeFactoryClass,
                "knimeNodeName" to knimeNodeName,
                "nodeDescription" to metadata.description,
            )
        )

        // Render output node settings file
        outputPorts.keys.forEach {
            val outputNodeDir = workflowDir.resolve(it)
            if(!outputNodeDir.toFile().exists()) {
                LOGGER.info("Creating output node directory at ${outputNodeDir.absolutePathString()}")
                outputNodeDir.toFile().mkdirs()
            }
            renderOutput(
                outputNodeDir
            )
        }
    }

    fun renderWorkflowSet(
        workflowDir: Path
    ) {
        // Load templates from jar resources
        val workflowSetTemplate = this::class.java.classLoader
            .getResourceAsStream(
                listOf(
                    WORKFLOW_TEMPLATE_RESOURCE_ROOT,
                    WORKFLOW_SET_TEMPLATE_FILE_NAME
                ).joinToString(File.separator)
            )
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: throw IllegalStateException("Workflow set template not found")
        templateHelper.loadTemplate(WORKFLOW_SET_TEMPLATE_FILE_NAME, workflowSetTemplate)
        val workflowSet = templateHelper.renderTemplate(WORKFLOW_SET_TEMPLATE_FILE_NAME, mapOf())
        val workflowSetFileName =  WORKFLOW_SET_TEMPLATE_FILE_NAME
            .split(".")
            .dropLast(1)
            .joinToString(".")
        val workflowSetFile = workflowDir.resolve(
            workflowSetFileName
        ).toFile()
        if (!workflowSetFile.exists()) {
            workflowSetFile.createNewFile()
        }
        workflowSetFile.writeText(workflowSet)
    }

    fun renderWorkflowMetadataXml(
        workflowDir: Path
    ) {
        val workflowMetadataXmlTemplate = this::class.java.classLoader
            .getResourceAsStream(
                listOf(
                    WORKFLOW_TEMPLATE_RESOURCE_ROOT,
                    WORKFLOW_METADATA_XML_TEMPLATE_FILE_NAME
                ).joinToString(File.separator)
            )
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: throw IllegalStateException("Workflow metadata xml template not found")

        templateHelper.loadTemplate(WORKFLOW_METADATA_XML_TEMPLATE_FILE_NAME, workflowMetadataXmlTemplate)
        val workflowMetadataXml = templateHelper.renderTemplate(WORKFLOW_METADATA_XML_TEMPLATE_FILE_NAME, mapOf())
        val workflowMetadataXmlFileName = WORKFLOW_METADATA_XML_TEMPLATE_FILE_NAME
            .split(".")
            .dropLast(1)
            .joinToString(".")
        val workflowMetadataXmlFile = workflowDir.resolve(
            workflowMetadataXmlFileName
        ).toFile()
        if (!workflowMetadataXmlFile.exists()) {
            workflowMetadataXmlFile.createNewFile()
        }
        workflowMetadataXmlFile.writeText(workflowMetadataXml)
    }

    fun renderWorkflowKnime(
        workflowDir: Path,
        model: Map<String, Any>,
    ) {
        val workflowKnimeTemplate = this::class.java.classLoader
            .getResourceAsStream(
                listOf(
                    WORKFLOW_TEMPLATE_RESOURCE_ROOT,
                    WORKFLOW_KNIME_TEMPLATE_FILE_NAME
                ).joinToString(File.separator)
            )
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: throw IllegalStateException("Workflow knime template not found")

        templateHelper.loadTemplate(WORKFLOW_KNIME_TEMPLATE_FILE_NAME, workflowKnimeTemplate)
        val workflowKnime = templateHelper.renderTemplate(WORKFLOW_KNIME_TEMPLATE_FILE_NAME, model)
        val workflowKnimeFileName = WORKFLOW_KNIME_TEMPLATE_FILE_NAME
            .split(".")
            .dropLast(1)
            .joinToString(".")
        val workflowKnimeFile = workflowDir.resolve(
            workflowKnimeFileName
        ).toFile()
        if (!workflowKnimeFile.exists()) {
            workflowKnimeFile.createNewFile()
        }
        workflowKnimeFile.writeText(workflowKnime)
    }

    fun renderInput(
        nodeDir: Path
    ) {
        val inputNodeSettingsTemplate = this::class.java.classLoader
            .getResourceAsStream(
                listOf(
                    WORKFLOW_TEMPLATE_RESOURCE_ROOT,
                    WORKFLOW_INPUT_NODE_SETTINGS_TEMPLATE_FILE_NAME
                ).joinToString(File.separator)
            )
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: throw IllegalStateException("Input node settings template not found")
        templateHelper.loadTemplate(WORKFLOW_INPUT_NODE_SETTINGS_TEMPLATE_FILE_NAME, inputNodeSettingsTemplate)
        val inputNodeSettings = templateHelper.renderTemplate(WORKFLOW_INPUT_NODE_SETTINGS_TEMPLATE_FILE_NAME, mapOf())
        val inputNodeSettingsFileName = WORKFLOW_INPUT_NODE_SETTINGS_TEMPLATE_FILE_NAME
            .split(".")
            .dropLast(1)
            .joinToString(".")
        val inputNodeSettingsFile = nodeDir.resolve(
            "settings.xml"
        ).toFile()
        if (!inputNodeSettingsFile.exists()) {
            inputNodeSettingsFile.createNewFile()
        }
        inputNodeSettingsFile.writeText(inputNodeSettings)
    }

    fun renderOutput(
        nodeDir: Path
    ) {
        val outputNodeSettingsTemplate = this::class.java.classLoader
            .getResourceAsStream(
                listOf(
                    WORKFLOW_TEMPLATE_RESOURCE_ROOT,
                    WORKFLOW_OUTPUT_NODE_SETTINGS_TEMPLATE_FILE_NAME
                ).joinToString(File.separator)
            )
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: throw IllegalStateException("Output node settings template not found")
        templateHelper.loadTemplate(WORKFLOW_OUTPUT_NODE_SETTINGS_TEMPLATE_FILE_NAME, outputNodeSettingsTemplate)
        val outputNodeSettings = templateHelper.renderTemplate(WORKFLOW_OUTPUT_NODE_SETTINGS_TEMPLATE_FILE_NAME, mapOf())
        val outputNodeSettingsFileName = WORKFLOW_OUTPUT_NODE_SETTINGS_TEMPLATE_FILE_NAME
            .split(".")
            .dropLast(1)
            .joinToString(".")
        val outputNodeSettingsFile = nodeDir.resolve(
            "settings.xml"
        ).toFile()
        if (!outputNodeSettingsFile.exists()) {
            outputNodeSettingsFile.createNewFile()
        }
        outputNodeSettingsFile.writeText(outputNodeSettings)
    }

    fun renderProcessingNode(
        nodeDir: Path,
        model: Map<String, Any>
    ) {
        val processingNodeSettingsTemplate = this::class.java.classLoader
            .getResourceAsStream(
                listOf(
                    WORKFLOW_TEMPLATE_RESOURCE_ROOT,
                    WORKFLOW_PROCESSING_NODE_SETTINGS_TEMPLATE_FILE_NAME
                ).joinToString(File.separator)
            )
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: throw IllegalStateException("Processing node settings template not found")
        templateHelper.loadTemplate(WORKFLOW_PROCESSING_NODE_SETTINGS_TEMPLATE_FILE_NAME, processingNodeSettingsTemplate)
        val processingNodeSettings = templateHelper.renderTemplate(WORKFLOW_PROCESSING_NODE_SETTINGS_TEMPLATE_FILE_NAME, model)
        val processingNodeSettingsFileName = WORKFLOW_PROCESSING_NODE_SETTINGS_TEMPLATE_FILE_NAME
            .split(".")
            .dropLast(1)
            .joinToString(".")
        val processingNodeSettingsFile = nodeDir.resolve(
            "settings.xml"
        ).toFile()
        if (!processingNodeSettingsFile.exists()) {
            processingNodeSettingsFile.createNewFile()
        }
        processingNodeSettingsFile.writeText(processingNodeSettings)
    }
}