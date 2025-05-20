package com.monadial.waygrid.common.domain.syntax

import cats.Applicative
import cats.implicits.*
import com.monadial.waygrid.common.domain.value.bytes.IsBytes

object BytesSyntax:
  extension (bytes: Array[Byte])
    def toDomain[A: IsBytes]: A                         = IsBytes[A].iso.get(bytes)
    def toDomainF[F[+_]: Applicative, A: IsBytes]: F[A] = toDomain[A].pure

  extension [A](a: A)(using IsBytes[A])
    def mapValue[B: IsBytes]: B =
      IsBytes[B].iso.get(IsBytes[A].iso.reverseGet(a))
    def mapValueF[F[+_]: Applicative, B: IsBytes]: F[B] = mapValue[B].pure
