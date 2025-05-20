package com.monadial.waygrid.common.domain.value.string

import com.monadial.waygrid.common.domain.instances.RefinedInstances.given
import com.monadial.waygrid.common.domain.instances.StringInstances.given
import com.monadial.waygrid.common.domain.value.ValueRefined
import eu.timepit.refined.*
import eu.timepit.refined.api.Validate
import eu.timepit.refined.cats.given
import io.circe.refined.given

abstract class StringValueRefined[P](using Validate[String, P])
    extends ValueRefined[String, P]
