package com.monadial.waygrid.system.iam

import cats.Parallel
import cats.effect.{ Async, Resource }
import cats.effect.std.Console
import com.monadial.waygrid.common.application.algebra.{ EventSink, EventSource, Logger, ThisNode }
import com.monadial.waygrid.common.application.program.WaygridApp
import com.monadial.waygrid.common.domain.SystemWaygridApp
import com.monadial.waygrid.system.iam.settings.IAMSettings
import com.suprnation.actor.ActorSystem
import org.typelevel.otel4s.metrics.MeterProvider
import org.typelevel.otel4s.trace.{ Tracer, TracerProvider }

import scala.annotation.nowarn

object Main extends WaygridApp[IAMSettings](SystemWaygridApp.IAM):

  @nowarn("msg=unused implicit parameter")
  override def programBuilder[
    F[+_]: {Async,
      Parallel, Console, Logger, ThisNode, MeterProvider, TracerProvider, EventSink, EventSource, Tracer}
  ](actorSystem: ActorSystem[F], settings: IAMSettings): Resource[F, Unit] =
    for
      _ <- Resource.eval(Logger[F].info(s"Starting IAM Service."))
    yield ()
