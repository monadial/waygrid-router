package com.monadial.waygrid.common.domain.instances

import java.time.Instant

import cats.data.Validated
import cats.{ Eq, Order, Show }
import com.monadial.waygrid.common.domain.algebra.value.codec.{
  Base64Codec,
  Base64DecodingError,
  BytesCodec,
  BytesDecodingError
}
import com.monadial.waygrid.common.domain.instances.LongInstances.given
import scodec.bits.ByteVector
import scodec.{ Decoder as SDecoder, Encoder as SEncoder }

object InstantInstances:
  given Eq[Instant]    = Eq.fromUniversalEquals
  given Order[Instant] = Order.fromComparable
  given Show[Instant]  = Show.fromToString

  given SEncoder[Instant] = summon[SEncoder[Long]].contramap(_.toEpochMilli)
  given SDecoder[Instant] = summon[SDecoder[Long]].map(Instant.ofEpochMilli)

  given BytesCodec[Instant] with
    inline def encodeToScalar(value: Instant): ByteVector =
      BytesCodec[Long]
        .encodeToScalar(value.toEpochMilli)

    inline def decodeFromScalar(value: ByteVector): Validated[BytesDecodingError, Instant] =
      BytesCodec[Long]
        .decodeFromScalar(value)
        .map(Instant.ofEpochMilli)

  given Base64Codec[Instant] with
    inline def encode(value: Instant): String =
      Base64Codec[Long]
        .encode(value.toEpochMilli)

    inline def decode(value: String): Validated[Base64DecodingError, Instant] =
      Base64Codec[Long]
        .decode(value)
        .map(Instant.ofEpochMilli)
