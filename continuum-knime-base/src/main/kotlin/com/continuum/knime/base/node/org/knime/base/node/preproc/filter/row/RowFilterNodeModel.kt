package com.continuum.knime.base.node.org.knime.base.node.preproc.filter.row

import com.continuum.core.commons.model.ContinuumWorkflowModel
import com.continuum.core.commons.node.KnimeNodeModel
import com.continuum.core.commons.utils.NodeInputReader
import com.continuum.core.commons.utils.NodeOutputWriter
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.File

@Component
class RowFilterNodeModel(
    @Value("\${continuum.core.worker.knime.workspace-storage-path}")
    override val knimeWorkspacesRoot: String,
    @Value("\${continuum.core.worker.knime.workflow-storage-path}")
    override val knimeWorkflowRootDir: String,
    @Value("\${continuum.core.worker.knime.executable-path:/Applications/KNIME 5.1.2.app/Contents/MacOS/knime}")
    override val knimeExecutablePath: String
) : KnimeNodeModel() {
    override val knimeNodeFactoryClass: String = "org.knime.base.node.preproc.filter.row.RowFilterNodeFactory"
    override val knimeNodeName: String = "Row Filter"

    companion object {
        private val LOGGER = LoggerFactory.getLogger(RowFilterNodeModel::class.java)
        private val objectMapper = jacksonObjectMapper()
        private val resourceRootPath = RowFilterNodeModel::class.java.name.split(".").joinToString(File.separator)
    }

    override val inputPorts = mapOf(
        "input-1" to ContinuumWorkflowModel.NodePort(
            name = "first input string",
            contentType = "text/plain"
        )
    )

    override val outputPorts = mapOf(
        "output-1" to ContinuumWorkflowModel.NodePort(
            name = "part 1",
            contentType = "text/plain"
        )
    )

    override val categories = listOf(
        "Processing/KNIME"
    )

    val rowFilterSchema: Map<String, Any> = objectMapper.readValue(
        this::class.java.classLoader
            .getResourceAsStream(
                resourceRootPath + File.separator + "properties.schema.json"
            )
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: throw IllegalStateException("Processing node settings template not found")
    )

    val rowFilterUISchema: Map<String, Any> = objectMapper.readValue(
        this::class.java.classLoader
            .getResourceAsStream(
                resourceRootPath + File.separator + "properties-ui.schema.json"
            )
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: throw IllegalStateException("Processing node settings template not found")
    )

    override val metadata = ContinuumWorkflowModel.NodeData(
        id = this.javaClass.name,
        description = "Row Filter Node",
        title = "Row Filter",
        subTitle = "KNIME Row Filter",
        nodeModel = this.javaClass.name,
        icon = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" strokeWidth={1.5} viewBox="0 0 24 24">
                <path d="M7 7V1.414a1 1 0 0 1 2 0V2h5a1 1 0 0 1 .8.4l.975 1.3a.5.5 0 0 1 0 .6L14.8 5.6a1 1 0 0 1-.8.4H9v10H7v-5H2a1 1 0 0 1-.8-.4L.225 9.3a.5.5 0 0 1 0-.6L1.2 7.4A1 1 0 0 1 2 7zm1 3V8H2l-.75 1L2 10zm0-5h6l.75-1L14 3H8z"/>
            </svg>
        """.trimIndent(),
        inputs = inputPorts,
        outputs = outputPorts,
        properties = mapOf(),
        propertiesSchema = rowFilterSchema,
        propertiesUISchema = rowFilterUISchema
    )

    override fun execute(
        properties: Map<String, Any>?,
        inputs: Map<String, NodeInputReader>,
        nodeOutputWriter: NodeOutputWriter
    ) {
        // Do nothing
    }

    override fun getProcessingNodeSettingsTemplate(): String {
        return this::class.java.classLoader
            .getResourceAsStream(
                resourceRootPath + File.separator + "settings.xml.ftl"
            )
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: throw IllegalStateException("Processing node settings template not found")
    }
}