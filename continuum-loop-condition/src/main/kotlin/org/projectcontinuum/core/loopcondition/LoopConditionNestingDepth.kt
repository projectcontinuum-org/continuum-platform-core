package org.projectcontinuum.core.loopcondition

/**
 * Measures how deeply `(`, `[` and `{` nest in a condition text, outside string literals and `//` comments, without
 * recursion. Runs before the CEL parser so that no parser recurses on hostile input
 * ([org.projectcontinuum.core.commons.validation.loop.LoopConditionLimits.MAX_NESTING_DEPTH]).
 *
 * String literals follow CEL lexing: an optional raw (`r`) and/or bytes (`b`) prefix, single or double quotes, single
 * or triple quoted. A backslash escapes the next character except in raw strings. An unterminated string ends the
 * scan; the CEL parser then reports the syntax error.
 */
internal object LoopConditionNestingDepth {

  /** Returns the deepest nesting and the 0-based code point offset where it was first reached (null for depth 0). */
  fun measure(text: String): NestingMeasurement {
    val codePoints = text.codePoints().toArray()
    val scanner = Scanner(codePoints = codePoints)
    return scanner.scan()
  }

  data class NestingMeasurement(val maxDepth: Int, val offsetOfMaxDepth: Int?)

  private class Scanner(private val codePoints: IntArray) {
    private var position = 0
    private var depth = 0
    private var maxDepth = 0
    private var offsetOfMaxDepth: Int? = null

    fun scan(): NestingMeasurement {
      while (position < codePoints.size) {
        val current = codePoints[position]
        when {
          isCommentStart() -> skipToLineEnd()
          isQuote(codePoint = current) -> skipStringLiteral(quoteStart = position, isRaw = hasRawPrefixBefore(quoteStart = position))
          isOpening(codePoint = current) -> openAt(offset = position)
          isClosing(codePoint = current) -> closeOne()
          else -> position += 1
        }
      }
      return NestingMeasurement(maxDepth = maxDepth, offsetOfMaxDepth = offsetOfMaxDepth)
    }

    private fun openAt(offset: Int) {
      depth += 1
      if (depth > maxDepth) {
        maxDepth = depth
        offsetOfMaxDepth = offset
      }
      position += 1
    }

    private fun closeOne() {
      if (depth > 0) depth -= 1
      position += 1
    }

    private fun isCommentStart(): Boolean =
      codePoints[position] == '/'.code && position + 1 < codePoints.size && codePoints[position + 1] == '/'.code

    private fun skipToLineEnd() {
      while (position < codePoints.size && codePoints[position] != '\n'.code) position += 1
    }

    /** A quote is raw when an `r`/`R` prefix (possibly next to a `b`/`B` prefix) stands directly before it. */
    private fun hasRawPrefixBefore(quoteStart: Int): Boolean {
      var prefixStart = quoteStart
      var isRaw = false
      while (prefixStart > 0 && quoteStart - prefixStart < 2 && isStringPrefix(codePoint = codePoints[prefixStart - 1])) {
        prefixStart -= 1
        if (codePoints[prefixStart] == 'r'.code || codePoints[prefixStart] == 'R'.code) isRaw = true
      }
      val prefixIsPartOfIdentifier = prefixStart > 0 && isIdentifierPart(codePoint = codePoints[prefixStart - 1])
      return isRaw && !prefixIsPartOfIdentifier
    }

    private fun skipStringLiteral(quoteStart: Int, isRaw: Boolean) {
      val quote = codePoints[quoteStart]
      val isTripleQuoted = quoteStart + 2 < codePoints.size &&
        codePoints[quoteStart + 1] == quote && codePoints[quoteStart + 2] == quote
      val delimiterLength = if (isTripleQuoted) 3 else 1
      position = quoteStart + delimiterLength
      while (position < codePoints.size) {
        val current = codePoints[position]
        if (current == '\\'.code && !isRaw) {
          position += 2
          continue
        }
        if (current == quote && closesLiteral(quote = quote, delimiterLength = delimiterLength)) {
          position += delimiterLength
          return
        }
        if (current == '\n'.code && !isTripleQuoted) return
        position += 1
      }
    }

    private fun closesLiteral(quote: Int, delimiterLength: Int): Boolean {
      if (position + delimiterLength > codePoints.size) return false
      return (0 until delimiterLength).all { index -> codePoints[position + index] == quote }
    }

    private fun isQuote(codePoint: Int) = codePoint == '"'.code || codePoint == '\''.code
    private fun isOpening(codePoint: Int) = codePoint == '('.code || codePoint == '['.code || codePoint == '{'.code
    private fun isClosing(codePoint: Int) = codePoint == ')'.code || codePoint == ']'.code || codePoint == '}'.code
    private fun isStringPrefix(codePoint: Int) =
      codePoint == 'r'.code || codePoint == 'R'.code || codePoint == 'b'.code || codePoint == 'B'.code
    private fun isIdentifierPart(codePoint: Int) =
      codePoint == '_'.code || (codePoint < 128 && Character.isLetterOrDigit(codePoint))
  }
}
