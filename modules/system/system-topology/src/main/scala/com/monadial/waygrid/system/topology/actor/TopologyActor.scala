package com.monadial.waygrid.system.topology.actor

import cats.Parallel
import cats.effect.implicits.*
import cats.effect.{ Async, Ref, Resource }
import cats.syntax.all.*
import com.monadial.waygrid.common.application.algebra.*
import com.monadial.waygrid.common.domain.model.topology.Topology
import com.monadial.waygrid.system.topology.actor.TopologyRequest.{
  RegisterNode,
  RegisteredNodes
}
import com.suprnation.actor.Actor.ReplyingReceive

import scala.concurrent.duration.*

enum TopologyRequest:
  case RegisterNode(nodeName: String)
  case RegisteredNodes

type TopologyActor[F[+_]]    = SupervisedActor[F, TopologyRequest]
type TopologyActorRef[F[+_]] = SupervisedActorRef[F, TopologyRequest]

object TopologyActor:
  def behavior[F[+_]: {Async, HasNode, Parallel,
    Logger}]: Resource[F, TopologyActor[F]] =
    for
      topology <- Resource.eval(Ref[F].of(Topology.initial))
    yield new TopologyActor[F]:
      override def receive
        : ReplyingReceive[F, TopologyRequest | SupervisedRequest, Any] =
        case RegisterNode(node) => ???

      override def preStart: F[Unit] =
        context
          .system
          .scheduler
          .scheduleWithFixedDelay(0.second, 1.second)(self ! RegisteredNodes)
          .start
          .void

      override def postStop: F[Unit] = Logger[F].info("Actor tplg stopped")
