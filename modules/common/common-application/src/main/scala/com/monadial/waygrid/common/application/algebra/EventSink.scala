package com.monadial.waygrid.common.application.algebra

import com.monadial.waygrid.common.application.model.event.{ Event, EventStream }
import com.monadial.waygrid.common.domain.model.event.Event as DomainEvent

trait EventSink[F[+_]]:
  def publish(stream: EventStream, event: Event[? <: DomainEvent]): F[Unit]
