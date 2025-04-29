package com.monadial.waygrid.common.domain.model.node

import com.monadial.waygrid.common.domain.model.event.Event

sealed trait NodeEvent extends Event
final case class NodeWasRegistered(node: String) extends NodeEvent
