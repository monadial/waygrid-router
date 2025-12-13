package com.monadial.waygrid.common.application.interpreter.storage

import cats.effect.{ Async, Resource }
import cats.implicits.*
import com.monadial.waygrid.common.application.util.circe.codecs.DomainTraversalStateCodecs.given
import com.monadial.waygrid.common.domain.algebra.storage.TraversalStateRepository
import com.monadial.waygrid.common.domain.model.routing.Value.TraversalId
import com.monadial.waygrid.common.domain.model.traversal.fsm.ConcurrentModification
import com.monadial.waygrid.common.domain.model.traversal.state.TraversalState
import com.monadial.waygrid.common.domain.model.traversal.state.Value.StateVersion
import doobie.*
import doobie.implicits.*
import doobie.postgres.implicits.*
import io.circe.parser.*
import io.circe.syntax.*

import scala.concurrent.duration.FiniteDuration

/**
 * PostgreSQL implementation of TraversalStateRepository.
 *
 * Uses JSONB for state storage with optimistic locking via version column.
 * Provides distributed locking with automatic expiration.
 *
 * Required tables (see migration V001__traversal_states.sql):
 * - traversal_states: State storage with JSONB and version column
 * - traversal_locks: Distributed lock table with TTL
 */
class PostgresTraversalStateRepository[F[_]: Async](
  xa: Transactor[F]
) extends TraversalStateRepository[F]:

  override def save(state: TraversalState): F[TraversalState] =
    val newVersion = state.stateVersion.increment
    val stateJson = state.copy(stateVersion = newVersion).asJson.noSpaces

    val query = sql"""
      INSERT INTO traversal_states (traversal_id, state, version, created_at, updated_at)
      VALUES (${state.traversalId.unwrap}, $stateJson::jsonb, ${newVersion.unwrap}, NOW(), NOW())
      ON CONFLICT (traversal_id) DO UPDATE SET
        state = EXCLUDED.state,
        version = EXCLUDED.version,
        updated_at = NOW()
      WHERE traversal_states.version = ${state.stateVersion.unwrap}
      RETURNING version
    """.query[Long]

    query.option.transact(xa).flatMap {
      case Some(returnedVersion) if returnedVersion == newVersion.unwrap =>
        Async[F].pure(state.copy(stateVersion = newVersion))
      case Some(returnedVersion) =>
        Async[F].raiseError(
          new RuntimeException(s"Concurrent modification: expected ${newVersion.unwrap}, got $returnedVersion")
        )
      case None =>
        // No row returned means the WHERE clause didn't match (concurrent modification)
        load(state.traversalId).flatMap {
          case Some(current) =>
            Async[F].raiseError(
              new RuntimeException(s"Concurrent modification: expected ${state.stateVersion.unwrap}, current is ${current.stateVersion.unwrap}")
            )
          case None =>
            // State didn't exist before, insert fresh
            val insertQuery = sql"""
              INSERT INTO traversal_states (traversal_id, state, version, created_at, updated_at)
              VALUES (${state.traversalId.unwrap}, $stateJson::jsonb, ${newVersion.unwrap}, NOW(), NOW())
            """.update.run

            insertQuery.transact(xa).as(state.copy(stateVersion = newVersion))
        }
    }

  override def load(traversalId: TraversalId): F[Option[TraversalState]] =
    sql"""
      SELECT state FROM traversal_states
      WHERE traversal_id = ${traversalId.unwrap}
    """.query[String]
      .option
      .transact(xa)
      .flatMap {
        case Some(json) =>
          parse(json).flatMap(_.as[TraversalState]) match
            case Right(state) => Async[F].pure(Some(state))
            case Left(err)    => Async[F].raiseError(new RuntimeException(s"Failed to decode state: $err"))
        case None => Async[F].pure(None)
      }

  override def delete(traversalId: TraversalId): F[Unit] =
    sql"DELETE FROM traversal_states WHERE traversal_id = ${traversalId.unwrap}"
      .update.run.transact(xa).void

  override def exists(traversalId: TraversalId): F[Boolean] =
    sql"SELECT 1 FROM traversal_states WHERE traversal_id = ${traversalId.unwrap}"
      .query[Int].option.transact(xa).map(_.isDefined)

  override def update(
    traversalId: TraversalId,
    expectedVersion: StateVersion,
    updateFn: TraversalState => TraversalState
  ): F[TraversalState] =
    for
      maybeState <- load(traversalId)
      result <- maybeState match
        case Some(state) if state.stateVersion == expectedVersion =>
          save(updateFn(state))
        case Some(state) =>
          Async[F].raiseError(
            new RuntimeException(s"Concurrent modification: expected ${expectedVersion.unwrap}, actual ${state.stateVersion.unwrap}")
          )
        case None =>
          Async[F].raiseError(new RuntimeException(s"State not found: $traversalId"))
    yield result

  override def listActive(limit: Int, offset: Int): F[List[TraversalId]] =
    sql"""
      SELECT traversal_id FROM traversal_states
      ORDER BY created_at DESC
      LIMIT $limit OFFSET $offset
    """.query[String]
      .to[List]
      .transact(xa)
      .map(_.map(TraversalId(_)))

  override def acquireLock(traversalId: TraversalId, ttl: FiniteDuration): F[Boolean] =
    val ttlSeconds = ttl.toSeconds
    sql"""
      INSERT INTO traversal_locks (traversal_id, locked_at, expires_at)
      VALUES (${traversalId.unwrap}, NOW(), NOW() + INTERVAL '1 second' * $ttlSeconds)
      ON CONFLICT (traversal_id) DO UPDATE SET
        locked_at = EXCLUDED.locked_at,
        expires_at = EXCLUDED.expires_at
      WHERE traversal_locks.expires_at < NOW()
    """.update.run.transact(xa).map(_ > 0)

  override def releaseLock(traversalId: TraversalId): F[Unit] =
    sql"DELETE FROM traversal_locks WHERE traversal_id = ${traversalId.unwrap}"
      .update.run.transact(xa).void

  override def extendLock(traversalId: TraversalId, ttl: FiniteDuration): F[Boolean] =
    val ttlSeconds = ttl.toSeconds
    sql"""
      UPDATE traversal_locks
      SET expires_at = NOW() + INTERVAL '1 second' * $ttlSeconds
      WHERE traversal_id = ${traversalId.unwrap}
        AND expires_at > NOW()
    """.update.run.transact(xa).map(_ > 0)

object PostgresTraversalStateRepository:
  def resource[F[_]: Async](xa: Transactor[F]): Resource[F, TraversalStateRepository[F]] =
    Resource.pure(new PostgresTraversalStateRepository[F](xa))
