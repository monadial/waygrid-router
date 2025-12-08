package com.monadial.waygrid.common.application.util.circe.codecs

import com.monadial.waygrid.common.application.util.circe.codecs.DomainRoutingDagCodecs.given
import com.monadial.waygrid.common.application.util.circe.codecs.DomainTraversalStateCodecs.given
import com.monadial.waygrid.common.domain.model.envelope.Value.Stamp
import io.circe.{ Decoder, Encoder }

object DomainStampCodecs:
  given Encoder[Stamp] = Encoder.derived[Stamp]
  given Decoder[Stamp] = Decoder.derived[Stamp]
