package com.monadial.waygrid.common.domain.instances

import cats.data.Validated
import cats.syntax.all.*
import com.monadial.waygrid.common.domain.algebra.value.codec.{
  Base64Codec,
  Base64DecodingError,
  BytesCodec,
  BytesDecodingError
}
import eu.timepit.refined.api.{ RefType, Validate }
import scodec.bits.ByteVector

object RefinedInstances:
  given [T, P, F[_, _]](using
    underlying: BytesCodec[T],
    validate: Validate[T, P],
    refType: RefType[F]
  ): BytesCodec[F[T, P]] with

    inline override def encodeToScalar(value: F[T, P]): ByteVector =
      underlying.encodeToScalar(refType.unwrap(value))

    inline override def decodeFromScalar(value: ByteVector)
      : Validated[BytesDecodingError, F[T, P]] =
      underlying
        .decodeFromScalar(value)
        .andThen: x =>
          refType
            .refine[P](x)
            .toValidated
            .leftMap(x => BytesDecodingError(x))

  given [T, P, F[_, _]](using
    underlying: Base64Codec[T],
    validate: Validate[T, P],
    refType: RefType[F]
  ): Base64Codec[F[T, P]] with
    inline override def encode(value: F[T, P]): String =
      underlying.encode(refType.unwrap(value))

    inline override def decode(value: String)
      : Validated[Base64DecodingError, F[T, P]] =
      underlying
        .decode(value)
        .andThen: x =>
          refType
            .refine[P](x)
            .toValidated
            .leftMap(x => Base64DecodingError(x))
