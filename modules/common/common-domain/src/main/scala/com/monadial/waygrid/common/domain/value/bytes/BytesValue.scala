package com.monadial.waygrid.common.domain.value.bytes

import com.monadial.waygrid.common.domain.instances.ByteVectorInstances.given
import com.monadial.waygrid.common.domain.value.Value
import scodec.bits.ByteVector

abstract class BytesValue extends Value[ByteVector]:
  given IsBytes[Type] = derive[IsBytes]
