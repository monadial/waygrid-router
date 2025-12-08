package com.monadial.waygrid.common.domain.model.traversal.spec

import com.monadial.waygrid.common.domain.model.resiliency.RetryPolicy
import com.monadial.waygrid.common.domain.model.routing.Value.DeliveryStrategy
import com.monadial.waygrid.common.domain.value.Address.ServiceAddress

final case class Node(
  address: ServiceAddress,
  retryPolicy: RetryPolicy,
  deliveryStrategy: DeliveryStrategy,
  onSuccess: Option[Node],
  onFailure: Option[Node],
  label: Option[String] = None
)
