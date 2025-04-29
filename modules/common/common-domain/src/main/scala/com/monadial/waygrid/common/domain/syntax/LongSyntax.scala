package com.monadial.waygrid.common.domain.syntax

import cats.Applicative
import cats.implicits.*
import com.monadial.waygrid.common.domain.value.long.IsLong

object LongSyntax:
  extension (long: Long)
    def toDomain[A: IsLong]: A = IsLong[A].iso.get(long)
    def toDomainF[F[+_]: Applicative, A: IsLong]: F[A] = toDomain[A].pure

  extension [A](a: A)(using IsLong[A])
    def mapValue[B: IsLong]: B = IsLong[B].iso.get(IsLong[A].iso.reverseGet(a))
    def mapValueF[F[+_] : Applicative, B: IsLong]: F[B] = mapValue[B].pure
