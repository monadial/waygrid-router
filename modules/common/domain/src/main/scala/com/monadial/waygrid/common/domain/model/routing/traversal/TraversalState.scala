package com.monadial.waygrid.common.domain.model.routing.traversal

import com.monadial.waygrid.common.domain.model.routing.Value.TraversalId
import com.monadial.waygrid.common.domain.model.traversal.dag.Value.{ EdgeGuard, NodeId }
import com.monadial.waygrid.common.domain.model.traversal.dag.{ Dag, Edge }
import com.monadial.waygrid.common.domain.model.vectorclock.VectorClock
import com.monadial.waygrid.common.domain.value.Address.NodeAddress

import java.time.Instant

/**
 * TraversalState tracks the causal and topological execution of a DAG routing traversal.
 * It records which nodes were started, succeeded, failed, or retried.
 * It is immutable, pure, and fully replayable.
 */
final case class TraversalState(
  traversalId: TraversalId,
  current: Set[NodeId],
  completed: Set[NodeId],
  failed: Set[NodeId],
  retries: Map[NodeId, Int],
  vectorClock: VectorClock,
  history: Vector[TraversalEvent],
  remainingCount: Int
):

  /** Compute updated vector clock and remaining count. */
  private def updateProgress(
    node: NodeId,
    actor: NodeAddress,
    otherVc: Option[VectorClock]
  ): (VectorClock, Int, Boolean) =
    val alreadyDone  = completed.contains(node) || failed.contains(node)
    val newRemaining = if alreadyDone then remainingCount else remainingCount - 1
    val vc           = otherVc.fold(vectorClock.tick(actor))(ovc => vectorClock.merge(ovc).tick(actor))
    (vc, newRemaining, alreadyDone)

  /** Record a traversal event and return the updated state. */
  private def record(evt: TraversalEvent): TraversalState =
    copy(history = history :+ evt, vectorClock = evt.vectorClock)

  /** Common helper to mark a node as transitioned (removes from current). */
  private def markTransition(node: NodeId): TraversalState =
    copy(current = current - node)

  /** Returns true if node is already terminal (completed or failed). */
  inline def isFinished(node: NodeId): Boolean =
    completed.contains(node) || failed.contains(node)

  /** Start executing a node. */
  def start(
    node: NodeId,
    actor: NodeAddress,
    otherVc: Option[VectorClock],
    at: Instant = Instant.now()
  ): TraversalState =
    val vc  = otherVc.fold(vectorClock.tick(actor))(ovc => vectorClock.merge(ovc).tick(actor))
    val evt = TraversalEvent.Started(node, actor, vc, at)
    copy(current = current + node).record(evt)

  def scheduleStart(
    node: NodeId,
    actor: NodeAddress,
    otherVc: Option[VectorClock],
    at: Instant = Instant.now()
  ): TraversalState =
    val vc  = otherVc.fold(vectorClock.tick(actor))(ovc => vectorClock.merge(ovc).tick(actor))
    val evt = TraversalEvent.ScheduledStart(node, actor, vc, at)
    record(evt)

  /** Mark node as succeeded. */
  def markSuccess(
    node: NodeId,
    actor: NodeAddress,
    otherVc: Option[VectorClock],
    at: Instant = Instant.now()
  ): TraversalState =
    val (vc, newRemaining, _) = updateProgress(node, actor, otherVc)
    val evt                   = TraversalEvent.Succeeded(node, actor, vc, at)
    markTransition(node)
      .copy(
        completed = completed + node,
        vectorClock = vc,
        remainingCount = newRemaining
      )
      .record(evt)

  /** Mark node as failed with optional reason. */
  def markFailure(
    node: NodeId,
    actor: NodeAddress,
    otherVc: Option[VectorClock],
    reason: Option[String] = None,
    at: Instant = Instant.now()
  ): TraversalState =
    val (vc, newRemaining, _) = updateProgress(node, actor, otherVc)
    val evt                   = TraversalEvent.Failed(node, actor, vc, at, reason)
    markTransition(node)
      .copy(
        failed = failed + node,
        vectorClock = vc,
        remainingCount = newRemaining
      )
      .record(evt)

  /** Record retry attempt for a node. */
  def markRetry(
    node: NodeId,
    actor: NodeAddress,
    attempt: Int,
    otherVc: Option[VectorClock],
    at: Instant = Instant.now()
  ): TraversalState =
    val vc  = otherVc.fold(vectorClock.tick(actor))(ovc => vectorClock.merge(ovc).tick(actor))
    val evt = TraversalEvent.Retried(node, actor, attempt, vc, at)
    val newRetries = retries.updatedWith(node) {
      case Some(current) => Some(math.max(current, attempt))
      case None          => Some(attempt)
    }
    copy(retries = newRetries).record(evt)

  /** Record traversal completion event. */
  def markTraversalCompleted(
    actor: NodeAddress,
    node: NodeId,
    otherVc: Option[VectorClock],
    at: Instant = Instant.now()
  ): TraversalState =
    val vc  = otherVc.fold(vectorClock.tick(actor))(ovc => vectorClock.merge(ovc).tick(actor))
    val evt = TraversalEvent.Completed(node, actor, vc, at)
    record(evt)

  /** Record traversal failure event with optional reason. */
  def markTraversalFailed(
    actor: NodeAddress,
    node: NodeId,
    otherVc: Option[VectorClock],
    reason: Option[String] = None,
    at: Instant = Instant.now()
  ): TraversalState =
    val vc  = otherVc.fold(vectorClock.tick(actor))(ovc => vectorClock.merge(ovc).tick(actor))
    val evt = TraversalEvent.FailedTraversal(node, actor, vc, at, reason)
    record(evt)

  /** Determine next nodes based on edge guard condition. */
  def nextNodes(guard: EdgeGuard, dag: Dag): List[NodeId] =
    val traversed = guard match
      case EdgeGuard.OnSuccess      => completed
      case EdgeGuard.OnFailure      => failed
      case EdgeGuard.Always         => completed ++ failed ++ current
      case EdgeGuard.OnAny          => completed ++ failed
      case EdgeGuard.OnTimeout      => failed    // Timeout is a type of failure
      case EdgeGuard.Conditional(_) => completed // Conditionals apply to completed nodes
    dag.edges.collect { case Edge(from, to, g) if traversed.contains(from) && g == guard => to }

  /** Automatically start all next nodes reachable from successful nodes. */
  def startNext(dag: Dag, guard: EdgeGuard, actor: NodeAddress, otherVc: Option[VectorClock]): TraversalState =
    val nextIds = nextNodes(guard, dag)
    nextIds.foldLeft(this)((acc, nid) => acc.start(nid, actor, otherVc))

  inline def version: Int = vectorClock
    .entries
    .foldLeft(0)((acc, _) => acc + 1)

  /** Returns true if all nodes are processed (completed or failed). */
  inline def isTraversalComplete: Boolean =
    current.isEmpty && remainingCount <= 0

  /** Number of retries attempted for this node. */
  def retryCount(node: NodeId): Int =
    retries.getOrElse(node, 0)

  /** Merge causal clocks (for distributed replay or multi-source merging). */
  def mergeClock(other: VectorClock): TraversalState =
    copy(vectorClock = vectorClock.merge(other))

  /** Rebuild traversal deterministically from event history. */
  def rebuildFromHistory(events: Seq[TraversalEvent], dag: Dag, origin: NodeAddress): TraversalState =
    events.foldLeft(TraversalState.initial(traversalId, dag, origin)) { (acc, evt) =>
      evt match
        case TraversalEvent.Started(n, a, vc, at) =>
          acc.start(n, a, None, at).copy(vectorClock = vc)
        case TraversalEvent.ScheduledStart(n, a, vc, at) =>
          acc.scheduleStart(n, a, None, at).copy(vectorClock = vc)
        case TraversalEvent.Succeeded(n, a, vc, at) =>
          acc.markSuccess(n, a, None, at).copy(vectorClock = vc)
        case TraversalEvent.Failed(n, a, vc, at, er) =>
          acc.markFailure(n, a, None, er, at).copy(vectorClock = vc)
        case TraversalEvent.Retried(n, a, attempt, vc, at) =>
          acc.markRetry(n, a, attempt, None, at).copy(vectorClock = vc)
        case TraversalEvent.Completed(n, a, vc, at) =>
          acc.markTraversalCompleted(a, n, None, at).copy(vectorClock = vc)
        case TraversalEvent.FailedTraversal(n, a, vc, at, er) =>
          acc.markTraversalFailed(a, n, None, er, at).copy(vectorClock = vc)
    }

  /** Recompute remaining count from DAG consistency. */
  private def recomputeRemaining(dag: Dag): Int =
    dag.nodes.size - (completed ++ failed).size

  /** Verify the remaining count matches computed value. */
  def verifyRemainingConsistency(dag: Dag): Boolean =
    remainingCount == recomputeRemaining(dag)

  inline def isStarted(node: NodeId): Boolean   = current.contains(node)
  inline def isCompleted(node: NodeId): Boolean = completed.contains(node)
  inline def isFailed(node: NodeId): Boolean    = failed.contains(node)

  inline def hasActiveWork: Boolean = current.nonEmpty
  inline def hasFailures: Boolean   = failed.nonEmpty
  inline def hasProgress: Boolean   = completed.nonEmpty || failed.nonEmpty

