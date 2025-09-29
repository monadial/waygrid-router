package com.monadial.waygrid.common.domain.model.event

import com.monadial.waygrid.common.domain.value.ulid.ULIDValue

object Value:
  type EventId = EventId.Type
  object EventId extends ULIDValue
