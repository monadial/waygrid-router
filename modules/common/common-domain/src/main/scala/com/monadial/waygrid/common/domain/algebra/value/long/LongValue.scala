package com.monadial.waygrid.common.domain.algebra.value.long

import com.monadial.waygrid.common.domain.algebra.value.Value
import com.monadial.waygrid.common.domain.instances.LongInstances.given

abstract class LongValue extends Value[Long]:
  given IsLong[Type] = derive[IsLong]
