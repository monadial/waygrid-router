package com.monadial.waygrid.common.domain.model.routing.dag

import com.monadial.waygrid.common.domain.model.routing.dag.Value.{ EdgeGuard, NodeId }

final case class Edge(
  from: NodeId,
  to: NodeId,
  guard: EdgeGuard
)
