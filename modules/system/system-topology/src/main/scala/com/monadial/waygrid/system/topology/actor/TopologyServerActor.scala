package com.monadial.waygrid.system.topology.actor

import com.monadial.waygrid.common.application.algebra.*
import com.monadial.waygrid.common.application.algebra.SupervisedRequest.{ Start, Stop }
import com.monadial.waygrid.common.application.syntax.EventRouterSyntax.route
import com.monadial.waygrid.common.application.syntax.EventSourceSyntax.subscribeRoutes
import com.monadial.waygrid.common.application.syntax.EventSyntax.fromDomainEvent
import com.monadial.waygrid.common.domain.model.topology.Value.ContractId

import cats.Parallel
import cats.effect.*
import cats.syntax.all.*
import com.monadial.waygrid.common.application.domain.model.event.{EventAddress, EventStream}
import com.monadial.waygrid.common.domain.model.node.Node
import com.monadial.waygrid.common.domain.model.topology.{JoinRequested, JoinedSuccessfully, LeaveRequested, LeftSuccessfully}
import com.suprnation.actor.Actor.ReplyingReceive

trait TopologyServerRequest
case class Join(contractId: ContractId, node: Node) extends TopologyServerRequest
case class Leave(contractId: ContractId)            extends TopologyServerRequest

type TopologyServerActor[F[+_]]    = SupervisedActor[F, TopologyServerRequest]
type TopologyServerActorRef[F[+_]] = SupervisedActorRef[F, TopologyServerRequest]

object TopologyServerActor:
  def behavior[F[+_]: {Async, HasNode, Concurrent, Parallel, Logger,
    EventSink, EventSource}]: Resource[F, TopologyServerActor[F]] =
    for
      eventSourceFiber <- Resource.eval(Ref.of[F, Option[Fiber[F, Throwable, Unit]]](None))
      stateRef         <- Resource.eval(Ref.of[F, Map[String, String]](Map.empty))
    yield new TopologyServerActor[F]:
      override def receive: ReplyingReceive[F, TopologyServerRequest | SupervisedRequest, Any] =
        case Start =>
          for
            _ <- Logger[F].info("Starting Topology Server Actor...")
            _ <- handleStart
          yield ()
        case Join(contractId, node) =>
          for
            _     <- Logger[F].info(s"Node: ${node.descriptor.show} joining with contract ID: ${contractId.show}")
            state <- stateRef.get
            _     <- stateRef.set(state + (contractId.show -> node.settingsPath.show))
            _     <- Logger[F].info(s"Current state: ${state.show}")
            _ <- JoinedSuccessfully(contractId)
              .fromDomainEvent[F](EventAddress("system-topology.out"))
              .flatMap(evt => EventSink[F].send(evt))
          yield ()
        case Leave(contractId) =>
          for
            _     <- Logger[F].info(s"Node leaving with contract ID: ${contractId.show}")
            state <- stateRef.get
            _     <- stateRef.set(state - contractId.show)
            _     <- Logger[F].info(s"Current state: ${state.show}")
            _ <- LeftSuccessfully(contractId)
              .fromDomainEvent[F](EventAddress("system-topology.out"))
              .flatMap(evt => EventSink[F].send(evt))
          yield ()

        case Stop =>
          for
            _        <- Logger[F].info("Stopping Topology Server Actor...")
            fiberOpt <- eventSourceFiber.get
            _ <- fiberOpt match
              case Some(fiber) => fiber.cancel
              case None        => Async[F].unit
          yield ()

      def handleStart: F[Unit] =
        for
          fiber <- EventSource[F]
            .subscribeRoutes(EventStream("system-topology.in")):
              route[F, JoinRequested]: event =>
                self ! Join(event.event.contractId, event.event.node)
              route[F, LeaveRequested]: event =>
                self ! Leave(event.event.contractId)
          _ <- eventSourceFiber.set(Some(fiber))
        yield ()
