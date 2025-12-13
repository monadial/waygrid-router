package com.monadial.waygrid.common.domain.algebra.storage

import com.monadial.waygrid.common.domain.model.routing.Value.TraversalId
import com.monadial.waygrid.common.domain.model.traversal.state.TraversalState
import com.monadial.waygrid.common.domain.model.traversal.state.Value.StateVersion

import scala.concurrent.duration.FiniteDuration

/**
 * Repository algebra for persistent storage of traversal state.
 *
 * Provides CRUD operations with optimistic locking support via state versions.
 * Implementations should handle concurrent modification detection and
 * provide distributed locking for cluster coordination.
 *
 * @tparam F Effect type
 */
trait TraversalStateRepository[F[_]]:

  /**
   * Save state to storage with optimistic locking.
   * Increments the state version and stores the updated state.
   *
   * @param state The state to save
   * @return Updated state with incremented version, or raises error on concurrent modification
   */
  def save(state: TraversalState): F[TraversalState]

  /**
   * Load state by traversal ID.
   *
   * @param traversalId The traversal identifier
   * @return Some(state) if found, None if not exists
   */
  def load(traversalId: TraversalId): F[Option[TraversalState]]

  /**
   * Delete state after traversal completion.
   *
   * @param traversalId The traversal identifier to delete
   */
  def delete(traversalId: TraversalId): F[Unit]

  /**
   * Check if state exists for a traversal.
   *
   * @param traversalId The traversal identifier
   * @return true if state exists
   */
  def exists(traversalId: TraversalId): F[Boolean]

  /**
   * Update state with optimistic locking.
   * Verifies the expected version matches before applying the update.
   *
   * @param traversalId     The traversal identifier
   * @param expectedVersion The version expected in storage (for conflict detection)
   * @param updateFn        Function to transform the current state
   * @return Updated state with incremented version, or raises ConcurrentModification error
   */
  def update(
    traversalId: TraversalId,
    expectedVersion: StateVersion,
    updateFn: TraversalState => TraversalState
  ): F[TraversalState]

  /**
   * List active traversals (for recovery on startup).
   *
   * @param limit  Maximum number of results
   * @param offset Offset for pagination
   * @return List of active traversal IDs
   */
  def listActive(limit: Int, offset: Int): F[List[TraversalId]]

  /**
   * Acquire distributed lock for traversal (for cluster coordination).
   * Used to ensure only one node processes a traversal at a time.
   *
   * @param traversalId The traversal identifier
   * @param ttl         Time-to-live for the lock (auto-expires after this duration)
   * @return true if lock was acquired, false if already held by another process
   */
  def acquireLock(traversalId: TraversalId, ttl: FiniteDuration): F[Boolean]

  /**
   * Release distributed lock.
   *
   * @param traversalId The traversal identifier
   */
  def releaseLock(traversalId: TraversalId): F[Unit]

  /**
   * Extend the TTL of an existing lock.
   * Used to keep the lock alive during long-running operations.
   *
   * @param traversalId The traversal identifier
   * @param ttl         New time-to-live for the lock
   * @return true if lock was extended, false if lock not held
   */
  def extendLock(traversalId: TraversalId, ttl: FiniteDuration): F[Boolean]

object TraversalStateRepository:
  def apply[F[_]](using repo: TraversalStateRepository[F]): TraversalStateRepository[F] = repo
