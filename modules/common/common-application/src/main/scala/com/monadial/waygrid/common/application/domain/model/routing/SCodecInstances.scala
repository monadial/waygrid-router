package com.monadial.waygrid.common.application.domain.model.routing

import com.monadial.waygrid.common.domain.model.routing.Value.{
  DeliveryStrategy,
  RepeatPolicy,
  RepeatTimes,
  RepeatUntilDate,
  RepeatUntilPolicy,
  RetryMaxRetries,
  RetryPolicy
}
import com.monadial.waygrid.common.domain.model.routing.{ RouteGraph, RouteNode }
import com.monadial.waygrid.common.application.instances.DurationInstances.given
import com.monadial.waygrid.common.domain.model.routing.Value.RetryPolicy.{ Exponential, Linear }
import scodec.Codec

import scala.concurrent.duration.FiniteDuration

object SCodecInstances:

  given Codec[RetryPolicy] = scodec
    .codecs
    .discriminated[RetryPolicy]
    .by(scodec.codecs.int8)
    .typecase(1, scodec.codecs.provide(RetryPolicy.NoRetry))
    .typecase(
      1,
      (Codec[FiniteDuration] :: Codec[RetryMaxRetries]).xmap(
        Linear.apply.tupled,
        {
          case Linear(delay, maxRetries) => (delay, maxRetries)
          case _                         => throw new IllegalArgumentException("Invalid Linear RetryPolicy format")
        }
      )
    )
    .typecase(
      2,
      (Codec[FiniteDuration] :: Codec[RetryMaxRetries]).xmap(
        Exponential.apply.tupled,
        {
          case Exponential(baseDelay, maxRetries) => (baseDelay, maxRetries)
          case _                                  => throw new IllegalArgumentException("Invalid Exponential RetryPolicy format")
        }
      )
    )

  given Codec[RepeatUntilPolicy] = scodec
    .codecs
    .discriminated[RepeatUntilPolicy]
    .by(scodec.codecs.int8)
    .typecase(1, scodec.codecs.provide(RepeatUntilPolicy.Indefinitely))
    .typecase(
      2,
      Codec[RepeatTimes]
        .xmap(
          RepeatUntilPolicy.Times.apply,
          {
            case RepeatUntilPolicy.Times(count) => count
            case _                              => throw new IllegalArgumentException("Invalid RepeatUntilPolicy.Times format")
          }
        )
    )
    .typecase(
      3,
      Codec[RepeatUntilDate]
        .xmap(
          RepeatUntilPolicy.UntilDate.apply,
          {
            case RepeatUntilPolicy.UntilDate(date) => date
            case _                                 => throw new IllegalArgumentException("Invalid RepeatUntilPolicy.UntilDate format")
          }
        )
    )

  given Codec[DeliveryStrategy] = scodec
    .codecs
    .discriminated[DeliveryStrategy]
    .by(scodec.codecs.int8)
    .typecase(1, scodec.codecs.provide(DeliveryStrategy.Immediate))
    .typecase(
      2,
      (Codec[FiniteDuration]).xmap(
        DeliveryStrategy.ScheduleAfter.apply,
        {
          case DeliveryStrategy.ScheduleAfter(delay) => delay
          case _                                     => throw new IllegalArgumentException("Invalid DeliveryStrategy.ScheduleAfter format")
        }
      )
    )

  given Codec[RepeatPolicy] = scodec
    .codecs
    .discriminated[RepeatPolicy]
    .by(scodec.codecs.int8)
    .typecase(1, scodec.codecs.provide(RepeatPolicy.NoRepeat))
    .typecase(
      2,
      Codec[FiniteDuration].xmap(
        RepeatPolicy.Repeat.apply,
        {
          case RepeatPolicy.Repeat(every) => every
          case _                          => throw new IllegalArgumentException("Invalid RepeatPolicy.Repeat format")
        }
      )
    )

  given Codec[RouteNode]  = Codec.derived
  given Codec[RouteGraph] = Codec.derived
