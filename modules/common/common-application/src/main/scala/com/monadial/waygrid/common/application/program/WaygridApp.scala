package com.monadial.waygrid.common.application.program

import com.monadial.waygrid.common.application.`macro`.EventCodecRegistry
import com.monadial.waygrid.common.application.algebra.{ HasNode, Logger }
import com.monadial.waygrid.common.application.interpreter.{
  AllEventCodecs,
  CirceSettingsLoaderInterpreter,
  EnvHasNodeInterpreter,
  OdinLoggerInterpreter
}
import com.monadial.waygrid.common.application.model.settings.NodeSettings
import com.monadial.waygrid.common.domain.model.node.{ Node, NodeDescriptor }

import cats.Parallel
import cats.effect.*
import cats.effect.std.{ Console, Env }
import cats.implicits.*
import cats.syntax.all.*
import io.circe.Decoder
import org.typelevel.otel4s.instrumentation.ce.IORuntimeMetrics
import org.typelevel.otel4s.metrics.MeterProvider
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
      bootTime         <- Resource.eval(Clock[F].realTimeInstant)
      given HasNode[F] <- EnvHasNodeInterpreter.resource[F](nodeDescriptor)
      programSettings  <- CirceSettingsLoaderInterpreter.resource[F, S].evalMap(_.load)
      thisNode         <- Resource.eval(HasNode[F].get)
      given Logger[F]  <- OdinLoggerInterpreter.resource[F](programSettings.logLevel)
      _                <- Resource.eval(Logger[F].info(s"Starting ${thisNode.clientId.show}..."))
      _                <- Resource.eval(Logger[F].info(s"Node address: ${thisNode.address.show}"))
      _                <- Resource.eval(Logger[F].info(s"Boot time: ${bootTime.toString}"))
      _                <- Resource.eval(EventCodecRegistry.debug)
      program          <- programBuilder[F](programSettings, thisNode)
    yield program

  /**
   * Must be implemented by concrete application.
   * Should return the long-lived effect or stream wrapped in Resource, e.g. server, actor system, etc.
   *
   * @param settings loaded settings for this node
   * @param thisNode fully resolved NodeDescriptor (with address & ID)
   * @return Resource representing the core service lifecycle
   */
  def programBuilder[F[+_]: {Async, Parallel, Console, Logger, HasNode, MeterProvider}](
    settings: S,
    thisNode: Node
  ): Resource[F, Unit]
