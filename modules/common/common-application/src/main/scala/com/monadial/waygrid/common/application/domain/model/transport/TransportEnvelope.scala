package com.monadial.waygrid.common.application.domain.model.transport

import com.monadial.waygrid.common.application.domain.model.transport.Value.TransportEnvelopeId

trait TransportEnvelope:
  val id: TransportEnvelopeId
