package com.monadial.waygrid.system.topology

import com.monadial.waygrid.common.application.actor.*
import com.monadial.waygrid.common.application.algebra.SupervisedRequest.{Start, Stop}
import com.monadial.waygrid.common.application.algebra.{EventSink, EventSource, Logger, ThisNode}
import com.monadial.waygrid.common.application.program.WaygridApp
import com.monadial.waygrid.common.domain.model.node.Value.NodeDescriptor
import com.monadial.waygrid.system.topology.actor.ProgramActor
import com.monadial.waygrid.system.topology.settings.TopologySettings
import cats.Parallel
import cats.effect.*
import cats.effect.std.Console
import cats.syntax.all.*
import com.monadial.waygrid.common.domain.model.node.Node
import com.suprnation.actor.ActorSystem
import org.typelevel.otel4s.metrics.MeterProvider

import scala.annotation.nowarn

object Main extends WaygridApp[TopologySettings](SystemWaygridApp.Topology):

  @nowarn def programBuilder[F[+_]: {Async, Parallel, Console, Logger, ThisNode, MeterProvider,
    EventSink,
    EventSource}](actorSystem: ActorSystem[F], settings: TopologySettings, thisNode: Node): Resource[F, Unit] =
    for
      httpServer <- HttpServerActor
        .behavior(settings.httpServer)
        .evalMap(actorSystem.actorOf(_, "http-server"))
      programActor <- ProgramActor
        .behavior[F](actorSystem)
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
