package com.monadial.waygrid.common.domain.instances

import cats.data.Validated
import com.monadial.waygrid.common.domain.algebra.value.codec.{
  Base64Codec,
  Base64DecodingError,
  BytesCodec,
  BytesDecodingError
}
import scodec.bits.ByteVector

import java.nio.ByteBuffer
import scodec.{ Attempt, Decoder as SDecoder, Encoder as SEncoder, codecs }

object IntegerInstances:

  given SEncoder[Int] = codecs.int32.asEncoder.contramap(identity)

  given SDecoder[Int] = codecs.int32.asDecoder.emap: value =>
      Attempt.successful(value)

  given BytesCodec[Int] with
    inline def encodeToScalar(value: Int): ByteVector = ByteVector(value)

    inline def decodeFromScalar(value: ByteVector): Validated[BytesDecodingError, Int] =
      Validated
        .catchNonFatal(ByteBuffer.wrap(value.toArrayUnsafe).getInt)
        .leftMap(x => BytesDecodingError(x.getMessage))

  given Base64Codec[Int] with
    inline def encode(value: Int): String =
      JavaBridge
        .base64Encoder(BytesCodec[Int].encodeToScalar(value))

    inline def decode(value: String): Validated[Base64DecodingError, Int] =
      Validated
        .catchNonFatal(JavaBridge.base64Decoder(value))
        .andThen(BytesCodec[Int].decodeFromScalar(_))
        .leftMap(x => Base64DecodingError(x.getMessage))
