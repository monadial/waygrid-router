package com.monadial.waygrid.common.domain.model.traversal.dag

import com.monadial.waygrid.common.domain.algebra.value.string.StringValue

object Value:
  type NodeId = NodeId.Type
  object NodeId extends StringValue

  type DagHash = DagHash.Type
  object DagHash extends StringValue

  enum EdgeGuard:
    case OnSuccess
    case OnFailure

