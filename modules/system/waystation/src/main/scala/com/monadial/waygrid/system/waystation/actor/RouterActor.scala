//package com.monadial.waygrid.system.waystation.actor
//
//import cats.Parallel
//import cats.effect.*
//import cats.syntax.all.*
//import com.monadial.waygrid.common.application.algebra.*
//import com.monadial.waygrid.common.application.algebra.SupervisedRequest.{ Restart, Start, Stop }
//import com.monadial.waygrid.common.application.syntax.EventRouterSyntax.event
//import com.monadial.waygrid.common.application.syntax.EventSourceSyntax.{ EventSubscriber, subscribeTo }
//import com.monadial.waygrid.common.application.util.cats.effect.FiberT
//import com.monadial.waygrid.common.domain.SystemWaygridApp
//import com.monadial.waygrid.common.domain.model.envelope.Value.TraversalStamp
//import com.monadial.waygrid.common.domain.model.routing.traversal.TraversalState
//import com.monadial.waygrid.common.domain.model.routing.traversal.Value.TraversalInput
//import com.monadial.waygrid.common.domain.model.traversal.dag.Dag
//import com.monadial.waygrid.common.domain.value.Address.EndpointDirection.Inbound
//import com.suprnation.actor.Actor.ReplyingReceive
//import com.suprnation.actor.ActorSystem
//
//import scala.annotation.nowarn
//
//sealed trait RouterRequest
//
//final case class StartRouting(
//  state: TraversalState,
//  dag: Dag,
//  event: RoutingWasRequested
//) extends RouterRequest
//
//final case class ContinueRouting(
//  state: TraversalState,
//  dag: Dag,
//  event: RoutingWasContinued
//) extends RouterRequest
//
//final case class StopRouting(
//  state: TraversalState,
//  input: TraversalInput
//) extends RouterRequest
//
//type RouterActor[F[+_]]    = SupervisedActor[F, RouterRequest]
//type RouterActorRef[F[+_]] = SupervisedActorRef[F, RouterRequest]
//
//object RouterActor:
//  @nowarn("msg=unused explicit parameter")
//  def behavior[F[+_]: {Async, Concurrent, Parallel, Logger, EventSource, EventSink,
//    ThisNode}](actorSystem: ActorSystem[F]): Resource[F, RouterActor[F]] =
//    for
//
//      traversalActor   <- TraversalActor.behavior[F].evalMap(actorSystem.actorOf(_, "traversal-actor"))
//      thisNode         <- Resource.eval(ThisNode[F].get)
//    yield new RouterActor[F]:
//      override def receive: ReplyingReceive[F, RouterRequest | SupervisedRequest, Any] =
//        case StartRouting(state, dag, input) =>
//          Logger[F].info("StartRouting") >> (traversalActor ! StartTraversal(state.traversalId, state, dag))
//        case ContinueRouting(state, dag, input) =>
//          Logger[F].info("ContinueRouting") *> (traversalActor ! ContinueTraversal(state.traversalId, state, dag))
//        case Start =>
//          for
//            _ <- Logger[F].info("Starting Router actor...")
//            _ <- handleStart
//          yield ()
//
//        case Stop =>
//          for
//            _ <- Logger[F].info("Stopping Router actor...")
//            _ <- handleStop
//          yield ()
//
//        case Restart =>
//          for
//            _ <- Logger[F].info("Restarting Router actor...")
//            _ <- handleStop
//            _ <- handleStart
//          yield ()
//
//      def handleStart: F[Unit] =
//        for
//          fiber <- EventSource[F]
//            .subscribe(
//              SystemWaygridApp.Waystation.toEndpoint(Inbound)
//            ) {
//              event[F, RoutingWasRequested]: event =>
//                for
//                  traversalStamp <- event.getLastStampF[F, TraversalStamp]
//                  _ <- self ! StartRouting(
//                    traversalStamp.state,
//                    traversalStamp.dag,
//                    event.message
//                  )
//                yield ()
//              event[F, RoutingWasContinued]: event =>
//                for
//                  traversalStamp <- event.getLastStampF[F, TraversalStamp]
//                  _ <- self ! ContinueRouting(
//                    traversalStamp.state,
//                    traversalStamp.dag,
//                    event.message
//                  )
//                yield ()
//            }
//          _ <- eventSourceFiber.set(Some(fiber))
//        yield ()
//
//      def handleStop: F[Unit] =
//        for
//          consumerFiber <- eventSourceFiber.get
//          _ <- consumerFiber match
//            case Some(fiber) => fiber.cancel
//            case None        => Async[F].unit
//          _ <- self.stop
//        yield ()
