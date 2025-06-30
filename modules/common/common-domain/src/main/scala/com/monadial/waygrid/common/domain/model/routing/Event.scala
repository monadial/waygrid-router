package com.monadial.waygrid.common.domain.model.routing

import com.monadial.waygrid.common.domain.model.event.Event

object Event:
  sealed trait RoutingEvent extends Event

  final case class RoutingRequested(
    route: Route
  ) extends RoutingEvent
