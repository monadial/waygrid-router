package com.monadial.waygrid.system.blob.store

import scala.annotation.nowarn

import cats.Parallel
import cats.effect.*
import cats.effect.std.Console
import cats.implicits.*
import com.monadial.waygrid.common.application.algebra.SupervisedRequest.{ Start, Stop }
import com.monadial.waygrid.common.application.algebra.{ EventSink, EventSource, Logger, ThisNode }
import com.monadial.waygrid.common.application.program.WaygridApp
import com.monadial.waygrid.common.domain.SystemWaygridApp
import com.monadial.waygrid.system.blob.store.actor.ProgramActor
import com.monadial.waygrid.system.blob.store.settings.BlobStoreSettings
import com.suprnation.actor.ActorSystem
import org.typelevel.otel4s.metrics.MeterProvider
import org.typelevel.otel4s.trace.{ Tracer, TracerProvider }

object Main extends WaygridApp[BlobStoreSettings](SystemWaygridApp.BlobStore):

  @nowarn("msg=unused implicit parameter")
  override def programBuilder[F[+_]: {Async,
    Parallel,
    Console,
    Logger,
    ThisNode,
    MeterProvider,
    TracerProvider,
    EventSink,
    EventSource,
    Tracer}](
    actorSystem: ActorSystem[F],
    settings: BlobStoreSettings
  ): Resource[F, Unit] =
    for
      _ <- Resource.eval(Logger[F].info("Starting Blob Store Service."))
      programActor <- ProgramActor
        .behavior[F](settings, actorSystem)
        .evalMap(actorSystem.actorOf(_, "program-actor"))
      _ <- Resource.eval(programActor ! Start)
      _ <- Resource.onFinalize:
          Logger[F].info("Shutting down program...") *>
            (programActor ! Stop) *>
            Concurrent[F].race(
              actorSystem.waitForTermination,
              Concurrent[F].sleep(settings.gracefulShutdownTimeout)
            ) *> Concurrent[F].unit
    yield ()
