package com.monadial.waygrid.common.domain.value.ulid

import com.monadial.waygrid.common.domain.algebra.TypeEvidence
import monocle.Iso
import wvlet.airframe.ulid.ULID

trait IsULID[A] extends TypeEvidence[ULID, A]

object IsULID:
  def apply[A: IsULID]: IsULID[A] = summon[IsULID[A]]

  given IsULID[ULID] with
    inline def iso: Iso[ULID, ULID] =
      Iso[ULID, ULID](identity)(identity)
