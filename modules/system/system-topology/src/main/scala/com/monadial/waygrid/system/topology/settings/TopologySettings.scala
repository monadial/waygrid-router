package com.monadial.waygrid.system.topology.settings

import com.monadial.waygrid.common.application.instances.DurationInstances.given
import com.monadial.waygrid.common.application.instances.OdinLoggerInstances.given
import com.monadial.waygrid.common.application.model.settings.{
  EventStreamSettings,
  NodeSettings
}
import io.circe.Codec
import io.odin.Level

import scala.concurrent.duration.Duration

final case class TopologySettings(
  override val debug: Boolean,
  override val logLevel: Level,
  override val gracefulShutdownTimeout: Duration,
  override val parallelism: Option[Int],
  override val eventStream: EventStreamSettings
) extends NodeSettings derives Codec.AsObject
