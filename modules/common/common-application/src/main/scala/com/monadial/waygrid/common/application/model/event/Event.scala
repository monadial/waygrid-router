package com.monadial.waygrid.common.application.model.event

import com.monadial.waygrid.common.domain.model.node.Node
import io.circe.Codec

final case class Event(
  id: EventId,
  topic: EventTopic,
  payload: String,
  sender: Node
) derives Codec.AsObject

