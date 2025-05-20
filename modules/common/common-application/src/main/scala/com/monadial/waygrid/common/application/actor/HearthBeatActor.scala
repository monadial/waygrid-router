package com.monadial.waygrid.common.application.actor

import cats.Parallel
import cats.effect.{ Async, Resource }
import cats.syntax.all.*
import com.monadial.waygrid.common.application.algebra.*
import com.suprnation.actor.Actor.ReplyingReceive

trait HearthBeatCommand

type HearthBeatActor[F[+_]]    = SupervisedActor[F, HearthBeatCommand]
type HearthBeatActorRef[F[+_]] = SupervisedActorRef[F, HearthBeatCommand]

object HearthBeatActor:
  def behavior[F[+_]: {Async, Parallel, Logger, HasNode}]: Resource[F, HearthBeatActor[F]] =
    for
      thisNode <- Resource.eval(HasNode[F].get)
    yield new HearthBeatActor[F]:
      override def receive: ReplyingReceive[F, HearthBeatCommand | SupervisedRequest, Any] =
        case SupervisedRequest.Start =>
          for
            _ <- Logger[F].info("Starting HearthBeatActor actor...")
          yield ()
        case SupervisedRequest.Stop =>
          for
            _ <- Logger[F].info("Stopping HearthBeatActor actor...")
            _ <- self.stop
          yield ()
