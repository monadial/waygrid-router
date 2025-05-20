package com.monadial.waygrid.common.application.algebra

import com.monadial.waygrid.common.application.model.event.{ Event, EventStream, RawEvent }
import com.monadial.waygrid.common.domain.model.event.Event as DomainEvent

import cats.effect.implicits.*
import cats.effect.std.Queue
import cats.effect.{ Async, Resource }
import cats.implicits.*
import cats.syntax.all.*
import fs2.Stream

/**
 * A low-level abstraction for reading raw event data from a named stream.
 *
 * Implementations (e.g. Kafka, in-memory, test doubles) return byte-based event streams
 * for a given logical event stream identifier.
 */
trait RawEventStream[F[+_]]:

  /**
   * Opens a raw event stream identified by the given logical stream descriptor.
   *
   * This method returns a resource-safe stream of raw events, typically as
   * serialized payloads (e.g., from Kafka, memory, or sockets), without deserialization.
   *
   * @param stream the logical stream identifier to open (e.g., a topic or channel)
   * @return a resource yielding a stream of raw event data
   */
  def open(stream: EventStream): Resource[F, Stream[F, RawEvent]]

/**
 * A high-level abstraction for subscribing to domain event streams.
 *
 * This interface supports subscription to one or more logical event streams (e.g., Kafka topics),
 * automatically deserializes and filters incoming events, and dispatches them to a user-defined handler.
 *
 * @tparam F the effect type (e.g. IO, Task)
 */
trait EventSource[F[+_]]:

  /**
   * Type alias for an event handler that selectively processes supported domain events.
   */
  type Handler = PartialFunction[Event[? <: DomainEvent], F[Unit]]

  /**
   * Subscribes to a single event stream.
   *
   * The provided handler will receive only matching domain events from the specified stream.
   *
   * @param stream  the logical event stream identifier (e.g., topic, channel)
   * @param handler a partial function to handle matching deserialized events
   * @return a Resource that manages the subscription's lifecycle
   */
  def subscribe(stream: EventStream)(handler: Handler): Resource[F, Unit]

  /**
   * Subscribes to multiple event streams concurrently.
   *
   * The provided handler will receive all matching events from any of the specified streams.
   *
   * @param streams list of logical event streams to subscribe to
   * @param handler a partial function to handle matching deserialized events from any stream
   * @return a Resource that manages the combined subscription lifecycle
   */
  def subscribeTo(streams: List[EventStream])(handler: Handler): Resource[F, Unit]

object EventSource:
  def apply[F[+_]: {Async}](using rawSource: RawEventStream[F]): Resource[F, EventSource[F]] =
    for
      eventStreamQueue <- Resource.eval(Queue.bounded[F, Option[RawEvent]](1000))
    yield new EventSource[F]:
      override def subscribe(stream: EventStream)(handler: Handler): Resource[F, Unit] =
        subscribeTo(List(stream))(handler)
      override def subscribeTo(streams: List[EventStream])(handler: Handler): Resource[F, Unit] =
        for
          mergedStreams <- streams
            .map(rawSource.open)
            .sequence
            .map(_.reduceLeft(_.merge(_)))
          producerFiber <- Resource.make(
            mergedStreams
              .map(Some(_))
              .enqueueUnterminated(eventStreamQueue)
              .compile
              .drain
              .start
          )(_.cancel)
          consumerFiber <- Resource.make(
            Stream
              .fromQueueNoneTerminated(eventStreamQueue)
              .compile
              .drain
              .start
          )(_.cancel)
        yield ()
