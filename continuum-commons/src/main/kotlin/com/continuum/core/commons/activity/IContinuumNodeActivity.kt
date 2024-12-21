package com.continuum.core.commons.activity

import com.continuum.core.commons.model.ContinuumWorkflowModel
import io.temporal.activity.ActivityInterface

@ActivityInterface
interface IContinuumNodeActivity {
  fun run(node: ContinuumWorkflowModel.Node): Map<String, Any>
}