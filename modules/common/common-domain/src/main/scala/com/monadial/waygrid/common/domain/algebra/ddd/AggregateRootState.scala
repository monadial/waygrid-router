package com.monadial.waygrid.common.domain.algebra.ddd

import com.monadial.waygrid.common.domain.algebra.value.long.LongValue

type Version = Version.Type
object Version extends LongValue:
  def initial: Version = Version(0L)

extension (version: Version)
  def bump: Version = Version(version.unwrap + 1L)

trait AggregateRootState[I, S <: AggregateRootState[I, S]]:
  def version: Version
  def bump: S
