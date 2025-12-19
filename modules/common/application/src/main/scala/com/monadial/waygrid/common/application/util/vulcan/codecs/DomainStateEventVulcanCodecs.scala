package com.monadial.waygrid.common.application.util.vulcan.codecs

import cats.syntax.all.*
import com.monadial.waygrid.common.application.util.vulcan.VulcanUtils.given
import com.monadial.waygrid.common.application.util.vulcan.codecs.DomainAddressVulcanCodecs.given
import com.monadial.waygrid.common.application.util.vulcan.codecs.DomainForkJoinVulcanCodecs.given
import com.monadial.waygrid.common.application.util.vulcan.codecs.DomainPrimitivesVulcanCodecs.given
import com.monadial.waygrid.common.application.util.vulcan.codecs.DomainVectorClockVulcanCodecs.given
import com.monadial.waygrid.common.domain.model.traversal.state.Event
import com.monadial.waygrid.common.domain.model.traversal.state.Event.StateEvent
import vulcan.Codec

/**
 * Vulcan Avro codecs for StateEvent sealed trait and all its variants.
 *
 * StateEvent is a large sealed trait with 20 variants for event sourcing.
 * Each variant is encoded as an Avro record and combined in a union.
 *
 * Import `DomainStateEventVulcanCodecs.given` to bring these codecs into scope.
 */
