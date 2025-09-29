package com.monadial.waygrid.common.domain.cryptography.hashing

import cats.Applicative
import cats.effect.kernel.Resource
import cats.implicits.*
import com.monadial.waygrid.common.domain.model.cryptography.hashing.Value.LongHash
import com.monadial.waygrid.common.domain.value.bytes.IsBytes
import com.monadial.waygrid.common.domain.value.long.IsLong
import com.monadial.waygrid.common.domain.value.string.IsString
import com.monadial.waygrid.common.domain.value.uri.IsURI
import net.openhft.hashing.LongHashFunction

trait Hasher[F[+_], +V]:
  def hashChars[I: IsString](input: I): F[V]
  def hashBytes[I: IsBytes](input: I): F[V]
  def hashLong[I: IsLong](input: I): F[V]
  def hashUri[I: IsURI](input: I): F[V]

object Hasher:
  def xxh3[F[+_]: Applicative]: Resource[F, Hasher[F, LongHash]] =
    for
      hasher <- Resource.pure(LongHashFunction.xx3())
    yield new Hasher[F, LongHash]:
      override def hashChars[I: IsString](input: I): F[LongHash] =
        hasher
          .hashChars(IsString[I].iso.reverseGet(input))
          .pure[F]
          .map(LongHash(_))

      override def hashBytes[I: IsBytes](input: I): F[LongHash] =
        hasher
          .hashBytes(IsBytes[I].iso.reverseGet(input).toByteBuffer)
          .pure[F]
          .map(LongHash(_))

      override def hashLong[I: IsLong](input: I): F[LongHash] =
        hasher
          .hashLong(IsLong[I].iso.reverseGet(input))
          .pure[F]
          .map(LongHash(_))

      override def hashUri[I: IsURI](input: I): F[LongHash] =
        hasher
          .hashChars(IsURI[I].iso.reverseGet(input).renderString)
          .pure[F]
          .map(LongHash(_))
