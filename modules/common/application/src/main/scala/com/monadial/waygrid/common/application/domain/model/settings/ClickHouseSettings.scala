package com.monadial.waygrid.common.application.domain.model.settings

import com.comcast.ip4s.{ Host, Port }
import com.monadial.waygrid.common.application.domain.model.Platform
import com.monadial.waygrid.common.application.instances.Ip4sInstances.given
import com.monadial.waygrid.common.application.instances.DurationInstances.given
import io.circe.Codec

import scala.concurrent.duration.{ DurationInt, FiniteDuration }

enum ConnectionType derives Codec.AsObject:
  case HTTP
  case HTTPS

final case class ClickHouseSettings(
  host: Host,
  port: Port,
  database: String,
  user: String,
  password: String,
  connectionType: ConnectionType,
  poolSize: Option[Int],
  debug: Option[Boolean],
  maxLifetime: Option[FiniteDuration],
  connectionTimeout: Option[FiniteDuration]
) derives Codec.AsObject

object ClickHouseSettings:
  // todo tune this parameters, later
  private val DefaultMaxLifetime: FiniteDuration       = 10.minutes
  private val DefaultConnectionTimeout: FiniteDuration = 1.minute

  extension (settings: ClickHouseSettings)
    inline def url: String =
      settings.connectionType match
        case ConnectionType.HTTP => s"jdbc:clickhouse:http://${settings.host}:${settings.port}/${settings.database}"
        case ConnectionType.HTTPS => s"jdbc:clickhouse:https://${settings.host}:${settings.port}/${settings.database}?ssl=true"

    inline def getPoolSize: Int =
      settings.poolSize.getOrElse(Platform.numberOfAvailableCpuCores)

    inline def getMaxLifetime: FiniteDuration =
      settings.maxLifetime.getOrElse(DefaultMaxLifetime)

    inline def getConnectionTimeout: FiniteDuration =
      settings.connectionTimeout.getOrElse(DefaultConnectionTimeout)
