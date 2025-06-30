package com.monadial.waygrid.common.application.domain.model.event

import com.monadial.waygrid.common.domain.model.event.Event as DomainEvent

final case class Event[D <: DomainEvent](
  id: EventId,
  address: EventAddress,
  event: D
)
