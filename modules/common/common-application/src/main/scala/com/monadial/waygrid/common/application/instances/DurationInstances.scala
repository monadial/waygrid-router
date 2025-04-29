package com.monadial.waygrid.common.application.instances

import io.circe.{Decoder, Encoder}

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.Try


object DurationInstances:

  given durationEncoder: Encoder[Duration] = Encoder
      .encodeString
      .contramap(_.toString)

  given durationDecoder: Decoder[Duration] = Decoder
      .decodeString
      .emapTry(s => Try(Duration(s)))

  // scala.concurrent.duration.FiniteDuration
  given Encoder[FiniteDuration] = durationEncoder
      .contramap(x => Duration(x.length, x.unit))

  given Decoder[FiniteDuration] = durationDecoder
      .map(x => FiniteDuration(x.length, x.unit))
