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

import java.nio.charset.Charset

object StringInstances:

  given BytesCodec[String] with
    override def decodeFromScalar(value: ByteVector): Validated[BytesDecodingError, String] =
      Validated
        .catchNonFatal(String(value.toArrayUnsafe, Charset.forName("UTF-8")))
        .leftMap(x => BytesDecodingError(x.getMessage))

    override def encodeToScalar(value: String): ByteVector =
      ByteVector(value.getBytes("UTF-8"))

  given SEncoder[String] = codecs.utf8.asEncoder
  given SDecoder[String] = codecs.utf8.asDecoder

  given Base64Codec[String] with
    inline def encode(value: String): String =
      JavaBridge
        .base64Encoder(BytesCodec[String].encodeToScalar(value))

    inline def decode(value: String): Validated[Base64DecodingError, String] =
      Validated
        .catchNonFatal(JavaBridge.base64Decoder(value))
        .andThen(BytesCodec[String].decodeFromScalar(_))
        .leftMap(x => Base64DecodingError(x.getMessage))
