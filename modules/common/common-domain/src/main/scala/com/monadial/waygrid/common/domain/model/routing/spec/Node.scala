package com.monadial.waygrid.common.domain.model.routing.spec

import com.monadial.waygrid.common.domain.model.node.Value.ServiceAddress
import com.monadial.waygrid.common.domain.model.routing.Value.{DeliveryStrategy, RetryPolicy}

final case class Node(
  address: ServiceAddress,
  retryPolicy: RetryPolicy,
  deliveryStrategy: DeliveryStrategy,
  onSuccess: Option[Node],
  onFailure: Option[Node],
  label: Option[String] = None
)
