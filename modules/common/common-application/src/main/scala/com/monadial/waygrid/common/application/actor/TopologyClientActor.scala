package com.monadial.waygrid.common.application.actor

import cats.Parallel
import cats.effect.*
import cats.syntax.all.*
import com.monadial.waygrid.common.application.actor.TopologyClientCommand.{
  BeginRegistration,
  BeginUnregistration,
  RegistrationTimeout
}
import com.monadial.waygrid.common.application.actor.TopologyClientState.Registering
import com.monadial.waygrid.common.application.algebra.SupervisedRequest.{ Start, Stop }
import com.monadial.waygrid.common.application.algebra.{ HasNode, Logger }
import com.suprnation.actor.Actor.{ Actor, ReplyingReceive }
import com.suprnation.actor.ActorRef.ActorRef
import com.suprnation.actor.ActorSystem
import scala.concurrent.duration.*
import java.time.Instant

sealed trait TopologyClientCommand
object TopologyClientCommand:
  case object BeginRegistration     extends TopologyClientCommand
  case object RegistrationCompleted extends TopologyClientCommand
  case object RegistrationTimeout   extends TopologyClientCommand
  case object RegistrationFailed    extends TopologyClientCommand
  case object BeginUnregistration   extends TopologyClientCommand

type TopologyClientActor[F[+_]]    = Actor[F, TopologyClientCommand]
type TopologyClientActorRef[F[+_]] = ActorRef[F, TopologyClientCommand]

enum TopologyClientState:
  case Idle
  case Registering[F[+_]](startedAt: Instant, registrationTimeout: Fiber[F, Throwable, Unit])
  case TimedOut

object TopologyClientActor:
  def behavior[F[+_]: {Async, Parallel, Logger, HasNode}](
    actorSystem: ActorSystem[F],
    programActorRef: ProgramSupervisorRef[F],
    httpServerActorRef: HttpServerActorRef[F]
  ): Resource[F, TopologyClientActor[F]] =
    for
      thisNode      <- Resource.eval(HasNode[F].get)
      topologyState <- Resource.eval(Ref.of(TopologyClientState.Idle))
      hearthBeatActorRer <- HearthBeatActor
        .behavior[F]
        .evalMap(actorSystem.actorOf(_, "heart-beat"))
    yield new TopologyClientActor[F]:
      override def receive: ReplyingReceive[F, TopologyClientCommand, Any] =
        case BeginRegistration =>
          for
            _ <- Logger[F].info("Starting registration...")
            _ <- handleBeginRegistration
          yield ()
        case BeginUnregistration =>
          for
            _ <- Logger[F].info("Starting unregistration...")
            _ <- programActorRef ! ProgramSupervisorRequest.StopProgram
            _ <- hearthBeatActorRer ! Stop
          yield ()
        case RegistrationTimeout =>
          for
            _ <- Logger[F].info("Registration timed out...")
            _ <- programActorRef ! ProgramSupervisorRequest.StopProgram
          yield ()

      private def handleBeginRegistration: F[Unit] =
        for
          startedAt <- Clock[F].realTimeInstant
          registrationTimeOutFiber <- context
            .system
            .scheduler
            .scheduleOnce_(5.seconds)(Logger[F].info("Registration timed out."))
          _ <- topologyState.set(Registering(startedAt, registrationTimeOutFiber))
        yield ()

      override def preStart: F[Unit] =
        for
          _ <- Logger[F].info("Starting TopologyClient actor...")
          _ <- httpServerActorRef ! Start
        yield ()
