package com.monadial.waygrid.origin.http

import com.monadial.waygrid.common.application.actor.TopologyClientCommand.{ RequestJoin, RequestLeave }
import com.monadial.waygrid.common.application.actor.{ HttpServerActor, TopologyClientActor }
import com.monadial.waygrid.common.application.algebra.*
import com.monadial.waygrid.common.application.program.WaygridApp
import com.monadial.waygrid.common.domain.model.node.Value.NodeDescriptor
import com.monadial.waygrid.origin.http.actor.ProgramActor
import com.monadial.waygrid.origin.http.settings.HttpSettings

import cats.Parallel
import cats.effect.*
import cats.effect.std.Console
import cats.syntax.all.*
import com.monadial.waygrid.common.domain.model.node.Node
import com.suprnation.actor.ActorSystem
import org.typelevel.otel4s.metrics.MeterProvider

import scala.annotation.nowarn

object Main extends WaygridApp[HttpSettings](NodeDescriptor.origin("http")):

  @nowarn("msg=unused implicit parameter")
  def programBuilder[F[+_]: {Async, Parallel, Console, Logger, HasNode,
    MeterProvider, EventSink, EventSource}](
    actorSystem: ActorSystem[F],
    settings: HttpSettings,
    thisNode: Node
  ): Resource[F, Unit] =
    for
      httpServer <- HttpServerActor
        .behavior(settings.httpServer)
        .evalMap(actorSystem.actorOf(_, "http-server"))
      program <- ProgramActor
        .behavior[F](httpServer)
        .evalMap(actorSystem.actorOf(_, "supervisor"))
      topologyClient <- TopologyClientActor
        .behavior[F](program, httpServer)
        .evalMap(actorSystem.actorOf(_, "topology-client"))
      _ <- Resource.eval(topologyClient ! RequestJoin)
      _ <- Resource.make(Concurrent[F].pure(actorSystem)): system =>
          Logger[F].info(
            "Shutting down actor system"
          ) *> (topologyClient ! RequestLeave) *> Concurrent[F].race(
            system.waitForTermination,
            Concurrent[F].sleep(settings.gracefulShutdownTimeout)
          ) *> Concurrent[F].unit
    yield ()
