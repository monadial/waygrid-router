package com.monadial.waygrid.common.domain.value.instant

import cats.Applicative
import com.monadial.waygrid.common.domain.instances.InstantInstances.given
import com.monadial.waygrid.common.domain.value.Value

import java.time.Instant

abstract class InstantValue extends Value[Instant]:
  given IsInstant[Type] = derive[IsInstant]

  inline def fromDouble[F[+_]: Applicative](value: Double): F[Type] =
    GenInstant[F]
      .fromDouble[Type](value)

  inline def now[F[+_]: Applicative]: F[Type] =
    GenInstant[F]
      .now[Type]
