package com.monadial.waygrid.common.application.actor

import cats.Parallel
import cats.effect.*
import cats.effect.implicits.*
import cats.effect.std.Queue
import cats.implicits.*
import com.monadial.waygrid.common.application.actor.EventDispatcherCommand.{DispatchEvent, DispatchEvents}
import com.monadial.waygrid.common.application.algebra.*
import com.monadial.waygrid.common.application.algebra.SupervisedRequest.Stop
import com.monadial.waygrid.common.application.algebra.SupervisedResponse.Stopped
import com.monadial.waygrid.common.application.model.event.{Event, EventId, EventTopic}
import com.suprnation.actor.Actor.ReplyingReceive
import fs2.{Chunk, Stream}
import io.circe.generic.semiauto.*

enum EventDispatcherCommand:
  case DispatchEvent(event: Event)
  case DispatchEvents(events: Chunk[Event])

object EventDispatcherCommand:
  def dispatchEvent[F[+_]: {HasNode, Async}](topic: EventTopic, payload: String): F[DispatchEvent] =
      for
        id <- EventId.next[F]
        node <- HasNode[F].node.pure[F]
        event <- Event(id, topic, payload, node).pure[F]
      yield DispatchEvent(event)

type EventDispatcherActor[F[+_]] = SupervisedActor[F, EventDispatcherCommand]
type EventDispatcherActorRef[F[+_]]  = SupervisedActorRef[F, EventDispatcherCommand]

object EventDispatcherActor:
  def behavior[F[+_]: {Async, Parallel, Logger, HasNode}](using sink: EventSink[F]): Resource[F, EventDispatcherActor[F]] =
    for
      eventQueue <- Resource.eval(Queue.unbounded[F, Option[Event]])
      eventSinkFiberRef <- Resource.eval(Ref.of[F, Option[Fiber[F, Throwable, Unit]]](None))
    yield new EventDispatcherActor[F]:
      override def receive: ReplyingReceive[F, EventDispatcherCommand | SupervisedRequest, Any] =
        case DispatchEvent(event) =>
          eventQueue
            .offer(Some(event))
            .void

        case DispatchEvents(events) =>
          events
            .traverse(event => eventQueue.offer(Some(event)))
            .void

        case Stop =>
          for
            _ <- Logger[F].info("Stopping EventDispatcher actor...")
            _ <- self.stop
          yield Stopped

      override def preStart: F[Unit] =
        for
          _ <- Logger[F].info("Starting EventDispatcherActor actor...")
          eventSinkFiber <- Stream
              .fromQueueNoneTerminated(eventQueue)
              .through(sink.pipe)
              .compile
              .drain
              .handleErrorWith(x => Logger[F].error("Stream error", x))
              .onCancel(Logger[F].info("EventDispatcherActor event sink is stopped"))
              .start
          _ <- eventSinkFiberRef.set(Some(eventSinkFiber))
        yield ()

      override def postStop: F[Unit] =
        for
          fiberOpt <- eventSinkFiberRef.get
          _ <- fiberOpt match
            case Some(fiber) =>
              Logger[F].debug("Cancelling event sink fiber") *>
                  eventQueue.offer(None) *>
                  fiber.cancel *>
                  Logger[F].debug("Event source sink is cancelled") *>
                  eventSinkFiberRef.set(None)
            case None =>
              Logger[F].warn("Event sink is already cancelled...")
          _ <- Logger[F].info("Stopped EventReceiverActor actor...")
        yield ()
