package com.monadial.waygrid.common.domain.instances

import cats.Show
import cats.data.Validated
import cats.kernel.Order
import com.monadial.waygrid.common.domain.algebra.value.codec.{Base64Codec, Base64DecodingError, BytesCodec, BytesDecodingError}
import com.monadial.waygrid.common.domain.instances.StringInstances.given
import io.circe.{Decoder as JsonDecoder, Encoder as JsonEncoder}
import scodec.bits.ByteVector
import scodec.{Attempt, Err, Decoder as SDecoder, Encoder as SEncoder}
import wvlet.airframe.ulid.ULID

import scala.util.{Failure, Success, Try}

object ULIDInstances:
  given Order[ULID] = (x: ULID, y: ULID) => x.compareTo(y)
  given Show[ULID]  = (t: ULID) => t.toString

  given SEncoder[ULID] = scodec.codecs.bytes.asEncoder.contramap(x => ByteVector(x.toBytes))
  given SDecoder[ULID] = scodec.codecs.bytes.asDecoder.emap: bytes =>
      Try(ULID.fromBytes(bytes.toArray)) match
        case Success(ulid) =>
          Attempt.successful(ulid)
        case Failure(ex) =>
          Attempt.failure(Err(ex.getMessage))

  given JsonEncoder[ULID] =
    JsonEncoder
      .encodeString
      .contramap(_.toString)

  given JsonDecoder[ULID] =
    JsonDecoder
      .decodeString
      .emapTry(str => Try(ULID.fromString(str)))

  given BytesCodec[ULID] with
    inline def encodeToScalar(value: ULID): ByteVector =
      BytesCodec[String]
        .encodeToScalar(value.toString)

    inline def decodeFromScalar(value: ByteVector): Validated[BytesDecodingError, ULID] =
      BytesCodec[String]
        .decodeFromScalar(value)
        .map(ULID.fromString)

  given Base64Codec[ULID] with
    inline def encode(value: ULID): String =
      JavaBridge
        .base64Encoder(BytesCodec[ULID].encodeToScalar(value))

    inline def decode(value: String): Validated[Base64DecodingError, ULID] =
      Validated
        .catchNonFatal(JavaBridge.base64Decoder(value))
        .andThen(x => Validated.catchNonFatal(ULID.fromBytes(x.toArrayUnsafe)))
        .leftMap(x => Base64DecodingError(x.getMessage))
