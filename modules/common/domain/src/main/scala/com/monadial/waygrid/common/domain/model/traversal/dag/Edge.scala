package com.monadial.waygrid.common.domain.model.traversal.dag

import com.monadial.waygrid.common.domain.model.traversal.dag.Value.{ EdgeGuard, NodeId }

final case class Edge(
  from: NodeId,
  to: NodeId,
  guard: EdgeGuard
)
