//package com.monadial.waygrid.system.waystation.fsm
//
//import cats.effect.IO
//import cats.{Applicative, MonadThrow, Traverse}
//import com.monadial.waygrid.common.application.domain.model.fsm.FSM
//import com.monadial.waygrid.common.domain.model.traversal.Event.*
//import com.monadial.waygrid.common.domain.model.traversal.fsm.TraversalFSM
//import com.monadial.waygrid.common.domain.model.traversal.state.TraversalState
//
//sealed trait Input
//
//
//sealed trait Output
//
//
//
//TraversalFSM.stateless[IO]
//
//
///**
// * ==TraversalFSM==
// *
// * Deterministic, stateless FSM for **linear DAG traversal** (no fanout/fanin).
// * Handles retries, scheduling, success/failure transitions and completion.
// */
//object TraversalFSM:
//
//  def stateless[F[+_]: Applicative]: FSM[F, TraversalState, Input, Output] =
//    FSM[F, TraversalState, Input, Output]: (state, input) =>
//      Applicative[F].pure:
//        case input:
//
//
//
//  private def onRequested(state: TraversalState, input: TraversalRequested) = ???
//
//  private def onResumed(state: TraversalState, input: TraversalResumed) = ???
//
//  private def onNodeSucceeded(state: TraversalState, input: NodeTraversalSucceeded) = ???
//
//  private def onNodeFailed(state: TraversalState, input: NodeTraversalFailed) = ???
//
////  def stateless[F[+_]: Applicative]: FSM[F, TraversalState, TraversalInput, TraversalOutcome] =
////    FSM[F, TraversalState, TraversalInput, TraversalOutcome] { (state, input) =>
////      Applicative[F].pure:
////          input match
////            case i: Start    => onStart(state, i)
////            case i: Continue => onContinue(state, i)
////            case i: Success  => onSuccess(state, i)
////            case i: Failure  => onFailure(state, i)
////            case null        => Result(state, NoAction(state.traversalId))
////    }
////
////  private def onStart(state: TraversalState, i: Start): Result[TraversalState, TraversalOutcome] =
////    if i.dag.nodes.isEmpty then
////      Result(state, Errored(i.id, "Empty DAG"))
////    else if state.hasProgress then
////      Result(state, Ignored(i.id))
////    else
////      i.dag.entryNode match
////        case None =>
////          Result(state, Errored(i.id, "Missing DAG entry node"))
////        case Some(entry) =>
////          entry.deliveryStrategy match
////            case DeliveryStrategy.Immediate =>
////              val nextState = state.start(entry.id, i.actor, None)
////              Result(nextState, Started(i.id, entry))
////            case DeliveryStrategy.ScheduleAfter(delay) =>
////              val nextState = state.scheduleStart(entry.id, i.actor, None)
////              Result(nextState, Scheduled(i.id, delay))
////
////  private def onContinue(state: TraversalState, i: Continue): Result[TraversalState, TraversalOutcome] =
////    if state.isTraversalComplete then
////      val updated =
////        state.current.lastOption
////          .orElse(state.completed.lastOption)
////          .fold(state)(nid => state.markTraversalCompleted(i.actor, nid, None))
////      Result(updated, Completed(i.id))
////    else
////      findRetryable(i, state)
////        .map: node =>
////          val restarted = state.start(node.id, i.actor, None)
////          Result(restarted, Next(i.id, node))
////        .orElse:
////          findScheduled(i, state).map: node =>
////              val started = state.start(node.id, i.actor, None)
////              Result(started, Started(i.id, node))
////        .getOrElse(Result(state, NoAction(i.id)))
////
////  private def onSuccess(state: TraversalState, i: Success): Result[TraversalState, TraversalOutcome] =
////    findStartedNode(i, state) match
////      case None =>
////        Result(state, NoAction(i.id))
////      case Some(node) =>
////        val succeeded = state.markSuccess(node.id, i.actor, None)
////        i.dag.nextNodes(node.id, EdgeGuard.OnSuccess).headOption match
////          case None =>
////            val updated = succeeded.markTraversalCompleted(i.actor, node.id, None)
////            Result(updated, Completed(i.id))
////          case Some(next) =>
////            next.deliveryStrategy match
////              case DeliveryStrategy.Immediate =>
////                val nextState = succeeded.start(next.id, i.actor, None)
////                Result(nextState, Next(i.id, next))
////              case DeliveryStrategy.ScheduleAfter(delay) =>
////                val nextState = state.scheduleStart(next.id, i.actor, None)
////                Result(nextState, Scheduled(i.id, delay))
////
////  private def onFailure(state: TraversalState, i: Failure): Result[TraversalState, TraversalOutcome] =
////    findStartedNode(i, state) match
////      case None =>
////        Result(state, NoAction(i.id))
////
////      case Some(node) =>
////        val failed  = state.markFailure(node.id, i.actor, None)
////        val attempt = failed.retryCount(node.id) + 1
////
////        node.retryPolicy.nextDelay(attempt, Some(state.traversalId.toHashKey)) match
////          case Some(delay) =>
////            val retried = failed.markRetry(node.id, i.actor, attempt, None)
////            Result(retried, Scheduled(i.id, delay))
////
////          case None =>
////            i.dag.nextNodes(node.id, EdgeGuard.OnFailure).headOption match
////              case Some(next) =>
////                val nextState = failed.start(next.id, i.actor, None)
////                Result(nextState, Next(i.id, next))
////              case None =>
////                val updated = failed.markTraversalFailed(i.actor, node.id, None)
////                Result(updated, Failed(i.id))
////
////  private def findStartedNode(i: TraversalInput, state: TraversalState): Option[Node] =
////    i.dag.nodes.values.find(n => state.isStarted(n.id))
////
////  private def findRetryable(i: Continue, state: TraversalState): Option[Node] =
////    i.dag.nodes.values.find: n =>
////        state.isFailed(n.id) && !state.isStarted(n.id) && {
////          val ct = state.retryCount(n.id)
////          n.retryPolicy match
////            case RetryPolicy.Linear(_, max)             => ct > 0 && ct <= max
////            case RetryPolicy.Exponential(_, max)        => ct > 0 && ct <= max
////            case RetryPolicy.BoundedExponential(_, _, max) => ct > 0 && ct <= max
////            case RetryPolicy.Fibonacci(_, max)          => ct > 0 && ct <= max
////            case RetryPolicy.Polynomial(_, _, max)      => ct > 0 && ct <= max
////            case RetryPolicy.DecorrelatedJitter(_, max) => ct > 0 && ct <= max
////            case RetryPolicy.FullJitter(_, max)         => ct > 0 && ct <= max
////            case RetryPolicy.None                       => false
////        }
////
////  private def findScheduled(i: Continue, state: TraversalState): Option[Node] =
////    i.dag.nodes.values.find: n =>
////        !state.isStarted(n.id) &&
////          !state.isCompleted(n.id) &&
////          !state.isFailed(n.id) &&
////          n.deliveryStrategy.isInstanceOf[DeliveryStrategy.ScheduleAfter]
////
////  extension (traversalId: TraversalId)
////    def toHashKey: HashKey = HashKey(BytesCodec[ULID].encodeToScalar(traversalId.unwrap))
