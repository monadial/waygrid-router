package com.monadial.waygrid.system.scheduler.actor

import com.monadial.waygrid.common.application.algebra.{ SupervisedActor, SupervisedActorRef }

import cats.effect.kernel.Resource

sealed trait TaskSchedulerRequest
final case class ScheduleTask() extends TaskSchedulerRequest

type TaskSchedulerActor[F[+_]]    = SupervisedActor[F, TaskSchedulerRequest]
type TaskSchedulerActorRef[F[+_]] = SupervisedActorRef[F, TaskSchedulerRequest]

object TaskSchedulerActor:

  def behavior[F[+_]]: Resource[F, TaskSchedulerActor[F]] = ???
