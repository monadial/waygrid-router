package com.monadial.waygrid.common.domain.instances

import cats.data.Validated
import com.monadial.waygrid.common.domain.instances.BytesInstances.given
import com.monadial.waygrid.common.domain.value.codec.{
  Base64Codec,
  Base64DecodingError,
  BytesCodec,
  BytesDecodingError
}

object StringInstances:

  given BytesCodec[String] with
    def encode(value: String): Array[Byte] =
      value
        .getBytes

    def decode(value: Array[Byte]): Validated[BytesDecodingError, String] =
      Validated
        .catchNonFatal(new String(value))
        .leftMap(
          x => BytesDecodingError(x.getMessage)
        )

  given Base64Codec[String] with
    def encode(value: String): String =
      JavaBridge
        .base64Encoder(BytesCodec[String].encode(value))

    def decode(value: String): Validated[Base64DecodingError, String] =
      Validated
        .catchNonFatal(JavaBridge.base64Decoder(value))
        .map(Base64Codec[Array[Byte]].encode(_))
        .leftMap(
          x => Base64DecodingError(x.getMessage)
        )
