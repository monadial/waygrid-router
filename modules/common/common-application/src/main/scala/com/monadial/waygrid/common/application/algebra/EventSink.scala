package com.monadial.waygrid.common.application.algebra

import cats.effect.Resource
import com.monadial.waygrid.common.application.domain.model.event.{Event, RawEvent}
import com.monadial.waygrid.common.domain.model.event.Event as DomainEvent
import fs2.Pipe

/**
 * A low-level abstraction for writing raw (serialized) events to a logical stream.
 *
 * This is the counterpart to `RawEventSource`. Implementations typically write to
 * transports like Kafka, WebSocket, or in-memory channels.
 */
trait EnvelopeSink[F[+_]]:
  /**
   * Opens a writable FS2 pipe to the given logical stream.
   *
   * @return a managed resource yielding a `Pipe` to send `RawEvent`s
   */
  def open: Resource[F, Pipe[F, RawEvent, Unit]]

/**
 * A high-level abstraction for sending typed domain events.
 *
 * Handles serialization and dispatching to logical streams using a backing transport.
 */
trait EventSink[F[+_]]:
  /**
   * A type alias for domain events.
   */
  type Evt = Event[? <: DomainEvent]

  /**
   * Sends a single domain event to the specified stream.
   *
   * @param stream the target stream
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
