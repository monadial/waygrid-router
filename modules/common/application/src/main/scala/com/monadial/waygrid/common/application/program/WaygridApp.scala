package com.monadial.waygrid.common.application.program

import cats.Parallel
import cats.effect.*
import cats.effect.std.{ Console, Env }
import cats.implicits.*
import com.monadial.waygrid.common.application.`macro`.CirceMessageCodecRegistryMacro
import com.monadial.waygrid.common.application.algebra.{ EventSink, EventSource, Logger, ThisNode }
import com.monadial.waygrid.common.application.domain.model.settings.NodeSettings
import com.monadial.waygrid.common.application.interpreter.*
import com.monadial.waygrid.common.application.kafka.{ KafkaEventSink, KafkaEventSource }
import com.monadial.waygrid.common.domain.model.node.Node
import com.monadial.waygrid.common.domain.model.node.Value.NodeDescriptor
import com.suprnation.actor.ActorSystem
import io.circe.Decoder
import org.typelevel.otel4s.experimental.metrics.RuntimeMetrics
import org.typelevel.otel4s.instrumentation.ce.IORuntimeMetrics
import org.typelevel.otel4s.metrics.{ Meter, MeterProvider }
import org.typelevel.otel4s.oteljava.OtelJava
import org.typelevel.otel4s.trace.{ Tracer, TracerProvider }

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
    Console[IO].println(s"🚀 Waygrid node ${nodeDescriptor.show} starting.") >> OtelJava
      .autoConfigured[IO]()
      .use: otel4s =>
        for
          given MeterProvider[IO]  <- otel4s.meterProvider.pure[IO]
          given TracerProvider[IO] <- otel4s.tracerProvider.pure[IO]
          program <-
            MeterProvider[IO]
              .get("jvm.runtime")
              .flatMap: meter =>
                given Meter[IO] = meter
                RuntimeMetrics
                  .register[IO]
                  .surround:
                    IORuntimeMetrics
                      .register[IO](runtime.metrics, IORuntimeMetrics.Config.default)
                      .surround:
                        programInterpreter[IO]
                          .useForever
                          .onCancel(Console[IO].println(
                            s"⚠️ Waygrid node ${nodeDescriptor.show} cancelled. Initiating graceful shutdown."
                          ))
                          .handleErrorWith(e =>
                            Console[IO].println(s"💥 Waygrid node ${nodeDescriptor.show} failed: ${e.getMessage}")
                          )
                          .guarantee(Console[IO].println(s"🧹 Releasing all resources for ${nodeDescriptor.show}"))
        yield program
      .onCancel:
        Console[IO].println(
          s"Waygrid program: ${nodeDescriptor.show} cancelled..."
        ) *> IO.unit

  private def programInterpreter[F[+_]: {Async, Parallel, Console, Env, MeterProvider,
    TracerProvider}]: Resource[F, Unit] =
    for
      bootTime             <- Resource.eval(Clock[F].realTimeInstant)
      given ThisNode[F]    <- EnvHasNodeInterpreter.resource[F](nodeDescriptor)
      programSettings      <- CirceSettingsLoaderInterpreter.resource[F, S].evalMap(_.load)
      thisNode             <- Resource.eval(ThisNode[F].get)
      given Logger[F]      <- OdinLoggerInterpreter.default(programSettings.logLevel)
      _                    <- Resource.eval(Logger[F].info(s"Logical address: ${thisNode.descriptor.toServiceAddress}"))
      _                    <- Resource.eval(Logger[F].info(s"Physical address: ${thisNode.address}"))
      _                    <- Resource.eval(Logger[F].info(s"Boot time: ${bootTime.toString}"))
      given Meter[F]       <- Resource.eval(MeterProvider[F].get(thisNode.id.show))
      given Tracer[F]      <- Resource.eval(TracerProvider[F].get(thisNode.id.show))
      given EventSink[F]   <- KafkaEventSink.default[F](programSettings.eventStream.kafka)
      given EventSource[F] <- KafkaEventSource.default[F](programSettings.eventStream.kafka)
      _                    <- Resource.eval(CirceMessageCodecRegistryMacro.debug)
      actorSystem          <- ActorSystem[F](thisNode.settingsPath.show)
      program              <- programBuilder[F](actorSystem, programSettings)
    yield program

  /**
   * Must be implemented by concrete application.
   * Should return the long-lived effect or stream wrapped in Resource, e.g. server, actor system, etc.
   *
   * @param settings loaded settings for this node
   * @return Resource representing the core service lifecycle
   */
  def programBuilder[F[+_]: {Async, Parallel, Console, Logger, ThisNode, MeterProvider, TracerProvider, EventSink,
    EventSource, Tracer}](
    actorSystem: ActorSystem[F],
    settings: S
  ): Resource[F, Unit]
