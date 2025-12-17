package com.monadial.waygrid.system.waystation.actor

import cats.Parallel
import cats.effect.{ Async, Concurrent, Ref, Resource }
import cats.implicits.*
import com.monadial.waygrid.common.application.algebra.*
import com.monadial.waygrid.common.domain.algebra.storage.{ DagRepository, TraversalStateRepository }
import com.monadial.waygrid.common.domain.model.routing.Value.TraversalId
import com.monadial.waygrid.common.domain.model.traversal.Event.*
import com.monadial.waygrid.common.domain.model.traversal.dag.Dag
import com.monadial.waygrid.common.domain.model.traversal.dag.Value.DagHash
import com.monadial.waygrid.common.domain.model.traversal.fsm.TraversalEffect
import com.monadial.waygrid.common.domain.model.traversal.fsm.TraversalSignal.{
  Begin,
  NodeFailure,
  NodeSuccess,
  Resume
}
import com.monadial.waygrid.common.domain.model.traversal.state.TraversalState
import com.suprnation.actor.Actor.ReplyingReceive
import org.typelevel.otel4s.trace.SpanContext

import scala.annotation.nowarn

sealed trait TraversalExecutorRequest:
  val traversalId: TraversalId

final case class ExecuteTraversal[E <: TraversalEvent](
  traversalId: TraversalId,
  dagHash: DagHash,
  event: E,
  spanCtx: SpanContext
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
  def behavior[F[+_]: {Async, Concurrent, Parallel, Logger, ThisNode,
    EventSink, TraversalStateRepository, DagRepository}]: Resource[F, TraversalActor[F]] =
    for
      thisNode    <- Resource.eval(ThisNode[F].get)
      thisActor   <- Resource.pure(thisNode.address)
      linearState <- Resource.eval(Ref.of[F, Map[TraversalId, TraversalState]](Map.empty))
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
          _ <- Logger[F].info(s"Traversal requested: $req")
        yield ()

      private def onTraversalResumed(req: ExecuteTraversal[TraversalResumed]) =
        for
          _ <- Logger[F].info(s"Traversal resumed: $req")
        yield ()

      private def onNodeTraversalSucceeded(req: ExecuteTraversal[NodeTraversalSucceeded]) =
        for
          _ <- Logger[F].info(s"Traversal succeeded: $req")
        yield ()

      private def onNodeTraversalFailed(req: ExecuteTraversal[NodeTraversalFailed]) =
        for
          _ <- Logger[F].info(s"Traversal failed: $req")
        yield ()

      private def onActorStart: F[Unit]   = Async[F].unit
      private def onActorStop: F[Unit]    = Async[F].unit
      private def onActorRestart: F[Unit] = Async[F].unit
