package com.monadial.waygrid.common.application.instances

import dev.profunktor.redis4cats.connection.RedisURI
import io.circe.{ Decoder, Encoder }

object Redis4CatsInstances:

  given Decoder[RedisURI] = Decoder
    .decodeString
    .emap:
      RedisURI.fromString(_) match
        case Right(uri) => Right(uri)
        case Left(err)  => Left(s"Invalid Redis URI: ${err.getMessage}")

  given Encoder[RedisURI] = Encoder
    .encodeString
    .contramap(_.toString)
