package com.monadial.waygrid.common.domain.instances

import cats.data.Validated
import com.monadial.waygrid.common.domain.instances.StringInstances.given
import com.monadial.waygrid.common.domain.value.codec.{Base64Codec, Base64DecodingError, BytesCodec, BytesDecodingError}
import io.circe.{Decoder as JsonDecoder, Encoder as JsonEncoder}
import org.http4s.Uri
import scodec.{Attempt, Err, Decoder as SDecoder, Encoder as SEncoder}

object URIInstances:

  given JsonEncoder[Uri] =
    JsonEncoder
      .encodeString
      .contramap(_.renderString)

  given JsonDecoder[Uri] =
    JsonDecoder
      .decodeString
      .emap(raw =>
        Uri.fromString(raw) match
          case Right(uri) => Right(uri)
          case Left(err)  => Left(err.message)
      )

  given SEncoder[Uri] = scodec.codecs.utf8.asEncoder.contramap(_.renderString)
  given SDecoder[Uri] = scodec.codecs.utf8.asDecoder.emap(raw =>
    Uri.fromString(raw) match
      case Right(uri) => Attempt.successful(uri)
      case Left(err) => Attempt.failure(Err(err.message))
  )

  given BytesCodec[Uri] with
    inline override def encode(value: Uri): Array[Byte] =
      BytesCodec[String].encode(value.renderString)

    inline override def decode(value: Array[Byte]): Validated[BytesDecodingError, Uri] =
      BytesCodec[String]
        .decode(value)
        .andThen { raw =>
          Uri.fromString(raw) match
            case Right(uri) => Validated.valid(uri)
            case Left(err)  => Validated.invalid(BytesDecodingError(err.message))
        }

  given Base64Codec[Uri] with
    inline override def encode(value: Uri): String =
      Base64Codec[String]
        .encode(value.renderString)

    inline override def decode(value: String): Validated[Base64DecodingError, Uri] =
      Base64Codec[String]
        .decode(value)
        .andThen { raw =>
          Uri.fromString(raw) match
            case Right(uri) => Validated.valid(uri)
            case Left(err)  => Validated.invalid(Base64DecodingError(err.message))
        }
