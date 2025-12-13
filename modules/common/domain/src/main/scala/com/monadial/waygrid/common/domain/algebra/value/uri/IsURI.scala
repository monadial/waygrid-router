package com.monadial.waygrid.common.domain.algebra.value.uri

import com.monadial.waygrid.common.domain.algebra.TypeEvidence

import org.http4s.Uri

trait IsURI[A] extends TypeEvidence[Uri, A]

object IsURI:
  def apply[A: IsURI]: IsURI[A] = summon[IsURI[A]]

  given IsURI[Uri] with
    inline def iso: monocle.Iso[Uri, Uri] =
      monocle.Iso[Uri, Uri](identity)(identity)
