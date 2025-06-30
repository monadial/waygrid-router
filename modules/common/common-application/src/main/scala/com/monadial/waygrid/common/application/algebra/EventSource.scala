package com.monadial.waygrid.common.application.algebra

import cats.effect.Resource
import com.monadial.waygrid.common.application.domain.model.event.{Event, EventStream, RawEvent}
import com.monadial.waygrid.common.domain.model.event.Event as DomainEvent
import fs2.Stream

/**
 * A low-level abstraction for reading raw (serialized) events from a stream.
 *
 * This is the counterpart to `RawEventSink`. Implementations typically read from
 * sources like Kafka topics or in-memory channels.
 */
trait RawEventSource[F[+_]]:
  /**
   * Opens a stream to read raw events from the given source.
   *
   * @param stream the source event stream
   * @return a managed resource yielding a stream of `RawEvent`s
   */
  def open(stream: EventStream): Resource[F, Stream[F, RawEvent]]

/**
 * A high-level abstraction for subscribing to deserialized domain events.
 *
 * Uses a handler to process selected events from one or more logical streams.
 */
trait EventSource[F[+_]]:
  /**
   * A type alias for domain events.
   */
  type Evt = Event[? <: DomainEvent]

  /**
   * A handler that selectively processes domain events.
   */
  type Handler = PartialFunction[Evt, F[Unit]]

  /**
   * Subscribes to a single stream with the provided handler.
   *
   * @param stream  the event stream to subscribe to
   * @param handler the handler for matching events
   * @return a managed subscription
   */
  def subscribe(stream: EventStream)(handler: Handler): Resource[F, Unit]

  /**
   * Subscribes to multiple streams concurrently with a single handler.
   *
   * @param streams list of event streams to subscribe to
   * @param handler the handler for all matching events
   * @return a managed subscription
   */
  def subscribeTo(streams: List[EventStream])(handler: Handler): Resource[F, Unit]

object EventSource:
  def apply[F[+_]](using ev: EventSource[F]): EventSource[F] = ev
