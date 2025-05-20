package com.monadial.waygrid.common.application.actor

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

enum ProgramSupervisorRequest:
  case StartProgram
  case StopProgram
  case RestartProgram

enum ProgramSupervisorState:
  case Idle
  case Starting
  case Running
  case Stopping

type ProgramSupervisorRef[F[+_]] = SupervisedActorRef[F, ProgramSupervisorRequest]

abstract class BaseProgramActor[F[+_]: {Async, Parallel, Logger}] extends SupervisedActor[F, ProgramSupervisorRequest]:
  override def receive: ReplyingReceive[F, ProgramSupervisorRequest | SupervisedRequest, Any] =
    case ProgramSupervisorRequest.StartProgram =>
      for
        _ <- Logger[F].info("Starting program supervisor...")
        _ <- startProgram
      yield ()

    case ProgramSupervisorRequest.StopProgram =>
      for
        _ <- Logger[F].info("Stopping program supervisor...")
        _ <- stopProgram
      yield ()

    case ProgramSupervisorRequest.RestartProgram =>
      for
        _ <- Logger[F].info("Restarting program supervisor...")
        _ <- restartProgram
      yield ()

  def startProgram: F[Unit]
  def stopProgram: F[Unit]
  def restartProgram: F[Unit]
