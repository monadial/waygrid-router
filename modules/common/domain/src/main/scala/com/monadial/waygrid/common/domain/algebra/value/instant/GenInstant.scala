package com.monadial.waygrid.common.domain.algebra.value.instant

import java.time.Instant

import cats.Applicative
import cats.implicits.*

trait GenInstant[F[+_]]:
  def fromDouble[A: IsInstant](value: Double): F[A]
  def now[A: IsInstant]: F[A]

object GenInstant:
  def apply[F[+_]](using ev: GenInstant[F]): GenInstant[F] = ev

  given [F[+_]: Applicative]: GenInstant[F] with
    override def fromDouble[A: IsInstant](value: Double): F[A] =
      Instant
        .ofEpochMilli(value.toLong)
        .pure[F]
        .map(IsInstant[A].iso.get)

    override def now[A: IsInstant]: F[A] =
      Instant
        .now()
        .pure[F]
        .map(IsInstant[A].iso.get)
