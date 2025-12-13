package com.monadial.waygrid.common.domain.algebra.value.bytes

import com.monadial.waygrid.common.domain.algebra.value.Value
import com.monadial.waygrid.common.domain.instances.ByteVectorInstances.given
import scodec.bits.ByteVector

abstract class BytesValue extends Value[ByteVector]:
  given IsBytes[Type] = derive[IsBytes]
