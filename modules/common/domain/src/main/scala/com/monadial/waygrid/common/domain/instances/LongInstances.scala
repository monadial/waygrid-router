package com.monadial.waygrid.common.domain.instances

import cats.data.Validated
import com.monadial.waygrid.common.domain.algebra.value.codec.{
  Base64Codec,
  Base64DecodingError,
  BytesCodec,
  BytesDecodingError
}
import scodec.bits.ByteVector
import scodec.{ Attempt, Decoder as SDecoder, Encoder as SEncoder, codecs }

object LongInstances:

  given BytesCodec[Long] with
    inline override def encodeToScalar(value: Long): ByteVector =
      ByteVector
        .fromLong(value)

    inline override def decodeFromScalar(value: ByteVector): Validated[BytesDecodingError, Long] =
      Validated
        .catchNonFatal(value.toLong())
        .leftMap(x => BytesDecodingError(x.getMessage))

  given SEncoder[Long] = codecs.int64.asEncoder.contramap(identity)
  given SDecoder[Long] = codecs.int64.asDecoder.emap: value =>
      Attempt.successful(value)

  given Base64Codec[Long] with
    inline override def decode(value: String): Validated[Base64DecodingError, Long] =
      Validated
        .catchNonFatal(JavaBridge.base64Decoder(value))
        .map(x => x.toLong())
        .leftMap(x => Base64DecodingError(x.getMessage))

    inline override def encode(value: Long): String =
      JavaBridge
        .base64Encoder(BytesCodec[Long].encodeToScalar(value))
