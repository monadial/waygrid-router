package com.monadial.waygrid.common.domain.model.routing.dag

import com.monadial.waygrid.common.domain.value.string.StringValue

object Value:
  type NodeId = NodeId.Type
  object NodeId extends StringValue

  type DagHash = DagHash.Type
  object DagHash extends StringValue

  enum EdgeGuard:
    case OnSuccess
    case OnFailure

