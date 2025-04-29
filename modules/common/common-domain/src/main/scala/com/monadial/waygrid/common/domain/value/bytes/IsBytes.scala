package com.monadial.waygrid.common.domain.value.bytes

import com.monadial.waygrid.common.domain.algebra.TypeEvidence

trait IsBytes[A] extends TypeEvidence[Array[Byte], A]

object IsBytes:
  def apply[A: IsBytes]: IsBytes[A] = summon[IsBytes[A]]

  given IsBytes[Array[Byte]] with
    def iso: monocle.Iso[Array[Byte], Array[Byte]] =
      monocle.Iso[Array[Byte], Array[Byte]](identity)(identity)
