package com.monadial.waygrid.system.scheduler.actor

import cats.Parallel
import cats.effect.Resource
import cats.effect.{Async, Ref}
import cats.syntax.all.*
import com.monadial.waygrid.common.application.algebra.SupervisedRequest.{Start, Stop}
import com.monadial.waygrid.common.application.algebra.{Logger, SupervisedActor, SupervisedActorRef, SupervisedRequest}
import com.monadial.waygrid.system.scheduler.model.schedule.Value.ScheduleShard
import com.suprnation.actor.Actor.ReplyingReceive

trait SchedulerSupervisorActorRequest

type SchedulerSupervisorActor[F[+_]] = SupervisedActor[F, SchedulerSupervisorActorRequest]
type SchedulerSupervisorActorRef[F[+_]] = SupervisedActorRef[F, SchedulerSupervisorActorRequest]

object SchedulerSupervisorActor:
  def behavior[F[+_]: {Async, Parallel, Logger}]: Resource[F, SchedulerSupervisorActor[F]] =
    for
      _assignedShards <- Resource.eval(Ref.of[F, Map[ScheduleShard, SchedulerShardActorRef[F]]](Map.empty))
    yield new SchedulerSupervisorActor[F]:
      override def receive: ReplyingReceive[F, SchedulerSupervisorActorRequest | SupervisedRequest, Any] =
        case Start =>
          Logger[F].info("[scheduler-supervisor] Started") *> Async[F].unit
        case Stop =>
          Logger[F].info("[scheduler-supervisor] Stopped") *> Async[F].unit
        case _: SchedulerSupervisorActorRequest =>
          // Scheduling shards are not implemented yet.
          Async[F].unit
