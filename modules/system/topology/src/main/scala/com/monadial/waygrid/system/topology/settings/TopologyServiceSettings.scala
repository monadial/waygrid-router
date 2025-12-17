package com.monadial.waygrid.system.topology.settings

import com.monadial.waygrid.common.application.domain.model.settings.{ PostgresSettings, RedisSettings }
import com.monadial.waygrid.common.domain.model.settings.ServiceSettings
import com.monadial.waygrid.common.application.instances.DurationInstances.given
import io.circe.Codec

import scala.concurrent.duration.Duration

final case class TopologyServiceSettings(
  postgres: PostgresSettings,
  redis: RedisSettings,
  leadership: LeadershipSettings
) extends ServiceSettings derives Codec.AsObject

final case class LeadershipSettings(
  lease: Duration
) derives Codec.AsObject
