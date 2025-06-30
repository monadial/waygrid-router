package com.monadial.waygrid.common.application.actor

import com.monadial.waygrid.common.application.algebra.SupervisedRequest.{ Restart, Start, Stop }

import cats.Parallel
import cats.effect.Async
import cats.syntax.all.*
import com.monadial.waygrid.common.application.algebra.{
  Logger,
  SupervisedActor,
  SupervisedActorRef,
  SupervisedRequest
}

import com.suprnation.actor.Actor.ReplyingReceive

trait ProgramSupervisorRequest

type BaseProgramActorRef[F[+_]] = SupervisedActorRef[F, ProgramSupervisorRequest]

abstract class BaseProgramActor[F[+_]: {Async, Parallel, Logger}] extends SupervisedActor[F, ProgramSupervisorRequest]:
  override def receive: ReplyingReceive[F, ProgramSupervisorRequest | SupervisedRequest, Any] =
    case Start =>
      for
        _ <- Logger[F].info("Starting program supervisor...")
        _ <- onProgramStart
      yield ()

    case Stop =>
      for
        _ <- Logger[F].info("Stopping program supervisor...")
        _ <- onProgramStop
        _ <- self.stop
      yield ()

    case Restart =>
      for
        _ <- Logger[F].info("Restarting program supervisor...")
        _ <- onProgramRestart
      yield ()

  def onProgramStart: F[Unit]
  def onProgramStop: F[Unit]
  def onProgramRestart: F[Unit]
