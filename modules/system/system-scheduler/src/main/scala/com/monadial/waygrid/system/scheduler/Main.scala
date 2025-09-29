package com.monadial.waygrid.system.scheduler

import cats.Parallel
import cats.effect.std.Console
import cats.effect.{Async, Resource}
import com.monadial.waygrid.common.application.algebra.{EventSink, EventSource, ThisNode, Logger}
import com.monadial.waygrid.common.application.program.WaygridApp
import com.monadial.waygrid.common.application.util.doobie.Doobie
import com.monadial.waygrid.common.domain.model.node.Node
import com.monadial.waygrid.system.common.program.SystemWaygridApp
import com.monadial.waygrid.system.scheduler.interpreter.DoobieScheduledTaskDataStorage
import com.monadial.waygrid.system.scheduler.model.eventdata.ScheduledTaskData
import com.monadial.waygrid.system.scheduler.model.schedule.Value.{ScheduleTime, ScheduleToken}
import com.monadial.waygrid.system.scheduler.settings.SchedulerSettings
import com.suprnation.actor.ActorSystem
import org.typelevel.otel4s.metrics.MeterProvider

import scala.annotation.nowarn

object Main extends WaygridApp[SchedulerSettings](SystemWaygridApp.Scheduler):

  @nowarn("msg=unused implicit parameter")
  override def programBuilder[F[+_]: {Async, Parallel, Console, Logger, ThisNode, MeterProvider, EventSink,
    EventSource}](
    actorSystem: ActorSystem[F],
    settings: SchedulerSettings,
    thisNode: Node
  ): Resource[F, Unit] =
    for
      xa <- Doobie.pooled(settings.service.postgres)
      storage <- DoobieScheduledTaskDataStorage.default(xa)
      token <- Resource.eval(ScheduleToken.next[F])
      time <- Resource.eval(ScheduleTime.now[F])
      _ <- Resource.eval(storage.put(ScheduledTaskData(token, time)))
      x <- Resource.eval(storage.find(token))
      x <- Resource.eval(storage.remove(token))
    yield ()

//    for
//      db <- Skunk.pooled(settings.service.postgres)
//      _ <- db.evalMap: sess =>
//        for
//          _ <- Logger[F].info(s"Starting Scheduler.")
//          sql = sql"select * from test where id = 1".query(varchar ~ int4)
//          x <- sess.execute(sql)
//          _ <- Logger[F].info(x.mkString(","))
//        yield ()
//    yield ()
