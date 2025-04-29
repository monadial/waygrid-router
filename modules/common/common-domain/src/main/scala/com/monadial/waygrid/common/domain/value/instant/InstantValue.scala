package com.monadial.waygrid.common.domain.value.instant

import com.monadial.waygrid.common.domain.instances.InstantInstances.given
import com.monadial.waygrid.common.domain.value.Value

import java.time.Instant

abstract class InstantValue extends Value[Instant]:
  given IsInstant[Type] = derive[IsInstant]
