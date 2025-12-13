package com.monadial.waygrid.common.domain.algebra.cryptography

import com.monadial.waygrid.common.domain.algebra.value.bytes.IsBytes
import com.monadial.waygrid.common.domain.algebra.value.long.IsLong
import com.monadial.waygrid.common.domain.algebra.value.string.IsString
import com.monadial.waygrid.common.domain.algebra.value.uri.IsURI

trait Hasher[F[+_], +V]:
  def hashChars[I: IsString](input: I): F[V]
  def hashBytes[I: IsBytes](input: I): F[V]
  def hashLong[I: IsLong](input: I): F[V]
  def hashUri[I: IsURI](input: I): F[V]

