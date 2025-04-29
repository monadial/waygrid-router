package com.monadial.waygrid.common.domain.instances

import cats.data.Validated
import com.monadial.waygrid.common.domain.value.codec.{Base64Codec, Base64DecodingError, BytesCodec, BytesDecodingError}

object LongInstances:

  given BytesCodec[Long] with
    def encode(value: Long): Array[Byte] =
      BigInt(value)
          .toByteArray

    def decode(value: Array[Byte]): Validated[BytesDecodingError, Long] =
      Validated
        .catchNonFatal(BigInt(value).toLong)
        .leftMap(x => BytesDecodingError(x.getMessage))

  given Base64Codec[Long] with
    def encode(value: Long): String =
      JavaBridge
        .base64Encoder(BytesCodec[Long].encode(value))

    def decode(value: String): Validated[Base64DecodingError, Long] =
      Validated
        .catchNonFatal(JavaBridge.base64Decoder(value))
        .map(BigInt(_).toLong)
        .leftMap(x => Base64DecodingError(x.getMessage))
