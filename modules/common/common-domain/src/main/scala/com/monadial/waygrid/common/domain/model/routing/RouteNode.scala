package com.monadial.waygrid.common.domain.model.routing

import com.monadial.waygrid.common.domain.model.resiliency.RetryPolicy
import com.monadial.waygrid.common.domain.model.routing.Value.DeliveryStrategy
import com.monadial.waygrid.common.domain.value.Address.ServiceAddress

final case class RouteNode(
  address: ServiceAddress,
  params: Map[String, String],
  retryPolicy: RetryPolicy,
  deliveryStrategy: DeliveryStrategy,
  onFailure: Option[RouteNode],
  onSuccess: Option[RouteNode]
)
