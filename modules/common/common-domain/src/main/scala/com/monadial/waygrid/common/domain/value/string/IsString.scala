package com.monadial.waygrid.common.domain.value.string

import com.monadial.waygrid.common.domain.algebra.TypeEvidence
import monocle.Iso

trait IsString[A] extends TypeEvidence[String, A]

object IsString:
  def apply[A: IsString]: IsString[A] = summon[IsString[A]]

  given IsString[String] with
    def iso: Iso[String, String] =
      Iso[String, String](identity)(identity)
