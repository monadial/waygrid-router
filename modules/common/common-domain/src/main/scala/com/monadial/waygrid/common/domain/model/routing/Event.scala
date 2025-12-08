//package com.monadial.waygrid.common.domain.model.traversal
//
//import com.monadial.waygrid.common.domain.algebra.messaging.event.Event
//import com.monadial.waygrid.common.domain.algebra.messaging.message.Groupable
//import com.monadial.waygrid.common.domain.algebra.messaging.message.Value.{MessageGroupId, MessageId}
//import com.monadial.waygrid.common.domain.model.routing.Value.TraversalId
//import com.monadial.waygrid.common.domain.model.traversal.dag.Value.NodeId
//import com.monadial.waygrid.common.domain.syntax.ULIDSyntax.mapValue
//
///**
// * ## Traversal and Node Traversal Events
// *
// * These domain events represent the lifecycle of a message traversal across
// * the routing DAG — from its initial request to completion, failure, or cancellation.
// *
// * The model separates **global traversal events** (overall orchestration)
// * from **node traversal events** (local processor or destination outcomes).
// *
// * Each event is immutable, typed, and grouped by its [[TraversalId]],
// * ensuring deterministic ordering and partitioning in distributed message streams.
// *
// * ---
// *
// * ### Traversal-level FSM
// *
// * ```mermaid
// * stateDiagram-v2
// *     [*] --> TraversalRequested : origin initiates traversal
// *     TraversalRequested --> TraversalScheduled : delayed/scheduled execution
// *     TraversalScheduled --> TraversalResumed : resumed by scheduler
// *     TraversalResumed --> NodeTraversalRequested
// *
// *     NodeTraversalRequested --> NodeTraversalSucceeded : node processed successfully
// *     NodeTraversalRequested --> NodeTraversalFailed : node failed
// *
// *     NodeTraversalSucceeded --> NodeTraversalRequested : next node in DAG
// *     NodeTraversalSucceeded --> [*] : terminal node reached
// *
// *     NodeTraversalFailed --> TraversalFailed : unrecoverable failure
// *     TraversalFailed --> [*]
// *     TraversalCancelled --> [*]
// * ```
// *
// * ---
// *
// * ### Event semantics
// *
// * | Event | Emitted by | Meaning |
// * |--------|------------|---------|
// * | **TraversalRequested** | Origin | A new traversal was initiated |
// * | **TraversalScheduled** | Router | Traversal scheduled for later execution |
// * | **TraversalResumed** | Scheduler | Traversal resumed after delay |
// * | **TraversalFailed** | Router | Traversal permanently failed |
// * | **TraversalCancelled** | Router / Control | Traversal cancelled before completion |
// * | **NodeTraversalRequested** | Router | Node execution was requested |
// * | **NodeTraversalSucceeded** | Processor / Destination | Node completed successfully |
// * | **NodeTraversalFailed** | Processor / Destination | Node failed to process or deliver message |
// *
// * ---
// *
// * ### Design rationale
// *
// * - Traversal events describe **global orchestration** of routing.
// * - Node traversal events describe **local node outcomes**.
// * - Together, they form a complete event stream describing traversal progress.
// * - The [[Groupable]] contract ensures all events of one traversal share the same group key.
// *
// * Example traversal event stream:
// * {{{
// * TraversalRequested
// * NodeTraversalRequested(nodeA)
// * NodeTraversalSucceeded(nodeA)
// * NodeTraversalRequested(nodeB)
// * NodeTraversalFailed(nodeB)
// * TraversalFailed
// * }}}
// */
//object Event:
//
//  /**
//   * Base trait for all traversal-related events.
//   * Each event belongs to a specific [[TraversalId]] and is grouped by it
//   * for deterministic ordering in Kafka or in-memory event streams.
//   */
//  sealed trait TraversalEvent extends Event with Groupable:
//    val traversalId: TraversalId
//    override def groupId: MessageGroupId = traversalId.mapValue[MessageGroupId]
//
//  /** Base trait for all node-specific traversal events. */
//  sealed trait NodeTraversalEvent extends TraversalEvent:
//    val nodeId: NodeId
//
//  /** A new traversal was initiated by an origin node. */
//  final case class TraversalRequested(
//    id: MessageId,
//    traversalId: TraversalId
//  ) extends TraversalEvent
//
//  /** The traversal was scheduled for deferred execution. */
//  final case class TraversalScheduled(
//    id: MessageId,
//    traversalId: TraversalId
//  ) extends TraversalEvent
//
//  /** The traversal resumed after a scheduled delay. */
//  final case class TraversalResumed(
//    id: MessageId,
//    traversalId: TraversalId
//  ) extends TraversalEvent
//
//  /** The traversal failed and will not continue. */
//  final case class TraversalFailed(
//    id: MessageId,
//    traversalId: TraversalId
//  ) extends TraversalEvent
//
//  /** The traversal was cancelled before reaching completion. */
//  final case class TraversalCancelled(
//    id: MessageId,
//    traversalId: TraversalId
//  ) extends TraversalEvent
//
//  /** A node was requested to process or deliver the message. */
//  final case class NodeTraversalRequested(
//    id: MessageId,
//    traversalId: TraversalId,
//    nodeId: NodeId
//  ) extends NodeTraversalEvent
//
//  /** A node successfully processed or delivered the message. */
//  final case class NodeTraversalSucceeded(
//    id: MessageId,
//    traversalId: TraversalId,
//    nodeId: NodeId
//  ) extends NodeTraversalEvent
//
//  /** A node failed to process or deliver the message. */
//  final case class NodeTraversalFailed(
//    id: MessageId,
//    traversalId: TraversalId,
//    nodeId: NodeId
//  ) extends NodeTraversalEvent
