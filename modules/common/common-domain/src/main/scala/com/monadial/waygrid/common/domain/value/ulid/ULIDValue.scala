package com.monadial.waygrid.common.domain.value.ulid

import cats.Applicative
import com.monadial.waygrid.common.domain.instances.ULIDInstances.given
import com.monadial.waygrid.common.domain.value.Value
import wvlet.airframe.ulid.ULID

abstract class ULIDValue extends Value[ULID]:
  given IsULID[Type] = derive[IsULID]

  inline def next[F[+_]: Applicative]: F[Type] =
    GenULID[F]
      .next[Type]

  inline def fromString[F[+_]: Applicative](ulid: String): F[Type] =
    GenULID[F]
      .fromString[Type](ulid)

  inline def fromStringUnsafe[F[+_]: Applicative](ulid: String): Type =
    GenULID[F]
      .fromStringUnsafe[Type](ulid)
