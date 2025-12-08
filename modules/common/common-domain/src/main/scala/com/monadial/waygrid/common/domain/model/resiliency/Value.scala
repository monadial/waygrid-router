package com.monadial.waygrid.common.domain.model.resiliency

import com.monadial.waygrid.common.domain.algebra.value.bytes.BytesValue

object Value:

  type HashKey = HashKey.Type
  object HashKey extends BytesValue

  /**
   * Jitter configuration controlling randomness amplitude and bias.
   *
   * @param maxPercent  maximum jitter deviation (e.g. 0.2 = ±20%)
   * @param mode        direction of jitter: symmetric or biased
   */
  final case class JitterConfig(maxPercent: Double, mode: JitterMode)

  enum JitterMode:
    case Symmetric, PositiveOnly, NegativeOnly

  object DefaultJitter:
    val Linear             = JitterConfig(0.10, JitterMode.Symmetric)
    val Exponential        = JitterConfig(0.25, JitterMode.Symmetric)
    val BoundedExponential = JitterConfig(0.20, JitterMode.PositiveOnly)
    val Polynomial         = JitterConfig(0.15, JitterMode.Symmetric)
    val Fibonacci          = JitterConfig(0.10, JitterMode.Symmetric)
    val Randomized         = JitterConfig(0.30, JitterMode.Symmetric)
