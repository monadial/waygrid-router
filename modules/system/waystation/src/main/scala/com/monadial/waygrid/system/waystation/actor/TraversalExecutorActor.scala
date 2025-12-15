package com.monadial.waygrid.system.waystation.actor

import cats.Parallel
import cats.effect.{ Async, Concurrent, Ref, Resource }
import cats.implicits.*
import com.monadial.waygrid.common.application.algebra.*
import com.monadial.waygrid.common.application.syntax.EnvelopeSyntax.send
import com.monadial.waygrid.common.application.syntax.EventSyntax.wrapIntoEnvelope
import com.monadial.waygrid.common.domain.algebra.messaging.message.Value.MessageId
import com.monadial.waygrid.common.domain.algebra.storage.DagRepository
import com.monadial.waygrid.common.domain.algebra.storage.TraversalStateRepository
import com.monadial.waygrid.common.domain.model.envelope.Value.TraversalRefStamp
import com.monadial.waygrid.common.domain.model.routing.Value.TraversalId
import com.monadial.waygrid.common.domain.model.scheduling.Event.TaskSchedulingRequested
import com.monadial.waygrid.common.domain.model.scheduling.Value.TaskId
import com.monadial.waygrid.common.domain.model.traversal.Event.*
import com.monadial.waygrid.common.domain.model.traversal.dag.Dag
import com.monadial.waygrid.common.domain.model.traversal.dag.Value.DagHash
import com.monadial.waygrid.common.domain.model.traversal.fsm.{ TraversalEffect, TraversalFSM, TraversalSignal }
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
import com.monadial.waygrid.common.domain.value.Address.EndpointDirection.Inbound

sealed trait TraversalExecutorRequest:
  val traversalId: TraversalId

