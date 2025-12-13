package com.monadial.waygrid.common.domain.algebra.value.string

import com.monadial.waygrid.common.domain.algebra.value.Value
import com.monadial.waygrid.common.domain.instances.StringInstances.given

abstract class StringValue extends Value[String]:
  given IsString[Type] = derive[IsString]
