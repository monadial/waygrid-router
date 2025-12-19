package com.monadial.waygrid.common.domain.model.traversal.state

import java.time.Instant

import com.monadial.waygrid.common.domain.model.routing.Value.TraversalId
import com.monadial.waygrid.common.domain.model.traversal.dag.Dag
import com.monadial.waygrid.common.domain.model.traversal.dag.Value.{ BranchId, EdgeGuard, ForkId, NodeId }
import com.monadial.waygrid.common.domain.model.traversal.state.Event.*
import com.monadial.waygrid.common.domain.model.traversal.state.Value.{ RemainingNodes, RetryAttempt, StateVersion }
import com.monadial.waygrid.common.domain.model.vectorclock.VectorClock
import com.monadial.waygrid.common.domain.value.Address.NodeAddress

/**
 * TraversalState tracks the causal and topological execution of a DAG routing traversal.
 * It records which nodes are currently being processed, succeeded, failed, or retried.
 * It is immutable, pure, and fully replayable via event sourcing.
 *
 * This implementation supports both linear and forking DAG traversal:
 * - Linear: single active node at a time (backward compatible)
 * - Forking: multiple active nodes across branches with fork/join synchronization
 *
 * @param traversalId Unique identifier for this traversal
 * @param active Set of currently executing nodes (replaces single 'current' for fork support)
 * @param completed Set of successfully completed nodes
 * @param failed Set of failed nodes
 * @param retries Retry attempt count per node
 * @param vectorClock Causal ordering for distributed execution
 * @param history Event sourcing log of all state transitions
 * @param remainingNodes Count of nodes not yet processed
 * @param forkScopes Active fork scopes (created on Fork, removed on Join)
 * @param branchStates State of each branch within active forks
 * @param pendingJoins Join nodes waiting for branches to complete
 * @param nodeToBranch Reverse index for O(1) lookup of which branch a node belongs to
 * @param traversalTimeoutId Optional ID of a scheduled traversal-level timeout (for cancellation)
 * @param stateVersion Version for optimistic locking in storage
 */
