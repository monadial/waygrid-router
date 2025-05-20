package com.monadial.waygrid.common.domain.model.topology

import com.monadial.waygrid.common.domain.model.event.Event

import io.circe.Codec

sealed trait TopologyEvent                      extends Event
final case class NodeJoinRequested()            extends TopologyEvent derives Codec.AsObject
final case class NodeJoinAccepted()             extends TopologyEvent derives Codec.AsObject
final case class NodeJoinRejected(test: String) extends TopologyEvent derives Codec.AsObject
