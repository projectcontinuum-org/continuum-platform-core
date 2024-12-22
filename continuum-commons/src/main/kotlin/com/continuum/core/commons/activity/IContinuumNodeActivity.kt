package com.continuum.core.commons.activity

import com.continuum.core.commons.model.ContinuumWorkflowModel
import com.continuum.core.commons.model.PortData
import io.temporal.activity.ActivityInterface

@ActivityInterface
interface IContinuumNodeActivity {
    fun run(
        node: ContinuumWorkflowModel.Node,
        inputs: Map<String, PortData>
    ): Map<String, PortData>
}