package com.monadial.waygrid.system.topology

import cats.Parallel
import cats.effect.*
import cats.effect.implicits.*
import cats.effect.std.{ Console, Env }
import cats.syntax.all.*
import com.monadial.waygrid.common.application.actor
import com.monadial.waygrid.common.application.actor.*
import com.monadial.waygrid.common.application.algebra.SupervisedRequest.{
  Start,
  Stop
}
import com.monadial.waygrid.common.application.algebra.{
  EventSink,
  EventSource,
  HasNode,
  Logger
}
import com.monadial.waygrid.common.application.interpreter.*
import com.monadial.waygrid.common.application.model.event.{
  EventTopic,
  EventTopicComponent,
  EventTopicService
}
import com.monadial.waygrid.common.application.model.settings.NodeSettings
import com.monadial.waygrid.common.domain.model.node.NodeDescriptor.System
import com.monadial.waygrid.common.domain.model.node.{
  NodeDescriptor,
  NodeService
}
import com.monadial.waygrid.system.topology.settings.TopologySettings
import com.suprnation.actor.ActorSystem
import fs2.{ Stream, Stream as otel4s }
import io.circe.Decoder
import org.typelevel.otel4s.instrumentation.ce.IORuntimeMetrics
import org.typelevel.otel4s.metrics.{ Meter, MeterProvider }
import org.typelevel.otel4s.oteljava.OtelJava
import org.typelevel.otel4s.trace.Tracer

import scala.concurrent.duration.*

trait App[S <: NodeSettings](val nodeDescriptor: NodeDescriptor)(using
  Decoder[S]
) extends IOApp.Simple:

  override def run: IO[Unit] =
    program[IO]

  protected def programInterpreter[F[+_]: {HasNode, Meter, Tracer, Async,
    Parallel, Env, Console}]: (S) => Resource[F, F[Unit]]

  private def program[F[+_]: {Async, Parallel, Env, Console, LiftIO}]: F[Unit] =
    Stream
      .resource:
        OtelJava
            .autoConfigured[F]()
            .use:
              otel4s =>
            given MeterProvider[F] = otel4s.meterProvider
            for
              given HasNode[F] <-
                Resource.eval(EnvHasNodeInterpreter.register[F](nodeDescriptor))
              settings <-
                Resource.eval(CirceSettingsLoaderInterpreter.resource[F, S].load)
              given Logger[F] <-
                Resource.eval(OdinLoggerInterpreter.default(settings.logLevel))
              given Meter[F] <- Resource.eval(
                otel4s.meterProvider.get(HasNode[F].get.clientId.show)
              )
              given Tracer[F] <- Resource.eval(
                otel4s.tracerProvider.get(HasNode[F].get.clientId.show)
              )
            yield ()
      .flatMap(_ => Stream.never[F])
      .compile
      .drain

object Main extends App[TopologySettings](System(NodeService("topology"))):
  override protected def programInterpreter[F[+_]: {HasNode, Meter, Tracer,
    Async, Parallel, Env, Console}]: TopologySettings => Resource[F, F[Unit]] =
    settings =>
      Resource.pure(Async[F].unit)

//  def run: IO[Unit] =
//    OtelJava.autoConfigured[IO]().use {
//      otel4s =>
//        given MeterProvider[IO] = otel4s.meterProvider
//        for
//          given Meter[IO]  <- otel4s.meterProvider.get("waygrid")
//          given Tracer[IO] <- otel4s.tracerProvider.get("waygrid")
//          _ <- IORuntimeMetrics
//            .register[IO](runtime.metrics, IORuntimeMetrics.Config.default)
//            .surround(program[IO])
//        yield ()
//    }
//
//  private def program[F[+_]: {]: F[Unit] =
//    Stream
//      .resource(programBuilder[F])
//
//      .compile
//      .drain
//
//
//
//
//
//
//
//
//  private def programBuilder[F[+_]: {Meter, Tracer, Async, Parallel, Env,
//    Console, LiftIO}]: Resource[F, Program[F]] =
//    for
//      given HasNode[F] <- Resource.eval(
//        EnvHasNodeInterpreter.register[F](System(NodeService("topology")))
//      )
//      settings <- Resource.eval(CirceSettingsLoaderInterpreter.loader[
//        F,
//        TopologySettings
//      ].load)
//
//      otel4s          <- OtelJava.autoConfigured[F]()
//      given Meter[F]  <- Resource.eval(otel4s.meterProvider.get("waygrid"))
//      given Tracer[F] <- Resource.eval(otel4s.tracerProvider.get("waygrid"))
//      given EventSink[F] <-
//        EventSinkInterpreter.kafka[F](settings.eventStream.kafka)
//      given EventSource[F] <-
//        EventSourceInterpreter.kafka[F](settings.eventStream.kafka)
//          .flatMap(EventSourceInterpreter.loggableEventSource)
//          .flatMap(EventSourceInterpreter.metricsCollectableEventSource)
//      actorSystem   <- ActorSystem[F]("waygrid-system")
//      supervisorRef <- SupervisorActor.behavior[F].evalMap(actorSystem.actorOf)
//      _             <- Resource.eval(supervisorRef ! Start)
//      eventDispatcher <-
//        EventDispatcherActor.behavior[F].evalMap(actorSystem.actorOf)
//      _ <- Resource.eval(supervisorRef ! Supervise[F](eventDispatcher))
//      programWithFinalizer <-
//        Resource.make(Concurrent[F].pure(Program(actorSystem, supervisorRef))):
//            program =>
//              (program.supervisor ! Stop) >>
//                Concurrent[F].race(
//                  program.actorSystem.waitForTermination,
//                  Concurrent[F].sleep(settings.gracefulShutdownTimeout)
//                ) >> Concurrent[F].unit
//    yield programWithFinalizer
