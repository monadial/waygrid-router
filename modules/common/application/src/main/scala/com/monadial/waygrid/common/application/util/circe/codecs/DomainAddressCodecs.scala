package com.monadial.waygrid.common.application.util.circe.codecs

import com.monadial.waygrid.common.domain.value.Address.{ Endpoint, EndpointDirection }
import io.circe.Codec

object DomainAddressCodecs:
  given Codec[EndpointDirection] = Codec.derived[EndpointDirection]
  given Codec[Endpoint]          = Codec.derived[Endpoint]
