package com.monadial.waygrid.system.topology.actor

import cats.Parallel
import cats.effect.{ Async, Concurrent, Ref, Resource }
import cats.syntax.all.*
import com.monadial.waygrid.common.application.algebra.*
import com.monadial.waygrid.common.application.algebra.SupervisedRequest.{ Restart, Start, Stop }
import com.monadial.waygrid.common.domain.model.node.Node
import com.monadial.waygrid.common.domain.model.topology.state.TopologyState
import com.monadial.waygrid.common.domain.value.Address.NodeAddress
import com.monadial.waygrid.system.topology.domain.model.election.Value.FencingToken
import com.monadial.waygrid.system.topology.settings.TopologyServiceSettings
import com.suprnation.actor.Actor.ReplyingReceive
import com.suprnation.actor.ActorSystem

sealed trait TopologyServerMessage

case class JoinNode(node: Node)                            extends TopologyServerMessage
case class NodeElectedAsLeader(fencingToken: FencingToken) extends TopologyServerMessage
case class NodeElectedAsFollower(leader: NodeAddress)      extends TopologyServerMessage

enum TopologyServerState:
  case NotStarted
  case Leader
  case Follower

type TopologyServerActor[F[+_]]    = SupervisedActor[F, TopologyServerMessage]
type TopologyServerActorRef[F[+_]] = SupervisedActorRef[F, TopologyServerMessage]

object TopologyServerActor:
  def behavior[F[+_]: {Async, Concurrent, Parallel, Logger, ThisNode}](
    setting: TopologyServiceSettings,
    actorSystem: ActorSystem[F]
  ): Resource[F, TopologyServerActor[F]] =
    for
      serverState   <- Resource.eval(Ref.of[F, TopologyServerState](TopologyServerState.NotStarted))
      topologyState <- Resource.eval(Ref.of[F, TopologyState](TopologyState.empty))
      topologyElectionActor <-
        TopologyElectionActor.behavior(setting).evalMap(actorSystem.actorOf(_, "topology-election-actor"))
    yield new TopologyServerActor[F]:
      override def receive: ReplyingReceive[F, TopologyServerMessage | SupervisedRequest, Any] =
        case Start   => onStart
        case Stop    => onStop
        case Restart => onRestart

      def onStart: F[Unit] =
        for
          _ <- Logger[F].debug("Starting Topology Server Actor...")
          _ <- topologyElectionActor ! StartElection
        yield ()

      def onStop: F[Unit] =
        for
          _ <- Logger[F].debug("Starting Topology Server Actor...")
        yield ()

      def onRestart: F[Unit] =
        for
          _ <- Logger[F].debug("Starting Topology Server Actor...")
        yield ()

//package com.monadial.waygrid.system.topology.actor
//
//import com.monadial.waygrid.common.application.algebra.*
//import com.monadial.waygrid.common.application.algebra.SupervisedRequest.{ Start, Stop }
//import com.monadial.waygrid.common.application.syntax.EventRouterSyntax.event
//import com.monadial.waygrid.common.application.syntax.EventSourceSyntax.subscribeTo
//import com.monadial.waygrid.common.domain.model.topology.Value.ContractId
//
//import cats.Parallel
//import cats.effect.*
//import cats.syntax.all.*
//import com.monadial.waygrid.common.application.domain.model.event.{EventAddress, EventStream}
//import com.monadial.waygrid.common.domain.model.node.Node
//import com.monadial.waygrid.common.domain.model.topology.{JoinRequested, JoinedSuccessfully, LeaveRequested, LeftSuccessfully}
//import com.suprnation.actor.Actor.ReplyingReceive
//
//trait TopologyServerRequest
//case class Join(contractId: ContractId, node: Node) extends TopologyServerRequest
//case class Leave(contractId: ContractId)            extends TopologyServerRequest
//
//type TopologyServerActor[F[+_]]    = SupervisedActor[F, TopologyServerRequest]
//type TopologyServerActorRef[F[+_]] = SupervisedActorRef[F, TopologyServerRequest]
//
//object TopologyServerActor:
//  def behavior[F[+_]: {Async, ThisNode, Concurrent, Parallel, Logger,
//    EventSink, EventSource}]: Resource[F, TopologyServerActor[F]] =
//    for
//      eventSourceFiber <- Resource.eval(Ref.of[F, Option[Fiber[F, Throwable, Unit]]](None))
//      stateRef         <- Resource.eval(Ref.of[F, Map[String, String]](Map.empty))
//    yield new TopologyServerActor[F]:
//      override def receive: ReplyingReceive[F, TopologyServerRequest | SupervisedRequest, Any] =
//        case Start =>
//          for
//            _ <- Logger[F].info("Starting Topology Server Actor...")
//            _ <- handleStart
//          yield ()
//        case Join(contractId, node) =>
//          for
//            _     <- Logger[F].info(s"Node: ${node.descriptor.show} joining with contract ID: ${contractId.show}")
//            state <- stateRef.get
//            _     <- stateRef.set(state + (contractId.show -> node.settingsPath.show))
//            _     <- Logger[F].info(s"Current state: ${state.show}")
//            _ <- JoinedSuccessfully(contractId)
//              .fromDomainEvent[F](EventAddress("system-topology.out"))
//              .flatMap(evt => EventSink[F].send(evt))
//          yield ()
//        case Leave(contractId) =>
//          for
//            _     <- Logger[F].info(s"Node leaving with contract ID: ${contractId.show}")
//            state <- stateRef.get
//            _     <- stateRef.set(state - contractId.show)
//            _     <- Logger[F].info(s"Current state: ${state.show}")
//            _ <- LeftSuccessfully(contractId)
//              .fromDomainEvent[F](EventAddress("system-topology.out"))
//              .flatMap(evt => EventSink[F].send(evt))
//          yield ()
//
//        case Stop =>
//          for
//            _        <- Logger[F].info("Stopping Topology Server Actor...")
//            fiberOpt <- eventSourceFiber.get
//            _ <- fiberOpt match
//              case Some(fiber) => fiber.cancel
//              case None        => Async[F].unit
//          yield ()
//
//      def handleStart: F[Unit] =
//        for
//          fiber <- EventSource[F]
//            .subsribeToSignalEndpoint(EventStream("system-topology.in")):
//              event[F, JoinRequested]: event =>
//                self ! Join(event.event.contractId, event.event.node)
//              event[F, LeaveRequested]: event =>
//                self ! Leave(event.event.contractId)
//          _ <- eventSourceFiber.set(Some(fiber))
//        yield ()
