package com.monadial.waygrid.system.waystation

import cats.Parallel
import cats.effect.*
import cats.effect.std.Console
import cats.implicits.*
import com.monadial.waygrid.common.application.algebra.SupervisedRequest.{Start, Stop}
import com.monadial.waygrid.common.application.algebra.{EventSink, EventSource, Logger, ThisNode}
import com.monadial.waygrid.common.application.program.WaygridApp
import com.monadial.waygrid.common.domain.SystemWaygridApp
import com.monadial.waygrid.system.waystation.actor.TraversalListenerActor
import com.monadial.waygrid.system.waystation.settings.WaystationSettings
import com.suprnation.actor.ActorSystem
import org.typelevel.otel4s.metrics.MeterProvider
import org.typelevel.otel4s.trace.{Tracer, TracerProvider}

import scala.annotation.nowarn

object Main extends WaygridApp[WaystationSettings](SystemWaygridApp.Waystation):

  @nowarn("msg=unused implicit parameter")
  override def programBuilder[F[+_] : {Async, Parallel, Console, Logger, ThisNode, MeterProvider, TracerProvider, EventSink, EventSource, Tracer}](actorSystem: ActorSystem[F], settings: WaystationSettings): Resource[F, Unit] =
    for
      _ <- Resource.eval(Logger[F].info(s"Starting Waystation Service."))
      traversalListenerActor <- TraversalListenerActor
          .behavior[F](actorSystem)
          .evalMap(actorSystem.actorOf(_, "traversal-listener-actor"))
      _ <- Resource.eval(traversalListenerActor ! Start)
      _ <- Resource.onFinalize:
        Logger[F].info("Shutting down program...") *>
            (traversalListenerActor ! Stop) *>
            Concurrent[F].race(
              actorSystem.waitForTermination,
              Concurrent[F].sleep(settings.gracefulShutdownTimeout)
            ) *> Concurrent[F].unit
    yield ()

