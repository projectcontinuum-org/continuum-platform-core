package com.continuum.core.commons.node

abstract class ProcessNodeModel {
  fun run(
    inputs: Map<String, Any>
  ): Map<String, Any?> {
    return  execute(inputs)
  }
  abstract fun execute(inputs: Map<String, Any>): Map<String, Any?>
}