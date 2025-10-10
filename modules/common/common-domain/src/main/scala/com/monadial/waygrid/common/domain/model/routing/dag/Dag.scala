package com.monadial.waygrid.common.domain.model.routing.dag

import com.monadial.waygrid.common.domain.model.routing.Value.RepeatPolicy
import com.monadial.waygrid.common.domain.model.routing.dag.Value.{DagHash, NodeId}

final case class Dag(
  hash: DagHash,
  entry: NodeId,
  repeatPolicy: RepeatPolicy,
  nodes: Map[NodeId, Node],
  edges: List[Edge]
)
