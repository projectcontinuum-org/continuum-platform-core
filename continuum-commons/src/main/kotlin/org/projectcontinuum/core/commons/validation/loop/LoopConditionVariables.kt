package org.projectcontinuum.core.commons.validation.loop

/** The one name a loop condition can read: the map of flow variables, `vars: map(string, dyn)`. */
object LoopConditionVariables {
  /** Flow variables are read as `vars["name"]` (any name) or `vars.name` (plain names). */
  const val IDENTIFIER = "vars"
}
