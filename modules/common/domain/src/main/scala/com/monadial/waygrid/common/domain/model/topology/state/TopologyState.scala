package com.monadial.waygrid.common.domain.model.topology.state

import com.monadial.waygrid.common.domain.model.node.Node
import com.monadial.waygrid.common.domain.value.Address.ServiceAddress

final case class TopologyState(
  map: Map[ServiceAddress, List[Node]]
)

object TopologyState:

  def empty: TopologyState = TopologyState(Map.empty)
