package com.monadial.waygrid.common.domain.model.routing

import com.monadial.waygrid.common.domain.model.routing.Value.RouteId

final case class Route(
  id: RouteId,
  graph: RouteGraph
)
