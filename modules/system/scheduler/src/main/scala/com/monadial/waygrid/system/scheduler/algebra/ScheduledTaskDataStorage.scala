package com.monadial.waygrid.system.scheduler.algebra

import com.monadial.waygrid.system.scheduler.model.eventdata.ScheduledTaskData
import com.monadial.waygrid.system.scheduler.model.schedule.Value.ScheduleToken

trait ScheduledTaskDataStorage[F[+_]]:
  def put(data: ScheduledTaskData): F[Unit]
  def remove(token: ScheduleToken): F[Unit]
  def find(token: ScheduleToken): F[Option[ScheduledTaskData]]
