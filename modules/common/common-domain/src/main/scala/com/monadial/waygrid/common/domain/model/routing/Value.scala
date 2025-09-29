package com.monadial.waygrid.common.domain.model.routing

import com.monadial.waygrid.common.domain.model.node.Value.ServiceAddress
import com.monadial.waygrid.common.domain.value.instant.InstantValue
import com.monadial.waygrid.common.domain.value.long.LongValue
import com.monadial.waygrid.common.domain.value.string.StringValue
import com.monadial.waygrid.common.domain.value.ulid.ULIDValue

import scala.concurrent.duration.FiniteDuration

object Value:
  type RouteId = RouteId.Type
  object RouteId extends ULIDValue

  type RouteSalt = RouteSalt.Type
  object RouteSalt extends StringValue

  type RetryMaxRetries = RetryMaxRetries.Type
  object RetryMaxRetries extends LongValue

  type RepeatTimes = RepeatTimes.Type
  object RepeatTimes extends LongValue

  type RepeatUntilDate = RepeatUntilDate.Type
  object RepeatUntilDate extends InstantValue

  type Address = ServiceAddress

  enum RetryPolicy:
    case NoRetry
    case Linear(delay: FiniteDuration, maxRetries: RetryMaxRetries)
    case Exponential(baseDelay: FiniteDuration, maxRetries: RetryMaxRetries)

  enum RepeatPolicy:
    case NoRepeat
    case Indefinitely(every: FiniteDuration)
    case Times(every: FiniteDuration, time: RepeatTimes)
    case Until(every: FiniteDuration, until: RepeatUntilDate)

  enum DeliveryStrategy:
    case Immediate
    case ScheduleAfter(delay: FiniteDuration)

