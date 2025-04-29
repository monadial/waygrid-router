package com.monadial.waygrid.common.domain.value.bytes

import com.monadial.waygrid.common.domain.instances.BytesInstances.given
import com.monadial.waygrid.common.domain.value.Value

abstract class BytesValue extends Value[Array[Byte]]:
  given IsBytes[Type] = derive[IsBytes]
