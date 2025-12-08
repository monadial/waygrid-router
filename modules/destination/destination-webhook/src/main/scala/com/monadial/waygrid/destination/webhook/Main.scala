package com.monadial.waygrid.destination.webhook

import cats.Parallel
import cats.effect.std.Console
import cats.effect.{Async, Resource}
import com.monadial.waygrid.common.application.algebra.{EventSink, EventSource, Logger, ThisNode}
import com.monadial.waygrid.common.application.program.WaygridApp
import com.monadial.waygrid.common.domain.model.node.Node
import com.monadial.waygrid.common.domain.model.node.Value.{NodeDescriptor, NodeService}
import com.monadial.waygrid.destination.webhook.settings.WebhookSettings
import com.suprnation.actor.ActorSystem
import org.typelevel.otel4s.metrics.MeterProvider
import org.typelevel.otel4s.trace.{Tracer, TracerProvider}

import scala.annotation.nowarn

object Main extends WaygridApp[WebhookSettings](NodeDescriptor.Destination(NodeService("webhook"))):

  @nowarn("msg=unused implicit parameter")
  override def programBuilder[F[+_]: {Async,
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
    settings: WebhookSettings,
    thisNode: Node
  ): Resource[F, Unit] =
    for
      _           <- Resource.eval(Logger[F].info(s"Starting Waystation Service."))
    yield ()
