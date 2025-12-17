package com.monadial.waygrid.system.blob.store.actor

import cats.implicits.given
import cats.Parallel
import cats.effect.{ Async, Resource }
import com.monadial.waygrid.common.application.actor.{ BaseProgramActor, HttpServerActor }
import com.monadial.waygrid.common.application.algebra.SupervisedRequest.{ Restart, Start, Stop }
import com.monadial.waygrid.common.application.algebra.{ Logger, ThisNode }
import com.monadial.waygrid.system.blob.store.settings.BlobStoreSettings
import com.suprnation.actor.ActorSystem
import org.typelevel.otel4s.metrics.MeterProvider
import org.typelevel.otel4s.trace.TracerProvider

type BlobStoreProgramActor[F[+_]] = BaseProgramActor[F, BlobStoreSettings]

object ProgramActor:

  def behavior[F[+_]: {Async, Parallel, Logger, ThisNode, MeterProvider, TracerProvider}](
    settings: BlobStoreSettings,
    actorSystem: ActorSystem[F]
  ): Resource[F, BlobStoreProgramActor[F]] =
    for
      httpServerActor <- HttpServerActor
        .behavior[F](settings.httpServer)
        .evalMap(actorSystem.actorOf(_, "http-server-actor"))
    yield new BlobStoreProgramActor[F](settings):
      override def onProgramStart: F[Unit] =
        for
          _ <- httpServerActor ! Start
        yield ()

      override def onProgramStop: F[Unit] =
        for
          _ <- httpServerActor ! Stop
        yield ()

      override def onProgramRestart: F[Unit] =
        for
          _ <- httpServerActor ! Restart
        yield ()
