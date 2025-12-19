package com.monadial.waygrid.common.application.domain.model.settings

import com.comcast.ip4s.{ Host, Port }
import com.monadial.waygrid.common.application.instances.Ip4sInstances.given
import io.circe.Codec

final case class HttpServerSettings(
  host: Host,
  port: Port,
  maxConnections: Option[Int]
) derives Codec.AsObject
