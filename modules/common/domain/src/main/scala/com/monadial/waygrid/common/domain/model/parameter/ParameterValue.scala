package com.monadial.waygrid.common.domain.model.parameter

/**
 * A parameter value that can be passed to a service (processor/destination).
 *
 * Parameters are either:
 *   - '''Literal values''': Static values defined directly in the spec
 *   - '''Secret references''': References to secrets stored in the secure store,
 *     resolved at runtime just before service invocation
 *
 * ==Usage Example==
 * {{{
 * // In a Spec Node:
 * parameters = Map(
 *   "apiKey"      -> ParameterValue.Secret(SecretReference("tenant-1/openai/key")),
 *   "model"       -> ParameterValue.StringVal("gpt-4"),
 *   "temperature" -> ParameterValue.FloatVal(0.7),
 *   "maxTokens"   -> ParameterValue.IntVal(1000),
 *   "stream"      -> ParameterValue.BoolVal(true)
 * )
 * }}}
 */
enum ParameterValue:
  /** String literal value */
  case StringVal(value: String)

  /** Integer literal value */
  case IntVal(value: Int)

  /** Floating-point literal value */
  case FloatVal(value: Double)

  /** Boolean literal value */
  case BoolVal(value: Boolean)

  /** Reference to a secret in the secure store (resolved at runtime) */
  case Secret(ref: SecretReference)

object ParameterValue:

  /** Convenience constructors */
  def string(value: String): ParameterValue = StringVal(value)
  def int(value: Int): ParameterValue = IntVal(value)
  def float(value: Double): ParameterValue = FloatVal(value)
  def bool(value: Boolean): ParameterValue = BoolVal(value)
  def secret(path: String): ParameterValue = Secret(SecretReference(SecretPath(path)))
  def secret(path: String, field: String): ParameterValue =
    Secret(SecretReference(SecretPath(path), field = Some(field)))
