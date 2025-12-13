package com.monadial.waygrid.common.application.actor

import cats.Parallel
import cats.effect.*
import cats.syntax.all.*
import com.monadial.waygrid.common.application.algebra.*
import com.monadial.waygrid.common.application.algebra.SupervisedRequest.Stop
import com.suprnation.actor.Actor.ReplyingReceive

trait SupervisorRequest extends SupervisedRequest

type SupervisorActor[F[+_]]    = SupervisedActor[F, SupervisorRequest]
type SupervisorActorRef[F[+_]] = SupervisedActorRef[F, SupervisorRequest]

type SupervisedActors[F[+_]] =
  Map[String, SupervisedActorRef[F, SupervisedRequest]]

final case class Supervise[F[+_]](
  actor: SupervisedActorRef[F, SupervisedRequest],
  stopPriority: Int = 0
) extends SupervisorRequest
final case class SupervisedStopped[F[+_]](actor: SupervisedActorRef[
  F,
  SupervisedRequest
]) extends SupervisedRequest

object SupervisorActor:
  def behavior[F[+_]: {Async, Parallel, Logger}]: Resource[F, SupervisorActor[F]] =
    for
      actorMapRef <- Resource.eval(Ref.of[F, SupervisedActors[F]](Map.empty))
    yield new SupervisorActor[F]:
      override def receive
        : ReplyingReceive[F, SupervisorRequest | SupervisedRequest, Any] =
        case Supervise(actor, shutdownPriority) =>
          for
            typedActor <-
              actor.asInstanceOf[SupervisedActorRef[F, SupervisedRequest]].pure
            _ <- Logger[F].info(s"Supervising actor: ${actor}")
            _ <-
              actorMapRef.update(_ + (actor.path.toString -> actor.asInstanceOf[
                SupervisedActorRef[F, SupervisedRequest]
              ]))
            _ <- context.watch(typedActor, SupervisedStopped(typedActor))
          yield ()
        case SupervisedStopped(actor) =>
          for
            typedActor <-
              actor.asInstanceOf[SupervisedActorRef[F, SupervisedRequest]].pure
            _        <- Logger[F].info(s"Supervised actor stopped: ${actor}")
            children <- context.children
            _ <- children.find(x => x.path == typedActor.path) match
              case Some(_) =>
                Logger[F].info(
                  s"Actor ${actor} is still alive, not removing from map"
                )
              case None =>
                Logger[F].info(s"Actor ${actor} is dead, removing from map")
            _ <- actorMapRef.update(_ - actor.path.toString)
          yield ()
        case Stop =>
          for
            _ <- Logger[F].info("Stopping Supervisor actor...")
            _ <- self.stop
          yield ()

      override def postStop: F[Unit] =
        for
          supervisedActors <- actorMapRef.get
          // send a PoisonPill to each child, and *wait* for each to terminate
          _ <- supervisedActors.toList.parTraverse_ {
            case (name, childRef) =>
              (childRef ! Stop) // only completes once child has run postStop
                .handleErrorWith(err =>
                  Logger[F].error(s"Error tearing down $name", err)
                )
          }
          // only now, once *all* children are really stopped:
          _ <-
            Logger[F].info("Supervisor: all supervised actors truly stopped.")
          _ <- actorMapRef.set(Map.empty)
        yield ()
