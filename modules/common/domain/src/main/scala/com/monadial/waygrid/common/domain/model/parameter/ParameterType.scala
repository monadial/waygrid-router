package com.monadial.waygrid.common.domain.model.parameter

import cats.data.NonEmptyList

/**
 * Type of a parameter that a service can accept.
 *
 * Used in [[ServiceParameterSchema]] to define what parameters
 * a processor or destination expects.
 */
enum ParameterType:
  /** String parameter */
  case StringType

  /** Integer parameter */
  case IntType

  /** Floating-point parameter */
  case FloatType

  /** Boolean parameter */
  case BoolType

  /** Enumeration with allowed values */
  case EnumType(values: NonEmptyList[String])

object ParameterType:
  /** Create an enum type from a list of allowed values */
  def oneOf(head: String, tail: String*): ParameterType =
    EnumType(NonEmptyList(head, tail.toList))
