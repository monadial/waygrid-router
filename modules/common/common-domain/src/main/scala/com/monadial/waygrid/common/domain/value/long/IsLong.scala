package com.monadial.waygrid.common.domain.value.long

import com.monadial.waygrid.common.domain.algebra.TypeEvidence
import monocle.Iso

trait IsLong[A] extends TypeEvidence[Long, A]

object IsLong:
  def apply[A: IsLong]: IsLong[A] = summon[IsLong[A]]

  given IsLong[Long] with
    def iso: Iso[Long, Long] =
      Iso[Long, Long](identity)(identity)
