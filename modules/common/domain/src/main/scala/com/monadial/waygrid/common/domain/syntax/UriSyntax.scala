package com.monadial.waygrid.common.domain.syntax

import cats.Applicative
import cats.implicits.*
import com.monadial.waygrid.common.domain.algebra.value.uri.IsURI
import org.http4s.Uri

object UriSyntax:
  extension (uri: Uri)
    def toDomain[A: IsURI]: A                        = IsURI[A].iso.get(uri)
    def toDomainF[F[_]: Applicative, A: IsURI]: F[A] = toDomain[A].pure

  extension [A](a: A)(using IsURI[A])
    def mapValue[B: IsURI]: B                         = IsURI[B].iso.get(IsURI[A].iso.reverseGet(a))
    def mapValueF[F[+_]: Applicative, B: IsURI]: F[B] = mapValue[B].pure
