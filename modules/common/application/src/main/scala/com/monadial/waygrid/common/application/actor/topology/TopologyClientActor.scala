package com.monadial.waygrid.common.application.actor.topology

import cats.Parallel
import cats.effect.{Concurrent, Ref, Resource, Temporal}
import com.monadial.waygrid.common.application.algebra.*
import com.monadial.waygrid.common.application.algebra.SupervisedRequest.{Restart, Start, Stop}
import com.monadial.waygrid.common.application.util.cats.effect.{FiberT, FiberType}
import com.suprnation.actor.Actor.ReplyingReceive
import com.suprnation.actor.ActorSystem

type TopologyClientActor[F[+_]]    = SupervisedActor[F, TopologyClientMessage]
type TopologyClientActorRef[F[+_]] = SupervisedActorRef[F, TopologyClientMessage]

sealed trait TopologyClientMessage
case object RegisterNode   extends TopologyClientMessage
case object UnregisterNode extends TopologyClientMessage
case object Heartbeat      extends TopologyClientMessage

enum TopologyClientState:
  case NotInitialized
  case Registering
  case SyncingSnapshot

trait TopologyStateSubscriber extends FiberType

object TopologyClientActor:

  def behavior[F[+_]: {Concurrent, Parallel, Temporal}](actorSystem: ActorSystem[F]): Resource[F, TopologyClientActor[F]] =
    for
      clientState <- Resource
        .pure:
          Ref.of[F, TopologyClientState](TopologyClientState.NotInitialized)
      consumerFiber <- Resource
          .pure:
            Ref.of[F, Option[FiberT[F, TopologyStateSubscriber, Unit]]]
      heartBeatActor <-
        TopologyHeartBeatActor
          .behavior[F]
          .evalMap(actorSystem.actorOf(_, "topology-heart-beat-actor"))
    yield new TopologyClientActor[F]:
      override def receive: ReplyingReceive[F, TopologyClientMessage | SupervisedRequest, Any] =
        // topology messages
        case RegisterNode   => onRegisterNode
        case UnregisterNode => onUnregisterNode
        case Heartbeat      => onHeartbeat


        // lifecycle messages
        case Start   => onStart
        case Stop    => onStop
        case Restart => onRestart

      private def onHeartbeat: F[Unit]   = ???
      private def onRegisterNode: F[Unit]   = ???
      private def onUnregisterNode: F[Unit] = ???

      private def onStart: F[Unit]   = ???
      private def onStop: F[Unit]    = ???
      private def onRestart: F[Unit] = ???
