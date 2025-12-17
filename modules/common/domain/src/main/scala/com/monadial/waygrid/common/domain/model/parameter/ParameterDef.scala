package com.monadial.waygrid.common.domain.model.parameter

/**
 * Definition of a parameter that a service accepts.
 *
 * This is part of the service's contract - when a service registers
 * with topology, it declares what parameters it accepts using a list
 * of [[ParameterDef]].
 *
 * ==Example==
 * {{{
 * // OpenAI processor declares its parameters:
 * List(
 *   ParameterDef("apiKey", ParameterType.StringType, required = true, sensitive = true),
 *   ParameterDef("model", ParameterType.oneOf("gpt-4", "gpt-3.5-turbo"), required = true),
 *   ParameterDef("temperature", ParameterType.FloatType, required = false,
 *                constraint = Some(ParameterConstraint(min = Some(0), max = Some(2)))),
 *   ParameterDef("maxTokens", ParameterType.IntType, required = false,
 *                defaultValue = Some(ParameterValue.IntVal(1000)))
 * )
 * }}}
 *
 * @param name         Parameter name (used as key in the parameters map)
 * @param paramType    Type of the parameter
 * @param required     Whether the parameter is required
 * @param sensitive    Whether the parameter contains sensitive data (should use SecretRef)
 * @param defaultValue Default value if not provided (only for optional parameters)
 * @param constraint   Optional constraints on the value
 * @param description  Human-readable description
 */
case class ParameterDef(
  name: String,
  paramType: ParameterType,
  required: Boolean,
  sensitive: Boolean = false,
  defaultValue: Option[ParameterValue] = None,
  constraint: Option[ParameterConstraint] = None,
  description: Option[String] = None
)

object ParameterDef:
  /** Create a required string parameter */
  def requiredString(name: String, description: String = ""): ParameterDef =
    ParameterDef(name, ParameterType.StringType, required = true,
      description = if description.isEmpty then None else Some(description))

  /** Create a required sensitive parameter (should use SecretRef) */
  def requiredSecret(name: String, description: String = ""): ParameterDef =
    ParameterDef(name, ParameterType.StringType, required = true, sensitive = true,
      description = if description.isEmpty then None else Some(description))

  /** Create an optional string parameter with default */
  def optionalString(name: String, default: String): ParameterDef =
    ParameterDef(name, ParameterType.StringType, required = false,
      defaultValue = Some(ParameterValue.StringVal(default)))

  /** Create an optional int parameter with default */
  def optionalInt(name: String, default: Int): ParameterDef =
    ParameterDef(name, ParameterType.IntType, required = false,
      defaultValue = Some(ParameterValue.IntVal(default)))

  /** Create an optional float parameter with default and constraints */
  def optionalFloat(name: String, default: Double, min: Double, max: Double): ParameterDef =
    ParameterDef(name, ParameterType.FloatType, required = false,
      defaultValue = Some(ParameterValue.FloatVal(default)),
      constraint = Some(ParameterConstraint(min = Some(BigDecimal(min)), max = Some(BigDecimal(max)))))

  /** Create a required enum parameter */
  def requiredEnum(name: String, values: String*): ParameterDef =
    require(values.nonEmpty, "Enum must have at least one value")
    ParameterDef(name, ParameterType.oneOf(values.head, values.tail*), required = true)

/**
 * Constraints on parameter values.
 *
 * @param minLength Minimum string length
 * @param maxLength Maximum string length
 * @param min       Minimum numeric value (for Int/Float)
 * @param max       Maximum numeric value (for Int/Float)
 * @param pattern   Regex pattern for string validation
 */
case class ParameterConstraint(
  minLength: Option[Int] = None,
  maxLength: Option[Int] = None,
  min: Option[BigDecimal] = None,
  max: Option[BigDecimal] = None,
  pattern: Option[String] = None
)

object ParameterConstraint:
  /** String length constraint */
  def length(min: Int, max: Int): ParameterConstraint =
    ParameterConstraint(minLength = Some(min), maxLength = Some(max))

  /** Numeric range constraint */
  def range(min: BigDecimal, max: BigDecimal): ParameterConstraint =
    ParameterConstraint(min = Some(min), max = Some(max))

  /** Regex pattern constraint */
  def regex(pattern: String): ParameterConstraint =
    ParameterConstraint(pattern = Some(pattern))
