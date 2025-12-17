package com.monadial.waygrid.common.application.util.circe.codecs

import com.monadial.waygrid.common.application.instances.DurationInstances.given
import com.monadial.waygrid.common.application.util.circe.DerivationConfiguration.given
import com.monadial.waygrid.common.domain.model.resiliency.RetryPolicy
import com.monadial.waygrid.common.domain.model.routing.Value.{ DeliveryStrategy, RepeatPolicy }
import io.circe.Codec

object DomainRoutingCodecs:
  given Codec[DeliveryStrategy] = Codec.derived[DeliveryStrategy]
  given Codec[RetryPolicy]      = Codec.derived[RetryPolicy]
  given Codec[RepeatPolicy]     = Codec.derived[RepeatPolicy]
