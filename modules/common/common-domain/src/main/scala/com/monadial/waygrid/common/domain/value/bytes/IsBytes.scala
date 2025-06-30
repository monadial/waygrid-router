package com.monadial.waygrid.common.domain.value.bytes

import com.monadial.waygrid.common.domain.algebra.TypeEvidence
import scodec.bits.ByteVector

trait IsBytes[A] extends TypeEvidence[ByteVector, A]

object IsBytes:
  def apply[A: IsBytes]: IsBytes[A] = summon[IsBytes[A]]

  given IsBytes[ByteVector] with
    inline def iso: monocle.Iso[ByteVector, ByteVector] =
      monocle.Iso[ByteVector, ByteVector](identity)(identity)
