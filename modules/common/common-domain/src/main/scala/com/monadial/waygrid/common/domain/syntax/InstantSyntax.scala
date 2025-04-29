package com.monadial.waygrid.common.domain.syntax

import cats.Applicative
import cats.implicits.*
import com.monadial.waygrid.common.domain.value.instant.IsInstant

import java.time.Instant

object InstantSyntax:
  extension (instant: Instant)
    def toDomain[A: IsInstant]: A = IsInstant[A].iso.get(instant)
    def toDomainF[F[+_]: Applicative, A: IsInstant]: F[A] = toDomain[A].pure

  extension [A](a: A)(using IsInstant[A])
    def mapValue[B: IsInstant]: B = IsInstant[B].iso.get(IsInstant[A].iso.reverseGet(a))
    def mapValueF[F[+_] : Applicative, B: IsInstant]: F[B] = mapValue[B].pure
