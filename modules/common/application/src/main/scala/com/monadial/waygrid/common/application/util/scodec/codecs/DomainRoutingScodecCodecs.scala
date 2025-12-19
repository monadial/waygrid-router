package com.monadial.waygrid.common.application.util.scodec.codecs

import java.time.Instant

import scala.concurrent.duration.FiniteDuration

import com.monadial.waygrid.common.application.instances.DurationInstances.given
import com.monadial.waygrid.common.domain.model.resiliency.RetryPolicy
import com.monadial.waygrid.common.domain.model.routing.Value.{
  DeliveryStrategy,
  RepeatPolicy,
  RepeatTimes,
  RepeatUntilDate
}
import scodec.*
import scodec.bits.*
import scodec.codecs.*

/**
 * Scodec binary codecs for routing-related domain types.
 *
 * Type discriminators:
 * - RetryPolicy: 0x00=None, 0x01=Linear, 0x02=Exponential, 0x03=BoundedExponential,
 *                0x04=Fibonacci, 0x05=Polynomial, 0x06=DecorrelatedJitter, 0x07=FullJitter
 * - RepeatPolicy: 0x00=NoRepeat, 0x01=Indefinitely, 0x02=Times, 0x03=Until
 * - DeliveryStrategy: 0x00=Immediate, 0x01=ScheduleAfter, 0x02=ScheduleAt
 */
