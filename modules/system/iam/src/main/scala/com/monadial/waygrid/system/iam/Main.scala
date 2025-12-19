package com.monadial.waygrid.system.iam

import scala.annotation.nowarn

import cats.Parallel
import cats.effect.std.Console
import cats.effect.{ Async, Resource }
import com.monadial.waygrid.common.application.algebra.{ EventSink, EventSource, Logger, ThisNode }
import com.monadial.waygrid.common.application.program.WaygridApp
import com.monadial.waygrid.common.domain.SystemWaygridApp
import com.monadial.waygrid.system.iam.settings.IAMSettings
import com.suprnation.actor.ActorSystem
import org.typelevel.otel4s.metrics.MeterProvider
import org.typelevel.otel4s.trace.{ Tracer, TracerProvider }

object Main extends WaygridApp[IAMSettings](SystemWaygridApp.IAM):

  @nowarn("msg=unused implicit parameter")
  override def programBuilder[
    F[+_]: {Async,
      Parallel, Console, Logger, ThisNode, MeterProvider, TracerProvider, EventSink, EventSource, Tracer}
  ](actorSystem: ActorSystem[F], settings: IAMSettings): Resource[F, Unit] =
    for
      _ <- Resource.eval(Logger[F].info("Starting IAM Service."))
    yield ()
