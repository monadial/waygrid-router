package com.monadial.waygrid.common.domain.algebra.value.integer

import com.monadial.waygrid.common.domain.algebra.value.Value
import com.monadial.waygrid.common.domain.instances.IntegerInstances.given

abstract class IntegerValue extends Value[Int]:
  given IsInteger[Type] = derive[IsInteger]
