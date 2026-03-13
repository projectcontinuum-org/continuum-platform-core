package org.projectcontinuum.core.commons.activity

import io.temporal.activity.ActivityInterface

@ActivityInterface
interface IInitializeActivity {
    fun getNodeTaskQueue(
        nodeIds: Set<String>
    ): Map<String, String>
}