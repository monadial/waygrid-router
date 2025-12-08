package com.monadial.waygrid.common.application.actor.topology

import cats.effect.Resource
import com.google.protobuf.duration.Duration
import com.monadial.waygrid.common.application.algebra.{ SupervisedActor, SupervisedActorRef }

type TopologyHearthBeatActor[F[+_]]    = SupervisedActor[F, TopologyClientMessage]
type TopologyHearthBeatActorRef[F[+_]] = SupervisedActorRef[F, TopologyClientMessage]

sealed trait TopologyHeartbeatMessage
final case class StartHeartbeat(interval: Duration) extends TopologyHeartbeatMessage
case object StopHeartbeat                           extends TopologyHeartbeatMessage

object TopologyHeartBeatActor:

  def behavior[F[+_]]: Resource[F, TopologyHearthBeatActor[F]] = ???