object DomainRoutingScodecCodecs:

  // ---------------------------------------------------------------------------
  // Value type codecs
  // ---------------------------------------------------------------------------

  given Codec[RepeatTimes] =
    int64.xmap(RepeatTimes(_), _.unwrap)

  given Codec[RepeatUntilDate] =
    int64.xmap(
      millis => RepeatUntilDate(Instant.ofEpochMilli(millis)),
      date => date.unwrap.toEpochMilli
    )

  // ---------------------------------------------------------------------------
  // RetryPolicy codec (sealed trait with type discriminator)
  // ---------------------------------------------------------------------------

  private val baseDurationCodec: Codec[FiniteDuration] = summon[Codec[FiniteDuration]]

  private val retryPolicyLinearDataCodec: Codec[(FiniteDuration, Int)] =
    (baseDurationCodec :: int32)

  private val retryPolicyBoundedExponentialDataCodec: Codec[(FiniteDuration, FiniteDuration, Int)] =
    (baseDurationCodec :: baseDurationCodec :: int32)

  private val retryPolicyPolynomialDataCodec: Codec[(FiniteDuration, Int, Double)] =
    (baseDurationCodec :: int32 :: double)

  given Codec[RetryPolicy] = Codec[RetryPolicy](
    (policy: RetryPolicy) =>
      policy match
        case RetryPolicy.None =>
          uint8.encode(0)
        case RetryPolicy.Linear(base, maxRetries) =>
          uint8.encode(1).flatMap(b => retryPolicyLinearDataCodec.encode((base, maxRetries)).map(b ++ _))
        case RetryPolicy.Exponential(base, maxRetries) =>
          uint8.encode(2).flatMap(b => retryPolicyLinearDataCodec.encode((base, maxRetries)).map(b ++ _))
        case RetryPolicy.BoundedExponential(base, cap, maxRetries) =>
          uint8.encode(3).flatMap(b =>
            retryPolicyBoundedExponentialDataCodec.encode((base, cap, maxRetries)).map(b ++ _)
          )
        case RetryPolicy.Fibonacci(base, maxRetries) =>
          uint8.encode(4).flatMap(b => retryPolicyLinearDataCodec.encode((base, maxRetries)).map(b ++ _))
        case RetryPolicy.Polynomial(base, maxRetries, power) =>
          uint8.encode(5).flatMap(b => retryPolicyPolynomialDataCodec.encode((base, maxRetries, power)).map(b ++ _))
        case RetryPolicy.DecorrelatedJitter(base, maxRetries) =>
          uint8.encode(6).flatMap(b => retryPolicyLinearDataCodec.encode((base, maxRetries)).map(b ++ _))
        case RetryPolicy.FullJitter(base, maxRetries) =>
          uint8.encode(7).flatMap(b => retryPolicyLinearDataCodec.encode((base, maxRetries)).map(b ++ _))
    ,
    (bits: BitVector) =>
      uint8.decode(bits).flatMap { result =>
        result.value match
          case 0 => Attempt.successful(DecodeResult(RetryPolicy.None, result.remainder))
          case 1 => retryPolicyLinearDataCodec.decode(result.remainder).map(_.map { case (base, maxRetries) =>
              RetryPolicy.Linear(base, maxRetries)
            })
          case 2 => retryPolicyLinearDataCodec.decode(result.remainder).map(_.map { case (base, maxRetries) =>
              RetryPolicy.Exponential(base, maxRetries)
            })
          case 3 => retryPolicyBoundedExponentialDataCodec.decode(result.remainder).map(_.map {
              case (base, cap, maxRetries) =>
                RetryPolicy.BoundedExponential(base, cap, maxRetries)
            })
          case 4 => retryPolicyLinearDataCodec.decode(result.remainder).map(_.map { case (base, maxRetries) =>
              RetryPolicy.Fibonacci(base, maxRetries)
            })
          case 5 => retryPolicyPolynomialDataCodec.decode(result.remainder).map(_.map {
              case (base, maxRetries, power) =>
                RetryPolicy.Polynomial(base, maxRetries, power)
            })
          case 6 => retryPolicyLinearDataCodec.decode(result.remainder).map(_.map { case (base, maxRetries) =>
              RetryPolicy.DecorrelatedJitter(base, maxRetries)
            })
          case 7 => retryPolicyLinearDataCodec.decode(result.remainder).map(_.map { case (base, maxRetries) =>
              RetryPolicy.FullJitter(base, maxRetries)
            })
          case n => Attempt.failure(Err(s"Unknown RetryPolicy discriminator: $n"))
      }
  )

  // ---------------------------------------------------------------------------
  // RepeatPolicy codec (enum with type discriminator)
  // ---------------------------------------------------------------------------

  private val durationCodec: Codec[FiniteDuration] = summon[Codec[FiniteDuration]]

  given Codec[RepeatPolicy] = Codec[RepeatPolicy](
    (policy: RepeatPolicy) =>
      policy match
        case RepeatPolicy.NoRepeat =>
          uint8.encode(0)
        case RepeatPolicy.Indefinitely(every) =>
          uint8.encode(1).flatMap(b => durationCodec.encode(every).map(b ++ _))
        case RepeatPolicy.Times(every, times) =>
          uint8.encode(2).flatMap(b =>
            (durationCodec :: summon[Codec[RepeatTimes]]).encode((every, times)).map(b ++ _)
          )
        case RepeatPolicy.Until(every, until) =>
          uint8.encode(3).flatMap(b =>
            (durationCodec :: summon[Codec[RepeatUntilDate]]).encode((every, until)).map(b ++ _)
          )
    ,
    (bits: BitVector) =>
      uint8.decode(bits).flatMap { result =>
        result.value match
          case 0 => Attempt.successful(DecodeResult(RepeatPolicy.NoRepeat, result.remainder))
          case 1 => durationCodec.decode(result.remainder).map(_.map(RepeatPolicy.Indefinitely(_)))
          case 2 =>
            (durationCodec :: summon[Codec[RepeatTimes]]).decode(result.remainder).map(_.map {
              case (every, times) => RepeatPolicy.Times(every, times)
            })
          case 3 =>
            (durationCodec :: summon[Codec[RepeatUntilDate]]).decode(result.remainder).map(_.map {
              case (every, until) => RepeatPolicy.Until(every, until)
            })
          case n => Attempt.failure(Err(s"Unknown RepeatPolicy discriminator: $n"))
      }
  )

  // ---------------------------------------------------------------------------
  // DeliveryStrategy codec (enum with type discriminator)
  // ---------------------------------------------------------------------------

  given Codec[DeliveryStrategy] = Codec[DeliveryStrategy](
    (strategy: DeliveryStrategy) =>
      strategy match
        case DeliveryStrategy.Immediate =>
          uint8.encode(0)
        case DeliveryStrategy.ScheduleAfter(delay) =>
          uint8.encode(1).flatMap(b => durationCodec.encode(delay).map(b ++ _))
        case DeliveryStrategy.ScheduleAt(time) =>
          uint8.encode(2).flatMap(b => int64.encode(time.toEpochMilli).map(b ++ _))
    ,
    (bits: BitVector) =>
      uint8.decode(bits).flatMap { result =>
        result.value match
          case 0 => Attempt.successful(DecodeResult(DeliveryStrategy.Immediate, result.remainder))
          case 1 => durationCodec.decode(result.remainder).map(_.map(DeliveryStrategy.ScheduleAfter(_)))
          case 2 =>
            int64.decode(result.remainder).map(_.map(ms => DeliveryStrategy.ScheduleAt(Instant.ofEpochMilli(ms))))
          case n => Attempt.failure(Err(s"Unknown DeliveryStrategy discriminator: $n"))
      }
  )
