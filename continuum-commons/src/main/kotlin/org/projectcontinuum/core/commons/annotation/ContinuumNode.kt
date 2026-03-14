package org.projectcontinuum.core.commons.annotation

import org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component


@Scope(SCOPE_PROTOTYPE)
@Component
annotation class ContinuumNode
