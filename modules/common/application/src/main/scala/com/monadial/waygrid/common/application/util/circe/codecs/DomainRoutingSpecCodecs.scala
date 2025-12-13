package com.monadial.waygrid.common.application.util.circe.codecs

import com.monadial.waygrid.common.application.util.circe.codecs.DomainRoutingCodecs.given
import com.monadial.waygrid.common.application.util.circe.DerivationConfiguration.given
import com.monadial.waygrid.common.application.instances.DurationInstances.given
import com.monadial.waygrid.common.domain.model.routing.Value.DeliveryStrategy
import com.monadial.waygrid.common.domain.model.traversal.spec.{Node, Spec}
import io.circe.Codec

object DomainRoutingSpecCodecs:
  given Codec[DeliveryStrategy] = Codec.derived[DeliveryStrategy]
  given Codec[Node] = Codec.derived[Node]
  given Codec[Spec] = Codec.derived[Spec]

