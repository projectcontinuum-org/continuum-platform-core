package com.continuum.core.commons.utils

import freemarker.cache.StringTemplateLoader
import freemarker.template.Configuration
import java.io.StringWriter

class TemplateHelper {
  private val configuration = Configuration(Configuration.VERSION_2_3_33)
  private val templateLoader = StringTemplateLoader()

  init {
    configuration.defaultEncoding = "UTF-8"
    configuration.templateLoader = templateLoader
  }

  fun renderTemplate(templateName: String, dataModel: Map<String, Any>): String {
    val template = configuration.getTemplate(templateName)
    val output = StringWriter()
    template.process(dataModel, output)
    return output.toString()
  }

  fun loadTemplate(templateName: String, templateString: String) {
    templateLoader.putTemplate(templateName, templateString)
  }
}