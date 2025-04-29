package com.monadial.waygrid.common.domain.model.topology

import cats.syntax.all.*
import com.monadial.waygrid.common.domain.model.event.Event

sealed trait TopologyEvent extends Event
final case class NodeJoinRequested() extends TopologyEvent
final case class NodeJoinAccepted() extends TopologyEvent
final case class NodeJoinRejected() extends TopologyEvent
