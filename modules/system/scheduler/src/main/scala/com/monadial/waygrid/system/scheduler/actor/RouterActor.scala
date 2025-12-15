package com.monadial.waygrid.system.scheduler.actor

import cats.Parallel
import cats.effect.*
import cats.syntax.all.*
import com.monadial.waygrid.common.application.algebra.*
import com.monadial.waygrid.common.application.algebra.SupervisedRequest.{Restart, Start, Stop}
import com.monadial.waygrid.common.application.syntax.EnvelopeSyntax.send
import com.monadial.waygrid.common.application.syntax.EventRouterSyntax.event
import com.monadial.waygrid.common.application.syntax.EventSourceSyntax.{EventSubscriber, subscribeTo}
import com.monadial.waygrid.common.application.syntax.EventSyntax.wrapIntoEnvelope
import com.monadial.waygrid.common.application.util.cats.effect.FiberT
import com.monadial.waygrid.common.domain.SystemWaygridApp
import com.monadial.waygrid.common.domain.algebra.messaging.message.Value.MessageId
import com.monadial.waygrid.common.domain.model.envelope.Value.TraversalRefStamp
import com.monadial.waygrid.common.domain.model.traversal.Event.TraversalResumed
import com.monadial.waygrid.common.domain.model.scheduling.Event.TaskSchedulingRequested
import com.monadial.waygrid.common.domain.value.Address.EndpointDirection.Inbound
import com.suprnation.actor.Actor.ReplyingReceive


sealed trait RouterRequest

type RouterActor[F[+_]]    = SupervisedActor[F, RouterRequest]
type RouterActorRef[F[+_]] = SupervisedActorRef[F, RouterRequest]

object RouterActor:

  def behavior[F[+_]: {Async, Concurrent, Parallel, Logger, EventSource, EventSink,
    ThisNode}]: Resource[F, RouterActor[F]] =
    for
      eventSourceFiber <- Resource.eval(Ref.of[F, Option[FiberT[F, EventSubscriber, Unit]]](None))
    yield new RouterActor[F]:
      override def receive: ReplyingReceive[F, RouterRequest | SupervisedRequest, Any] =
        case Start =>
          for
            _ <- Logger[F].info("Starting Router actor...")
            _ <- handleStart
          yield ()

        case Stop =>
          for
            _ <- Logger[F].info("Stopping Router actor...")
            _ <- handleStop
          yield ()

        case Restart =>
          for
            _ <- Logger[F].info("Restarting Router actor...")
            _ <- handleStop
            _ <- handleStart
          yield ()

      def handleStart: F[Unit] =
        for
          fiber <- EventSource[F].subscribeTo(
            SystemWaygridApp.Scheduler.toEndpoint(Inbound)
          ):
            event[F, TaskSchedulingRequested]: envelope =>
              val traversalRef =
                envelope.findStamp[TraversalRefStamp].getOrElse(
                  throw new RuntimeException(s"[scheduler] No traversal ref stamp found in envelope: $envelope")
                )
              for
                thisNode <- ThisNode[F].get
                messageId <- MessageId.next[F]
                _ <-
                  TraversalResumed(messageId, envelope.message.traversalId, envelope.message.nodeId)
                    .wrapIntoEnvelope[F](SystemWaygridApp.Waystation.toEndpoint(Inbound))
                    .map(_.addStamp(traversalRef))
                    .flatMap(_.send(None))
              yield ()
          _ <- eventSourceFiber.set(Some(fiber))
        yield ()

      def handleStop: F[Unit] =
        for
          consumerFiber <- eventSourceFiber.get
          _ <- consumerFiber match
            case Some(fiber) => fiber.cancel
            case None        => Async[F].unit
          _ <- self.stop
        yield ()
