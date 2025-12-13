package com.monadial.waygrid.common.domain.model.traversal.state

import com.monadial.waygrid.common.domain.algebra.value.integer.IntegerValue
import com.monadial.waygrid.common.domain.algebra.value.long.LongValue

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

  /**
   * Version number for optimistic locking in persistent storage.
   * Incremented on each state update to detect concurrent modifications.
   */
  type StateVersion = StateVersion.Type
  object StateVersion extends LongValue:

    /** Initial version for new state */
    val Initial: StateVersion = StateVersion(0L)

    extension (self: StateVersion)
      /** Increment version for next update */
      def increment: StateVersion =
        StateVersion(self.unwrap + 1)

      /** Check if this version is newer than another */
      def isNewerThan(other: StateVersion): Boolean =
        self.unwrap > other.unwrap