final case class TraversalState(
  traversalId: TraversalId,
  active: Set[NodeId],
  completed: Set[NodeId],
  failed: Set[NodeId],
  retries: Map[NodeId, RetryAttempt],
  vectorClock: VectorClock,
  history: Vector[StateEvent],
  remainingNodes: RemainingNodes,
  forkScopes: Map[ForkId, ForkScope] = Map.empty,
  branchStates: Map[BranchId, BranchState] = Map.empty,
  pendingJoins: Map[NodeId, PendingJoin] = Map.empty,
  nodeToBranch: Map[NodeId, BranchId] = Map.empty,
  traversalTimeoutId: Option[String] = None,
  stateVersion: StateVersion = StateVersion.Initial
):

  // ---------------------------------------------------------------------------
  // Backward Compatibility
  // ---------------------------------------------------------------------------

  /**
   * @deprecated Use `active` instead. Returns the first active node for backward compatibility.
   */
  def current: Option[NodeId] = active.headOption

  // ---------------------------------------------------------------------------
  // Private Helpers
  // ---------------------------------------------------------------------------

  /** Merge with foreign vector clock if present, then tick for actor. */
  private def advanceClock(
    actor: NodeAddress,
    foreignVectorClock: Option[VectorClock]
  ): VectorClock =
    foreignVectorClock
      .fold(vectorClock)(vc => vectorClock.merge(vc))
      .tick(actor)

  /** Record an event and update the vector clock. */
  private def record(event: StateEvent): TraversalState =
    copy(
      history = history :+ event,
      vectorClock = event.vectorClock
    )

  // ---------------------------------------------------------------------------
  // State Transitions (Linear - backward compatible)
  // ---------------------------------------------------------------------------

  /**
   * Start executing a node. Marks the node as active and records the start event.
   */
  def start(
    node: NodeId,
    actor: NodeAddress,
    foreignVectorClock: Option[VectorClock]
  ): TraversalState =
    val vc    = advanceClock(actor, foreignVectorClock)
    val event = TraversalStarted(node, actor, vc)
    copy(active = active + node).record(event)

  /**
   * Schedule a node for future execution. Records the schedule event but
   * does not mark the node as active until it is actually started.
   */
  def schedule(
    node: NodeId,
    actor: NodeAddress,
    scheduledAt: Instant,
    foreignVectorClock: Option[VectorClock]
  ): TraversalState =
    val vc    = advanceClock(actor, foreignVectorClock)
    val event = TraversalScheduled(node, actor, scheduledAt, vc)
    record(event)

  /**
   * Resume execution of a previously scheduled node.
   * Marks the node as active and records the resume event.
   */
  def resume(
    node: NodeId,
    actor: NodeAddress,
    foreignVectorClock: Option[VectorClock]
  ): TraversalState =
    val vc    = advanceClock(actor, foreignVectorClock)
    val event = TraversalResumed(node, actor, vc)
    copy(active = active + node).record(event)

  /**
   * Mark a node as successfully completed.
   * Removes from active and failed, adds to completed.
   * Only decrements remaining count if the node wasn't already counted.
   */
  def successNode(
    node: NodeId,
    actor: NodeAddress,
    foreignVectorClock: Option[VectorClock]
  ): TraversalState =
    val vc             = advanceClock(actor, foreignVectorClock)
    val event          = NodeTraversalSucceeded(node, actor, vc)
    val alreadyCounted = completed.contains(node) || failed.contains(node)
    val newRemaining =
      if alreadyCounted then remainingNodes
      else remainingNodes.decrement
    copy(
      active = active - node,
      completed = completed + node,
      failed = failed - node, // Remove from failed if it was there (e.g., after retry)
      remainingNodes = newRemaining
    ).record(event)

  /**
   * Mark a node as failed.
   * Removes from active, adds to failed, decrements remaining count.
   */
  def failNode(
    node: NodeId,
    actor: NodeAddress,
    foreignVectorClock: Option[VectorClock],
    reason: Option[String] = None
  ): TraversalState =
    val vc    = advanceClock(actor, foreignVectorClock)
    val event = NodeTraversalFailed(node, actor, vc, reason)
    val newRemaining =
      if completed.contains(node) || failed.contains(node)
      then remainingNodes
      else remainingNodes.decrement
    copy(
      active = active - node,
      failed = failed + node,
      remainingNodes = newRemaining
    ).record(event)

  /**
   * Record a retry attempt for a node.
   * Increments the retry counter and records the event.
   * Note: The node remains in failed set to track that it was already counted
   * for remainingNodes. It will be removed from failed when it succeeds.
   */
  def retryNode(
    node: NodeId,
    actor: NodeAddress,
    foreignVectorClock: Option[VectorClock]
  ): TraversalState =
    val vc             = advanceClock(actor, foreignVectorClock)
    val currentAttempt = retries.getOrElse(node, RetryAttempt(0))
    val nextAttempt    = currentAttempt.increment
    val event          = NodeTraversalRetried(node, actor, nextAttempt, vc)
    copy(
      retries = retries.updated(node, nextAttempt)
      // Keep node in failed set to track it was already counted
    ).record(event)

  /**
   * Mark the entire traversal as completed.
   * Called when all nodes have been processed successfully.
   * Sets remainingNodes to 0 to indicate traversal is done.
   */
  def complete(
    node: NodeId,
    actor: NodeAddress,
    foreignVectorClock: Option[VectorClock]
  ): TraversalState =
    val vc    = advanceClock(actor, foreignVectorClock)
    val event = TraversalCompleted(node, actor, vc)
    copy(
      active = Set.empty,
      remainingNodes = RemainingNodes(0)
    ).record(event)

  /**
   * Mark the entire traversal as failed.
   * Called when a node fails and there's no failure edge or retries left.
   */
  def fail(
    node: NodeId,
    actor: NodeAddress,
    foreignVectorClock: Option[VectorClock],
    reason: Option[String] = None
  ): TraversalState =
    val vc    = advanceClock(actor, foreignVectorClock)
    val event = TraversalFailed(node, actor, vc, reason)
    record(event)

  /**
   * Mark the traversal as canceled.
   */
  def cancel(
    node: NodeId,
    actor: NodeAddress,
    foreignVectorClock: Option[VectorClock]
  ): TraversalState =
    val vc    = advanceClock(actor, foreignVectorClock)
    val event = TraversalCanceled(node, actor, vc)
    copy(active = Set.empty).record(event)

  // ---------------------------------------------------------------------------
  // Fork/Join State Transitions
  // ---------------------------------------------------------------------------

  /**
   * Start a fork by creating a new ForkScope and initializing branches.
   *
   * @param forkNode The fork node ID
   * @param forkId Unique ID for this fork scope
   * @param branchEntries Map of branch IDs to their entry nodes
   * @param parentScope For nested forks, the parent fork's ID
   * @param parentBranchId For nested forks, the branch ID of the parent branch that spawned this fork.
   *                       This is stored explicitly to restore context when the inner fork's join completes.
   * @param timeout Optional deadline for the fork
   * @param actor Node address performing this operation
   * @param foreignVectorClock Optional foreign clock to merge
   * @param now Current timestamp for recording fork start time (passed explicitly for purity)
   */
  def startFork(
    forkNode: NodeId,
    forkId: ForkId,
    branchEntries: Map[BranchId, NodeId],
    parentScope: Option[ForkId],
    parentBranchId: Option[BranchId],
    timeout: Option[Instant],
    actor: NodeAddress,
    foreignVectorClock: Option[VectorClock],
    now: Instant
  ): TraversalState =
    val vc       = advanceClock(actor, foreignVectorClock)
    val branches = branchEntries.keySet
    val event    = Event.ForkStarted(forkNode, forkId, branches, actor, vc)

    val scope = ForkScope(
      forkId = forkId,
      forkNodeId = forkNode,
      branches = branches,
      parentScope = parentScope,
      parentBranchId = parentBranchId,
      startedAt = now,
      timeout = timeout
    )

    val initialBranchStates = branchEntries.map { case (branchId, entryNode) =>
      branchId -> BranchState.initial(branchId, forkId, entryNode)
    }

    copy(
      forkScopes = forkScopes + (forkId -> scope),
      branchStates = branchStates ++ initialBranchStates
    ).record(event)

  /**
   * Start a branch within a fork.
   * Updates nodeToBranch index for O(1) lookup.
   */
  def startBranch(
    node: NodeId,
    branchId: BranchId,
    forkId: ForkId,
    actor: NodeAddress,
    foreignVectorClock: Option[VectorClock]
  ): TraversalState =
    val vc    = advanceClock(actor, foreignVectorClock)
    val event = Event.BranchStarted(node, branchId, forkId, actor, vc)

    val updatedBranch = branchStates.get(branchId).map(_.start(node))

    copy(
      active = active + node,
      branchStates = updatedBranch.fold(branchStates)(b => branchStates.updated(branchId, b)),
      nodeToBranch = nodeToBranch + (node -> branchId)
    ).record(event)

  /**
   * Advance a running branch to its next node.
   * Updates the branch's current node, marks that node as active,
   * and maintains the nodeToBranch index.
   */
  def advanceBranch(
    node: NodeId,
    branchId: BranchId,
    forkId: ForkId,
    actor: NodeAddress,
    foreignVectorClock: Option[VectorClock]
  ): TraversalState =
    val vc    = advanceClock(actor, foreignVectorClock)
    val event = Event.BranchAdvanced(node, branchId, forkId, actor, vc)

    val oldNode       = branchStates.get(branchId).flatMap(_.currentNode)
    val updatedBranch = branchStates.get(branchId).map(_.advanceTo(node))

    // Update index: remove old node mapping (if exists), add new node mapping
    val updatedIndex = oldNode.fold(nodeToBranch)(old => nodeToBranch - old) + (node -> branchId)

    copy(
      active = active + node,
      branchStates = updatedBranch.fold(branchStates)(b => branchStates.updated(branchId, b)),
      nodeToBranch = updatedIndex
    ).record(event)

  /**
   * Complete a branch successfully.
   * Removes the node from nodeToBranch index.
   */
  def completeBranch(
    node: NodeId,
    branchId: BranchId,
    forkId: ForkId,
    actor: NodeAddress,
    foreignVectorClock: Option[VectorClock]
  ): TraversalState =
    val vc    = advanceClock(actor, foreignVectorClock)
    val event = Event.BranchCompleted(node, branchId, forkId, BranchResult.Success(None), actor, vc)

    val updatedBranch = branchStates.get(branchId).map(_.complete())
    val updatedPendingJoins = pendingJoins.map { case (joinNode, pj) =>
      if pj.forkId == forkId then joinNode -> pj.branchCompleted(branchId)
      else joinNode                        -> pj
    }

    copy(
      active = active - node,
      branchStates = updatedBranch.fold(branchStates)(b => branchStates.updated(branchId, b)),
      pendingJoins = updatedPendingJoins,
      nodeToBranch = nodeToBranch - node
    ).record(event)

  /**
   * Fail a branch.
   * Removes the node from nodeToBranch index.
   */
  def failBranch(
    node: NodeId,
    branchId: BranchId,
    forkId: ForkId,
    reason: String,
    actor: NodeAddress,
    foreignVectorClock: Option[VectorClock]
  ): TraversalState =
    val vc    = advanceClock(actor, foreignVectorClock)
    val event = Event.BranchCompleted(node, branchId, forkId, BranchResult.Failure(reason), actor, vc)

    val updatedBranch = branchStates.get(branchId).map(_.fail(reason))
    val updatedPendingJoins = pendingJoins.map { case (joinNode, pj) =>
      if pj.forkId == forkId then joinNode -> pj.branchFailed(branchId)
      else joinNode                        -> pj
    }

    copy(
      active = active - node,
      branchStates = updatedBranch.fold(branchStates)(b => branchStates.updated(branchId, b)),
      pendingJoins = updatedPendingJoins,
      nodeToBranch = nodeToBranch - node
    ).record(event)

  /**
   * Cancel a branch (e.g., when OR join completes).
   * Removes the node from nodeToBranch index.
   */
  def cancelBranch(
    node: NodeId,
    branchId: BranchId,
    forkId: ForkId,
    reason: String,
    actor: NodeAddress,
    foreignVectorClock: Option[VectorClock]
  ): TraversalState =
    val vc    = advanceClock(actor, foreignVectorClock)
    val event = Event.BranchCanceled(node, branchId, forkId, reason, actor, vc)

    val updatedBranch = branchStates.get(branchId).map(_.cancel)
    val updatedPendingJoins = pendingJoins.map { case (joinNode, pj) =>
      if pj.forkId == forkId then joinNode -> pj.branchCanceled(branchId)
      else joinNode                        -> pj
    }

    copy(
      active = active - node,
      branchStates = updatedBranch.fold(branchStates)(b => branchStates.updated(branchId, b)),
      pendingJoins = updatedPendingJoins,
      nodeToBranch = nodeToBranch - node
    ).record(event)

  /**
   * Mark a branch as timed out.
   * Removes the node from nodeToBranch index and notifies pending joins
   * that this branch has failed (timeout is treated as failure for join purposes).
   */
  def timeoutBranch(
    node: NodeId,
    branchId: BranchId,
    forkId: ForkId,
    actor: NodeAddress,
    foreignVectorClock: Option[VectorClock]
  ): TraversalState =
    val vc    = advanceClock(actor, foreignVectorClock)
    val event = Event.BranchTimedOut(node, branchId, forkId, actor, vc)

    val updatedBranch = branchStates.get(branchId).map(_.timeout)

    // Notify pending joins that this branch has failed (timeout = failure for join logic)
    val updatedPendingJoins = pendingJoins.map { case (joinNode, pj) =>
      if pj.forkId == forkId then joinNode -> pj.branchFailed(branchId)
      else joinNode                        -> pj
    }

    copy(
      active = active - node,
      branchStates = updatedBranch.fold(branchStates)(b => branchStates.updated(branchId, b)),
      pendingJoins = updatedPendingJoins,
      nodeToBranch = nodeToBranch - node
    ).record(event)

  /**
   * Register a branch arriving at a join node.
   */
  def registerJoinArrival(
    joinNode: NodeId,
    branchId: BranchId,
    forkId: ForkId,
    pendingJoin: PendingJoin,
    actor: NodeAddress,
    foreignVectorClock: Option[VectorClock]
  ): TraversalState =
    val vc    = advanceClock(actor, foreignVectorClock)
    val event = Event.JoinReached(joinNode, branchId, forkId, actor, vc)

    copy(
      pendingJoins = pendingJoins + (joinNode -> pendingJoin)
    ).record(event)

  /**
   * Complete a join when its condition is satisfied.
   * Cleans up fork scope, branch states, and nodeToBranch index.
   */
  def completeJoin(
    joinNode: NodeId,
    forkId: ForkId,
    completedBranches: Set[BranchId],
    actor: NodeAddress,
    foreignVectorClock: Option[VectorClock]
  ): TraversalState =
    val vc    = advanceClock(actor, foreignVectorClock)
    val event = Event.JoinCompleted(joinNode, forkId, completedBranches, actor, vc)

    // Clean up fork scope and branch states
    val branchesToRemove = forkScopes.get(forkId).map(_.branches).getOrElse(Set.empty)

    // Clean up nodeToBranch index - remove all entries pointing to branches being removed
    val nodesToRemove = nodeToBranch.filter { case (_, branchId) => branchesToRemove.contains(branchId) }.keySet

    copy(
      completed = completed + joinNode,
      forkScopes = forkScopes - forkId,
      branchStates = branchStates -- branchesToRemove,
      pendingJoins = pendingJoins - joinNode,
      nodeToBranch = nodeToBranch -- nodesToRemove
    ).record(event)

  /**
   * Handle join timeout.
   */
  def timeoutJoin(
    joinNode: NodeId,
    forkId: ForkId,
    pendingBranches: Set[BranchId],
    actor: NodeAddress,
    foreignVectorClock: Option[VectorClock]
  ): TraversalState =
    val vc    = advanceClock(actor, foreignVectorClock)
    val event = Event.JoinTimedOut(joinNode, forkId, pendingBranches, actor, vc)

    copy(
      failed = failed + joinNode,
      pendingJoins = pendingJoins - joinNode
    ).record(event)

  // ---------------------------------------------------------------------------
  // Traversal-Level Timeout
  // ---------------------------------------------------------------------------

  /**
   * Schedule a traversal-level timeout.
   * Records the timeout ID for later cancellation and logs the scheduling event.
   *
   * @param entryNode The entry node ID (used for event logging)
   * @param timeoutId Unique identifier for this timeout (for cancellation)
   * @param deadline When the timeout should fire
   */
  def scheduleTraversalTimeout(
    entryNode: NodeId,
    timeoutId: String,
    deadline: Instant,
    actor: NodeAddress,
    foreignVectorClock: Option[VectorClock]
  ): TraversalState =
    val vc    = advanceClock(actor, foreignVectorClock)
    val event = TraversalTimeoutScheduled(entryNode, timeoutId, deadline, actor, vc)
    copy(traversalTimeoutId = Some(timeoutId)).record(event)

  /**
   * Clear a previously scheduled traversal timeout.
   * Called when traversal completes before timeout fires.
   */
  def clearTraversalTimeout: TraversalState =
    copy(traversalTimeoutId = None)

  /**
   * Handle a traversal-level timeout.
   * Cancels all active work and fails the traversal.
   *
   * @param fallbackNode Node to use in event if no active nodes (typically entry node)
   */
  def timeoutTraversal(
    fallbackNode: NodeId,
    actor: NodeAddress,
    foreignVectorClock: Option[VectorClock]
  ): TraversalState =
    val vc             = advanceClock(actor, foreignVectorClock)
    val node           = active.headOption.getOrElse(fallbackNode)
    val activeBranches = branchStates.values.filter(_.isActive).map(_.branchId).toSet
    val event          = TraversalTimedOut(node, active, activeBranches, actor, vc)
    copy(
      active = Set.empty,
      traversalTimeoutId = None
    ).record(event)

  // ---------------------------------------------------------------------------
  // State Queries
  // ---------------------------------------------------------------------------

  /** Returns true if the traversal has made any progress (completed or failed nodes). */
  inline def hasProgress: Boolean =
    completed.nonEmpty || failed.nonEmpty

  /** Returns true if there is a node currently being processed. */
  inline def hasActiveWork: Boolean =
    active.nonEmpty

  /** Returns true if any nodes have failed. */
  inline def hasFailures: Boolean =
    failed.nonEmpty

  /** Returns true if a specific node is currently being processed. */
  inline def isStarted(node: NodeId): Boolean =
    active.contains(node)

  /** Returns true if a node has been completed. */
  inline def isCompleted(node: NodeId): Boolean =
    completed.contains(node)

  /** Returns true if a node has failed. */
  inline def isFailed(node: NodeId): Boolean =
    failed.contains(node)

  /** Returns true if node is already terminal (completed or failed). */
  inline def isFinished(node: NodeId): Boolean =
    completed.contains(node) || failed.contains(node)

  /** Returns true if all nodes are processed (completed or failed). */
  inline def isTraversalComplete: Boolean =
    active.isEmpty && remainingNodes.unwrap <= 0

  /** Number of retries attempted for this node. */
  def retryCount(node: NodeId): Int =
    retries.get(node).map(_.unwrap).getOrElse(0)

  /** Returns the current vector clock version. */
  inline def version: Long =
    vectorClock.entries.values.sum

  // ---------------------------------------------------------------------------
  // DAG Navigation
  // ---------------------------------------------------------------------------

  /** Determine next nodes based on edge guard condition. */
  def nextNodes(guard: EdgeGuard, dag: Dag): List[NodeId] =
    val traversed = guard match
      case EdgeGuard.OnSuccess      => completed
      case EdgeGuard.OnFailure      => failed
      case EdgeGuard.Always         => completed ++ failed ++ active
      case EdgeGuard.OnAny          => completed ++ failed
      case EdgeGuard.OnTimeout      => failed    // Timeout is a type of failure
      case EdgeGuard.Conditional(_) => completed // Conditionals apply to completed nodes
    dag.edges.collect {
      case edge if traversed.contains(edge.from) && edge.guard == guard => edge.to
    }

  // ---------------------------------------------------------------------------
  // Clock Operations
  // ---------------------------------------------------------------------------

  /** Merge causal clocks (for distributed replay or multi-source merging). */
  def mergeClock(other: VectorClock): TraversalState =
    copy(vectorClock = vectorClock.merge(other))

  // ---------------------------------------------------------------------------
  // Consistency Verification
  // ---------------------------------------------------------------------------

  /** Verify the remaining count matches computed value. */
  def verifyRemainingConsistency(dag: Dag): Boolean =
    remainingNodes.unwrap == dag.nodes.size - (completed ++ failed).size

