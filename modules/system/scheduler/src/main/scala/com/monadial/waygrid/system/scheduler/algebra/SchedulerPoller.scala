package com.monadial.waygrid.system.scheduler.algebra

import cats.effect.{ Fiber, Resource }
import com.monadial.waygrid.system.scheduler.model.schedule.Value.ScheduleToken

import scala.concurrent.duration.FiniteDuration

trait SchedulerPoller[F[+_]]:
  def acquire(
    pollEvery: FiniteDuration,
    maxPerPoll: Int,
    handler: ScheduleToken => F[Unit]
  ): Resource[F, Fiber[F, Throwable, Unit]]
