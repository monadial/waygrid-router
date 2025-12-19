package com.monadial.waygrid.common.domain.instances

import java.util

import cats.Show
import cats.data.Validated
import cats.kernel.Order
import com.monadial.waygrid.common.domain.algebra.value.codec.{
  Base64Codec,
  Base64DecodingError,
  BytesCodec,
  BytesDecodingError
}
import scodec.bits.ByteVector

object BytesInstances:
  given Order[Array[Byte]] = Order.from((x, y) => util.Arrays.compareUnsigned(x, y))
  given Show[Array[Byte]]  = Show.show(bytes => s"ArrayByte(${bytes.mkString(", ")})")

  given BytesCodec[Array[Byte]] with
    override def decodeFromScalar(value: ByteVector): Validated[BytesDecodingError, Array[Byte]] =
      Validated
        .catchNonFatal(value.toArrayUnsafe)
        .leftMap(x => BytesDecodingError(x.getMessage))

    override def encodeToScalar(value: Array[Byte]): ByteVector =
      ByteVector(value)

  given Base64Codec[Array[Byte]] with
    override def decode(value: String): Validated[Base64DecodingError, Array[Byte]] =
      Validated
        .catchNonFatal(JavaBridge.base64Decoder(value))
        .map(x => x.toArrayUnsafe)
        .leftMap(x => Base64DecodingError(x.getMessage))

    override def encode(value: Array[Byte]): String =
      JavaBridge
        .base64Encoder(BytesCodec[Array[Byte]].encodeToScalar(value))

//    inline def encode(value: Array[Byte]): String = JavaBridge.base64Encoder(value)
//    inline def decode(value: String): Validated[Base64DecodingError, Array[Byte]] =
//      Validated
//        .catchNonFatal(JavaBridge.base64Decoder(value))
//        .leftMap(x => Base64DecodingError(x.getMessage))
