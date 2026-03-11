package org.projectcontinuum.core.api.server.service

import org.projectcontinuum.core.api.server.model.NodeExplorerItemType
import org.projectcontinuum.core.api.server.model.NodeExplorerTreeItem
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.springframework.stereotype.Service

@Service
class NodeExplorerService {

    fun getChildren(parentId: String): List<NodeExplorerTreeItem> {
        return mockNodeTree[parentId] ?: emptyList()
    }

    fun search(query: String): List<NodeExplorerTreeItem> {
        if (query.isBlank()) return emptyList()
        val lowerQuery = query.lowercase()
        return getAllNodes()
            .filter { it.name.lowercase().contains(lowerQuery) ||
                      it.nodeInfo?.description?.lowercase()?.contains(lowerQuery) == true ||
                      it.nodeInfo?.title?.lowercase()?.contains(lowerQuery) == true }
    }

    private fun getAllNodes(): List<NodeExplorerTreeItem> {
        return mockNodeTree.values.flatten()
            .filter { it.type == NodeExplorerItemType.NODE }
    }

    // SVG icons from node models
    private object Icons {
        val REST = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M12 21a9.004 9.004 0 008.716-6.747M12 21a9.004 9.004 0 01-8.716-6.747M12 21c2.485 0 4.5-4.03 4.5-9S14.485 3 12 3m0 18c-2.485 0-4.5-4.03-4.5-9S9.515 3 12 3m0 0a8.997 8.997 0 017.843 4.582M12 3a8.997 8.997 0 00-7.843 4.582m15.686 0A11.953 11.953 0 0112 10.5c-2.998 0-5.74-1.1-7.843-2.918m15.686 0A8.959 8.959 0 0121 12c0 .778-.099 1.533-.284 2.253m0 0A17.919 17.919 0 0112 16.5c-3.162 0-6.133-.815-8.716-2.247m0 0A9.015 9.015 0 013 12c0-1.605.42-3.113 1.157-4.418" />
            </svg>
        """.trimIndent()

        val FILTER = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M12 3c2.755 0 5.455.232 8.083.678.533.09.917.556.917 1.096v1.044a2.25 2.25 0 01-.659 1.591l-5.432 5.432a2.25 2.25 0 00-.659 1.591v2.927a2.25 2.25 0 01-1.244 2.013L9.75 21v-6.568a2.25 2.25 0 00-.659-1.591L3.659 7.409A2.25 2.25 0 013 5.818V4.774c0-.54.384-1.006.917-1.096A48.32 48.32 0 0112 3z" />
            </svg>
        """.trimIndent()

        val JOIN = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M13.5 16.875h3.375m0 0h3.375m-3.375 0V13.5m0 3.375v3.375M6 10.5h2.25a2.25 2.25 0 002.25-2.25V6a2.25 2.25 0 00-2.25-2.25H6A2.25 2.25 0 003.75 6v2.25A2.25 2.25 0 006 10.5zm0 9.75h2.25A2.25 2.25 0 0010.5 18v-2.25a2.25 2.25 0 00-2.25-2.25H6a2.25 2.25 0 00-2.25 2.25V18A2.25 2.25 0 006 20.25zm9.75-9.75H18a2.25 2.25 0 002.25-2.25V6A2.25 2.25 0 0018 3.75h-2.25A2.25 2.25 0 0013.5 6v2.25a2.25 2.25 0 002.25 2.25z" />
            </svg>
        """.trimIndent()

        val PIVOT = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M7.5 21L3 16.5m0 0L7.5 12M3 16.5h13.5m0-13.5L21 7.5m0 0L16.5 12M21 7.5H7.5" />
            </svg>
        """.trimIndent()

        val CRYPTO = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M16.5 10.5V6.75a4.5 4.5 0 10-9 0v3.75m-.75 11.25h10.5a2.25 2.25 0 002.25-2.25v-6.75a2.25 2.25 0 00-2.25-2.25H6.75a2.25 2.25 0 00-2.25 2.25v6.75a2.25 2.25 0 002.25 2.25z" />
            </svg>
        """.trimIndent()

        val TABLE = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
            	<path d="M21.5 2h-19a.5.5 0 0 0-.5.5v19a.5.5 0 0 0 .5.5h19a.5.5 0 0 0 .5-.5v-19a.5.5 0 0 0-.5-.5zm-13 19H3v-5.5h5.5V21zm0-6.5H3v-5h5.5v5zm0-6H3V3h5.5v5.5zm6 12.5h-5v-5.5h5V21zm0-6.5h-5v-5h5v5zm0-6h-5V3h5v5.5zM21 21h-5.5v-5.5H21V21zm0-6.5h-5.5v-5H21v5zm0-6h-5.5V3H21v5.5z" fill="currentColor"/>
            </svg>
        """.trimIndent()

