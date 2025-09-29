package com.monadial.waygrid.common.domain.model.routing

import com.monadial.waygrid.common.domain.model.routing.Value.RouteId
import com.monadial.waygrid.common.domain.model.routing.dag.Dag

final case class Route(
  id: RouteId,
  dag: Dag
)
