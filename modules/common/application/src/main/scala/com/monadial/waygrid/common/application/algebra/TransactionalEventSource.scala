package com.monadial.waygrid.common.application.algebra

import cats.effect.{ Fiber, Resource }
import com.monadial.waygrid.common.application.domain.model.event.{ Event, EventStream }
import com.monadial.waygrid.common.domain.algebra.messaging.event.Event as DomainEvent

trait TransactionalEventSource[F[+_]]:
  /**
   * A type alias for domain events.
   */
  type Evt = Event[? <: DomainEvent]

  /**
   * A handler that selectively processes domain events.
   */
  type Handler = PartialFunction[Evt, F[Unit]]

  def subscribe(stream: EventStream, handler: Handler): Resource[F, Unit]

  def subscribeTo(streams: List[EventStream], handler: Handler): F[Fiber[F, Throwable, Unit]]

object TransactionalEventSource:
  def apply[F[+_]](using ev: TransactionalEventSource[F]): TransactionalEventSource[F] = ev