        val BATCH = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M6 6.878V6a2.25 2.25 0 012.25-2.25h7.5A2.25 2.25 0 0118 6v.878m-12 0c.235-.083.487-.128.75-.128h10.5c.263 0 .515.045.75.128m-12 0A2.25 2.25 0 004.5 9v.878m13.5-3A2.25 2.25 0 0119.5 9v.878m0 0a2.246 2.246 0 00-.75-.128H5.25c-.263 0-.515.045-.75.128m15 0A2.25 2.25 0 0121 12v6a2.25 2.25 0 01-2.25 2.25H5.25A2.25 2.25 0 013 18v-6c0-.98.626-1.813 1.5-2.122" />
            </svg>
        """.trimIndent()

        val TIME = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M12 6v6h4.5m4.5 0a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
        """.trimIndent()

        val SCRIPT = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
              <path d="M4.456 5.25a1.5 1.5 0 0 1 1.5-1.5h12.586c.89 0 1.337 1.077.707 1.707L12.706 12l6.543 6.543c.63.63.184 1.707-.707 1.707H5.956a1.5 1.5 0 0 1-1.06-.44a1.5 1.5 0 0 1-.44-1.06zm8.25-1.5L4.456 12m8.25 0l-7.81 7.81" fill="none" stroke="currentColor" stroke-linejoin="round" stroke-width="1.5"/>
            </svg>
        """.trimIndent()

        val CHART = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M3.75 3v11.25A2.25 2.25 0 006 16.5h2.25M3.75 3h-1.5m1.5 0h16.5m0 0h1.5m-1.5 0v11.25A2.25 2.25 0 0118 16.5h-2.25m-7.5 0h7.5m-7.5 0l-1 3m8.5-3l1 3m0 0l.5 1.5m-.5-1.5h-9.5m0 0l-.5 1.5M9 11.25v1.5M12 9v3.75m3-6v6" />
            </svg>
        """.trimIndent()

        val SPLIT = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M3 7.5L7.5 3m0 0L12 7.5M7.5 3v13.5m13.5 0L16.5 21m0 0L12 16.5m4.5 4.5V7.5" />
            </svg>
        """.trimIndent()

        val TEXT = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M3.75 6.75h16.5M3.75 12h16.5m-16.5 5.25H12" />
            </svg>
        """.trimIndent()

