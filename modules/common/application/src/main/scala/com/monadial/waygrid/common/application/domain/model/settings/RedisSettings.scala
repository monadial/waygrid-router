package com.monadial.waygrid.common.application.domain.model.settings

import dev.profunktor.redis4cats.connection.RedisURI
import com.monadial.waygrid.common.application.instances.Redis4CatsInstances.given
import io.circe.Codec

final case class RedisSettings(
  uris: List[RedisURI]
) derives Codec.AsObject
