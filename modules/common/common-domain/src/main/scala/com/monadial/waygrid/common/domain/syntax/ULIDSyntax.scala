package com.monadial.waygrid.common.domain.syntax

import cats.Applicative
import cats.implicits.*
import com.monadial.waygrid.common.domain.value.ulid.IsULID
import wvlet.airframe.ulid.ULID

object ULIDSyntax:
  extension (ulid: ULID)
    def toDomain[A: IsULID]: A                        = IsULID[A].iso.get(ulid)
    def toDomainF[F[_]: Applicative, A: IsULID]: F[A] = toDomain[A].pure

  extension [A](a: A)(using IsULID[A])
    def mapValue[B: IsULID]: B                         = IsULID[B].iso.get(IsULID[A].iso.reverseGet(a))
    def mapValueF[F[+_]: Applicative, B: IsULID]: F[B] = mapValue[B].pure
