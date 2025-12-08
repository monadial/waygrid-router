package com.monadial.waygrid.common.domain.model.node

import com.monadial.waygrid.common.domain.algebra.messaging.event.Event
import com.monadial.waygrid.common.domain.algebra.messaging.message.Value.MessageId
import io.circe.Codec

object Event:
  sealed trait NodeEvent extends Event
  final case class NodeWasRegistered(
    id: MessageId
  ) extends NodeEvent derives Codec.AsObject
