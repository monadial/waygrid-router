package com.monadial.waygrid.common.domain.model.routing

import com.monadial.waygrid.common.domain.model.node.Value.ServiceAddress
import com.monadial.waygrid.common.domain.model.routing.Value.{ DeliveryStrategy, RetryPolicy }

final case class RouteNode(
  address: ServiceAddress,
  params: Map[String, String],
  retryPolicy: RetryPolicy,
  deliveryStrategy: DeliveryStrategy,
  onFailure: Option[RouteNode],
  onSuccess: Option[RouteNode]
)
