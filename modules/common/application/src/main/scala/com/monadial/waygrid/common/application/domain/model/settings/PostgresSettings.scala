package com.monadial.waygrid.common.application.domain.model.settings

import com.comcast.ip4s.{ Host, Port }
import com.monadial.waygrid.common.application.instances.Ip4sInstances.given
import io.circe.Codec

final case class PostgresSettings(
  host: Host,
  port: Port,
  database: String,
  user: String,
  password: String,
  poolSize: Option[Int],
  debug: Option[Boolean]
) derives Codec.AsObject:
  val MigrationsTable: String = "migrations"

object PostgresSettings:
  extension (settings: PostgresSettings)
    inline def url: String =
      s"jdbc:postgresql://${settings.host}:${settings.port}/${settings.database}"
