package com.monadial.waygrid.common.domain.algebra.value.integer

import com.monadial.waygrid.common.domain.algebra.value.ValueRefined
import com.monadial.waygrid.common.domain.instances.IntegerInstances.given
import com.monadial.waygrid.common.domain.instances.RefinedInstances.given
import eu.timepit.refined.api.Validate
import eu.timepit.refined.cats.given
import io.circe.refined.given

abstract class IntegerValueRefined[P](using Validate[Int, P])
    extends ValueRefined[Int, P]
