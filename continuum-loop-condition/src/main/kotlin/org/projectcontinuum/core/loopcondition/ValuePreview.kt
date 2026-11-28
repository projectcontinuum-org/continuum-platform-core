package org.projectcontinuum.core.loopcondition

import dev.cel.common.values.CelByteString
import dev.cel.common.values.NullValue
import org.projectcontinuum.core.commons.validation.loop.LoopConditionLimits
import org.projectcontinuum.core.commons.validation.loop.LoopConditionOutcome
import java.time.Duration
import java.time.Instant
import java.util.Optional

/**
 * Describes a value a `dyn` condition returned instead of true or false: its CEL type name and a short preview, so a
 * message can say `the condition returned a string ("yes")`. The preview is cut to
 * [LoopConditionLimits.VALUE_PREVIEW_LENGTH] characters; strings are quoted.
 */
internal object ValuePreview {

  fun nonBoolean(value: Any?): LoopConditionOutcome.NonBoolean =
    LoopConditionOutcome.NonBoolean(typeName = typeNameOf(value = value), preview = previewOf(value = value))

  fun typeNameOf(value: Any?): String = when (value) {
    null, is NullValue -> "null"
    is String -> "string"
    is Long, is Int -> "int"
    is Double, is Float -> "double"
    is Number -> if (value.javaClass.simpleName == "UnsignedLong") "uint" else "double"
    is CelByteString -> "bytes"
    is List<*> -> "list"
    is Map<*, *> -> "map"
    is Instant -> "timestamp"
    is Duration -> "duration"
    is Optional<*> -> "optional"
    else -> value.javaClass.simpleName
  }

  private fun previewOf(value: Any?): String {
    val text = when (value) {
      null, is NullValue -> "null"
      is String -> "\"$value\""
      else -> value.toString()
    }
    val limit = LoopConditionLimits.VALUE_PREVIEW_LENGTH
    return if (text.length <= limit) text else text.take(limit - 3) + "..."
  }
}