object TraversalState:
  /** Construct empty traversal state for a DAG. */
  def initial(id: TraversalId, dag: Dag, origin: NodeAddress): TraversalState =
    TraversalState(
      traversalId = id,
      current = Set.empty,
      completed = Set.empty,
      failed = Set.empty,
      retries = Map.empty,
      vectorClock = VectorClock.initial(origin),
      history = Vector.empty,
      remainingCount = dag.nodes.size
    )

sealed trait TraversalEvent:
  def node: NodeId
  def actor: NodeAddress
  def vectorClock: VectorClock
  def at: Instant

object TraversalEvent:

  final case class Started(
    node: NodeId,
    actor: NodeAddress,
    vectorClock: VectorClock,
    at: Instant
  ) extends TraversalEvent

  final case class ScheduledStart(
    node: NodeId,
    actor: NodeAddress,
    vectorClock: VectorClock,
    at: Instant
  ) extends TraversalEvent

  final case class Succeeded(
    node: NodeId,
    actor: NodeAddress,
    vectorClock: VectorClock,
    at: Instant
  ) extends TraversalEvent

  final case class Failed(
    node: NodeId,
    actor: NodeAddress,
    vectorClock: VectorClock,
    at: Instant,
    reason: Option[String] = None
  ) extends TraversalEvent

  final case class Retried(
    node: NodeId,
    actor: NodeAddress,
    attempt: Int,
    vectorClock: VectorClock,
    at: Instant
  ) extends TraversalEvent

  final case class Completed(
    node: NodeId,
    actor: NodeAddress,
    vectorClock: VectorClock,
    at: Instant
  ) extends TraversalEvent

  final case class FailedTraversal(
    node: NodeId,
    actor: NodeAddress,
    vectorClock: VectorClock,
    at: Instant,
    reason: Option[String] = None
  ) extends TraversalEvent
