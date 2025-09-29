package com.monadial.waygrid.common.domain.model.routing.dag

import com.monadial.waygrid.common.domain.model.node.Value.ServiceAddress
import com.monadial.waygrid.common.domain.model.routing.Value.{DeliveryStrategy, RetryPolicy}
import com.monadial.waygrid.common.domain.model.routing.dag.Value.NodeId

final case class Node(
  id: NodeId,
  retryPolicy: RetryPolicy,
  deliveryStrategy: DeliveryStrategy,
  address: ServiceAddress,
  label: Option[String] = None
)
