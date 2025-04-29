package com.monadial.waygrid.common.domain.instances

import cats.Show
import cats.data.Validated
import cats.kernel.Order
import com.monadial.waygrid.common.domain.value.codec.{Base64Codec, Base64DecodingError, BytesCodec, BytesDecodingError}

import java.util

object BytesInstances:
  given Order[Array[Byte]] = Order.from((x, y) => util.Arrays.compareUnsigned(x, y))
  given Show[Array[Byte]] = Show.show(bytes => s"ArrayByte(${bytes.mkString(", ")})")

  given BytesCodec[Array[Byte]] with
    def encode(value: Array[Byte]): Array[Byte] = value
    def decode(value: Array[Byte]): Validated[BytesDecodingError, Array[Byte]] =
      Validated
          .valid(value)

  given Base64Codec[Array[Byte]] with
    def encode(value: Array[Byte]): String = JavaBridge.base64Encoder(value)
    def decode(value: String): Validated[Base64DecodingError, Array[Byte]] =
      Validated
          .catchNonFatal(JavaBridge.base64Decoder(value))
          .leftMap(x => Base64DecodingError(x.getMessage))

