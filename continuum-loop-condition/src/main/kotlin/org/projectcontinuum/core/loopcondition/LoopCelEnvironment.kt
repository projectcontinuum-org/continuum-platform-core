package org.projectcontinuum.core.loopcondition

import dev.cel.bundle.Cel
import dev.cel.bundle.CelFactory
import dev.cel.checker.CelStandardDeclarations
import dev.cel.common.CelAbstractSyntaxTree
import dev.cel.common.CelOptions
import dev.cel.common.CelValidationResult
import dev.cel.common.types.MapType
import dev.cel.common.types.SimpleType
import dev.cel.extensions.CelExtensionLibrary
import dev.cel.extensions.CelExtensions
import dev.cel.parser.CelStandardMacro
import dev.cel.runtime.CelRuntime
import dev.cel.validator.CelValidator
import dev.cel.validator.CelValidatorFactory
import dev.cel.validator.validators.DurationLiteralValidator
import dev.cel.validator.validators.RegexLiteralValidator
import dev.cel.validator.validators.TimestampLiteralValidator
import org.projectcontinuum.core.commons.validation.loop.LoopConditionLimits
import org.projectcontinuum.core.commons.validation.loop.LoopConditionVariables

/**
 * The one place that builds cel-java for loop conditions. The validator (api-server, and the orchestrator's re-check at
 * loop start) and the evaluator (orchestrator) both use [shared], so a condition that validates is evaluated with
 * exactly the same functions, macros and options.
 *
 * - One declared variable, `vars: map(string, dyn)`, and an empty container: no other name resolves.
 * - Options: integers and doubles compare by value, optional syntax (`vars[?"x"]`, `vars.?x`) on, standard macros on,
 *   and the cost caps of [LoopConditionLimits] (parse recursion, comprehension iterations, regex program size).
 * - Extensions: strings, math, lists, sets, regex, encoders, bindings (`cel.bind`), optional, comprehensions
 *   (two-variable macros). Not protos (no proto types are declared) and not native types. Every library whose factory
 *   takes a version is pinned to the version named below, so a cel-java upgrade cannot add functions silently;
 *   strings, encoders and bindings have no version parameter, and the function-name pin test covers them.
 * - No clock function exists in CEL (`now()` is an undeclared reference), and cel-java calls no clock, random or UUID
 *   source, so evaluation is deterministic.
 *
 * cel-java types never leave this module. Immutable and thread-safe.
 */
// The standard (deprecated in 0.15.0) builder is the runtime the in-workflow replay tests verify; the newer planner
// builder rejects null values inside the `vars` map, which flow variables may hold.
@Suppress("DEPRECATION")
internal class LoopCelEnvironment private constructor() {

  companion object {
    /** Latest math library version in cel-java 0.15.0 (`math.sqrt`, the bit functions, ...). */
    const val MATH_VERSION = 2

    /** Latest lists library version in cel-java 0.15.0 (`lists.range`, `sortBy`, ...). */
    const val LISTS_VERSION = 2

    /** Latest optional library version in cel-java 0.15.0 (`first`, `last`, `optional.unwrap`, ...). */
    const val OPTIONAL_VERSION = 2

    /** The only sets library version in cel-java 0.15.0. */
    const val SETS_VERSION = 0

    /** The only regex library version in cel-java 0.15.0. */
    const val REGEX_VERSION = 0

    /** The only comprehensions library version in cel-java 0.15.0. */
    const val COMPREHENSIONS_VERSION = 0

    private const val SOURCE_DESCRIPTION = "loop condition"

    /** Built once per JVM; first use costs about 100 ms (class loading), so the orchestrator warms it up at start. */
    val shared: LoopCelEnvironment by lazy { LoopCelEnvironment() }
  }

  private val options: CelOptions = CelOptions.current()
    .enableHeterogeneousNumericComparisons(true)
    .enableOptionalSyntax(true)
    .maxParseRecursionDepth(LoopConditionLimits.MAX_PARSE_RECURSION_DEPTH)
    .comprehensionMaxIterations(LoopConditionLimits.MAX_COMPREHENSION_ITERATIONS)
    .maxRegexProgramSize(LoopConditionLimits.MAX_REGEX_PROGRAM_SIZE)
    .build()

  private val stringsLibrary = CelExtensions.strings()
  private val mathLibrary = CelExtensions.math(MATH_VERSION)
  private val listsLibrary = CelExtensions.lists(LISTS_VERSION)
  private val setsLibrary = CelExtensions.sets(SETS_VERSION)
  private val regexLibrary = CelExtensions.regex(REGEX_VERSION)
  private val encodersLibrary = CelExtensions.encoders(options)
  private val bindingsLibrary = CelExtensions.bindings()
  private val optionalLibrary = CelExtensions.optional(OPTIONAL_VERSION)
  private val comprehensionsLibrary = CelExtensions.comprehensions(COMPREHENSIONS_VERSION)

  private val featureSets: List<CelExtensionLibrary.FeatureSet> = listOf(
    stringsLibrary,
    mathLibrary,
    listsLibrary,
    setsLibrary,
    regexLibrary,
    encodersLibrary,
    bindingsLibrary,
    optionalLibrary,
    comprehensionsLibrary
  )

  private val cel: Cel = CelFactory.standardCelBuilder()
    .setOptions(options)
    .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
    .addCompilerLibraries(
      stringsLibrary,
      mathLibrary,
      listsLibrary,
      setsLibrary,
      regexLibrary,
      encodersLibrary,
      bindingsLibrary,
      optionalLibrary,
      comprehensionsLibrary
    )
    .addRuntimeLibraries(
      stringsLibrary,
      mathLibrary,
      listsLibrary,
      setsLibrary,
      regexLibrary,
      encodersLibrary,
      optionalLibrary,
      comprehensionsLibrary
    )
    .addVar(LoopConditionVariables.IDENTIFIER, MapType.create(SimpleType.STRING, SimpleType.DYN))
    .build()

  /** Runs cel-java's regex, timestamp and duration literal checks on a checked AST. */
  val literalValidator: CelValidator = CelValidatorFactory.standardCelValidatorBuilder(cel)
    .addAstValidators(RegexLiteralValidator.INSTANCE, TimestampLiteralValidator.INSTANCE, DurationLiteralValidator.INSTANCE)
    .build()

  /** Parses [text] (macros expanded). May throw [StackOverflowError] or a runtime exception for hostile input. */
  fun parse(text: String): CelValidationResult = cel.parse(text, SOURCE_DESCRIPTION)

  /** Type-checks a parsed AST against `vars: map(string, dyn)` and every enabled library. */
  fun check(parsedAst: CelAbstractSyntaxTree): CelValidationResult = cel.check(parsedAst)

  /** The runnable program of a checked AST. Programs are immutable and may be evaluated many times. */
  fun program(checkedAst: CelAbstractSyntaxTree): CelRuntime.Program = cel.createProgram(checkedAst)

  /** Every function and macro name the environment offers: standard CEL plus the enabled extension libraries. */
  fun functionNames(): Set<String> {
    val standardNames = CelStandardDeclarations.getAllFunctionNames()
    val standardMacroNames = CelStandardMacro.STANDARD_MACROS.map { macro -> macro.function }
    val extensionNames = featureSets.flatMap { featureSet ->
      featureSet.functions().map { declaration -> declaration.name() } + featureSet.macros().map { macro -> macro.function }
    }
    return (standardNames + standardMacroNames + extensionNames).toSortedSet()
  }
}
