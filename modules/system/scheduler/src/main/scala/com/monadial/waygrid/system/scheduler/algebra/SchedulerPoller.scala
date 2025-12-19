package com.monadial.waygrid.system.scheduler.algebra

import scala.concurrent.duration.FiniteDuration

import cats.effect.{ Fiber, Resource }
import com.monadial.waygrid.system.scheduler.model.schedule.Value.ScheduleToken

trait SchedulerPoller[F[+_]]:
  def acquire(
    pollEvery: FiniteDuration,
    maxPerPoll: Int,
    handler: ScheduleToken => F[Unit]
  ): Resource[F, Fiber[F, Throwable, Unit]]
