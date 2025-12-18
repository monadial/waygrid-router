package com.monadial.waygrid.common.domain.instances

import cats.data.Validated
import com.monadial.waygrid.common.domain.algebra.value.codec.{
  Base64Codec,
  Base64DecodingError,
  BytesCodec,
  BytesDecodingError
}
import scodec.bits.ByteVector
import scodec.{ Decoder as SDecoder, Encoder as SEncoder, codecs }

object IntegerInstances:

  given SEncoder[Int] = codecs.int32.asEncoder
  given SDecoder[Int] = codecs.int32.asDecoder

  given BytesCodec[Int] with
    inline def encodeToScalar(value: Int): ByteVector = ByteVector.fromInt(value)

    inline def decodeFromScalar(value: ByteVector): Validated[BytesDecodingError, Int] =
      Validated
        .catchNonFatal(value.toInt())
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
