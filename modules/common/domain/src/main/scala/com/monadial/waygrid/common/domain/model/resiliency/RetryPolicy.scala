package com.monadial.waygrid.common.domain.model.resiliency

import scala.concurrent.duration.*

/**
 * Represents a general retry policy for any operation (network, IO, routing, etc).
 * Pure, domain-level description — no side effects or runtime semantics.
 */
sealed trait RetryPolicy

sealed trait RetryPolicyWithMaxRetries extends RetryPolicy:
  def maxRetries: Int

sealed trait RetryPolicyWithBaseDelay extends RetryPolicy:
  def base: FiniteDuration

object RetryPolicy:
  case object None extends RetryPolicy

  final case class Linear(
    base: FiniteDuration,
    maxRetries: Int
  ) extends RetryPolicy with RetryPolicyWithBaseDelay with RetryPolicyWithMaxRetries

  final case class Exponential(
    base: FiniteDuration,
    maxRetries: Int
  ) extends RetryPolicy with RetryPolicyWithBaseDelay with RetryPolicyWithMaxRetries

  final case class BoundedExponential(
    base: FiniteDuration,
    cap: FiniteDuration,
    maxRetries: Int
  ) extends RetryPolicy with RetryPolicyWithBaseDelay with RetryPolicyWithMaxRetries

  final case class Fibonacci(
    base: FiniteDuration,
    maxRetries: Int
  ) extends RetryPolicy with RetryPolicyWithBaseDelay with RetryPolicyWithMaxRetries

  final case class Polynomial(
    base: FiniteDuration,
    maxRetries: Int,
    power: Double
  ) extends RetryPolicy with RetryPolicyWithBaseDelay with RetryPolicyWithMaxRetries

  final case class DecorrelatedJitter(
    base: FiniteDuration,
    maxRetries: Int
  ) extends RetryPolicy with RetryPolicyWithBaseDelay with RetryPolicyWithMaxRetries

  final case class FullJitter(
    base: FiniteDuration,
    maxRetries: Int
  ) extends RetryPolicy with RetryPolicyWithBaseDelay with RetryPolicyWithMaxRetries
