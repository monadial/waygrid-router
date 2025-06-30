package com.monadial.waygrid.common.domain.instances

import cats.data.Validated
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import com.monadial.waygrid.common.domain.value.codec.{Base64Codec, Base64DecodingError, BytesCodec, BytesDecodingError}
import scodec.{Attempt, codecs, Decoder as SDecoder, Encoder as SEncoder}

object LongInstances:




  given BytesCodec[Long] with
    inline def encode(value: Long): Array[Byte] =
      BigInt(value)
        .toByteArray

    inline def decode(value: Array[Byte]): Validated[BytesDecodingError, Long] =
      Validated
        .catchNonFatal(BigInt(value).toLong)
        .leftMap(x => BytesDecodingError(x.getMessage))

  given SEncoder[Long] = codecs.int64.asEncoder.contramap(identity)
  given SDecoder[Long] = codecs.int64.asDecoder.emap: value =>
    Attempt.successful(value)

  given Base64Codec[Long] with
    inline def encode(value: Long): String =
      JavaBridge
        .base64Encoder(BytesCodec[Long].encode(value))

    inline def decode(value: String): Validated[Base64DecodingError, Long] =
      Validated
        .catchNonFatal(JavaBridge.base64Decoder(value))
        .map(BigInt(_).toLong)
        .leftMap(x => Base64DecodingError(x.getMessage))

  given JsonValueCodec[Long] = JsonCodecMaker.make
