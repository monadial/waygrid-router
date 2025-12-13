package com.monadial.waygrid.system.waystation.actor

import cats.Parallel
import cats.effect.{ Async, Concurrent, Resource }
import cats.implicits.*
import com.monadial.waygrid.common.application.algebra.*
import com.monadial.waygrid.common.domain.model.routing.Value.TraversalId
import com.monadial.waygrid.common.domain.model.traversal.Event.*
import com.monadial.waygrid.common.domain.model.traversal.dag.Dag
import com.monadial.waygrid.common.domain.model.traversal.fsm.{ TraversalEffect, TraversalFSM, TraversalSignal }
import com.monadial.waygrid.common.domain.model.traversal.fsm.TraversalSignal.{
  Begin,
  NodeFailure,
  NodeSuccess,
  Resume
}
import com.monadial.waygrid.common.domain.model.traversal.state.TraversalState
import com.suprnation.actor.Actor.ReplyingReceive
import org.typelevel.otel4s.metrics.Meter
import org.typelevel.otel4s.trace.{ SpanContext, Tracer }

import scala.annotation.nowarn

sealed trait TraversalExecutorRequest:
  val traversalId: TraversalId

final case class ExecuteTraversal[E <: TraversalEvent](
  traversalId: TraversalId,
  state: TraversalState,
  dag: Dag,
  event: E,
  spanCtx: SpanContext,
) extends TraversalExecutorRequest

object ExecuteTraversal:
  extension (req: ExecuteTraversal[?])
    def withEvent[E <: TraversalEvent]: ExecuteTraversal[E] =
      req.asInstanceOf[ExecuteTraversal[E]]

  extension (req: ExecuteTraversal[TraversalRequested])
    def toBeginSignal = Begin(req.traversalId)

  extension (req: ExecuteTraversal[TraversalResumed])
    def toResumeSignal = Resume(req.traversalId, req.event.nodeId)

  extension (req: ExecuteTraversal[NodeTraversalSucceeded])
    def toNodeSuccessSignal = NodeSuccess(req.traversalId, req.event.nodeId)

  extension (req: ExecuteTraversal[NodeTraversalFailed])
    def toNodeFailureSignal: NodeFailure = NodeFailure(req.traversalId, req.event.nodeId, None)

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
  def behavior[F[+_]: {Async, Concurrent, Parallel, Logger, ThisNode, Tracer, Meter,
    EventSink}]: Resource[F, TraversalActor[F]] =
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
          _      <- Logger[F].info(s"Traversal requested: $req")
          result <- traverse(req.dag, req.state, req.toBeginSignal, req.spanCtx)
        yield ()

      private def onTraversalResumed(req: ExecuteTraversal[TraversalResumed]) =
        for
          _      <- Logger[F].info(s"Traversal resumed: $req")
          result <- traverse(req.dag, req.state, req.toResumeSignal, req.spanCtx)
        yield ()

      private def onNodeTraversalSucceeded(req: ExecuteTraversal[NodeTraversalSucceeded]) =
        for
          _      <- Logger[F].info(s"Traversal succeeded: $req")
          result <- traverse(req.dag, req.state, req.toNodeSuccessSignal, req.spanCtx)
        yield ()

      private def onNodeTraversalFailed(req: ExecuteTraversal[NodeTraversalFailed]) =
        for
          _      <- Logger[F].info(s"Traversal failed: $req")
          result <- traverse(req.dag, req.state, req.toNodeFailureSignal, req.spanCtx)
        yield ()

      private def onActorStart: F[Unit]   = Async[F].unit
      private def onActorStop: F[Unit]    = Async[F].unit
      private def onActorRestart: F[Unit] = Async[F].unit

      private def traverse(dag: Dag, state: TraversalState, signal: TraversalSignal, spanCtx: SpanContext) =
        TraversalFSM
          .stateless(dag, thisActor)
          .run(state, signal)
