package com.monadial.waygrid.common.domain.model.routing

import com.monadial.waygrid.common.domain.value.instant.InstantValue
import com.monadial.waygrid.common.domain.value.long.LongValue
import com.monadial.waygrid.common.domain.value.ulid.ULIDValue

import scala.concurrent.duration.FiniteDuration

object Value:
  type RouteId = RouteId.Type
  object RouteId extends ULIDValue

  type RetryMaxRetries = RetryMaxRetries.Type
  object RetryMaxRetries extends LongValue

  type RepeatTimes = RepeatTimes.Type
  object RepeatTimes extends LongValue

  type RepeatUntilDate = RepeatUntilDate.Type
  object RepeatUntilDate extends InstantValue

  enum RetryPolicy:
    case NoRetry
    case Linear(delay: FiniteDuration, maxRetries: RetryMaxRetries)
    case Exponential(baseDelay: FiniteDuration, maxRetries: RetryMaxRetries)

  enum RepeatPolicy:
    case NoRepeat
    case Repeat(every: FiniteDuration)
    case RepeatUntil(until: RepeatUntilPolicy)

  enum RepeatUntilPolicy:
    case Indefinitely
    case Times(count: RepeatTimes)
    case UntilDate(date: RepeatUntilDate)

  enum DeliveryStrategy:
    case Immediate
    case ScheduleAfter(delay: FiniteDuration)

