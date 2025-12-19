package com.monadial.waygrid.system.topology

import com.monadial.waygrid.common.application.algebra._
import com.monadial.waygrid.common.application.algebra.SupervisedRequest.Stop
import com.monadial.waygrid.common.application.program.WaygridApp
import com.monadial.waygrid.common.domain.SystemWaygridApp
import com.monadial.waygrid.system.topology.actor.TopologyServerActor
import com.monadial.waygrid.system.topology.settings.TopologySettings

import scala.annotation.nowarn

import cats.implicits._
import cats.Parallel
import cats.effect._
import cats.effect.std.Console
import com.suprnation.actor.ActorSystem
import org.typelevel.otel4s.metrics.MeterProvider
import org.typelevel.otel4s.trace.{ Tracer, TracerProvider }

object Main extends WaygridApp[TopologySettings](SystemWaygridApp.Topology):

  @nowarn("msg=unused implicit parameter")
  override def programBuilder[F[+_]: {Async, Parallel, Console, Logger, ThisNode, MeterProvider, TracerProvider,
    EventSink, EventSource,
    Tracer}](actorSystem: ActorSystem[F], settings: TopologySettings): Resource[F, Unit] =
    for
      _ <- Resource.eval(Logger[F].info(s"Starting Topology Service."))
      topologyServerActor <- TopologyServerActor
        .behavior(settings.service, actorSystem)
        .evalMap(actorSystem.actorOf(_, "topology-server-actor"))
      _ <- Resource.eval(topologyServerActor ! SupervisedRequest.Start)
      _ <- Resource.onFinalize:
          Logger[F].info("Shutting down program...") *>
            (topologyServerActor ! Stop) *>
            Concurrent[F].race(
              actorSystem.waitForTermination,
              Concurrent[F].sleep(settings.gracefulShutdownTimeout)
            ) *> Concurrent[F].unit
    yield ()

//  @nowarn def programBuilder[F[+_]: {Async, Parallel, Console, Logger, ThisNode, MeterProvider,
//    EventSink,
//    EventSource}](actorSystem: ActorSystem[F], settings: TopologySettings, thisNode: Node): Resource[F, Unit] =
//    for
//      httpServer <- HttpServerActor
//        .behavior(settings.httpServer)
//        .evalMap(actorSystem.actorOf(_, "http-server"))
//      programActor <- ProgramActor
//        .behavior[F](actorSystem)
//        .evalMap(actorSystem.actorOf(_, "program-actor"))
//
//      _ <- Resource.eval(programActor ! Start)

//    yield ()
