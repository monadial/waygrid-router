package com.monadial.waygrid.common.domain.model.node

import com.monadial.waygrid.common.domain.model.event.Event
import io.circe.Codec

object Event:
  sealed trait NodeEvent               extends Event
  final case class NodeWasRegistered() extends NodeEvent derives Codec.AsObject
