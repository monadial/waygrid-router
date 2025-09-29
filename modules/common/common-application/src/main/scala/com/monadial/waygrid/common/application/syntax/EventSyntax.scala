package com.monadial.waygrid.common.application.syntax

import com.monadial.waygrid.common.application.algebra.ThisNode

import cats.effect.Async
import cats.implicits.*
import com.monadial.waygrid.common.application.domain.model.event.{Event, EventAddress, EventId, RawEvent, RawPayload}
import com.monadial.waygrid.common.domain.model.event.Event as DomainEvent

object EventSyntax:
  extension (raw: RawEvent)
    def toEvent[E <: DomainEvent](payload: E): Event[E] =
      Event(
        id = raw.id,
        address = raw.address,
        event = payload
      )

  extension [E <: DomainEvent](event: Event[E])
    def toRawEvent(rawPayload: RawPayload): RawEvent =
      RawEvent(
        id = event.id,
        address = event.address,
        payload = rawPayload
      )

  extension [E <: DomainEvent](event: E)
    def fromDomainEvent[F[+_]: {Async, ThisNode}](address: EventAddress): F[Event[E]] =
      for
        id   <- EventId.next[F]
        node <- ThisNode[F].get
      yield Event(id, address, event)
