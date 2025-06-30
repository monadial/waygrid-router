package com.monadial.waygrid.system.waystation

import com.monadial.waygrid.common.application.algebra.{ EventSink, EventSource, HasNode, Logger }
import com.monadial.waygrid.common.application.program.WaygridApp
import com.monadial.waygrid.common.domain.model.node.Value.NodeDescriptor
import com.monadial.waygrid.system.waystation.settings.WaystationSettings

import cats.Parallel
import cats.effect.std.Console
import cats.effect.{ Async, Resource }
import com.monadial.waygrid.common.domain.model.node.Node
import com.suprnation.actor.ActorSystem
import org.typelevel.otel4s.metrics.MeterProvider

import scala.annotation.nowarn

object Main extends WaygridApp[WaystationSettings](NodeDescriptor.system("waystation")):

  @nowarn("msg=unused implicit parameter")
  override def programBuilder[F[+_]: {Async, Parallel, Console, Logger, HasNode, MeterProvider, EventSink,
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
