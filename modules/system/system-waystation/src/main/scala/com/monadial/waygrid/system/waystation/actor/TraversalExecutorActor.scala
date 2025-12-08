package com.monadial.waygrid.system.waystation.actor

import cats.Parallel
import cats.effect.{Async, Concurrent, Resource}
import cats.implicits.*
import com.monadial.waygrid.common.application.algebra.*
import com.monadial.waygrid.common.domain.model.routing.Value.TraversalId
import com.monadial.waygrid.common.domain.model.traversal.Event.*
import com.monadial.waygrid.common.domain.model.traversal.dag.Dag
import com.monadial.waygrid.common.domain.model.traversal.fsm.{TraversalEffect, TraversalFSM}
import com.monadial.waygrid.common.domain.model.traversal.fsm.TraversalSignal.Begin
import com.monadial.waygrid.common.domain.model.traversal.state.TraversalState
import com.suprnation.actor.Actor.ReplyingReceive
import org.typelevel.otel4s.trace.SpanContext

import scala.annotation.nowarn

sealed trait TraversalExecutorRequest:
  val traversalId: TraversalId

final case class ExecuteTraversal[E <: TraversalEvent](
  traversalId: TraversalId,
  state: TraversalState,
  dag: Dag,
  event: E,
) extends TraversalExecutorRequest

object ExecuteTraversal:
  extension (req: ExecuteTraversal[?])
    def withEvent[E <: TraversalEvent]: ExecuteTraversal[E] =
      req.asInstanceOf[ExecuteTraversal[E]]

  extension (req: ExecuteTraversal[TraversalRequested])
    def toBeginSignal = Begin(req.traversalId)

  extension (req: ExecuteTraversal[TraversalResumed])
    def toResumeSignal = Begin(req.traversalId)

  extension (req: ExecuteTraversal[NodeTraversalSucceeded])
    def toNodeSuccessSignal = Begin(req.traversalId)

  extension (req: ExecuteTraversal[NodeTraversalFailed])
    def toNodeFailureSignal = Begin(req.traversalId)

final case class TraversalFinished(
  traversalId: TraversalId,
  state: TraversalState,
  effect: TraversalEffect,
  dag: Dag,
  spanCtx: SpanContext
) extends TraversalExecutorRequest

type TraversalActor[F[+_]]    = SupervisedActor[F, TraversalExecutorRequest]
type TraversalActorRef[F[+_]] = SupervisedActorRef[F, TraversalExecutorRequest]

object TraversalExecutorActor:
  @nowarn("msg=unused implicit parameter")
  def behavior[F[+_]: {Async, Concurrent, Parallel, Logger, ThisNode, EventSink}]: Resource[F, TraversalActor[F]] =
    for
      thisNode  <- Resource.eval(ThisNode[F].get)
      thisActor <- Resource.pure(thisNode.address)
    yield new TraversalActor[F]:
      override def receive: ReplyingReceive[F, TraversalExecutorRequest | SupervisedRequest, Any] =
        // todo find a way to avoid this ugly cast, due type erasure
        case req: ExecuteTraversal[?] =>
          req.event match
            case evt: TraversalRequested     => onTraversalRequested(req.withEvent)
            case evt: TraversalResumed       => onTraversalResumed(req.withEvent)
            case evt: NodeTraversalSucceeded => onNodeTraversalSucceeded(req.withEvent)
            case evt: NodeTraversalFailed    => onNodeTraversalFailed(req.withEvent)
            case evt                         => Async[F].raiseError(new RuntimeException(s"Unknown event: $evt"))

        // lifecycle handlers
        case SupervisedRequest.Start   => onActorStart
        case SupervisedRequest.Stop    => onActorStop
        case SupervisedRequest.Restart => onActorRestart

      private def onTraversalRequested(req: ExecuteTraversal[TraversalRequested]) =
        for
          fsmResult <- TraversalFSM
            .stateless[F](req.dag)
//            .withDecorator(LoggingFSMDecorator.decorator)
            .run(req.state, req.toBeginSignal)
          _ <- Logger[F].debug(s"FSM result: $fsmResult")
        yield ()

      private def onTraversalResumed(req: ExecuteTraversal[TraversalResumed]) =
        for
          fsmResult <- TraversalFSM
            .stateless[F](req.dag)
            .run(req.state, req.toResumeSignal)
          _ <- Logger[F].debug(s"FSM result: $fsmResult")
        yield ()

      private def onNodeTraversalSucceeded(req: ExecuteTraversal[NodeTraversalSucceeded]) =
        for
          fsmResult <- TraversalFSM
            .stateless[F](req.dag)
            .run(req.state, req.toNodeSuccessSignal)
          _ <- Logger[F].debug(s"FSM result: $fsmResult")
        yield ()

      private def onNodeTraversalFailed(req: ExecuteTraversal[NodeTraversalFailed]) =
        for
          fsmResult <- TraversalFSM
            .stateless[F](req.dag)
            .run(req.state, req.toNodeFailureSignal)
          _ <- Logger[F].debug(s"FSM result: $fsmResult")
        yield ()

      private def onActorStart: F[Unit]   = Async[F].unit
      private def onActorStop: F[Unit]    = Async[F].unit
      private def onActorRestart: F[Unit] = Async[F].unit
