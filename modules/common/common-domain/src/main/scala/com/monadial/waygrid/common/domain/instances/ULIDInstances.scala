package com.monadial.waygrid.common.domain.instances

import cats.Show
import cats.data.Validated
import cats.kernel.Order
import com.monadial.waygrid.common.domain.value.codec.{Base64Codec, Base64DecodingError, BytesCodec, BytesDecodingError}
import io.circe.{Decoder as JsonDecoder, Encoder as JsonEncoder}
import wvlet.airframe.ulid.ULID

import scala.util.Try

object ULIDInstances:
  given Order[ULID] = (x: ULID, y: ULID) => x.compareTo(y)
  given Show[ULID]  = (t: ULID) => t.toString

  given JsonEncoder[ULID] =
    JsonEncoder
        .encodeString
        .contramap(_.toString)

  given JsonDecoder[ULID] =
    JsonDecoder
        .decodeString
        .emapTry(str => Try(ULID.fromString(str)))

  given BytesCodec[ULID] with
    def encode(value: ULID): Array[Byte] =
      value
        .toBytes

    def decode(value: Array[Byte]): Validated[BytesDecodingError, ULID] =
      Validated
        .catchNonFatal(ULID.fromBytes(value))
        .leftMap(x => BytesDecodingError(x.getMessage))


  given Base64Codec[ULID] with
    def encode(value: ULID): String =
      JavaBridge
        .base64Encoder(BytesCodec[ULID].encode(value))

    def decode(value: String): Validated[Base64DecodingError, ULID] =
      Validated
        .catchNonFatal(JavaBridge.base64Decoder(value))
        .andThen(x => Validated.catchNonFatal(ULID.fromBytes(x)))
        .leftMap(x => Base64DecodingError(x.getMessage))

