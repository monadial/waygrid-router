package com.monadial.waygrid.common.application.algebra

import com.monadial.waygrid.common.domain.algebra.messaging.event.Event
import com.monadial.waygrid.common.domain.model.envelope.DomainEnvelope
import com.monadial.waygrid.common.domain.value.Address.Endpoint
import org.typelevel.otel4s.trace.SpanContext

/**
 * A high-level abstraction for sending typed domain events.
 *
 * Handles serialization and dispatching to logical streams using a backing transport.
 */
trait EventSink[F[+_]]:
  /**
   * Sends a single domain event to the specified stream.
   *
   * @param event the event to send
   */
  def send(endpoint: Endpoint, event: DomainEnvelope[? <: Event], ctx: Option[SpanContext]): F[Unit]

  /**
   * Sends a batch of events to the specified stream.
   *
   * @param events chunk of events to send
   */
  def sendBatch(events: List[(Endpoint, DomainEnvelope[? <: Event], Option[SpanContext])]): F[Unit]

object EventSink:
  def apply[F[+_]](using ev: EventSink[F]): EventSink[F] = ev
