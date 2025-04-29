  package com.monadial.waygrid.common.application.actor

import cats.Parallel
import cats.effect.*
import cats.effect.implicits.*
import cats.syntax.all.*
import com.monadial.waygrid.common.application.actor.EventReceiverCommand.EventReceived
import com.monadial.waygrid.common.application.algebra.*
import com.monadial.waygrid.common.application.algebra.SupervisedRequest.Stop
import com.monadial.waygrid.common.application.model.event.{Event, EventTopic}
import com.suprnation.actor.Actor.ReplyingReceive

import scala.language.postfixOps

  type EventReceiverActor[F[+_]] = SupervisedActor[F, EventReceiverCommand]
  type EventReceiverActorRef[F[+_]] = SupervisedActorRef[F, EventReceiverCommand]

  enum EventReceiverCommand:
    case EventReceived(event: Event)

  object EventReceiverActor:
    def behavior[F[+_]: {HasNode, Logger, Parallel, Async}](topic: EventTopic)(using source: EventSource[F]): Resource[F, EventReceiverActor[F]] =
      for
        eventSourceFiberRef <- Resource.eval(Ref.of[F, Option[Fiber[F, Throwable, Unit]]](None))
      yield new EventReceiverActor[F]:

        override def receive: ReplyingReceive[F, EventReceiverCommand | SupervisedRequest, Any] =
          case EventReceived(event) =>
            for
              _ <- Logger[F].info(s"Event: ${event.id} received.")
            yield ()

          case Stop =>
            Logger[F].info("Stopping EventReceiverActor actor...") *> self.stop

        override def preStart: F[Unit] =
          for
            _ <- Logger[F].info("Starting EventReceiverActor actor...")
            eventSourceFiber <- source
              .stream(topic)
              .parEvalMapUnordered(16): event =>
                self ! EventReceived(event)
              .compile
              .drain
              .handleErrorWith(x => Logger[F].error("Stream error", x))
              .onCancel(Logger[F].info("EventReceiverActor event source is stopped"))
              .start
            _ <- eventSourceFiberRef.set(Some(eventSourceFiber))
          yield ()

        override def postStop: F[Unit] =
          for
            fiberOpt <- eventSourceFiberRef.get
            _ <- fiberOpt match
              case Some(fiber) =>
                Logger[F].debug("Cancelling event source fiber") *>
                    fiber.cancel *>
                    Logger[F].debug("Event source fiber is cancelled") *>
                    eventSourceFiberRef.set(None)
              case None =>
                Logger[F].warn("Event source is already cancelled...")
            _ <- Logger[F].info("Stopped EventReceiverActor actor...")
          yield ()
