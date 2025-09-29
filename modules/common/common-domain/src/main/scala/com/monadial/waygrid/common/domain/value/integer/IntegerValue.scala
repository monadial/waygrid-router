package com.monadial.waygrid.common.domain.value.integer

import com.monadial.waygrid.common.domain.instances.IntegerInstances.given
import com.monadial.waygrid.common.domain.value.Value

abstract class IntegerValue extends Value[Int]:
  given IsInteger[Type] = derive[IsInteger]
