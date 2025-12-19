package com.monadial.waygrid.common.domain.algebra.value.instant

import java.time.Instant

import cats.Applicative
import com.monadial.waygrid.common.domain.algebra.value.Value
import com.monadial.waygrid.common.domain.instances.InstantInstances.given

abstract class InstantValue extends Value[Instant]:
  given IsInstant[Type] = derive[IsInstant]

  inline def fromDouble[F[+_]: Applicative](value: Double): F[Type] =
    GenInstant[F]
      .fromDouble[Type](value)

  inline def now[F[+_]: Applicative]: F[Type] =
    GenInstant[F]
      .now[Type]
