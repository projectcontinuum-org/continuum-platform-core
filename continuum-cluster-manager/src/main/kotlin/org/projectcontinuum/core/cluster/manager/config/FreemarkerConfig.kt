package org.projectcontinuum.core.cluster.manager.config

import freemarker.template.Configuration
import freemarker.template.TemplateExceptionHandler
import org.springframework.context.annotation.Bean

@org.springframework.context.annotation.Configuration
class FreemarkerConfig {

  @Bean
  fun freemarkerConfiguration(): Configuration {
    val cfg = Configuration(Configuration.VERSION_2_3_34)
    cfg.setClassLoaderForTemplateLoading(this::class.java.classLoader, "/templates")
    cfg.defaultEncoding = "UTF-8"
    cfg.templateExceptionHandler = TemplateExceptionHandler.RETHROW_HANDLER
    cfg.logTemplateExceptions = false
    return cfg
  }
}
