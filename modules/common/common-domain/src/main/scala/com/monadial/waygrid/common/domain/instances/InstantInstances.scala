package com.monadial.waygrid.common.domain.instances

import cats.data.Validated
import cats.{Eq, Order, Show}
import com.monadial.waygrid.common.domain.instances.LongInstances.given
import com.monadial.waygrid.common.domain.value.codec.{Base64Codec, Base64DecodingError, BytesCodec, BytesDecodingError}
import scodec.bits.BitVector
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
    inline def encode(value: Instant): Array[Byte] =
      BytesCodec[Long]
        .encode(value.toEpochMilli)

    inline def decode(value: Array[Byte]): Validated[BytesDecodingError, Instant] =
      BytesCodec[Long]
        .decode(value)
        .map(Instant.ofEpochMilli)

  given Base64Codec[Instant] with
    inline def encode(value: Instant): String =
      Base64Codec[Long]
        .encode(value.toEpochMilli)

    inline def decode(value: String): Validated[Base64DecodingError, Instant] =
      Base64Codec[Long]
        .decode(value)
        .map(Instant.ofEpochMilli)
