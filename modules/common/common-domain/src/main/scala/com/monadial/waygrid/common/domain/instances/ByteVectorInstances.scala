package com.monadial.waygrid.common.domain.instances

import cats.Show
import cats.data.Validated
import cats.kernel.Order
import com.monadial.waygrid.common.domain.value.codec.{Base64Codec, Base64DecodingError, BytesCodec, BytesDecodingError}
import io.circe.{Json, Decoder as JsonDecoder, Encoder as JsonEncoder}
import scodec.bits.ByteVector
import scodec.{Decoder as SDecoder, Encoder as SEncoder}

object ByteVectorInstances:

  given JsonDecoder[ByteVector] = JsonDecoder[String].emap(ByteVector.fromBase64Descriptive(_))
  given JsonEncoder[ByteVector] = JsonEncoder.instance(bv => Json.fromString(bv.toBase64))

  given SDecoder[ByteVector] = scodec.codecs.bytes.asDecoder
  given SEncoder[ByteVector] = scodec.codecs.bytes.asEncoder

  given Order[ByteVector] = Order.from((x, y) => x.compareTo(y))
  given Show[ByteVector] = Show.show(bytes => s"ByteVector(${bytes.toHex})")

  given BytesCodec[ByteVector] with
    inline def encode(value: ByteVector): Array[Byte] =
      value
        .toArray
    inline def decode(value: Array[Byte]): Validated[BytesDecodingError, ByteVector] =
      Validated
        .valid(ByteVector(value))

  given Base64Codec[ByteVector] with
    inline def encode(value: ByteVector): String =
      JavaBridge.base64Encoder(value.toArray)

    inline def decode(value: String): Validated[Base64DecodingError, ByteVector] =
      Validated
        .catchNonFatal(ByteVector(JavaBridge.base64Decoder(value)))
        .leftMap(x => Base64DecodingError(x.getMessage))
