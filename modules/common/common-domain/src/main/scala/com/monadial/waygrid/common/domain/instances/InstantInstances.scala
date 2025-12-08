package com.monadial.waygrid.common.domain.instances

import cats.data.Validated
import cats.{Eq, Order, Show}
import com.monadial.waygrid.common.domain.algebra.value.codec.{Base64Codec, Base64DecodingError, BytesCodec, BytesDecodingError}
import com.monadial.waygrid.common.domain.instances.LongInstances.given
import scodec.bits.{BitVector, ByteVector}
import scodec.{Attempt, DecodeResult, SizeBound, Codec as SCodec}

import java.time.Instant

object InstantInstances:
  given Eq[Instant]    = Eq.fromUniversalEquals
  given Order[Instant] = Order.fromComparable
  given Show[Instant]  = Show.fromToString

  given SCodec[Instant] = new SCodec[Instant]:
    override def decode(bits: BitVector): Attempt[DecodeResult[Instant]] = ???

    override def encode(value: Instant): Attempt[BitVector] = ???

    override def sizeBound: SizeBound = ???


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
