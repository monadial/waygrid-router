package com.monadial.waygrid.common.application.domain.model.envelope

import com.monadial.waygrid.common.domain.value.ulid.ULIDValue

object Value:
  type EnvelopeId = EnvelopeId.Type
  object EnvelopeId extends ULIDValue

