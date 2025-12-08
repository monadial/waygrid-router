package com.monadial.waygrid.common.domain.model.traversal.state

import com.monadial.waygrid.common.domain.algebra.value.integer.IntegerValue

object Value:

  type RemainingNodes = RemainingNodes.Type
  object RemainingNodes extends IntegerValue:

    extension (self: RemainingNodes)
      def decrement: RemainingNodes =
        RemainingNodes(self.unwrap - 1)

  type RetryAttempt = RetryAttempt.Type
  object RetryAttempt extends IntegerValue:

    extension (self: RetryAttempt)
      def increment: RetryAttempt =
        RetryAttempt(self.unwrap + 1)
