package com.monadial.waygrid.common.domain.value.long

import com.monadial.waygrid.common.domain.instances.LongInstances.given
import com.monadial.waygrid.common.domain.value.Value

abstract class LongValue extends Value[Long]:
  given IsLong[Type] = derive[IsLong]
