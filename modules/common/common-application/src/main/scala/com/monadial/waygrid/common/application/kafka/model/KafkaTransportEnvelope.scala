package com.monadial.waygrid.common.application.kafka.model

import com.monadial.waygrid.common.application.domain.model.transport.TransportEnvelope
import com.monadial.waygrid.common.application.domain.model.transport.Value.TransportEnvelopeId
import io.circe.Codec

final case class KafkaTransportEnvelope(
  id: TransportEnvelopeId
) extends TransportEnvelope derives Codec.AsObject
