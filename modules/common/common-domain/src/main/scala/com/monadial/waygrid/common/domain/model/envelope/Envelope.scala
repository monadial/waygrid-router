package com.monadial.waygrid.common.domain.model.envelope

import com.monadial.waygrid.common.domain.model.message.Message

final case class Envelope[M <: Message](
  message: M
)
