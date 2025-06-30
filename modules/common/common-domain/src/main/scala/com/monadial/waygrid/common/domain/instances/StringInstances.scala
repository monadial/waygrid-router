package com.monadial.waygrid.common.domain.instances

import cats.data.Validated
import com.monadial.waygrid.common.domain.instances.BytesInstances.given
import com.monadial.waygrid.common.domain.value.codec.{Base64Codec, Base64DecodingError, BytesCodec, BytesDecodingError}
import scodec.{Attempt, codecs, Decoder as SDecoder, Encoder as SEncoder}

import java.nio.charset.Charset


object StringInstances:



  given BytesCodec[String] with
    inline def encode(value: String): Array[Byte] =
      value
        .getBytes(Charset.forName("UTF-8"))

    inline def decode(value: Array[Byte]): Validated[BytesDecodingError, String] =
      Validated
        .catchNonFatal(String(value, Charset.forName("UTF-8")))
        .leftMap(x => BytesDecodingError(x.getMessage))

  given SEncoder[String] = codecs.utf8.asEncoder.contramap(identity)
  given SDecoder[String] = codecs.utf8.asDecoder.emap: bytes =>
    Attempt.successful(bytes)

  given Base64Codec[String] with
    inline def encode(value: String): String =
      JavaBridge
        .base64Encoder(BytesCodec[String].encode(value))

    inline def decode(value: String): Validated[Base64DecodingError, String] =
      Validated
        .catchNonFatal(JavaBridge.base64Decoder(value))
        .map(Base64Codec[Array[Byte]].encode(_))
        .leftMap(x => Base64DecodingError(x.getMessage))


