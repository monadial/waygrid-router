package com.monadial.waygrid.common.domain.algebra.messaging.event

import com.monadial.waygrid.common.domain.algebra.value.ulid.ULIDValue

object Value:
  type EventId = EventId.Type
  object EventId extends ULIDValue