object DomainStateEventVulcanCodecs:

  // ---------------------------------------------------------------------------
  // Linear Traversal Events
  // ---------------------------------------------------------------------------

  private given Codec[Event.TraversalStarted] = Codec.record(
    name = "TraversalStarted",
    namespace = "com.monadial.waygrid.common.domain.model.traversal.state"
  ) { field =>
    (
      field("node", _.node),
      field("actor", _.actor),
      field("vectorClock", _.vectorClock)
    ).mapN(Event.TraversalStarted.apply)
  }

  private given Codec[Event.TraversalScheduled] = Codec.record(
    name = "TraversalScheduled",
    namespace = "com.monadial.waygrid.common.domain.model.traversal.state"
  ) { field =>
    (
      field("node", _.node),
      field("actor", _.actor),
      field("scheduledAt", _.scheduledAt),
      field("vectorClock", _.vectorClock)
    ).mapN(Event.TraversalScheduled.apply)
  }

  private given Codec[Event.TraversalResumed] = Codec.record(
    name = "TraversalResumed",
    namespace = "com.monadial.waygrid.common.domain.model.traversal.state"
  ) { field =>
    (
      field("node", _.node),
      field("actor", _.actor),
      field("vectorClock", _.vectorClock)
    ).mapN(Event.TraversalResumed.apply)
  }

  private given Codec[Event.TraversalCompleted] = Codec.record(
    name = "TraversalCompleted",
    namespace = "com.monadial.waygrid.common.domain.model.traversal.state"
  ) { field =>
    (
      field("node", _.node),
      field("actor", _.actor),
      field("vectorClock", _.vectorClock)
    ).mapN(Event.TraversalCompleted.apply)
  }

  private given Codec[Event.TraversalFailed] = Codec.record(
    name = "TraversalFailed",
    namespace = "com.monadial.waygrid.common.domain.model.traversal.state"
  ) { field =>
    (
      field("node", _.node),
      field("actor", _.actor),
      field("vectorClock", _.vectorClock),
      field("reason", _.reason)
    ).mapN(Event.TraversalFailed.apply)
  }

  private given Codec[Event.TraversalCanceled] = Codec.record(
    name = "TraversalCanceled",
    namespace = "com.monadial.waygrid.common.domain.model.traversal.state"
  ) { field =>
    (
      field("node", _.node),
      field("actor", _.actor),
      field("vectorClock", _.vectorClock)
    ).mapN(Event.TraversalCanceled.apply)
  }

  private given Codec[Event.NodeTraversalRetried] = Codec.record(
    name = "NodeTraversalRetried",
    namespace = "com.monadial.waygrid.common.domain.model.traversal.state"
  ) { field =>
    (
      field("node", _.node),
      field("actor", _.actor),
      field("attempt", _.attempt),
      field("vectorClock", _.vectorClock)
    ).mapN(Event.NodeTraversalRetried.apply)
  }

  private given Codec[Event.NodeTraversalSucceeded] = Codec.record(
    name = "NodeTraversalSucceeded",
    namespace = "com.monadial.waygrid.common.domain.model.traversal.state"
  ) { field =>
    (
      field("node", _.node),
      field("actor", _.actor),
      field("vectorClock", _.vectorClock)
    ).mapN(Event.NodeTraversalSucceeded.apply)
  }

  private given Codec[Event.NodeTraversalFailed] = Codec.record(
    name = "NodeTraversalFailed",
    namespace = "com.monadial.waygrid.common.domain.model.traversal.state"
  ) { field =>
    (
      field("node", _.node),
      field("actor", _.actor),
      field("vectorClock", _.vectorClock),
      field("reason", _.reason)
    ).mapN(Event.NodeTraversalFailed.apply)
  }

  // ---------------------------------------------------------------------------
  // Fork/Join Events
  // ---------------------------------------------------------------------------

  private given Codec[Event.ForkStarted] = Codec.record(
    name = "ForkStarted",
    namespace = "com.monadial.waygrid.common.domain.model.traversal.state"
  ) { field =>
    (
      field("node", _.node),
      field("forkId", _.forkId),
      field("branches", _.branches),
      field("actor", _.actor),
      field("vectorClock", _.vectorClock)
    ).mapN(Event.ForkStarted.apply)
  }

  private given Codec[Event.BranchStarted] = Codec.record(
    name = "BranchStarted",
    namespace = "com.monadial.waygrid.common.domain.model.traversal.state"
  ) { field =>
    (
      field("node", _.node),
      field("branchId", _.branchId),
      field("forkId", _.forkId),
      field("actor", _.actor),
      field("vectorClock", _.vectorClock)
    ).mapN(Event.BranchStarted.apply)
  }

  private given Codec[Event.BranchAdvanced] = Codec.record(
    name = "BranchAdvanced",
    namespace = "com.monadial.waygrid.common.domain.model.traversal.state"
  ) { field =>
    (
      field("node", _.node),
      field("branchId", _.branchId),
      field("forkId", _.forkId),
      field("actor", _.actor),
      field("vectorClock", _.vectorClock)
    ).mapN(Event.BranchAdvanced.apply)
  }

  private given Codec[Event.BranchCompleted] = Codec.record(
    name = "BranchCompleted",
    namespace = "com.monadial.waygrid.common.domain.model.traversal.state"
  ) { field =>
    (
      field("node", _.node),
      field("branchId", _.branchId),
      field("forkId", _.forkId),
      field("result", _.result),
      field("actor", _.actor),
      field("vectorClock", _.vectorClock)
    ).mapN(Event.BranchCompleted.apply)
  }

  private given Codec[Event.BranchCanceled] = Codec.record(
    name = "BranchCanceled",
    namespace = "com.monadial.waygrid.common.domain.model.traversal.state"
  ) { field =>
    (
      field("node", _.node),
      field("branchId", _.branchId),
      field("forkId", _.forkId),
      field("reason", _.reason),
      field("actor", _.actor),
      field("vectorClock", _.vectorClock)
    ).mapN(Event.BranchCanceled.apply)
  }

  private given Codec[Event.BranchTimedOut] = Codec.record(
    name = "BranchTimedOut",
    namespace = "com.monadial.waygrid.common.domain.model.traversal.state"
  ) { field =>
    (
      field("node", _.node),
      field("branchId", _.branchId),
      field("forkId", _.forkId),
      field("actor", _.actor),
      field("vectorClock", _.vectorClock)
    ).mapN(Event.BranchTimedOut.apply)
  }

  private given Codec[Event.JoinReached] = Codec.record(
    name = "JoinReached",
    namespace = "com.monadial.waygrid.common.domain.model.traversal.state"
  ) { field =>
    (
      field("node", _.node),
      field("branchId", _.branchId),
      field("forkId", _.forkId),
      field("actor", _.actor),
      field("vectorClock", _.vectorClock)
    ).mapN(Event.JoinReached.apply)
  }

  private given Codec[Event.JoinCompleted] = Codec.record(
    name = "JoinCompleted",
    namespace = "com.monadial.waygrid.common.domain.model.traversal.state"
  ) { field =>
    (
      field("node", _.node),
      field("forkId", _.forkId),
      field("completedBranches", _.completedBranches),
      field("actor", _.actor),
      field("vectorClock", _.vectorClock)
    ).mapN(Event.JoinCompleted.apply)
  }

  private given Codec[Event.JoinTimedOut] = Codec.record(
    name = "JoinTimedOut",
    namespace = "com.monadial.waygrid.common.domain.model.traversal.state"
  ) { field =>
    (
      field("node", _.node),
      field("forkId", _.forkId),
      field("pendingBranches", _.pendingBranches),
      field("actor", _.actor),
      field("vectorClock", _.vectorClock)
    ).mapN(Event.JoinTimedOut.apply)
  }

  // ---------------------------------------------------------------------------
  // Traversal-Level Events
  // ---------------------------------------------------------------------------

  private given Codec[Event.TraversalTimeoutScheduled] = Codec.record(
    name = "TraversalTimeoutScheduled",
    namespace = "com.monadial.waygrid.common.domain.model.traversal.state"
  ) { field =>
    (
      field("node", _.node),
      field("timeoutId", _.timeoutId),
      field("deadline", _.deadline),
      field("actor", _.actor),
      field("vectorClock", _.vectorClock)
    ).mapN(Event.TraversalTimeoutScheduled.apply)
  }

  private given Codec[Event.TraversalTimedOut] = Codec.record(
    name = "TraversalTimedOut",
    namespace = "com.monadial.waygrid.common.domain.model.traversal.state"
  ) { field =>
    (
      field("node", _.node),
      field("activeNodes", _.activeNodes),
      field("activeBranches", _.activeBranches),
      field("actor", _.actor),
      field("vectorClock", _.vectorClock)
    ).mapN(Event.TraversalTimedOut.apply)
  }

  // ---------------------------------------------------------------------------
  // StateEvent Union
  // ---------------------------------------------------------------------------

  /**
   * StateEvent sealed trait as Avro union of all 20 event types.
   */
  given Codec[StateEvent] = Codec.union[StateEvent] { alt =>
    // Linear events
    alt[Event.TraversalStarted] |+|
      alt[Event.TraversalScheduled] |+|
      alt[Event.TraversalResumed] |+|
      alt[Event.TraversalCompleted] |+|
      alt[Event.TraversalFailed] |+|
      alt[Event.TraversalCanceled] |+|
      alt[Event.NodeTraversalRetried] |+|
      alt[Event.NodeTraversalSucceeded] |+|
      alt[Event.NodeTraversalFailed] |+|
      // Fork/Join events
      alt[Event.ForkStarted] |+|
      alt[Event.BranchStarted] |+|
      alt[Event.BranchAdvanced] |+|
      alt[Event.BranchCompleted] |+|
      alt[Event.BranchCanceled] |+|
      alt[Event.BranchTimedOut] |+|
      alt[Event.JoinReached] |+|
      alt[Event.JoinCompleted] |+|
      alt[Event.JoinTimedOut] |+|
      // Traversal-level events
      alt[Event.TraversalTimeoutScheduled] |+|
      alt[Event.TraversalTimedOut]
  }
