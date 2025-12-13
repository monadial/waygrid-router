package com.monadial.waygrid.common.application.instances

import com.comcast.ip4s.{ Host, Port }
import io.circe.{ Decoder, Encoder }

import scala.util.{ Failure, Success }

object Ip4sInstances:

  given Encoder[Host] = Encoder
    .encodeString
    .contramap(_.toString)

  given Decoder[Host] = Decoder
    .decodeString
    .emapTry(str =>
      Host.fromString(str) match
        case Some(value) => Success(value)
        case None        => Failure(throw new Exception("unable to decode host"))
    )

  given Encoder[Port] = Encoder
    .encodeInt
    .contramap(_.value)

  given Decoder[Port] = Decoder
    .decodeInt
    .emapTry(int =>
      Port.fromInt(int) match
        case Some(value) => Success(value)
        case None        => Failure(throw new Exception("unable to decode port"))
    )
