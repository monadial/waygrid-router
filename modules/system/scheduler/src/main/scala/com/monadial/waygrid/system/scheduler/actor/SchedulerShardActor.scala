package com.monadial.waygrid.system.scheduler.actor

import cats.Parallel
import cats.effect.{Async, Resource}
import cats.syntax.all.*
import com.monadial.waygrid.common.application.algebra.SupervisedRequest.{Start, Stop}
import com.monadial.waygrid.common.application.algebra.{Logger, SupervisedActor, SupervisedActorRef, SupervisedRequest}
import com.monadial.waygrid.common.application.syntax.FSMSyntax
import com.monadial.waygrid.system.scheduler.model.schedule.Value.{ScheduleTime, ScheduleToken}
import com.suprnation.actor.Actor.ReplyingReceive

trait SchedulerShardActorRequest
case class Schedule(token: ScheduleToken, time: ScheduleTime)   extends SchedulerShardActorRequest
case class Unschedule(token: ScheduleToken)                     extends SchedulerShardActorRequest
case class Reschedule(token: ScheduleToken, time: ScheduleTime) extends SchedulerShardActorRequest
case class Due(token: ScheduleToken, time: ScheduleTime)        extends SchedulerShardActorRequest

type SchedulerShardActor[F[+_]]    = SupervisedActor[F, SchedulerShardActorRequest]
type SchedulerShardActorRef[F[+_]] = SupervisedActorRef[F, SchedulerShardActorRequest]

object SchedulerShardActor:
  
  def fsm = FSMSyntax
  
  
  
  
  def behavior[F[+_]: {Async, Logger, Parallel}](partition: Int): Resource[F, SchedulerShardActor[F]] =
    Resource
      .pure:
        new SchedulerShardActor[F]:
          override def receive: ReplyingReceive[F, SchedulerShardActorRequest | SupervisedRequest, Any] =
            case Start                   => onStart
            case Stop                    => onStop
            case Schedule(token, time)   => onSchedule(token, time)
            case Unschedule(token)       => onUnschedule(token)
            case Reschedule(token, time) => onReschedule(token, time)
            case Due(token, time)        => onDue(token, time)

          private def onStart: F[Unit] =
            for
              _ <- Logger[F].info(s"Starting Scheduler Actor on partition: $partition")
            yield ()

          private def onStop: F[Unit] =
            for
              _ <- Logger[F].info(s"Stopping Scheduler Actor on partition: $partition")
            yield ()

          private def onSchedule(token: ScheduleToken, time: ScheduleTime): F[Unit] =
            for
              _ <- Logger[F].info("Scheduling task with token: " + token.show + " at time: " + time.show)
            yield ()

          private def onUnschedule(token: ScheduleToken): F[Unit] =
            for
              _ <- Logger[F].info("Unscheduling task with token: " + token.show)
            yield ()

          private def onReschedule(token: ScheduleToken, time: ScheduleTime): F[Unit] =
            for
              _ <- Logger[F].info("Rescheduling task with token: " + token.show + " to time: " + time.show)
            yield ()

          private def onDue(token: ScheduleToken, time: ScheduleTime): F[Unit] =
            for
              _ <- Logger[F].info("Task with token: " + token.show + " is due at time: " + time.show)
            yield ()
