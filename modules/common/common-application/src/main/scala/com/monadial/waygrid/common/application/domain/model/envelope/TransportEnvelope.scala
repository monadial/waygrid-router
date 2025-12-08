package com.monadial.waygrid.common.application.domain.model.envelope

import com.monadial.waygrid.common.application.domain.model.envelope.Value.MessageContent
import com.monadial.waygrid.common.domain.model.envelope.Value.EnvelopeId
import com.monadial.waygrid.common.domain.model.envelope.{Envelope, EnvelopeStamps}
import com.monadial.waygrid.common.domain.value.Address.{Endpoint, NodeAddress}

final case class TransportEnvelope(
  id: EnvelopeId,
  sender: NodeAddress,
  endpoint: Endpoint,
  message: MessageContent,
  stamps: EnvelopeStamps
) extends Envelope[MessageContent]
