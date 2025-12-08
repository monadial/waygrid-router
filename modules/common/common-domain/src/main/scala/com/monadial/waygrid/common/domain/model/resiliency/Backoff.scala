package com.monadial.waygrid.common.domain.model.resiliency

import com.monadial.waygrid.common.domain.model.resiliency.Value.{DefaultJitter, HashKey, JitterConfig, JitterMode}

import scala.concurrent.duration.*
import scala.util.Random
import scala.util.hashing.MurmurHash3

/**
 * Typeclass for computing the next retry delay given a [[RetryPolicy]].
 * Deterministic and referentially transparent.
 */
trait Backoff[P <: RetryPolicy]:
  def nextDelay(policy: P, attempt: Int, hashKey: Option[HashKey] = None): Option[FiniteDuration]

object Backoff:

  inline def apply[P <: RetryPolicy](using ev: Backoff[P]): Backoff[P] = ev

  private def hashJitter(hashKey: HashKey, cfg: JitterConfig)(delay: FiniteDuration): FiniteDuration =
    val hash    = MurmurHash3.bytesHash(hashKey.unwrap.toArray)
    val millis  = delay.toMillis
    val percent = (hash % 100).abs.toDouble / 100.0 * cfg.maxPercent
    val sign =
      cfg.mode match
        case JitterMode.Symmetric    => if (hash & 1) == 0 then 1.0 else -1.0
        case JitterMode.PositiveOnly => 1.0
        case JitterMode.NegativeOnly => -1.0
    val jitter = 1.0 + (sign * percent)
    (millis * jitter).millis

  private def deterministicJitter(delay: FiniteDuration, cfg: JitterConfig): FiniteDuration =
    val millis = delay.toMillis
    val factor = 1.0 + (((millis % 97).toDouble / 100.0) * cfg.maxPercent)
    (millis * factor).millis

  private def randomJitter(base: FiniteDuration, max: FiniteDuration, cfg: JitterConfig): FiniteDuration =
    val minMillis = base.toMillis
    val maxMillis = max.toMillis
    val diff      = (maxMillis - minMillis).max(1L)
    val rand      = Random.nextLong(diff)
    val raw       = minMillis + rand
    val jittered: Double = cfg.mode match
      case JitterMode.Symmetric    => raw.toDouble
      case JitterMode.PositiveOnly => raw + (diff * cfg.maxPercent)
      case JitterMode.NegativeOnly => raw - (diff * cfg.maxPercent)
    jittered.toLong.millis

  private def fibNum(n: Int): Int =
    @annotation.tailrec
    def go(i: Int, a: Int, b: Int): Int =
      if i <= 0 then a else go(i - 1, b, a + b)
    go(n, 0, 1)

  given Backoff[RetryPolicy.None.type] with
    def nextDelay(p: RetryPolicy.None.type, attempt: Int, hashKey: Option[HashKey]): Option[FiniteDuration] = None

  given Backoff[RetryPolicy.Linear] with
    private inline def cfg = DefaultJitter.Linear
    def nextDelay(p: RetryPolicy.Linear, attempt: Int, hashKey: Option[HashKey]): Option[FiniteDuration] =
      Option.when(attempt <= p.maxRetries):
          hashKey.fold(deterministicJitter(p.base, cfg))(key => hashJitter(key, cfg)(p.base))

  given Backoff[RetryPolicy.Exponential] with
    private inline def cfg = DefaultJitter.Exponential
    def nextDelay(p: RetryPolicy.Exponential, attempt: Int, hashKey: Option[HashKey]): Option[FiniteDuration] =
      Option.when(attempt <= p.maxRetries):
          val exp = p.base * math.pow(2, attempt - 1).toLong
          hashKey.fold(deterministicJitter(exp, cfg))(key => hashJitter(key, cfg)(exp))

  given Backoff[RetryPolicy.BoundedExponential] with
    private inline def cfg = DefaultJitter.BoundedExponential
    def nextDelay(p: RetryPolicy.BoundedExponential, attempt: Int, hashKey: Option[HashKey]): Option[FiniteDuration] =
      Option.when(attempt <= p.maxRetries):
          val raw     = p.base * math.pow(2, attempt - 1).toLong
          val bounded = if raw > p.cap then p.cap else raw
          hashKey.fold(deterministicJitter(bounded, cfg))(key => hashJitter(key, cfg)(bounded))

  given Backoff[RetryPolicy.Polynomial] with
    private inline def cfg = DefaultJitter.Polynomial
    def nextDelay(p: RetryPolicy.Polynomial, attempt: Int, hashKey: Option[HashKey]): Option[FiniteDuration] =
      Option.when(attempt <= p.maxRetries):
          val value = p.base * math.pow(attempt.toDouble, p.power).toLong
          hashKey.fold(deterministicJitter(value, cfg))(key => hashJitter(key, cfg)(value))

  given Backoff[RetryPolicy.Fibonacci] with
    private inline def cfg = DefaultJitter.Fibonacci
    def nextDelay(p: RetryPolicy.Fibonacci, attempt: Int, hashKey: Option[HashKey]): Option[FiniteDuration] =
      Option.when(attempt <= p.maxRetries):
          val fib   = fibNum(attempt)
          val delay = p.base * fib.toLong
          hashKey.fold(deterministicJitter(delay, cfg))(key => hashJitter(key, cfg)(delay))

  given Backoff[RetryPolicy.DecorrelatedJitter] with
    private inline def cfg = DefaultJitter.Randomized
    def nextDelay(p: RetryPolicy.DecorrelatedJitter, attempt: Int, hashKey: Option[HashKey]): Option[FiniteDuration] =
      Option.when(attempt <= p.maxRetries):
          val maxDelay = p.base * math.pow(3, attempt - 1).toLong
          randomJitter(p.base, maxDelay, cfg)

  given Backoff[RetryPolicy.FullJitter] with
    private inline def cfg = DefaultJitter.Randomized
    def nextDelay(p: RetryPolicy.FullJitter, attempt: Int, hashKey: Option[HashKey]): Option[FiniteDuration] =
      Option.when(attempt <= p.maxRetries):
          val maxDelay = p.base * math.pow(2, attempt - 1).toLong
          randomJitter(0.millis, maxDelay, cfg)

  given Backoff[RetryPolicy] with
    def nextDelay(p: RetryPolicy, attempt: Int, hashKey: Option[HashKey]): Option[FiniteDuration] =
      p match
        case p: RetryPolicy.Linear => Backoff[RetryPolicy.Linear]
            .nextDelay(p, attempt, hashKey)
        case p: RetryPolicy.Exponential => Backoff[RetryPolicy.Exponential]
            .nextDelay(p, attempt, hashKey)
        case p: RetryPolicy.BoundedExponential => Backoff[RetryPolicy.BoundedExponential]
            .nextDelay(p, attempt, hashKey)
        case p: RetryPolicy.Polynomial => Backoff[RetryPolicy.Polynomial]
            .nextDelay(p, attempt, hashKey)
        case p: RetryPolicy.Fibonacci => Backoff[RetryPolicy.Fibonacci]
            .nextDelay(p, attempt, hashKey)
        case p: RetryPolicy.DecorrelatedJitter => Backoff[RetryPolicy.DecorrelatedJitter]
            .nextDelay(p, attempt, hashKey)
        case p: RetryPolicy.FullJitter => Backoff[RetryPolicy.FullJitter]
            .nextDelay(p, attempt, hashKey)
        case RetryPolicy.None => None

  extension [P <: RetryPolicy](policy: P)(using ev: Backoff[P])
    def nextDelay(attempt: Int, hashKey: Option[HashKey] = None): Option[FiniteDuration] =
      ev.nextDelay(policy, attempt, hashKey)
