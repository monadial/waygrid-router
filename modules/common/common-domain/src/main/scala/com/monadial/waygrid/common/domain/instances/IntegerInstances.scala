package com.monadial.waygrid.common.domain.instances

import cats.data.Validated
import com.monadial.waygrid.common.domain.value.codec.{
  Base64Codec,
  Base64DecodingError,
  BytesCodec,
  BytesDecodingError
}

import java.nio.ByteBuffer
import scodec.{Attempt, codecs, Decoder as SDecoder, Encoder as SEncoder}

object IntegerInstances:

  given SEncoder[Int] = codecs.int32.asEncoder.contramap(identity)

  given SDecoder[Int] = codecs.int32.asDecoder.emap: value =>
    Attempt.successful(value)

  given BytesCodec[Int] with
    inline def encode(value: Int): Array[Byte] =
      ByteBuffer
        .allocate(4)
        .putInt(value)
        .array()

    inline def decode(value: Array[Byte]): Validated[BytesDecodingError, Int] =
      Validated
        .catchNonFatal(ByteBuffer.wrap(value).getInt)
        .leftMap(x => BytesDecodingError(x.getMessage))

  given Base64Codec[Int] with
    inline def encode(value: Int): String =
      JavaBridge
        .base64Encoder(BytesCodec[Int].encode(value))

    inline def decode(value: String): Validated[Base64DecodingError, Int] =
      Validated
        .catchNonFatal(JavaBridge.base64Decoder(value))
        .andThen(BytesCodec[Int].decode(_))
        .leftMap(x => Base64DecodingError(x.getMessage))
