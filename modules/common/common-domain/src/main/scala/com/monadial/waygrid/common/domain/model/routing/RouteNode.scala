package com.monadial.waygrid.common.domain.model.routing

import com.monadial.waygrid.common.domain.model.Waygrid.Address
import com.monadial.waygrid.common.domain.model.routing.Value.{DeliveryStrategy, RetryPolicy}

final case class RouteNode(
  address: Address,
//  params: Map[String, String],
  retryPolicy: RetryPolicy,
  deliveryStrategy: DeliveryStrategy,
  onFailure: Option[RouteNode],
  onSuccess: Option[RouteNode]
)
