package com.monadial.waygrid.common.application.instances

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.{ Duration, FiniteDuration }
import scala.util.Try

import io.circe.{ Decoder as JsonDecoder, Encoder as JsonEncoder }
import scodec.*

object DurationInstances:

  given Codec[FiniteDuration] =
    (scodec.codecs.int64 :: scodec.codecs.variableSizeBytes(scodec.codecs.int32, scodec.codecs.utf8))
      .xmap(
        { case (length, unit) => FiniteDuration(length, TimeUnit.valueOf(unit)) },
        duration => (duration.length, duration.unit.name())
      )

  given durationEncoder: JsonEncoder[Duration] = JsonEncoder
    .encodeString
    .contramap(_.toString)

  given durationDecoder: JsonDecoder[Duration] = JsonDecoder
    .decodeString
    .emapTry(s => Try(Duration(s)))

  // scala.concurrent.duration.FiniteDuration
  given JsonEncoder[FiniteDuration] = durationEncoder
    .contramap(x => Duration(x.length, x.unit))

  given JsonDecoder[FiniteDuration] = durationDecoder
    .map(x => FiniteDuration(x.length, x.unit))
