package com.monadial.waygrid.system.topology.settings

import scala.concurrent.duration.Duration

import com.monadial.waygrid.common.application.domain.model.settings.{ PostgresSettings, RedisSettings }
import com.monadial.waygrid.common.application.instances.DurationInstances.given
import com.monadial.waygrid.common.domain.model.settings.ServiceSettings
import io.circe.Codec

final case class TopologyServiceSettings(
  postgres: PostgresSettings,
  redis: RedisSettings,
  leadership: LeadershipSettings
) extends ServiceSettings derives Codec.AsObject

final case class LeadershipSettings(
  lease: Duration
) derives Codec.AsObject
