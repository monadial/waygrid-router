package com.monadial.waygrid.origin.http.actor

import cats.Parallel
import cats.effect.{Async, Resource}
import cats.syntax.all.*
import com.monadial.waygrid.common.application.actor.HttpServerActorCommand.UnregisterRoute
import com.monadial.waygrid.common.application.actor.{BaseProgramActor, HttpServerActorRef}
import com.monadial.waygrid.common.application.algebra.Logger

trait SupervisorActorRequest

object ProgramActor:
  def behavior[F[+_]: {Async, Parallel, Logger}](httpServerRef: HttpServerActorRef[F]): Resource[F, BaseProgramActor[F]] =
    Resource
      .pure:
      new BaseProgramActor[F]:
        override def onProgramStart: F[Unit] =
          for
            _ <- Logger[F].info("Starting program...")
          yield ()

        override def onProgramStop: F[Unit] =
          for
            _ <- Logger[F].info("Stopping program...")
            _ <- httpServerRef ! UnregisterRoute("v1.ingest")
          yield ()

        override def onProgramRestart: F[Unit] = ???
