package com.monadial.waygrid.common.application.domain.model.event

import com.monadial.waygrid.common.domain.algebra.messaging.event.Event as DomainEvent

final case class Event[D <: DomainEvent](
  id: EventId,
  address: EventAddress,
  event: D
)
