package com.monadial.waygrid.common.domain.model.routing

import com.monadial.waygrid.common.domain.model.event.Event

object Event:
  sealed trait RoutingEvent extends Event

  final case class RoutingWasRequested() extends RoutingEvent
  final case class MessageWasRouted() extends RoutingEvent
  final case class RoutingSucceeded() extends RoutingEvent
  final case class RoutingFailed() extends RoutingEvent


