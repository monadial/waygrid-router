package com.monadial.waygrid.origin.http.settings

import com.monadial.waygrid.common.application.model.settings.{EventStreamSettings, HttpServerSettings, NodeSettings}
import io.circe.Codec
import io.odin.Level
import com.monadial.waygrid.common.application.instances.OdinLoggerInstances.given
import com.monadial.waygrid.common.application.instances.DurationInstances.given

import scala.concurrent.duration.Duration

final case class HttpSettings(
  override val debug: Boolean,
  override val logLevel: Level,
  override val gracefulShutdownTimeout: Duration,
  override val parallelism: Option[Int],
  override val eventStream: EventStreamSettings,
  override val httpServer: HttpServerSettings
) extends NodeSettings derives Codec.AsObject
