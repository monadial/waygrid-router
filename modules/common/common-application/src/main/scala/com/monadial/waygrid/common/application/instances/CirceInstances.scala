package com.monadial.waygrid.common.application.instances

import com.monadial.waygrid.common.domain.instances.StringInstances.given
import com.monadial.waygrid.common.domain.value.codec.{ BytesCodec, BytesDecodingError }

import cats.data.Validated
import cats.syntax.all.*
import io.circe.*
import io.circe.parser.*

object CirceInstances:

  given BytesCodec[Json] with
    def encode(value: Json): Array[Byte] =
      BytesCodec[String]
        .encode(value.noSpaces)

    def decode(value: Array[Byte]): Validated[BytesDecodingError, Json] =
      BytesCodec[String]
        .decode(value)
        .andThen: x =>
          parse(x)
            .leftMap(e => BytesDecodingError(e.getMessage))
            .toValidated
