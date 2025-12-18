package com.monadial.waygrid.common.domain.algebra.value.bytes

import com.monadial.waygrid.common.domain.algebra.value.ValueRefined
import com.monadial.waygrid.common.domain.instances.ByteVectorInstances.given
import com.monadial.waygrid.common.domain.instances.RefinedInstances.given
import eu.timepit.refined.api.Validate
import eu.timepit.refined.cats.given
import io.circe.refined.given
import scodec.bits.ByteVector

abstract class BytesValueRefined[P](using Validate[ByteVector, P])
    extends ValueRefined[ByteVector, P]
