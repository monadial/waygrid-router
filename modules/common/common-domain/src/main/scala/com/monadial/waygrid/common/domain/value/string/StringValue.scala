package com.monadial.waygrid.common.domain.value.string

import com.monadial.waygrid.common.domain.instances.StringInstances.given
import com.monadial.waygrid.common.domain.value.Value

abstract class StringValue extends Value[String]:
  given IsString[Type] = derive[IsString]
