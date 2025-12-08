package com.monadial.waygrid.common.domain.model.routing

import com.monadial.waygrid.common.domain.algebra.value.instant.InstantValue
import com.monadial.waygrid.common.domain.algebra.value.long.LongValue
import com.monadial.waygrid.common.domain.algebra.value.string.StringValue
import com.monadial.waygrid.common.domain.algebra.value.ulid.ULIDValue

import java.time.Instant
import scala.concurrent.duration.FiniteDuration

object Value:
  type RouteId = RouteId.Type
  object RouteId extends ULIDValue

  type TraversalId = TraversalId.Type
  object TraversalId extends ULIDValue

  type RouteSalt = RouteSalt.Type
  object RouteSalt extends StringValue

  type RetryMaxRetries = RetryMaxRetries.Type
  object RetryMaxRetries extends LongValue

  type RepeatTimes = RepeatTimes.Type
  object RepeatTimes extends LongValue

  type RepeatUntilDate = RepeatUntilDate.Type
  object RepeatUntilDate extends InstantValue

  enum RepeatPolicy:
    case NoRepeat
    case Indefinitely(every: FiniteDuration)
    case Times(every: FiniteDuration, time: RepeatTimes)
    case Until(every: FiniteDuration, until: RepeatUntilDate)

  enum DeliveryStrategy:
    case Immediate
    case ScheduleAfter(delay: FiniteDuration)
    case ScheduleAt(time: Instant)

