package com.monadial.waygrid.common.domain.model.traversal.spec

import com.monadial.waygrid.common.domain.model.routing.Value.RepeatPolicy

final case class Spec(
  entryPoint: Node,
  repeatPolicy: RepeatPolicy,
)