        val JSON = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M17.25 6.75L22.5 12l-5.25 5.25m-10.5 0L1.5 12l5.25-5.25m7.5-3l-4.5 16.5" />
            </svg>
        """.trimIndent()
    }

    private fun createNodeData(
        id: String,
        title: String,
        description: String,
        icon: String
    ): ContinuumWorkflowModel.NodeData {
        return ContinuumWorkflowModel.NodeData(
            id = id,
            title = title,
            description = description,
            icon = icon,
            nodeModel = "org.projectcontinuum.mock.node.$id",
            inputs = mapOf(
                "input-1" to ContinuumWorkflowModel.NodePort(name = "Input", contentType = "application/json")
            ),
            outputs = mapOf(
                "output-1" to ContinuumWorkflowModel.NodePort(name = "Output", contentType = "application/json")
            ),
            properties = emptyMap(),
            propertiesSchema = emptyMap(),
            propertiesUISchema = emptyMap()
        )
    }

    private val mockNodeTree: Map<String, List<NodeExplorerTreeItem>> = mapOf(
        // Root level categories (matching actual node model categories)
        "" to listOf(
          NodeExplorerTreeItem(
            id = "trigger",
            name = "Trigger",
            hasChildren = true,
            type = NodeExplorerItemType.CATEGORY
          ),
          NodeExplorerTreeItem(
            id = "table-data",
            name = "Table & Data Structures",
            hasChildren = true,
            type = NodeExplorerItemType.CATEGORY
          ),
          NodeExplorerTreeItem(
            id = "integration",
            name = "Integration & API",
            hasChildren = true,
            type = NodeExplorerItemType.CATEGORY
          ),
          NodeExplorerTreeItem(
            id = "filter",
            name = "Filter & Select",
            hasChildren = true,
            type = NodeExplorerItemType.CATEGORY
          ),
          NodeExplorerTreeItem(
            id = "transform",
            name = "Transform",
            hasChildren = true,
            type = NodeExplorerItemType.CATEGORY
          ),
          NodeExplorerTreeItem(
            id = "flow-control",
            name = "Flow Control",
            hasChildren = true,
            type = NodeExplorerItemType.CATEGORY
          ),
          NodeExplorerTreeItem(
            id = "aggregation",
            name = "Aggregation & Grouping",
            hasChildren = true,
            type = NodeExplorerItemType.CATEGORY
          ),
          NodeExplorerTreeItem(
            id = "processing",
            name = "Processing",
            hasChildren = true,
            type = NodeExplorerItemType.CATEGORY
          ),
          NodeExplorerTreeItem(
            id = "join-merge",
            name = "Join & Merge",
            hasChildren = true,
            type = NodeExplorerItemType.CATEGORY
          ),
          NodeExplorerTreeItem(
            id = "security",
            name = "Security & Encryption",
            hasChildren = true,
            type = NodeExplorerItemType.CATEGORY
          ),
          NodeExplorerTreeItem(
            id = "string-text",
            name = "String & Text",
            hasChildren = true,
            type = NodeExplorerItemType.CATEGORY
          ),
          NodeExplorerTreeItem(
            id = "json",
            name = "JSON & Data Parsing",
            hasChildren = true,
            type = NodeExplorerItemType.CATEGORY
          ),
          NodeExplorerTreeItem(
            id = "analysis",
            name = "Analysis & Statistics",
            hasChildren = true,
            type = NodeExplorerItemType.CATEGORY
          )
        ),

        // Trigger nodes
        "trigger" to listOf(
          NodeExplorerTreeItem(
            id = "trigger/time-trigger",
            name = "Start Node",
            nodeInfo = createNodeData(
              "org.projectcontinuum.base.node.TimeTriggerNodeModel",
              "Start Node",
              "Starts the workflow execution with the current time as the output",
              Icons.TIME
            ),
            hasChildren = false,
            type = NodeExplorerItemType.NODE
          )
        ),

        // Table & Data Structures nodes
        "table-data" to listOf(
          NodeExplorerTreeItem(
            id = "table-data/create-table",
            name = "Create Table",
            nodeInfo = createNodeData(
              "org.projectcontinuum.base.node.CreateTableNodeModel",
              "Create Table",
              "Creates a structured table from FreeMarker template configuration",
              Icons.TABLE
            ),
            hasChildren = false,
            type = NodeExplorerItemType.NODE
          )
        ),

        // Integration & API nodes
        "integration" to listOf(
          NodeExplorerTreeItem(
            id = "integration/rest-client",
            name = "REST Client",
            nodeInfo = createNodeData(
              "org.projectcontinuum.base.node.RestNodeModel",
              "REST Client",
              "Makes HTTP requests for each row using FreeMarker templated URLs and payloads",
              Icons.REST
            ),
            hasChildren = false,
            type = NodeExplorerItemType.NODE
          )
        ),

        // Filter & Select nodes
        "filter" to listOf(
          NodeExplorerTreeItem(
            id = "filter/dynamic-row-filter",
            name = "Dynamic Row Filter",
            nodeInfo = createNodeData(
              "org.projectcontinuum.base.node.DynamicRowFilterNodeModel",
              "Dynamic Row Filter",
              "Filters rows where the specified column value is greater than the threshold",
              Icons.FILTER
            ),
            hasChildren = false,
            type = NodeExplorerItemType.NODE
          )
        ),

        // Transform nodes
        "transform" to listOf(
          NodeExplorerTreeItem(
            id = "transform/pivot-columns",
            name = "Pivot Columns",
            nodeInfo = createNodeData(
              "org.projectcontinuum.base.node.PivotColumnsNodeModel",
              "Pivot Columns",
              "Pivots table so pivot column values become new columns with value column as cell values",
              Icons.PIVOT
            ),
            hasChildren = false,
            type = NodeExplorerItemType.NODE
          ),
          NodeExplorerTreeItem(
            id = "transform/kotlin-script",
            name = "Kotlin Script",
            nodeInfo = createNodeData(
              "org.projectcontinuum.base.node.KotlinScriptNodeModel",
              "Kotlin Script",
              "Run a Kotlin script for each row, adding script_result column",
              Icons.SCRIPT
            ),
            hasChildren = false,
            type = NodeExplorerItemType.NODE
          )
        ),

        // Flow Control nodes
        "flow-control" to listOf(
          NodeExplorerTreeItem(
            id = "flow-control/conditional-splitter",
            name = "Conditional Splitter",
            nodeInfo = createNodeData(
              "org.projectcontinuum.base.node.ConditionalSplitterNodeModel",
              "Conditional Splitter",
              "Splits rows into two outputs based on threshold comparison",
              Icons.SPLIT
            ),
            hasChildren = false,
            type = NodeExplorerItemType.NODE
          )
        ),

        // Aggregation & Grouping nodes
        "aggregation" to listOf(
          NodeExplorerTreeItem(
            id = "aggregation/batch-accumulator",
            name = "Batch Accumulator",
            nodeInfo = createNodeData(
              "org.projectcontinuum.base.node.BatchAccumulatorNodeModel",
              "Batch Accumulator",
              "Groups rows into batches and adds batch_id and row_count columns",
              Icons.BATCH
            ),
            hasChildren = false,
            type = NodeExplorerItemType.NODE
          ),
          NodeExplorerTreeItem(
            id = "aggregation/time-window",
            name = "Time Window Aggregator",
            nodeInfo = createNodeData(
              "org.projectcontinuum.base.node.TimeWindowAggregatorNodeModel",
              "Time Window Aggregator",
              "Aggregates values into time windows, summing by window buckets",
              Icons.TIME
            ),
            hasChildren = false,
            type = NodeExplorerItemType.NODE
          )
        ),

        // Processing nodes
        "processing" to listOf(
          NodeExplorerTreeItem(
            id = "processing/joint-node",
            name = "Joint Node",
            nodeInfo = createNodeData(
              "org.projectcontinuum.base.node.JointNodeModel",
              "Joint Node",
              "Joint the input strings into one",
              Icons.JOIN
            ),
            hasChildren = false,
            type = NodeExplorerItemType.NODE
          ),
          NodeExplorerTreeItem(
            id = "processing/column-splitter",
            name = "Column Splitter",
            nodeInfo = createNodeData(
              "org.projectcontinuum.base.node.SplitNodeModel",
              "Column Splitter",
              "Split a column into two parts",
              Icons.SPLIT
            ),
            hasChildren = false,
            type = NodeExplorerItemType.NODE
          ),
          NodeExplorerTreeItem(
            id = "processing/column-join",
            name = "Column Join Node",
            nodeInfo = createNodeData(
              "org.projectcontinuum.base.node.ColumnJoinNodeModel",
              "Column Join Node",
              "Joins two columns from left and right tables into one output column",
              Icons.JOIN
            ),
            hasChildren = false,
            type = NodeExplorerItemType.NODE
          )
        ),

        // Join & Merge nodes
        "join-merge" to listOf(
          NodeExplorerTreeItem(
            id = "join-merge/join-multiple-keys",
            name = "Join on Multiple Keys",
            nodeInfo = createNodeData(
              "org.projectcontinuum.base.node.JoinOnMultipleKeysNodeModel",
              "Join on Multiple Keys",
              "Performs inner join on two tables using two key columns from each table",
              Icons.JOIN
            ),
            hasChildren = false,
            type = NodeExplorerItemType.NODE
          )
        ),

        // Security & Encryption nodes
        "security" to listOf(
          NodeExplorerTreeItem(
            id = "security/crypto-hasher",
            name = "Crypto Hasher",
            nodeInfo = createNodeData(
              "org.projectcontinuum.base.node.CryptoHasherNodeModel",
              "Crypto Hasher",
              "Generates SHA-256 hash of column values",
              Icons.CRYPTO
            ),
            hasChildren = false,
            type = NodeExplorerItemType.NODE
          )
        ),

        // String & Text nodes
        "string-text" to listOf(
          NodeExplorerTreeItem(
            id = "string-text/text-normalizer",
            name = "Text Normalizer",
            nodeInfo = createNodeData(
              "org.projectcontinuum.base.node.TextNormalizerNodeModel",
              "Text Normalizer",
              "Normalizes text by trimming, lowercasing, and removing non-alphanumeric characters",
              Icons.TEXT
            ),
            hasChildren = false,
            type = NodeExplorerItemType.NODE
          )
        ),

        // JSON & Data Parsing nodes
        "json" to listOf(
          NodeExplorerTreeItem(
            id = "json/json-exploder",
            name = "JSON Exploder",
            nodeInfo = createNodeData(
              "org.projectcontinuum.base.node.JsonExploderNodeModel",
              "JSON Exploder",
              "Parses JSON strings and flattens keys into new columns",
              Icons.JSON
            ),
            hasChildren = false,
            type = NodeExplorerItemType.NODE
          )
        ),

        // Analysis & Statistics nodes
        "analysis" to listOf(
          NodeExplorerTreeItem(
            id = "analysis/anomaly-detector",
            name = "Anomaly Detector",
            nodeInfo = createNodeData(
              "org.projectcontinuum.base.node.AnomalyDetectorZScoreNodeModel",
              "Anomaly Detector",
              "Detects outliers using Z-score method (flags values with |Z| > 2)",
              Icons.CHART
            ),
            hasChildren = false,
            type = NodeExplorerItemType.NODE
          )
        )
    )
}
