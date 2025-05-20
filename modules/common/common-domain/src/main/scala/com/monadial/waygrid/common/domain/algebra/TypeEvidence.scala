package com.monadial.waygrid.common.domain.algebra

import cats.Functor
import cats.syntax.functor.*
import monocle.Iso

trait TypeEvidence[A, B]:
  def iso: Iso[A, B] // isomorphism: A ~> B

  extension [F[_]](fa: F[A])
    def mapIso(using F: Functor[F]): F[B] =
      fa
        .map(iso.get)

  extension [F[_]](fb: F[B])
    def reverseMapIso(using F: Functor[F]): F[A] =
      fb
        .map(iso.reverseGet)

object TypeEvidence:
  def apply[A, B](using ev: TypeEvidence[A, B]): TypeEvidence[A, B] = ev
