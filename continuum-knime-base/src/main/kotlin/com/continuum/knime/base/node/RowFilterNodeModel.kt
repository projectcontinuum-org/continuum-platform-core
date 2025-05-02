package com.continuum.knime.base.node

import com.continuum.core.commons.model.ContinuumWorkflowModel
import com.continuum.core.commons.node.KnimeNodeModel
import com.continuum.core.commons.utils.NodeInputReader
import com.continuum.core.commons.utils.NodeOutputWriter
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

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
        private val objectMapper = jacksonObjectMapper()
    }

    final override val inputPorts = mapOf(
        "input-1" to ContinuumWorkflowModel.NodePort(
            name = "first input string",
            contentType = "text/plain"
        )
    )

    final override val outputPorts = mapOf(
        "output-1" to ContinuumWorkflowModel.NodePort(
            name = "part 1",
            contentType = "text/plain"
        )
    )

    final override val categories = listOf(
        "Processing/KNIME"
    )

    val rowFilterSchema: Map<String, Any> = objectMapper.readValue("""
        {
          "type": "object",
          "properties": {
            "rowFilter": {
              "type": "object",
              "properties": {
                "RowFilter_TypeID": {
                  "type": "string",
                  "enum": [
                    "RowID_RowFilter",
                    "RangeVal_RowFilter",
                    "StringComp_RowFilter",
                    "RowNumber_RowFilter"
                  ]
                },
                "RegExprRowFilterInclude": {
                  "type": "boolean"
                },
                "RegExprRowFilterStart": {
                  "type": "boolean"
                },
                "RegExprRowFilterPattern": {
                  "type": "string"
                },
                "RegExprRowFilterCaseSense": {
                  "type": "boolean"
                },
                "ColumnName": {
                  "type": "string"
                },
                "include": {
                  "type": "boolean"
                },
                "deepFiltering": {
                  "type": "boolean"
                },
                "lowerBound": {
                  "type": "object",
                  "properties": {
                    "StringCell": {
                      "type": "string"
                    }
                  }
                },
                "upperBound": {
                  "type": "object",
                  "properties": {
                    "StringCell": {
                      "type": "string"
                    }
                  }
                },
                "CaseSensitive": {
                  "type": "boolean"
                },
                "Pattern": {
                  "type": "string"
                },
                "hasWildCards": {
                  "type": "boolean"
                },
                "isRegExpr": {
                  "type": "boolean"
                },
                "RowRangeStart": {
                  "type": "integer"
                },
                "RowRangeEnd": {
                  "type": "integer"
                },
                "RowRangeInclude": {
                  "type": "boolean"
                }
              }
            }
          }
        }
    """.trimIndent())

    val rowFilterUISchema: Map<String, Any> = objectMapper.readValue("""
        {
          "type": "VerticalLayout",
          "elements": [
            {
              "type": "Control",
              "scope": "#/properties/rowFilter/properties/RowFilter_TypeID",
              "label": "Row Filter Type"
            },
            {
              "type": "Group",
              "label": "RowID Row Filter",
              "rule": {
                "effect": "SHOW",
                "condition": {
                  "scope": "#/properties/rowFilter/properties/RowFilter_TypeID",
                  "schema": {
                    "const": "RowID_RowFilter"
                  }
                }
              },
              "elements": [
                {
                  "type": "Control",
                  "scope": "#/properties/rowFilter/properties/RegExprRowFilterInclude"
                },
                {
                  "type": "Control",
                  "scope": "#/properties/rowFilter/properties/RegExprRowFilterStart"
                },
                {
                  "type": "Control",
                  "scope": "#/properties/rowFilter/properties/RegExprRowFilterPattern"
                },
                {
                  "type": "Control",
                  "scope": "#/properties/rowFilter/properties/RegExprRowFilterCaseSense"
                }
              ]
            },
            {
              "type": "Group",
              "label": "Range Value Row Filter",
              "rule": {
                "effect": "SHOW",
                "condition": {
                  "scope": "#/properties/rowFilter/properties/RowFilter_TypeID",
                  "schema": {
                    "const": "RangeVal_RowFilter"
                  }
                }
              },
              "elements": [
                {
                  "type": "Control",
                  "scope": "#/properties/rowFilter/properties/ColumnName"
                },
                {
                  "type": "Control",
                  "scope": "#/properties/rowFilter/properties/include"
                },
                {
                  "type": "Control",
                  "scope": "#/properties/rowFilter/properties/deepFiltering"
                },
                {
                  "type": "Group",
                  "label": "Lower Bound",
                  "elements": [
                    {
                      "type": "Control",
                      "scope": "#/properties/rowFilter/properties/lowerBound/properties/StringCell"
                    }
                  ]
                },
                {
                  "type": "Group",
                  "label": "Upper Bound",
                  "elements": [
                    {
                      "type": "Control",
                      "scope": "#/properties/rowFilter/properties/upperBound/properties/StringCell"
                    }
                  ]
                }
              ]
            },
            {
              "type": "Group",
              "label": "String Comparison Row Filter",
              "rule": {
                "effect": "SHOW",
                "condition": {
                  "scope": "#/properties/rowFilter/properties/RowFilter_TypeID",
                  "schema": {
                    "const": "StringComp_RowFilter"
                  }
                }
              },
              "elements": [
                {
                  "type": "Control",
                  "scope": "#/properties/rowFilter/properties/ColumnName"
                },
                {
                  "type": "Control",
                  "scope": "#/properties/rowFilter/properties/include"
                },
                {
                  "type": "Control",
                  "scope": "#/properties/rowFilter/properties/deepFiltering"
                },
                {
                  "type": "Control",
                  "scope": "#/properties/rowFilter/properties/CaseSensitive"
                },
                {
                  "type": "Control",
                  "scope": "#/properties/rowFilter/properties/Pattern"
                },
                {
                  "type": "Control",
                  "scope": "#/properties/rowFilter/properties/hasWildCards"
                },
                {
                  "type": "Control",
                  "scope": "#/properties/rowFilter/properties/isRegExpr"
                }
              ]
            },
            {
              "type": "Group",
              "label": "Row Number Row Filter",
              "rule": {
                "effect": "SHOW",
                "condition": {
                  "scope": "#/properties/rowFilter/properties/RowFilter_TypeID",
                  "schema": {
                    "const": "RowNumber_RowFilter"
                  }
                }
              },
              "elements": [
                {
                  "type": "Control",
                  "scope": "#/properties/rowFilter/properties/RowRangeStart"
                },
                {
                  "type": "Control",
                  "scope": "#/properties/rowFilter/properties/RowRangeEnd"
                },
                {
                  "type": "Control",
                  "scope": "#/properties/rowFilter/properties/RowRangeInclude"
                }
              ]
            }
          ]
        }
    """.trimIndent())

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
}