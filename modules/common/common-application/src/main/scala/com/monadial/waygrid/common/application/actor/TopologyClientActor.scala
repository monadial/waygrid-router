package com.monadial.waygrid.common.application.actor

import cats.effect.Resource
import com.monadial.waygrid.common.application.algebra.{SupervisedActor, SupervisedActorRef}

sealed trait TopologyClientCommand

type TopologyClientActor[F[+_]]    = SupervisedActor[F, TopologyClientCommand]
type TopologyClientActorRef[F[+_]] = SupervisedActorRef[F, TopologyClientCommand]

object TopologyClientActor:
  def behavior[F[+_]]: Resource[F, TopologyClientActor[F]] = ???







//
//import cats.Parallel
//import cats.effect.*
//import cats.syntax.all.*
//import com.monadial.waygrid.common.application.actor.TopologyClientCommand.{RequestJoin, RequestLeave}
//import com.monadial.waygrid.common.application.algebra.*
//import com.monadial.waygrid.common.application.algebra.SupervisedRequest.{Start, Stop}
//import com.monadial.waygrid.common.application.domain.model.event.EventStream
//import com.monadial.waygrid.common.application.syntax.EventRouterSyntax.route
//import com.monadial.waygrid.common.application.syntax.EventSourceSyntax.subscribeRoutes
//import com.monadial.waygrid.common.domain.model.topology.Value.ContractId
//import com.monadial.waygrid.common.domain.model.topology.{JoinedSuccessfully, LeftSuccessfully}
//import com.suprnation.actor.Actor.ReplyingReceive
//
//import java.time.Instant
//
//
//object TopologyClientCommand:
//  case object RequestJoin  extends TopologyClientCommand
//  case object RequestLeave extends TopologyClientCommand
//

//
//enum TopologyClientState:
//  case Idle
//  case Joining[F[+_]](startedAt: Instant)
//  case Joined
//  case TimedOut
//
//object TopologyClientActor:
//  def behavior[F[+_]: {Async, Parallel, Logger, ThisNode, EventSource}](
//    programActor: BaseProgramActorRef[F],
//    httpActor: HttpServerActorRef[F]
//  ): Resource[F, TopologyClientActor[F]] =
//    for
//      state            <- Resource.eval(Ref.of[F, TopologyClientState](TopologyClientState.Idle))
//      eventSourceFiber <- Resource.eval(Ref.of[F, Option[Fiber[F, Throwable, Unit]]](None))
//      contractIdRef    <- Resource.eval(Ref.of[F, Option[ContractId]](None))
//      thisNode         <- Resource.eval(ThisNode[F].get)
//    yield new TopologyClientActor[F]:
//      override def receive: ReplyingReceive[F, TopologyClientCommand | SupervisedRequest, Any] =
//        case RequestJoin  => handleJoin
//        case RequestLeave => handleLeave
//
//      private def handleJoin: F[Unit] =
//        for
//          startedAt  <- Clock[F].realTimeInstant
//          _          <- Logger[F].info(s"Node: ${thisNode.descriptor.show}@${startedAt} starting to join into cluster...")
//          contractId <- ContractId.next[F]
//          _          <- contractIdRef.set(Some(contractId))
////          _ <- JoinRequested(contractId, thisNode)
////            .fromDomainEvent[F](EventAddress("system-topology.in"))
////            .flatMap(evt => EventSink[F].send(evt))
//          fiber <- EventSource[F].subscribeRoutes(EventStream("system-topology.out")):
//              route[F, JoinedSuccessfully]: event =>
//                for
//                  contractId <- contractIdRef.get
//                  _ <- contractId match
//                    case Some(id) if id == event.message.contractId =>
//                      for
//                        _ <- Logger[F].info(s"Node: ${thisNode.descriptor.show} successfully joined the cluster.")
//                        _ <- programActor ! Start
//                      yield ()
//
//                    case _ =>
//                      Logger[F].warn(
//                        s"Node: ${thisNode.descriptor.show} received a join confirmation for an unknown contract ID."
//                      )
//                yield ()
//              route[F, LeftSuccessfully]: event =>
//                for
//                  contractId <- contractIdRef.get
//                  _ <- contractId match
//                    case Some(id) if id == event.message.contractId =>
//                      for
//                        _        <- Logger[F].info(s"Node: ${thisNode.descriptor.show} left the cluster successfully.")
//                        _        <- state.set(TopologyClientState.Idle)
//                        fiberOpt <- eventSourceFiber.get
//                        _ <- fiberOpt match
//                          case Some(fiber) => fiber.cancel
//                          case None        => Async[F].unit
//                        _ <- programActor ! Stop
//                        _ <- self.stop
//                      yield ()
//
//                    case _ =>
//                      Logger[F].warn(
//                        s"Node: ${thisNode.descriptor.show} received a leave confirmation for an unknown contract ID."
//                      )
//                yield ()
//          _ <- eventSourceFiber.set(Some(fiber))
//        yield ()
//
//      private def handleLeave: F[Unit] =
//        for
//          _ <- Logger[F].info(s"Node: ${thisNode.descriptor.show} preparing for leave from cluster...")
////          _ <- contractIdRef.get.flatMap:
////              case Some(contractId) =>
////                for
////                  _ <- Envelope.toWaystation()
////
////
////
////                      LeaveRequested(contractId)
////                    .fromDomainEvent[F](EventAddress("system-topology.in"))
////                    .flatMap(evt => EventSink[F].send(evt))
////                yield ()
////              case None =>
////                Logger[F].warn(s"Node: ${thisNode.descriptor.show} has no contract ID to leave the cluster.")
//        yield ()
//
//      override def preStart: F[Unit] =
//        for
//          _ <- httpActor ! Start
//        yield ()