final case class ExecuteTraversal[E <: TraversalEvent](
  traversalId: TraversalId,
  dagHash: DagHash,
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
  def behavior[F[+_]: {Async, Concurrent, Parallel, Logger, ThisNode,
    EventSink, TraversalStateRepository, DagRepository}]: Resource[F, TraversalActor[F]] =
    for
      thisNode  <- Resource.eval(ThisNode[F].get)
      thisActor <- Resource.pure(thisNode.address)
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
          _      <- Logger[F].info(s"Traversal requested: $req")
          _      <- traverse(req.dagHash, req.toBeginSignal, req.spanCtx)
        yield ()

      private def onTraversalResumed(req: ExecuteTraversal[TraversalResumed]) =
        for
          _      <- Logger[F].info(s"Traversal resumed: $req")
          _      <- traverse(req.dagHash, req.toResumeSignal, req.spanCtx)
        yield ()

      private def onNodeTraversalSucceeded(req: ExecuteTraversal[NodeTraversalSucceeded]) =
        for
          _      <- Logger[F].info(s"Traversal succeeded: $req")
          _      <- traverse(req.dagHash, req.toNodeSuccessSignal, req.spanCtx)
        yield ()

      private def onNodeTraversalFailed(req: ExecuteTraversal[NodeTraversalFailed]) =
        for
          _      <- Logger[F].info(s"Traversal failed: $req")
          _      <- traverse(req.dagHash, req.toNodeFailureSignal, req.spanCtx)
        yield ()

      private def onActorStart: F[Unit]   = Async[F].unit
      private def onActorStop: F[Unit]    = Async[F].unit
      private def onActorRestart: F[Unit] = Async[F].unit

      private def traverse(dagHash: DagHash, signal: TraversalSignal, spanCtx: SpanContext): F[Unit] =
        DagRepository[F].load(dagHash).flatMap {
          case None =>
            Logger[F].error(s"[TraversalExecutorActor] Missing DAG for hash=$dagHash traversalId=${signal.traversalId}")
          case Some(dag) =>
            val loadState: F[TraversalState] =
              if dag.isLinear then
                linearState.get.map(_.getOrElse(
                  signal.traversalId,
                  TraversalState.initial(signal.traversalId, thisActor, dag)
                ))
              else
                TraversalStateRepository[F]
                  .load(signal.traversalId)
                  .map(_.getOrElse(TraversalState.initial(signal.traversalId, thisActor, dag)))

            loadState.flatMap { state =>
              TraversalFSM
                .stateless(dag, thisActor)
                .run(state, signal)
                .flatMap { result =>
                  result.output match
                    case Left(err) =>
                      Logger[F].error(s"[TraversalExecutorActor] FSM error for ${signal.traversalId}: $err")
                    case Right(effect) =>
                      for
                        persisted <-
                          if dag.isLinear then
                            linearState.update(_.updated(signal.traversalId, result.state)) *> Async[F].pure(result.state)
                          else
                            TraversalStateRepository[F].save(result.state)
                        _ <- handleEffect(dag, persisted, effect, spanCtx)
                      yield ()
                }
            }
        }

      private def handleEffect(
        dag: Dag,
        state: TraversalState,
        effect: TraversalEffect,
        spanCtx: SpanContext
      ): F[Unit] =
        effect match
          case TraversalEffect.NoOp(_) =>
            Async[F].unit

          case TraversalEffect.DispatchNode(_, nodeId) =>
            dag.nodeOf(nodeId) match
              case None =>
                Logger[F].error(s"[TraversalExecutorActor] Unknown nodeId in DispatchNode: $nodeId")
              case Some(node) =>
                for
                  messageId <- MessageId.next[F]
                  env <- NodeTraversalRequested(messageId, state.traversalId, nodeId)
                    .wrapIntoEnvelope[F](node.address.toInboundEndpoint)
                    .map(_.addStamp(TraversalRefStamp(dag.hash)))
                  _ <- env.send[F](Some(spanCtx))
                yield ()

          case TraversalEffect.DispatchNodes(_, nodes) =>
            nodes.traverse_ { case (nodeId, _) =>
              dag.nodeOf(nodeId) match
                case None =>
                  Logger[F].error(s"[TraversalExecutorActor] Unknown nodeId in DispatchNodes: $nodeId")
                case Some(node) =>
                  for
                    messageId <- MessageId.next[F]
                    env <- NodeTraversalRequested(messageId, state.traversalId, nodeId)
                      .wrapIntoEnvelope[F](node.address.toInboundEndpoint)
                      .map(_.addStamp(TraversalRefStamp(dag.hash)))
                    _ <- env.send[F](Some(spanCtx))
                  yield ()
            }

          case TraversalEffect.Schedule(_, scheduledAt, nodeId) =>
            for
              messageId <- MessageId.next[F]
              taskId    <- TaskId.next[F]
              env <- TaskSchedulingRequested(messageId, state.traversalId, taskId, scheduledAt, nodeId)
                .wrapIntoEnvelope[F](com.monadial.waygrid.common.domain.SystemWaygridApp.Scheduler.toEndpoint(Inbound))
                .map(_.addStamp(TraversalRefStamp(dag.hash)))
              _ <- env.send[F](Some(spanCtx))
            yield ()

          case TraversalEffect.ScheduleRetry(_, scheduledAt, _, nodeId) =>
            for
              messageId <- MessageId.next[F]
              taskId    <- TaskId.next[F]
              env <- TaskSchedulingRequested(messageId, state.traversalId, taskId, scheduledAt, nodeId)
                .wrapIntoEnvelope[F](com.monadial.waygrid.common.domain.SystemWaygridApp.Scheduler.toEndpoint(Inbound))
                .map(_.addStamp(TraversalRefStamp(dag.hash)))
              _ <- env.send[F](Some(spanCtx))
            yield ()

          case TraversalEffect.Complete(_) =>
            if dag.isLinear then
              linearState.update(_ - state.traversalId)
            else TraversalStateRepository[F].delete(state.traversalId)

          case TraversalEffect.Fail(_) =>
            if dag.isLinear then
              linearState.update(_ - state.traversalId)
            else TraversalStateRepository[F].delete(state.traversalId)

          case TraversalEffect.Cancel(_) =>
            if dag.isLinear then
              linearState.update(_ - state.traversalId)
            else TraversalStateRepository[F].delete(state.traversalId)

          case other =>
            Logger[F].warn(s"[TraversalExecutorActor] Unhandled traversal effect: $other")
