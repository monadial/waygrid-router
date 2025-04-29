package com.monadial.waygrid.common.application.model.settings

import io.odin.Level

import scala.concurrent.duration.Duration

trait NodeSettings:
  val debug: Boolean
  val logLevel: Level
  val gracefulShutdownTimeout: Duration
  val parallelism: Option[Int]
  val eventStream: EventStreamSettings

  // If `numberOfAvailableCpuCores` is less than one, either your processor is about to die,
  // or your JVM has a serious bug in it, or the universe is about to blow up.
  private lazy val numberOfAvailableCpuCores = Runtime.getRuntime.availableProcessors()

  def asyncParallelism: Int = parallelism.getOrElse(numberOfAvailableCpuCores)

