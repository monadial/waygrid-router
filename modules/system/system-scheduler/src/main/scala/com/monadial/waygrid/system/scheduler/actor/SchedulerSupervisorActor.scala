package com.monadial.waygrid.system.scheduler.actor

import cats.Parallel
import cats.effect.Resource
import cats.effect.{Async, Ref}
import com.monadial.waygrid.common.application.algebra.{SupervisedActor, SupervisedActorRef, SupervisedRequest}
import com.monadial.waygrid.system.scheduler.model.schedule.Value.ScheduleShard
import com.suprnation.actor.Actor.ReplyingReceive

trait SchedulerSupervisorActorRequest

type SchedulerSupervisorActor[F[+_]] = SupervisedActor[F, SchedulerSupervisorActorRequest]
type SchedulerSupervisorActorRef[F[+_]] = SupervisedActorRef[F, SchedulerSupervisorActorRequest]

object SchedulerSupervisorActor:
  def behavior[F[+_]: {Async, Parallel}]: Resource[F, SchedulerSupervisorActor[F]] =
    for
      assignedShards <- Resource.pure(Ref.of[F, Map[ScheduleShard, SchedulerShardActorRef[F]]](Map.empty))



    yield new SchedulerSupervisorActor[F]:
      override def receive: ReplyingReceive[F, SchedulerSupervisorActorRequest | SupervisedRequest, Any] = ???








