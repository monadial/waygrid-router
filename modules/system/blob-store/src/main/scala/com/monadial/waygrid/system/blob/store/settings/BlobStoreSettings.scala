package com.monadial.waygrid.system.blob.store.settings

import com.monadial.waygrid.common.application.domain.model.settings.{
  EventStreamSettings,
  HttpServerSettings,
  NodeSettings
}
import com.monadial.waygrid.common.application.instances.DurationInstances.given
import com.monadial.waygrid.common.application.instances.OdinLoggerInstances.given

import io.circe.Codec
import io.odin.Level

import scala.concurrent.duration.Duration

final case class BlobStoreSettings(
  override val debug: Boolean,
  override val logLevel: Level,
  override val gracefulShutdownTimeout: Duration,
  override val parallelism: Option[Int],
  override val eventStream: EventStreamSettings,
  override val httpServer: HttpServerSettings
) extends NodeSettings derives Codec.AsObject
