package com.monadial.waygrid.common.application.domain.model.transport

import com.monadial.waygrid.common.domain.value.ulid.ULIDValue

object Value:

  type TransportEnvelopeId = TransportEnvelopeId.Type
  object TransportEnvelopeId extends ULIDValue

