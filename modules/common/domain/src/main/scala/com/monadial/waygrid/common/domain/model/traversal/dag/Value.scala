package com.monadial.waygrid.common.domain.model.traversal.dag

import com.monadial.waygrid.common.domain.algebra.value.string.StringValue
import com.monadial.waygrid.common.domain.algebra.value.ulid.ULIDValue
import com.monadial.waygrid.common.domain.model.traversal.condition.Condition

object Value:
  type NodeId = NodeId.Type
  object NodeId extends StringValue

  type DagHash = DagHash.Type
  object DagHash extends StringValue

  type ForkId = ForkId.Type
  object ForkId extends ULIDValue

  type BranchId = BranchId.Type
  object BranchId extends ULIDValue

  /**
   * Edge guards determine when an edge should be traversed based on the
   * outcome of the source node.
   */
  enum EdgeGuard:
    /** Execute when upstream node succeeds */
    case OnSuccess
    /** Execute when upstream node fails (after retries exhausted) */
    case OnFailure
    /** Execute regardless of success or failure */
    case Always
    /** Execute on first result (for OR join semantics) */
    case OnAny
    /** Execute when upstream node times out */
    case OnTimeout
    /** Execute when predicate evaluates to true */
    case Conditional(condition: Condition)
