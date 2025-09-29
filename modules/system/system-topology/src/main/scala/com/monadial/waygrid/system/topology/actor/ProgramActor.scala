package com.monadial.waygrid.system.topology.actor

import com.monadial.waygrid.common.application.actor.BaseProgramActor
import com.monadial.waygrid.common.application.algebra.SupervisedRequest.{ Start, Stop }
import com.monadial.waygrid.common.application.algebra.{ EventSink, EventSource, ThisNode, Logger }

import cats.implicits.*
import cats.Parallel
import cats.effect.{ Async, Resource }
import com.suprnation.actor.ActorSystem

import scala.annotation.nowarn

object ProgramActor:
  @nowarn def behavior[F[+_]: {Async, ThisNode, Parallel, EventSink, EventSource,
    Logger}](actorSystem: ActorSystem[F]): Resource[F, BaseProgramActor[F]] =
    for
      topologyServer <- TopologyServerActor.behavior[F].evalMap(actorSystem.actorOf(_, "topology-server"))
    yield new BaseProgramActor[F]:
      override def onProgramStart: F[Unit] =
        for
          _ <- Logger[F].info("Starting Program Actor...")
          _ <- topologyServer ! Start
        yield ()

      override def onProgramStop: F[Unit] =
        for
          _ <- Logger[F].info("Stopping Program Actor...")
          _ <- topologyServer ! Stop
        yield ()

      override def onProgramRestart: F[Unit] = Async[F].unit
