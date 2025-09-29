package com.monadial.waygrid.common.application.algebra

import com.monadial.waygrid.common.domain.model.envelope.Envelope
import com.monadial.waygrid.common.domain.model.event.Event as DomainEvent

/**
 * A router for domain‐typed events.
 *
 * Once you’ve built one, you simply call `route(evt)` and it will
 * invoke all matching handlers (in priority order) on their own fibers.
 */
trait EventRouter[F[_]]:
  /**
   * Route a single event into zero or more handlers.
   *
   * @param evt  the event to route
   * @return     an effect that completes once all matching handlers have been launched
   */
  def route(evt: Envelope[? <: DomainEvent]): F[Unit]
