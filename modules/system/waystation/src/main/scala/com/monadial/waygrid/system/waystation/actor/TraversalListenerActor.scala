package com.monadial.waygrid.system.waystation.actor

import cats.Parallel
import cats.effect.{ Async, Concurrent, Ref, Resource }
import cats.implicits.*
import com.monadial.waygrid.common.application.algebra.*
import com.monadial.waygrid.common.application.algebra.SupervisedRequest.Stop
import com.monadial.waygrid.common.application.syntax.EventRouterSyntax.event
import com.monadial.waygrid.common.application.syntax.EventSourceSyntax.{
  EventSubscriber,
  subscribeToWaystationInboundEvents
}
import com.monadial.waygrid.common.application.util.cats.effect.FiberT
import com.monadial.waygrid.common.domain.model.envelope.DomainEnvelope
import com.monadial.waygrid.common.domain.model.envelope.Value.TraversalRefStamp
import com.monadial.waygrid.common.domain.model.traversal.Event.*
import com.monadial.waygrid.common.domain.model.traversal.dag.Value.DagHash
import com.suprnation.actor.Actor.ReplyingReceive
import com.suprnation.actor.ActorSystem
import org.typelevel.otel4s.trace.SpanContext

sealed trait TraversalListenerRequest
final case class Handle[E <: TraversalEvent](
  dagHash: DagHash,
  event: E,
  spanCtx: SpanContext
) extends TraversalListenerRequest

object Handle:
  extension (req: Handle[?])
    def withEvent[E <: TraversalEvent]: Handle[E] =
      req.asInstanceOf[Handle[E]]

enum ListenerState:
  case Stopped
  case Running
  case Restarting

type TraversalListenerActor[F[+_]]    = SupervisedActor[F, TraversalListenerRequest]
type TraversalListenerActorRef[F[+_]] = SupervisedActorRef[F, TraversalListenerRequest]

object TraversalListenerActor:

  def behavior[F[+_]: {Async, Concurrent, Parallel, Logger, ThisNode,
    EventSink, EventSource, com.monadial.waygrid.common.domain.algebra.storage.TraversalStateRepository,
    com.monadial.waygrid.common.domain.algebra.storage.DagRepository}](actorSystem: ActorSystem[F])
    : Resource[F, TraversalListenerActor[F]] =
    for
      traversalExecutorActor <- TraversalExecutorActor
        .behavior[F]
        .evalMap(actorSystem.actorOf(_, "traversal-executor"))
      eventSubscriberFiber   <- Resource.eval(Ref.of[F, Option[FiberT[F, EventSubscriber, Unit]]](None))
      traversalListenerState <- Resource.eval(Ref.of[F, ListenerState](ListenerState.Stopped))
    yield new TraversalListenerActor[F]:
      override def receive: ReplyingReceive[F, TraversalListenerRequest | SupervisedRequest, Any] =
        case req: Handle[?] =>
          req.event match
            case evt: TraversalRequested     => executeTraversal(req.withEvent, req.spanCtx)
            case evt: TraversalResumed       => executeTraversal(req.withEvent, req.spanCtx)
            case evt: NodeTraversalSucceeded => executeTraversal(req.withEvent, req.spanCtx)
            case evt: NodeTraversalFailed    => executeTraversal(req.withEvent, req.spanCtx)
            case evt                         => Logger[F].error(s"[TraversalListenerActor] Unknown traversal event: $evt")

        // lifecycle handlers
        case SupervisedRequest.Start   => onActorStart
        case SupervisedRequest.Stop    => onActorStop
        case SupervisedRequest.Restart => onActorRestart

//      private def onTraversalRequested(evt: Handle[TraversalRequested]): F[Unit] =
//        traversalExecutorActor ! ExecuteTraversal[TraversalRequested](evt.event.traversalId, evt.,)
//
//      private def onTraversalResumed(evt: Handle[TraversalResumed]): F[Unit] =
//        traversalExecutorActor ! ExecuteTraversal[TraversalResumed](evt.event.traversalId, evt.event)
//
//      private def onNodeTraversalSucceeded(evt: Handle[NodeTraversalSucceeded]): F[Unit] =
//        traversalExecutorActor ! ExecuteTraversal[NodeTraversalSucceeded](evt.event.traversalId, evt.event)
//
//      private def onNodeTraversalFailed(evt: Handle[NodeTraversalFailed]): F[Unit] =
//        traversalExecutorActor ! ExecuteTraversal[NodeTraversalFailed](evt.event.traversalId, evt.event)

      private def onActorStart: F[Unit] =
        for
          _ <- Logger[F].debug("[TraversalListenerActor] Starting...")
          esf <- EventSource[F]
            .subscribeToWaystationInboundEvents:
              event[F, TraversalRequested](dispatch[TraversalRequested])
              event[F, TraversalResumed](dispatch[TraversalResumed])
              event[F, NodeTraversalSucceeded](dispatch[NodeTraversalSucceeded])
              event[F, NodeTraversalFailed](dispatch[NodeTraversalFailed])
            .onError: err =>
              Logger[F].error(s"[TraversalListenerActor] EventSource error: $err")
          _ <- eventSubscriberFiber.set(Some(esf))
        yield ()

      private def executeTraversal[E <: TraversalEvent](
        handle: Handle[E],
        spanCtx: SpanContext
      ): F[Unit] = traversalExecutorActor ! ExecuteTraversal[E](
        handle.event.traversalId,
        handle.dagHash,
        handle.event,
        spanCtx
      )

      private def dispatch[E <: TraversalEvent](envelope: DomainEnvelope[E]): F[Unit] =
        for
          traversalRef <- envelope
            .findStamp[TraversalRefStamp]
            .pure[F]
            .map(_.getOrElse(throw new RuntimeException(s"No traversal ref stamp found in envelope: $envelope")))
          _ <- self ! Handle(traversalRef.dagHash, envelope.message, SpanContext.invalid)
        yield ()

      private def onActorStop: F[Unit] =
        for
          _   <- Logger[F].debug("[TraversalListenerActor] Stopping...")
          esf <- eventSubscriberFiber.get
          _ <- esf match
            case Some(es) => es.cancel
            case None     => Async[F].unit
          _ <- traversalExecutorActor ! Stop
          _ <- self.stop
        yield ()

      private def onActorRestart: F[Unit] = Async[F].unit
