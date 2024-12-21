package com.continuum.core.commons.node

abstract class TriggerNodeModel {
  fun run(): Map<String, Any?> {
    return execute()
  }
  abstract fun execute(): Map<String, Any?>
}