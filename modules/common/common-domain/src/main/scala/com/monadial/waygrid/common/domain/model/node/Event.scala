package com.monadial.waygrid.common.domain.model.node

import com.monadial.waygrid.common.domain.model.event.Event

import io.circe.Codec

sealed trait NodeEvent                                               extends Event
final case class NodeWasRegistered(node: String, name: List[String]) extends NodeEvent derives Codec.AsObject
