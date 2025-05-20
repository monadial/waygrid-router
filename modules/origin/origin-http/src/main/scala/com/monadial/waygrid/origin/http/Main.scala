package com.monadial.waygrid.origin.http

import com.monadial.waygrid.common.application.actor.TopologyClientCommand.{ BeginRegistration, BeginUnregistration }
import com.monadial.waygrid.common.application.actor.{ HttpServerActor, TopologyClientActor }
import com.monadial.waygrid.common.application.algebra.{ HasNode, Logger }
import com.monadial.waygrid.common.application.program.WaygridApp
import com.monadial.waygrid.common.domain.model.node.{ Node, NodeDescriptor }
import com.monadial.waygrid.origin.http.actor.ProgramActor
import com.monadial.waygrid.origin.http.settings.HttpSettings

import cats.Parallel
import cats.effect.*
import cats.effect.std.Console
import cats.syntax.all.*
import com.suprnation.actor.ActorSystem
import org.typelevel.otel4s.metrics.MeterProvider

object Main extends WaygridApp[HttpSettings](NodeDescriptor.origin("http")):
  override def programBuilder[F[+_]: {Async, Parallel, Console, Logger, HasNode, MeterProvider}](
    settings: HttpSettings,
    thisNode: Node
  ): Resource[F, Unit] =
    for
      actorSystem <- ActorSystem[F](thisNode.clientId.show)
      httpServer <- HttpServerActor
        .behavior(settings.httpServer)
        .evalMap(actorSystem.actorOf(_, "http-server"))
      program <- ProgramActor
        .behavior[F](httpServer)
        .evalMap(actorSystem.actorOf(_, "supervisor"))
      topologyClient <- TopologyClientActor
        .behavior[F](actorSystem, program, httpServer)
        .evalMap(actorSystem.actorOf(_, "topology-client"))
      _ <- Resource.eval(topologyClient ! BeginRegistration)
      _ <- Resource.make(Concurrent[F].pure(actorSystem)): system =>
          Logger[F].info(
            "Shutting down actor system"
          ) *> (topologyClient ! BeginUnregistration) *> Concurrent[F].race(
            system.waitForTermination,
            Concurrent[F].sleep(settings.gracefulShutdownTimeout)
          ) *> Concurrent[F].unit
    yield ()
