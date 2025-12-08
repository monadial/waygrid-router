package com.monadial.waygrid.common.domain.model.traversal.dag

import com.monadial.waygrid.common.domain.model.resiliency.RetryPolicy
import com.monadial.waygrid.common.domain.model.routing.Value.DeliveryStrategy
import com.monadial.waygrid.common.domain.model.traversal.dag.Value.NodeId
import com.monadial.waygrid.common.domain.value.Address.ServiceAddress

final case class Node(
  id: NodeId,
  label: Option[String],
  retryPolicy: RetryPolicy,
  deliveryStrategy: DeliveryStrategy,
  address: ServiceAddress
)
