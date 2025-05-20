package com.monadial.waygrid.origin.http.actor

import cats.syntax.all.*
import cats.Parallel
import cats.effect.Async
import cats.effect.kernel.Resource
import com.monadial.waygrid.common.application.actor.BaseProgramActor
import com.monadial.waygrid.common.application.actor.HttpServerActorRef
import com.monadial.waygrid.common.application.algebra.Logger
import com.monadial.waygrid.common.application.algebra.SupervisedRequest.{Start, Stop}

trait SupervisorActorRequest

object ProgramActor:
  def behavior[F[+_]: {Async, Parallel, Logger}](httpServerRef: HttpServerActorRef[F]): Resource[F, BaseProgramActor[F]] =
    Resource
      .pure:
      new BaseProgramActor[F]:
        override def startProgram: F[Unit] =
          for
            _ <- Logger[F].info("Starting program...")
            _ <- httpServerRef ! Start
          yield ()

        override def stopProgram: F[Unit] =
          for
            _ <- Logger[F].info("Stopping program...")
            _ <- httpServerRef ! Stop
          yield ()

        override def restartProgram: F[Unit] = ???
