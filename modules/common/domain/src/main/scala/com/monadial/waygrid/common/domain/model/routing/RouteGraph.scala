package com.monadial.waygrid.common.domain.model.routing

import com.monadial.waygrid.common.domain.model.routing.Value.RepeatPolicy

final case class RouteGraph(
  entryPoint: RouteNode,
  repeatPolicy: RepeatPolicy
)
