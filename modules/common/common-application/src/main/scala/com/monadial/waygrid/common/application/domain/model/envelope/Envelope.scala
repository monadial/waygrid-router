package com.monadial.waygrid.common.application.domain.model.envelope

import com.monadial.waygrid.common.domain.model.message.Message
import com.monadial.waygrid.common.domain.model.node.Node

final case class Envelope[M <: Message](
  id: EnvelopeId,
  message: M,
  sender: Node
)
