package com.monadial.waygrid.common.domain.value.long

import com.monadial.waygrid.common.domain.instances.LongInstances.given
import com.monadial.waygrid.common.domain.instances.RefinedInstances.given
import com.monadial.waygrid.common.domain.value.ValueRefined
import eu.timepit.refined.api.Validate
import eu.timepit.refined.cats.given
import io.circe.refined.given

abstract class LongValueRefined[P](using Validate[Long, P])
    extends ValueRefined[Long, P]
