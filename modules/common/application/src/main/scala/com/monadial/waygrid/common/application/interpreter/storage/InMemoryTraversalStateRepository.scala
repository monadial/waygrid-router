package com.monadial.waygrid.common.application.interpreter.storage

import cats.effect.{ Async, Ref, Resource, Sync }
import cats.implicits.*
import com.monadial.waygrid.common.domain.algebra.storage.TraversalStateRepository
import com.monadial.waygrid.common.domain.model.routing.Value.TraversalId
import com.monadial.waygrid.common.domain.model.traversal.fsm.ConcurrentModification
import com.monadial.waygrid.common.domain.model.traversal.state.TraversalState
import com.monadial.waygrid.common.domain.model.traversal.state.Value.StateVersion

import java.time.Instant
import scala.concurrent.duration.FiniteDuration

/**
 * In-memory implementation of TraversalStateRepository.
 *
 * Uses Cats Effect Ref for thread-safe state management.
 * Primarily intended for testing and development scenarios.
 *
 * Features:
 * - Full optimistic locking support via state versions
 * - Distributed locking simulation with TTL expiration
 * - Thread-safe concurrent access via Ref
 */
class InMemoryTraversalStateRepository[F[_]: Sync](
  states: Ref[F, Map[TraversalId, TraversalState]],
  locks: Ref[F, Map[TraversalId, Instant]]
) extends TraversalStateRepository[F]:

  override def save(state: TraversalState): F[TraversalState] =
    val newVersion = state.stateVersion.increment
    val updatedState = state.copy(stateVersion = newVersion)

    states.modify { currentStates =>
      currentStates.get(state.traversalId) match
        case Some(existing) if existing.stateVersion != state.stateVersion =>
          // Concurrent modification detected
          (currentStates, Left(ConcurrentModification(
            state.traversalId,
            state.stateVersion,
            existing.stateVersion
          )))
        case _ =>
          // Either new entry or version matches - update state
          (currentStates.updated(state.traversalId, updatedState), Right(updatedState))
    }.flatMap {
      case Right(result) => Sync[F].pure(result)
      case Left(error) => Sync[F].raiseError(error)
    }

  override def load(traversalId: TraversalId): F[Option[TraversalState]] =
    states.get.map(_.get(traversalId))

  override def delete(traversalId: TraversalId): F[Unit] =
    states.update(_ - traversalId)

  override def exists(traversalId: TraversalId): F[Boolean] =
    states.get.map(_.contains(traversalId))

  override def update(
    traversalId: TraversalId,
    expectedVersion: StateVersion,
    updateFn: TraversalState => TraversalState
  ): F[TraversalState] =
    states.modify { currentStates =>
      currentStates.get(traversalId) match
        case Some(state) if state.stateVersion == expectedVersion =>
          val updated = updateFn(state)
          val newVersion = updated.stateVersion.increment
          val finalState = updated.copy(stateVersion = newVersion)
          (currentStates.updated(traversalId, finalState), Right(finalState))
        case Some(state) =>
          (currentStates, Left(ConcurrentModification(
            traversalId,
            expectedVersion,
            state.stateVersion
          )))
        case None =>
          (currentStates, Left(new RuntimeException(s"State not found: $traversalId")))
    }.flatMap {
      case Right(result) => Sync[F].pure(result)
      case Left(error: ConcurrentModification) => Sync[F].raiseError(error)
      case Left(error: Throwable) => Sync[F].raiseError(error)
    }

  override def listActive(limit: Int, offset: Int): F[List[TraversalId]] =
    states.get.map { statesMap =>
      statesMap.keys.toList
        .sortBy(_.unwrap) // Consistent ordering
        .slice(offset, offset + limit)
    }

  override def acquireLock(traversalId: TraversalId, ttl: FiniteDuration): F[Boolean] =
    val now = Instant.now()
    val expiresAt = now.plusNanos(ttl.toNanos)

    locks.modify { currentLocks =>
      currentLocks.get(traversalId) match
        case Some(lockExpiry) if lockExpiry.isAfter(now) =>
          // Lock still held by another process
          (currentLocks, false)
        case _ =>
          // Lock expired or doesn't exist - acquire it
          (currentLocks.updated(traversalId, expiresAt), true)
    }

  override def releaseLock(traversalId: TraversalId): F[Unit] =
    locks.update(_ - traversalId)

  override def extendLock(traversalId: TraversalId, ttl: FiniteDuration): F[Boolean] =
    val now = Instant.now()
    val newExpiry = now.plusNanos(ttl.toNanos)

    locks.modify { currentLocks =>
      currentLocks.get(traversalId) match
        case Some(lockExpiry) if lockExpiry.isAfter(now) =>
          // Lock still held - extend it
          (currentLocks.updated(traversalId, newExpiry), true)
        case _ =>
          // Lock expired or doesn't exist - cannot extend
          (currentLocks, false)
    }

object InMemoryTraversalStateRepository:

  /**
   * Create a new in-memory repository as a Resource.
   *
   * The repository state is isolated to this instance and will be
   * garbage collected when the Resource is released.
   */
  def resource[F[_]: Async]: Resource[F, TraversalStateRepository[F]] =
    Resource.eval(make[F])

  /**
   * Create a new in-memory repository.
   */
  def make[F[_]: Async]: F[TraversalStateRepository[F]] =
    for
      states <- Ref.of[F, Map[TraversalId, TraversalState]](Map.empty)
      locks  <- Ref.of[F, Map[TraversalId, Instant]](Map.empty)
    yield new InMemoryTraversalStateRepository[F](states, locks)

  /**
   * Create a new in-memory repository with pre-populated states.
   * Useful for testing scenarios.
   */
  def withInitialStates[F[_]: Async](
    initialStates: Map[TraversalId, TraversalState]
  ): F[TraversalStateRepository[F]] =
    for
      states <- Ref.of[F, Map[TraversalId, TraversalState]](initialStates)
      locks  <- Ref.of[F, Map[TraversalId, Instant]](Map.empty)
    yield new InMemoryTraversalStateRepository[F](states, locks)
