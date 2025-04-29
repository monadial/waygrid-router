package com.monadial.waygrid.system.topology

import cats.Parallel
import cats.effect.*
import cats.effect.std.{Console, Env}
import cats.syntax.all.*
import com.monadial.waygrid.common.application.actor
import com.monadial.waygrid.common.application.actor.*
import com.monadial.waygrid.common.application.algebra.SupervisedRequest.{Start, Stop}
import com.monadial.waygrid.common.application.algebra.{EventSink, EventSource, HasNode, Logger}
import com.monadial.waygrid.common.application.interpreter.*
import com.monadial.waygrid.common.application.syntax.NodeSyntax.toEventTopic
import com.monadial.waygrid.common.domain.model.node.NodeDescriptor.System
import com.monadial.waygrid.common.domain.model.node.{NodeDescriptor, NodeService}
import com.monadial.waygrid.system.topology.settings.TopologySettings
import com.suprnation.actor.ActorSystem
import fs2.Stream

final case class Program[F[+_]: Async](
                                          actorSystem: ActorSystem[F],
                                          supervisor: SupervisorActorRef[F]
                                      )

object Main extends IOApp.Simple:

  private def programBuilder[F[+_] : {Async, Parallel, Env, Console}]: Resource[F, Program[F]] =
    for
      given HasNode[F] <- Resource.eval(EnvHasNodeInterpreter.register[F](System(NodeService("topology"))))
      settings <- Resource.eval(CirceSettingsLoaderInterpreter.loader[F, TopologySettings].load)
      given Logger[F] <- OdinLoggerInterpreter.resource[F](settings.logLevel)
      given EventSink[F] <- EventSinkInterpreter.kafka[F](settings.eventStream.kafka)
          .flatMap(EventSinkInterpreter.loggableEventSink)
          .flatMap(EventSinkInterpreter.metricsCollectableEventSink)
      given EventSource[F] <- EventSourceInterpreter.kafka[F](settings.eventStream.kafka)
          .flatMap(EventSourceInterpreter.loggableEventSource)
          .flatMap(EventSourceInterpreter.metricsCollectableEventSource)
      actorSystem <- ActorSystem[F]("waygrid-system")
      supervisorRef <- SupervisorActor.behavior[F].evalMap(actorSystem.actorOf)
      _ <- Resource.eval(supervisorRef ! Start)
      eventReceiver <- EventReceiverActor.behavior[F](System(NodeService("topology")).toEventTopic()).evalMap(actorSystem.actorOf)
      eventDispatcher <- EventDispatcherActor.behavior[F].evalMap(actorSystem.actorOf)
      _ <- Resource.eval(supervisorRef ! Supervise[F](eventReceiver))
      _ <- Resource.eval(supervisorRef ! Supervise[F](eventDispatcher))
      programWithFinalizer <- Resource.make(Concurrent[F].pure(Program(actorSystem, supervisorRef))): program =>
        (program.supervisor ! Stop) >>
            Concurrent[F].race(
              program.actorSystem.waitForTermination,
              Concurrent[F].sleep(settings.gracefulShutdownTimeout)
            ) >> Concurrent[F].unit
    yield programWithFinalizer

  def run: IO[Unit] =
    Stream
      .resource(programBuilder[IO])
      .flatMap(_ => Stream.never[IO])
      .onFinalize:
        Console[IO].println("")
      .compile
      .drain
