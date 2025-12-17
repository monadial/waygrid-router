package com.monadial.waygrid.common.domain.algebra

import cats.data.Validated
import cats.data.ValidatedNel
import cats.syntax.all.*
import com.monadial.waygrid.common.domain.model.parameter.*

/**
 * Validates parameter values against a parameter schema.
 *
 * This algebra validates:
 * - Required parameters are present
 * - Parameter types match their definitions
 * - Constraints (min/max, length, pattern) are satisfied
 * - Enum values are valid
 * - Sensitive parameters use SecretReference
 *
 * ==Example==
 * {{{
 * val schema = ServiceParameterSchema(
 *   serviceAddress = ServiceAddress("waygrid://processor/openai"),
 *   parameters = List(
 *     ParameterDef.requiredSecret("apiKey", "OpenAI API key"),
 *     ParameterDef.requiredEnum("model", "gpt-4", "gpt-3.5-turbo"),
 *     ParameterDef.optionalFloat("temperature", default = 0.7, min = 0.0, max = 2.0)
 *   )
 * )
 *
 * val params = Map(
 *   "apiKey" -> ParameterValue.Secret(SecretReference("tenant-123/openai")),
 *   "model" -> ParameterValue.StringVal("gpt-4")
 * )
 *
 * ParameterValidator.validate(schema, params) // Valid with defaulted temperature
 * }}}
 */
