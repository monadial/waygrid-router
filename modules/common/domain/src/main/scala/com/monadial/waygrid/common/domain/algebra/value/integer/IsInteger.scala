package com.monadial.waygrid.common.domain.algebra.value.integer

import com.monadial.waygrid.common.domain.algebra.TypeEvidence

trait IsInteger[A] extends TypeEvidence[Int, A]

object IsInteger:
  def apply[A: IsInteger]: IsInteger[A] = summon[IsInteger[A]]

  given IsInteger[Int] with
    inline def iso: monocle.Iso[Int, Int] =
      monocle.Iso[Int, Int](identity)(identity)

