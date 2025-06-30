package com.monadial.waygrid.common.application.program

import com.monadial.waygrid.common.application.`macro`.CirceEventCodecRegistryMacro
import com.monadial.waygrid.common.application.algebra.{ EventSink, EventSource, HasNode, Logger }
import com.monadial.waygrid.common.application.interpreter.*
import com.monadial.waygrid.common.domain.model.node.Value.NodeDescriptor

import cats.Parallel
import cats.effect.*
import cats.effect.std.{ Console, Env }
import cats.implicits.*
import cats.syntax.all.*
import com.monadial.waygrid.common.application.domain.model.settings.NodeSettings
import com.monadial.waygrid.common.domain.model.node.Node
import com.suprnation.actor.ActorSystem
import io.circe.Decoder
import org.typelevel.otel4s.instrumentation.ce.IORuntimeMetrics
import org.typelevel.otel4s.metrics.{ Meter, MeterProvider }
import org.typelevel.otel4s.oteljava.OtelJava
import org.typelevel.otel4s.trace.TracerProvider

/**
 * Abstract application bootstrap base for any Waygrid node.
 * Encapsulates lifecycle setup including telemetry, logging, configuration, and domain context.
 *
 * @param nodeDescriptor node descriptor used for registration, logging, and tracing identity
 * @tparam S settings type for this node (must be decodable from environment/config)
 */
trait WaygridApp[S <: NodeSettings](
  private val nodeDescriptor: NodeDescriptor
)(using Decoder[S]) extends IOApp.Simple:
  override def run: IO[Unit] =
    AllEventCodecs.codecs() // Register all event codecs
    OtelJava
      .autoConfigured[IO]()
      .use: otel4s =>
        for
          given MeterProvider[IO]  <- otel4s.meterProvider.pure[IO]
          given TracerProvider[IO] <- otel4s.tracerProvider.pure[IO]
          program <-
            IORuntimeMetrics
              .register[IO](runtime.metrics, IORuntimeMetrics.Config.default)
              .surround:
                programInterpreter[IO]
                  .useForever
        yield program
      .onCancel:
        Console[IO].println(
          s"Waygrid program: ${nodeDescriptor.show} cancelled..."
        ) *> IO.unit

  private def programInterpreter[F[+_]: {Async, Parallel, Console, Env, MeterProvider}]: Resource[F, Unit] =
    for
      bootTime             <- Resource.eval(Clock[F].realTimeInstant)
      given HasNode[F]     <- EnvHasNodeInterpreter.resource[F](nodeDescriptor)
      given Meter[F]       <- Resource.eval(MeterProvider[F].get("waygrid-app.origin"))
      programSettings      <- CirceSettingsLoaderInterpreter.resource[F, S].evalMap(_.load)
      thisNode             <- Resource.eval(HasNode[F].get)
      given Logger[F]      <- OdinLoggerInterpreter.resource[F](programSettings.logLevel)
      given EventSink[F]   <- EventSinkInterpreter.kafka[F](programSettings.eventStream.kafka)
      given EventSource[F] <- EventSourceInterpreter.kafka[F](programSettings.eventStream.kafka)
//      _                    <- Resource.eval(Logger[F].info(s"Starting ${thisNode.clientId.show}..."))
//      _                    <- Resource.eval(Logger[F].info(s"Node address: ${thisNode.address.show}"))
      _           <- Resource.eval(Logger[F].info(s"Boot time: ${bootTime.toString}"))
      _           <- Resource.eval(CirceEventCodecRegistryMacro.debug)
      actorSystem <- ActorSystem[F](thisNode.settingsPath.show)
      program     <- programBuilder[F](actorSystem, programSettings, thisNode)
    yield program

  /**
   * Must be implemented by concrete application.
   * Should return the long-lived effect or stream wrapped in Resource, e.g. server, actor system, etc.
   *
   * @param settings loaded settings for this node
   * @param thisNode fully resolved NodeDescriptor (with address & ID)
   * @return Resource representing the core service lifecycle
   */
  def programBuilder[F[+_]: {Async, Parallel, Console, Logger, HasNode, MeterProvider, EventSink, EventSource}](
    actorSystem: ActorSystem[F],
    settings: S,
    thisNode: Node
  ): Resource[F, Unit]