object ParameterValidator:

  /** Validation error types */
  enum ValidationError:
    /** Required parameter is missing */
    case MissingRequired(paramName: String)

    /** Parameter type doesn't match definition */
    case TypeMismatch(paramName: String, expected: ParameterType, actual: ParameterValue)

    /** Enum value is not in allowed list */
    case InvalidEnumValue(paramName: String, value: String, allowed: List[String])

    /** Numeric value is out of range */
    case OutOfRange(paramName: String, value: BigDecimal, constraint: ParameterConstraint)

    /** String length constraint violated */
    case LengthViolation(paramName: String, length: Int, constraint: ParameterConstraint)

    /** String pattern constraint violated */
    case PatternViolation(paramName: String, value: String, pattern: String)

    /** Sensitive parameter should use SecretReference */
    case SensitiveNotSecret(paramName: String)

    /** Unknown parameter not in schema */
    case UnknownParameter(paramName: String)

    def message: String = this match
      case MissingRequired(name) => s"Required parameter '$name' is missing"
      case TypeMismatch(name, expected, actual) =>
        s"Parameter '$name' type mismatch: expected $expected but got ${actual.productPrefix}"
      case InvalidEnumValue(name, value, allowed) =>
        s"Parameter '$name' value '$value' not in allowed values: ${allowed.mkString(", ")}"
      case OutOfRange(name, value, constraint) =>
        val range = (constraint.min, constraint.max) match
          case (Some(min), Some(max)) => s"[$min, $max]"
          case (Some(min), None)      => s">= $min"
          case (None, Some(max))      => s"<= $max"
          case _                      => "unknown"
        s"Parameter '$name' value $value is out of range $range"
      case LengthViolation(name, len, constraint) =>
        val range = (constraint.minLength, constraint.maxLength) match
          case (Some(min), Some(max)) => s"[$min, $max]"
          case (Some(min), None)      => s">= $min"
          case (None, Some(max))      => s"<= $max"
          case _                      => "unknown"
        s"Parameter '$name' length $len is out of range $range"
      case PatternViolation(name, value, pattern) =>
        s"Parameter '$name' value '$value' does not match pattern '$pattern'"
      case SensitiveNotSecret(name) =>
        s"Sensitive parameter '$name' should use SecretReference"
      case UnknownParameter(name) =>
        s"Unknown parameter '$name' not defined in schema"

  type ValidationResult[A] = ValidatedNel[ValidationError, A]

  /**
   * Validate parameters against a schema.
   *
   * @param schema The service parameter schema
   * @param params The parameter values to validate
   * @param allowUnknown Whether to allow parameters not in the schema (default: false)
   * @return Validated result with all errors accumulated
   */
  def validate(
    schema: ServiceParameterSchema,
    params: Map[String, ParameterValue],
    allowUnknown: Boolean = false
  ): ValidationResult[Map[String, ParameterValue]] =
    val requiredCheck = validateRequired(schema, params)
    val typeChecks = params.toList.traverse { case (name, value) =>
      schema.get(name) match
        case Some(paramDef)       => validateParameter(name, value, paramDef)
        case None if allowUnknown => (name, value).validNel
        case None                 => ValidationError.UnknownParameter(name).invalidNel
    }

    (requiredCheck, typeChecks).mapN { (_, validatedParams) =>
      // Apply defaults for missing optional parameters
      val withDefaults = schema.parameters.foldLeft(validatedParams.toMap) { (acc, paramDef) =>
        if acc.contains(paramDef.name) then acc
        else paramDef.defaultValue.fold(acc)(default => acc + (paramDef.name -> default))
      }
      withDefaults
    }

  /**
   * Validate that all required parameters are present.
   */
  private def validateRequired(
    schema: ServiceParameterSchema,
    params: Map[String, ParameterValue]
  ): ValidationResult[Unit] =
    val missing = schema.requiredNames -- params.keySet
    if missing.isEmpty then ().validNel
    else
      missing.toList.map(ValidationError.MissingRequired(_)).foldLeft(().validNel[ValidationError]) {
        (acc, err) => acc *> err.invalidNel
      }

  /**
   * Validate a single parameter value against its definition.
   */
  private def validateParameter(
    name: String,
    value: ParameterValue,
    paramDef: ParameterDef
  ): ValidationResult[(String, ParameterValue)] =
    val sensitiveCheck = validateSensitive(name, value, paramDef)
    val typeCheck      = validateType(name, value, paramDef.paramType)
    val constraintCheck = paramDef.constraint.fold(().validNel[ValidationError])(c =>
      validateConstraint(name, value, c)
    )

    (sensitiveCheck, typeCheck, constraintCheck).mapN((_, _, _) => (name, value))

  /**
   * Validate that sensitive parameters use SecretReference.
   */
  private def validateSensitive(
    name: String,
    value: ParameterValue,
    paramDef: ParameterDef
  ): ValidationResult[Unit] =
    if paramDef.sensitive then
      value match
        case ParameterValue.Secret(_) => ().validNel
        case _                        => ValidationError.SensitiveNotSecret(name).invalidNel
    else ().validNel

  /**
   * Validate that the value type matches the expected type.
   */
  private def validateType(
    name: String,
    value: ParameterValue,
    expectedType: ParameterType
  ): ValidationResult[Unit] =
    (value, expectedType) match
      case (ParameterValue.StringVal(_), ParameterType.StringType) => ().validNel
      case (ParameterValue.IntVal(_), ParameterType.IntType)       => ().validNel
      case (ParameterValue.FloatVal(_), ParameterType.FloatType)   => ().validNel
      case (ParameterValue.BoolVal(_), ParameterType.BoolType)     => ().validNel
      case (ParameterValue.StringVal(v), ParameterType.EnumType(allowed)) =>
        if allowed.toList.contains(v) then ().validNel
        else ValidationError.InvalidEnumValue(name, v, allowed.toList).invalidNel
      case (ParameterValue.Secret(_), _) =>
        // Secrets are resolved at runtime, type checking happens then
        ().validNel
      case _ =>
        ValidationError.TypeMismatch(name, expectedType, value).invalidNel

  /**
   * Validate value against constraints.
   */
  private def validateConstraint(
    name: String,
    value: ParameterValue,
    constraint: ParameterConstraint
  ): ValidationResult[Unit] =
    value match
      case ParameterValue.StringVal(s) =>
        val lengthCheck  = validateStringLength(name, s, constraint)
        val patternCheck = validatePattern(name, s, constraint)
        (lengthCheck, patternCheck).mapN((_, _) => ())

      case ParameterValue.IntVal(i) =>
        validateNumericRange(name, BigDecimal(i), constraint)

      case ParameterValue.FloatVal(f) =>
        validateNumericRange(name, BigDecimal(f), constraint)

      case _ => ().validNel

  private def validateStringLength(
    name: String,
    value: String,
    constraint: ParameterConstraint
  ): ValidationResult[Unit] =
    val len   = value.length
    val minOk = constraint.minLength.forall(len >= _)
    val maxOk = constraint.maxLength.forall(len <= _)
    if minOk && maxOk then ().validNel
    else ValidationError.LengthViolation(name, len, constraint).invalidNel

  private def validatePattern(
    name: String,
    value: String,
    constraint: ParameterConstraint
  ): ValidationResult[Unit] =
    constraint.pattern match
      case Some(pattern) if !value.matches(pattern) =>
        ValidationError.PatternViolation(name, value, pattern).invalidNel
      case _ => ().validNel

  private def validateNumericRange(
    name: String,
    value: BigDecimal,
    constraint: ParameterConstraint
  ): ValidationResult[Unit] =
    val minOk = constraint.min.forall(value >= _)
    val maxOk = constraint.max.forall(value <= _)
    if minOk && maxOk then ().validNel
    else ValidationError.OutOfRange(name, value, constraint).invalidNel
