package com.monadial.waygrid.common.application.syntax

import com.monadial.waygrid.common.application.model.event.{ Event, RawEvent, RawPayload }
import com.monadial.waygrid.common.domain.model.event.Event as DomainEvent

object EventSyntax:
  extension (raw: RawEvent)
    def toEvent[E <: DomainEvent](payload: E): Event[E] =
      Event(
        id = raw.id,
        event = payload
      )

  extension [E <: DomainEvent](event: Event[E])
    def toRawEvent(rawPayload: RawPayload): RawEvent =
      RawEvent(
        id = event.id,
        payload = rawPayload
      )
