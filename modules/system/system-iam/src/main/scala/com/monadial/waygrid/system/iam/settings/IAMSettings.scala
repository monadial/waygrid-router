package com.monadial.waygrid.system.iam.settings

import com.monadial.waygrid.common.application.domain.model.settings.{
  EventStreamSettings,
  HttpServerSettings,
  NodeSettings,
  WithServiceSettings
}
import com.monadial.waygrid.common.application.instances.OdinLoggerInstances.given
import com.monadial.waygrid.common.application.instances.DurationInstances.given
import io.circe.Codec
import io.odin.Level

import scala.concurrent.duration.Duration

final case class IAMSettings(
  override val debug: Boolean,
  override val logLevel: Level,
  override val gracefulShutdownTimeout: Duration,
  override val parallelism: Option[Int],
  override val eventStream: EventStreamSettings,
  override val httpServer: HttpServerSettings,
  override val service: IAMServiceSettings
) extends NodeSettings with WithServiceSettings[IAMServiceSettings] derives Codec.AsObject
