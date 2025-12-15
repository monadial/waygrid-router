package com.monadial.waygrid.origin.http

import cats.Parallel
import cats.effect.*
import cats.effect.std.Console
import cats.syntax.all.*
import com.monadial.waygrid.common.application.actor.HttpServerActor
import com.monadial.waygrid.common.application.actor.HttpServerActorCommand.RegisterRoute
import com.monadial.waygrid.common.application.algebra.*
import com.monadial.waygrid.common.application.algebra.SupervisedRequest.Start
import com.monadial.waygrid.common.application.interpreter.storage.InMemoryDagRepository
import com.monadial.waygrid.common.application.program.WaygridApp
import com.monadial.waygrid.common.domain.algebra.DagCompiler
import com.monadial.waygrid.common.domain.algebra.storage.DagRepository
import com.monadial.waygrid.common.domain.model.node.Value.{ NodeDescriptor, NodeService }
import com.monadial.waygrid.origin.http.http.resource.v1.IngestResource
import com.monadial.waygrid.origin.http.settings.HttpSettings
import com.suprnation.actor.ActorSystem
import org.typelevel.otel4s.metrics.MeterProvider
import org.typelevel.otel4s.trace.{ Tracer, TracerProvider }

import scala.annotation.nowarn

object Main extends WaygridApp[HttpSettings](NodeDescriptor.Origin(NodeService("http"))):

  @nowarn("msg=unused implicit parameter")
  def programBuilder[F[+_]: {Async,
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
    settings: HttpSettings,
  ): Resource[F, Unit] =
    for
      dagRepo <- Resource.eval(InMemoryDagRepository.make[F])
      given DagRepository[F] = dagRepo
      httpServer <- HttpServerActor
        .behavior(settings.httpServer)
        .evalMap(actorSystem.actorOf(_, "http-server"))
      compiler <- DagCompiler.default[F]
      _        <- Resource.eval(httpServer ! RegisterRoute[F]("v1.ingest", IngestResource.ingest[F](compiler)))
      _        <- Resource.eval(httpServer ! Start)
      _ <- Resource.make(Concurrent[F].pure(actorSystem)): system =>
          Logger[F].info(
            "Shutting down actor system"
          ) *> Concurrent[F].race(
            system.waitForTermination,
            Concurrent[F].sleep(settings.gracefulShutdownTimeout)
          ) *> Concurrent[F].unit
    yield ()
