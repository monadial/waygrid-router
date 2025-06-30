package com.monadial.waygrid.origin.http.actor

import com.monadial.waygrid.common.application.actor.{BaseProgramActor, HttpServerActorRef}
import com.monadial.waygrid.common.application.actor.HttpServerActorCommand.{RegisterRoute, UnregisterRoute}
import com.monadial.waygrid.common.application.algebra.Logger
import com.monadial.waygrid.origin.http.http.resource.v1.RoutingResource

import cats.Parallel
import cats.effect.Async
import cats.effect.kernel.Resource
import cats.syntax.all.*

trait SupervisorActorRequest

object ProgramActor:
  def behavior[F[+_]: {Async, Parallel, Logger}](httpServerRef: HttpServerActorRef[F]): Resource[F, BaseProgramActor[F]] =
    Resource
      .pure:
      new BaseProgramActor[F]:
        override def onProgramStart: F[Unit] =
          for
            _ <- Logger[F].info("Starting program...")
            _ <- httpServerRef ! RegisterRoute("v1.ingest", RoutingResource.ingest[F])
          yield ()

        override def onProgramStop: F[Unit] =
          for
            _ <- Logger[F].info("Stopping program...")
            _ <- httpServerRef ! UnregisterRoute("v1.ingest")
          yield ()

        override def onProgramRestart: F[Unit] = ???
