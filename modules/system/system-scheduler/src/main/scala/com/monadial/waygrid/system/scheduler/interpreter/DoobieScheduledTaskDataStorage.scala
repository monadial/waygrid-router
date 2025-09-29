package com.monadial.waygrid.system.scheduler.interpreter

import cats.effect.{ Async, Resource }
import cats.syntax.all.*
import com.monadial.waygrid.common.application.algebra.Logger
import com.monadial.waygrid.system.scheduler.algebra.ScheduledTaskDataStorage
import com.monadial.waygrid.system.scheduler.model.eventdata.ScheduledTaskData
import com.monadial.waygrid.system.scheduler.model.schedule.Value.{ ScheduleTime, ScheduleToken }
import doobie.*
import doobie.postgres.implicits.*
import doobie.implicits.*
import doobie.util.meta.Meta
import doobie.util.transactor.Transactor
import wvlet.airframe.ulid.ULID

import java.time.Instant

final case class DoobieScheduledTaskDataStorage[F[+_]: {Async, Logger}](xa: Transactor[F])
    extends ScheduledTaskDataStorage[F]:

  given Meta[ScheduleToken] = Meta[String].timap(x => ScheduleToken(ULID(x)))(x => x.unwrap.toString)
  given Meta[ScheduleTime]  = Meta[Instant].timap(ScheduleTime(_))(x => x.unwrap)

  override def put(data: ScheduledTaskData): F[Unit] =
    for
      _ <- Logger[F].info("Saving data to database")
      statement <-
        sql"""
          INSERT INTO scheduled_task_data (id, time) VALUES (${data.token}, ${data.time})
          """
          .update
          .pure[F]
      res <- statement
        .run
        .transact(xa)
      _ <- Logger[F].info(s"Saved data to database: $res")
    yield ()

  override def remove(token: ScheduleToken): F[Unit] =
    for
      _ <- Logger[F].info(s"Removing task for token: ${token}")
      statement <-
        sql"""
           DELETE FROM scheduled_task_data WHERE id = ${token}
           """
          .update
          .pure[F]
      _ <- statement
        .run
        .transact(xa)
    yield ()

  override def find(token: ScheduleToken): F[Option[ScheduledTaskData]] =
    for
      _ <- Logger[F].info(s"Finding task for token: ${token}")
      statement <-
        sql"""
           SELECT id, time FROM scheduled_task_data WHERE id = ${token}
           """
          .query[ScheduledTaskData]
          .pure[F]
      res <- statement
        .option
        .transact(xa)
      _ <- res.fold(Logger[F].info(s"No task found for token: ${token}"))(x => Logger[F].info(s"Task found: $x"))
    yield res

object DoobieScheduledTaskDataStorage:
  def default[F[+_]: {Async, Logger}](xa: Transactor[F]): Resource[F, DoobieScheduledTaskDataStorage[F]] =
    Resource.pure(new DoobieScheduledTaskDataStorage[F](xa))
