package com.monadial.waygrid.common.domain.instances

import scala.util.{ Failure, Success, Try }

import cats.Show
import cats.data.Validated
import cats.kernel.Order
import com.monadial.waygrid.common.domain.algebra.value.codec.{
  Base64Codec,
  Base64DecodingError,
  BytesCodec,
  BytesDecodingError
}
import io.circe.{ Decoder as JsonDecoder, Encoder as JsonEncoder }
import scodec.bits.ByteVector
import scodec.{ Attempt, Decoder as SDecoder, Encoder as SEncoder, Err }
import wvlet.airframe.ulid.ULID

object ULIDInstances:
  given Order[ULID] = (x: ULID, y: ULID) => x.compareTo(y)
  given Show[ULID]  = (t: ULID) => t.toString

  // ULID is 16 bytes fixed
  given SEncoder[ULID] = scodec.codecs.bytes(16).asEncoder.contramap(x => ByteVector(x.toBytes))
  given SDecoder[ULID] = scodec.codecs.bytes(16).asDecoder.emap: bytes =>
      Try(ULID.fromBytes(bytes.toArray)) match
        case Success(ulid) => Attempt.successful(ulid)
        case Failure(ex)   => Attempt.failure(Err(ex.getMessage))

  given JsonEncoder[ULID] =
    JsonEncoder
      .encodeString
      .contramap(_.toString)

  given JsonDecoder[ULID] =
    JsonDecoder
      .decodeString
      .emapTry(str => Try(ULID.fromString(str)))

  // ULID is 16 bytes fixed (128-bit)
  given BytesCodec[ULID] with
    inline def encodeToScalar(value: ULID): ByteVector =
      ByteVector(value.toBytes)

    inline def decodeFromScalar(value: ByteVector): Validated[BytesDecodingError, ULID] =
      Validated
        .catchNonFatal(ULID.fromBytes(value.toArray))
        .leftMap(ex => BytesDecodingError(ex.getMessage))

  given Base64Codec[ULID] with
    inline def encode(value: ULID): String =
      JavaBridge
        .base64Encoder(BytesCodec[ULID].encodeToScalar(value))

    inline def decode(value: String): Validated[Base64DecodingError, ULID] =
      Validated
        .catchNonFatal(JavaBridge.base64Decoder(value))
        .andThen(BytesCodec[ULID].decodeFromScalar(_))
        .leftMap(x => Base64DecodingError(x.getMessage))
