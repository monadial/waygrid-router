package com.monadial.waygrid.system.scheduler.algebra

import com.monadial.waygrid.system.scheduler.model.schedule.Value.{Schedule, ScheduleTime, ScheduleToken}

trait SchedulerStorage[F[+_]]:
  def put(schedule: Schedule): F[Unit]
  def remove(token: ScheduleToken): F[Unit]
  def find(token: ScheduleToken): F[Option[ScheduleTime]]
  def update(newSchedule: Schedule): F[Unit]
