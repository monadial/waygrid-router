package com.monadial.waygrid.system.scheduler.settings

import com.monadial.waygrid.common.application.domain.model.settings.{ PostgresSettings, RedisSettings }
import com.monadial.waygrid.common.domain.model.settings.ServiceSettings
import io.circe.Codec

final case class SchedulerServiceSettings(
  redis: RedisSettings,
  postgres: PostgresSettings
) extends ServiceSettings derives Codec.AsObject
