package org.projectcontinuum.core.cluster.manager

import org.projectcontinuum.core.cluster.manager.config.OverlayProperties
import org.projectcontinuum.core.cluster.manager.config.WorkbenchProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(WorkbenchProperties::class, OverlayProperties::class)
class App

fun main(args: Array<String>) {
  runApplication<App>(*args)
}