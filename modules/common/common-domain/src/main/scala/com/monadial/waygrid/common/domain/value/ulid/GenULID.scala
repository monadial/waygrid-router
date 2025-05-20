package com.monadial.waygrid.common.domain.value.ulid

import cats.Applicative
import cats.implicits.*
import wvlet.airframe.ulid.ULID

trait GenULID[F[+_]]:
  def next[A: IsULID]: F[A]
  def fromString[A: IsULID](ulid: String): F[A]

object GenULID:
  def apply[F[+_]](using ev: GenULID[F]): GenULID[F] = ev

  given [F[+_]: Applicative]: GenULID[F] with
    override def next[A: IsULID]: F[A] =
      ULID
        .newULID
        .pure[F]
        .map(IsULID[A].iso.get)

    override def fromString[A: IsULID](ulid: String): F[A] =
      ULID
        .fromString(ulid)
        .pure[F]
        .map(IsULID[A].iso.get)
