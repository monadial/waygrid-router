package com.monadial.waygrid.common.domain.value.instant

import com.monadial.waygrid.common.domain.algebra.TypeEvidence
import monocle.Iso

import java.time.Instant

trait IsInstant[A] extends TypeEvidence[Instant, A]

object IsInstant:
  def apply[A: IsInstant]: IsInstant[A] = summon[IsInstant[A]]

  given IsInstant[Instant] with
    inline def iso: Iso[Instant, Instant] =
      Iso[Instant, Instant](identity)(identity)
