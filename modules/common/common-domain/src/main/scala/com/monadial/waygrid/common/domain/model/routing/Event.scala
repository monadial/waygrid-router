package com.monadial.waygrid.common.domain.model.routing

import com.monadial.waygrid.common.domain.model.event.Event
import com.monadial.waygrid.common.domain.model.node.Value.{ NodeAddress, ServiceAddress }
import com.monadial.waygrid.common.domain.model.routing.Value.TraversalId
import com.monadial.waygrid.common.domain.model.routing.dag.Dag

object Event:
  sealed trait RoutingEvent extends Event

  final case class RoutingWasRequested(
    traversalId: TraversalId,
    dag: Dag
  ) extends RoutingEvent

  final case class MessageWasRouted(
    traversalId: TraversalId,
    nextHop: ServiceAddress,
    dag: Dag
  ) extends RoutingEvent

  final case class RoutingSucceeded(
    traversalId: TraversalId,
    onHop: NodeAddress
  ) extends RoutingEvent

  final case class RoutingFailed(
    traversalId: TraversalId,
    onHop: NodeAddress
  ) extends RoutingEvent
