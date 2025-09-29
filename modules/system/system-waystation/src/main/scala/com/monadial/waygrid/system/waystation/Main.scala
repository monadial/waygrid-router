package com.monadial.waygrid.system.waystation

import cats.Parallel
import cats.effect.std.Console
import cats.effect.{Async, Resource}
import com.monadial.waygrid.common.application.algebra.{EventSink, EventSource, Logger, ThisNode}
import com.monadial.waygrid.common.application.program.WaygridApp
import com.monadial.waygrid.common.domain.model.node.Node
import com.monadial.waygrid.system.common.program.SystemWaygridApp
import com.monadial.waygrid.system.waystation.settings.WaystationSettings
import com.suprnation.actor.ActorSystem
import org.typelevel.otel4s.metrics.MeterProvider

import scala.annotation.nowarn

object Main extends WaygridApp[WaystationSettings](SystemWaygridApp.Waystation):

  @nowarn("msg=unused implicit parameter")
  override def programBuilder[F[+_]: {Async, Parallel, Console, Logger, ThisNode, MeterProvider, EventSink,
    EventSource}](
    actorSystem: ActorSystem[F],
    settings: WaystationSettings,
    thisNode: Node
  ): Resource[F, Unit] =
    for
      _ <- Resource.eval(
        Logger[F].info(s"Starting Waystation.")
      )
    yield ()
