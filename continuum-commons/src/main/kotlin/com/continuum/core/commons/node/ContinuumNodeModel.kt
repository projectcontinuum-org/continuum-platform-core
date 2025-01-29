package com.continuum.core.commons.node

import com.continuum.core.commons.model.ContinuumWorkflowModel

interface ContinuumNodeModel {
    val categories: List<String>
    val metadata: ContinuumWorkflowModel.NodeData
}