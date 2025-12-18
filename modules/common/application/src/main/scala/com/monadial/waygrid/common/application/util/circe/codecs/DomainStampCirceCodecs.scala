package com.monadial.waygrid.common.application.util.circe.codecs

import com.monadial.waygrid.common.application.util.circe.codecs.DomainRoutingDagCirceCodecs.given
import com.monadial.waygrid.common.application.util.circe.codecs.DomainTraversalStateCirceCodecs.given
import com.monadial.waygrid.common.domain.model.envelope.Value.Stamp
import io.circe.{ Decoder, Encoder }

object DomainStampCirceCodecs:
  given Encoder[Stamp] = Encoder.derived[Stamp]
  given Decoder[Stamp] = Decoder.derived[Stamp]