object TraversalState:
  /**
   * Construct initial traversal state for a DAG.
   */
  def initial(id: TraversalId, node: NodeAddress, dag: Dag): TraversalState =
    TraversalState(
      traversalId = id,
      active = Set.empty,
      completed = Set.empty,
      failed = Set.empty,
      retries = Map.empty,
      vectorClock = VectorClock.initial(node),
      history = Vector.empty,
      remainingNodes = RemainingNodes(dag.nodes.size)
    )

  // ---------------------------------------------------------------------------
  // Fork/Join Queries
  // ---------------------------------------------------------------------------

  extension (state: TraversalState)
    /** Check if there are active forks */
    def hasActiveForks: Boolean = state.forkScopes.nonEmpty

    /** Get the current fork depth (for nested forks) */
    def forkDepth: Int =
      def depth(forkId: Option[ForkId]): Int = forkId match
        case None     => 0
        case Some(id) => 1 + depth(state.forkScopes.get(id).flatMap(_.parentScope))
      state.forkScopes.values.map(s => depth(Some(s.forkId))).maxOption.getOrElse(0)

    /**
     * Get branch for a node if it's within a fork.
     * Uses the nodeToBranch reverse index for O(1) lookup.
     */
    def branchForNode(nodeId: NodeId): Option[BranchState] =
      state.nodeToBranch.get(nodeId).flatMap(state.branchStates.get)

    /** Check if a join is ready to complete */
    def isJoinReady(joinNodeId: NodeId): Boolean =
      state.pendingJoins.get(joinNodeId).exists(_.isSatisfied)

    /** Check if a join has failed */
    def hasJoinFailed(joinNodeId: NodeId): Boolean =
      state.pendingJoins.get(joinNodeId).exists(_.hasFailed)
