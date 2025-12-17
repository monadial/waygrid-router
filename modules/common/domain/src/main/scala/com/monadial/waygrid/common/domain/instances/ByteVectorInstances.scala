package com.monadial.waygrid.common.domain.instances

import cats.Show
import cats.data.Validated
import cats.kernel.Order
import com.monadial.waygrid.common.domain.algebra.value.codec.{
  Base64Codec,
  Base64DecodingError,
  BytesCodec,
  BytesDecodingError
}
import io.circe.{ Decoder as JsonDecoder, Encoder as JsonEncoder, Json }
import scodec.bits.ByteVector
import scodec.{ Decoder as SDecoder, Encoder as SEncoder }

object ByteVectorInstances:

  given JsonDecoder[ByteVector] = JsonDecoder[String].emap(ByteVector.fromBase64Descriptive(_))
  given JsonEncoder[ByteVector] = JsonEncoder.instance(bv => Json.fromString(bv.toBase64))

  given SDecoder[ByteVector] = scodec.codecs.bytes.asDecoder
  given SEncoder[ByteVector] = scodec.codecs.bytes.asEncoder

  given Order[ByteVector] = Order.from((x, y) => x.compareTo(y))
  given Show[ByteVector]  = Show.show(bytes => s"ByteVector(${bytes.toHexDumpColorized})")

  given BytesCodec[ByteVector] with
    override inline def decodeFromScalar(value: ByteVector): Validated[BytesDecodingError, ByteVector] =
      Validated
        .valid(value)

    override inline def encodeToScalar(value: ByteVector): ByteVector = value

  given Base64Codec[ByteVector] with
    override inline def encode(value: ByteVector): String =
      JavaBridge.base64Encoder(value)

    override inline def decode(value: String): Validated[Base64DecodingError, ByteVector] =
      Validated
        .catchNonFatal(JavaBridge.base64Decoder(value))
        .leftMap(x => Base64DecodingError(x.getMessage))
