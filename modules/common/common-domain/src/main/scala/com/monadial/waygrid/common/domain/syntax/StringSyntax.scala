package com.monadial.waygrid.common.domain.syntax

import cats.Applicative
import cats.implicits.*
import com.monadial.waygrid.common.domain.algebra.value.string.IsString

object StringSyntax:
  extension (string: String)
    def toDomain[A: IsString]: A                         = IsString[A].iso.get(string)
    def toDomainF[F[+_]: Applicative, A: IsString]: F[A] = toDomain[A].pure

  extension [A](a: A)(using IsString[A])
    def mapValue[B: IsString]: B =
      IsString[B].iso.get(IsString[A].iso.reverseGet(a))
    def mapValueF[F[+_]: Applicative, B: IsString]: F[B] = mapValue[B].pure
