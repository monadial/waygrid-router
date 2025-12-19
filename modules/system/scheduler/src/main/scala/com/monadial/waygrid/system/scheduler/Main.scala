package com.monadial.waygrid.system.scheduler

import scala.annotation.nowarn

import cats.Parallel
import cats.effect.*
import cats.effect.std.Console
import cats.implicits.*
import com.monadial.waygrid.common.application.algebra.SupervisedRequest.{ Start, Stop }
import com.monadial.waygrid.common.application.algebra.{ EventSink, EventSource, Logger, ThisNode }
import com.monadial.waygrid.common.application.program.WaygridApp
import com.monadial.waygrid.common.domain.SystemWaygridApp
import com.monadial.waygrid.system.scheduler.actor.RouterActor
import com.monadial.waygrid.system.scheduler.settings.SchedulerSettings
import com.suprnation.actor.ActorSystem
import org.typelevel.otel4s.metrics.MeterProvider
import org.typelevel.otel4s.trace.{ Tracer, TracerProvider }

object Main extends WaygridApp[SchedulerSettings](SystemWaygridApp.Scheduler):

  @nowarn("msg=unused implicit parameter")
  override def programBuilder[F[+_]: {Async, Parallel, Console, Logger, ThisNode, MeterProvider, TracerProvider,
    EventSink,
    EventSource, Tracer}](
    actorSystem: ActorSystem[F],
    settings: SchedulerSettings
  ): Resource[F, Unit] =
    for
      _           <- Resource.eval(Logger[F].info("Starting Scheduler Service."))
      routerActor <- RouterActor.behavior[F].evalMap(actorSystem.actorOf(_, "router-actor"))
      _           <- Resource.eval(routerActor ! Start)
      _ <- Resource
        .onFinalize:
          Logger[F].info("Shutting down program...") *>
            (routerActor ! Stop) *>
            Concurrent[F].race(
              actorSystem.waitForTermination,
              Concurrent[F].sleep(settings.gracefulShutdownTimeout)
            ) *> Concurrent[F].unit
    yield ()
