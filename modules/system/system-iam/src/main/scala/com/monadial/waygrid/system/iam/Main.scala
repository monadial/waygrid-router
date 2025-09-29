package com.monadial.waygrid.system.iam

import cats.Parallel
import cats.effect.{Async, Resource}
import cats.effect.std.Console
import com.monadial.waygrid.common.application.algebra.{EventSink, EventSource, Logger, ThisNode}
import com.monadial.waygrid.common.application.program.WaygridApp
import com.monadial.waygrid.common.domain.model.node.Node
import com.monadial.waygrid.system.common.program.SystemWaygridApp
import com.monadial.waygrid.system.iam.settings.IAMSettings
import com.suprnation.actor.ActorSystem
import org.typelevel.otel4s.metrics.MeterProvider

import scala.annotation.nowarn

object Main extends WaygridApp[IAMSettings](SystemWaygridApp.IAM):

  @nowarn("msg=unused implicit parameter")
  override def programBuilder[F[+_]: {Async, Parallel, Console, Logger, ThisNode, MeterProvider, EventSink,
    EventSource}](
    actorSystem: ActorSystem[F],
    settings: IAMSettings,
    thisNode: Node
  ): Resource[F, Unit] =
    Resource.eval(Logger[F].info(s"Starting IAM Service."))
