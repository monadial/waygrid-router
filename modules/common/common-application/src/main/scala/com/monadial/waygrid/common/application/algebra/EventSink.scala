package com.monadial.waygrid.common.application.algebra

import com.monadial.waygrid.common.application.domain.model.envelope.Envelope
import com.monadial.waygrid.common.domain.model.event.Event

/**
 * A high-level abstraction for sending typed domain events.
 *
 * Handles serialization and dispatching to logical streams using a backing transport.
 */
trait EventSink[F[+_]]:
  /**
   * A type alias for domain events.
   */
  type Evt = Envelope[? <: Event]

  /**
   * Sends a single domain event to the specified stream.
   *
   * @param event the event to send
   */
  def send(event: Evt): F[Unit]

  /**
   * Sends a batch of events to the specified stream.
   *
   * @param events chunk of events to send
   */
  def sendBatch(events: List[Evt]): F[Unit]

object EventSink:
  def apply[F[+_]](using ev: EventSink[F]): EventSink[F] = ev
