package com.monadial.waygrid.common.domain.model.envelope

import com.monadial.waygrid.common.domain.model.node.Node

object Value:
  enum Stamp:
    case ReceivedBy(node: Node)
