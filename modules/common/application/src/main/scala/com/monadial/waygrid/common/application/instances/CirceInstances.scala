package com.monadial.waygrid.common.application.instances

import com.monadial.waygrid.common.domain.instances.StringInstances.given
import cats.data.Validated
import cats.syntax.all.*
import com.monadial.waygrid.common.domain.algebra.value.codec.{ BytesCodec, BytesDecodingError }
import io.circe.*
import io.circe.parser.*
import scodec.bits.ByteVector

object CirceInstances:

  given BytesCodec[Json] with
    override def decodeFromScalar(value: ByteVector): Validated[BytesDecodingError, Json] =
      BytesCodec[String]
        .decodeFromScalar(value)
        .andThen: x =>
          parse(x)
            .leftMap(e => BytesDecodingError(e.getMessage))
            .toValidated

    override def encodeToScalar(value: Json): ByteVector =
      BytesCodec[String]
        .encodeToScalar(value.noSpaces)
