package com.monadial.waygrid.common.application.interpreter.storage

import cats.effect.{ Async, Resource }
import cats.implicits.*
import com.monadial.waygrid.common.application.util.circe.codecs.DomainTraversalStateCodecs.given
import com.monadial.waygrid.common.domain.algebra.storage.TraversalStateRepository
import com.monadial.waygrid.common.domain.model.routing.Value.TraversalId
import com.monadial.waygrid.common.domain.model.traversal.state.TraversalState
import com.monadial.waygrid.common.domain.model.traversal.state.Value.StateVersion
import dev.profunktor.redis4cats.RedisCommands
import io.circe.parser.*
import io.circe.syntax.*

import scala.concurrent.duration.FiniteDuration

/**
 * Redis implementation of TraversalStateRepository.
 *
 * Uses JSON serialization for state storage with TTL support.
 * Provides distributed locking via SETNX with automatic expiration.
 *
 * Key patterns:
 * - State: {keyPrefix}{traversalId}
 * - Version: {keyPrefix}{traversalId}:version
 * - Lock: {lockPrefix}{traversalId}
 */
class RedisTraversalStateRepository[F[_]: Async](
  redis: RedisCommands[F, String, String],
  keyPrefix: String = "waygrid:traversal:state:",
  lockPrefix: String = "waygrid:traversal:lock:",
  defaultTtl: FiniteDuration
) extends TraversalStateRepository[F]:

  private def stateKey(id: TraversalId): String = s"$keyPrefix${id.unwrap}"
  private def versionKey(id: TraversalId): String = s"$keyPrefix${id.unwrap}:version"
  private def lockKey(id: TraversalId): String = s"$lockPrefix${id.unwrap}"

  override def save(state: TraversalState): F[TraversalState] =
    val newVersion = state.stateVersion.increment
    val stateJson = state.copy(stateVersion = newVersion).asJson.noSpaces

    for
      // Check current version using Redis WATCH for optimistic locking
      currentVersionStr <- redis.get(versionKey(state.traversalId))
      currentVersion = currentVersionStr.map(_.toLong).getOrElse(0L)

      result <-
        if currentVersion == state.stateVersion.unwrap then
          for
            _ <- redis.set(stateKey(state.traversalId), stateJson)
            _ <- redis.set(versionKey(state.traversalId), newVersion.unwrap.toString)
            _ <- redis.expire(stateKey(state.traversalId), defaultTtl)
            _ <- redis.expire(versionKey(state.traversalId), defaultTtl)
          yield state.copy(stateVersion = newVersion)
        else
          Async[F].raiseError(
            new RuntimeException(
              s"Concurrent modification: expected ${state.stateVersion.unwrap}, actual $currentVersion"
            )
          )
    yield result

  override def load(traversalId: TraversalId): F[Option[TraversalState]] =
    redis.get(stateKey(traversalId)).flatMap {
      case Some(json) =>
        parse(json).flatMap(_.as[TraversalState]) match
          case Right(state) => Async[F].pure(Some(state))
          case Left(err)    => Async[F].raiseError(new RuntimeException(s"Failed to decode: $err"))
      case None => Async[F].pure(None)
    }

  override def delete(traversalId: TraversalId): F[Unit] =
    redis.del(stateKey(traversalId), versionKey(traversalId)).void

  override def exists(traversalId: TraversalId): F[Boolean] =
    redis.exists(stateKey(traversalId))

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
            new RuntimeException(
              s"Concurrent modification: expected ${expectedVersion.unwrap}, actual ${state.stateVersion.unwrap}"
            )
          )
        case None =>
          Async[F].raiseError(new RuntimeException(s"State not found: $traversalId"))
    yield result

  override def listActive(limit: Int, offset: Int): F[List[TraversalId]] =
    // Use Redis SCAN to find all state keys
    redis.keys(s"$keyPrefix*").map { keys =>
      keys
        .filterNot(_.endsWith(":version"))
        .map(_.stripPrefix(keyPrefix))
        .map(s => TraversalId.fromStringUnsafe[cats.Id](s))
        .slice(offset, offset + limit)
        .toList
    }

  override def acquireLock(traversalId: TraversalId, ttl: FiniteDuration): F[Boolean] =
    redis.setNx(lockKey(traversalId), "locked").flatMap { acquired =>
      if acquired then
        redis.expire(lockKey(traversalId), ttl).as(true)
      else
        Async[F].pure(false)
    }

  override def releaseLock(traversalId: TraversalId): F[Unit] =
    redis.del(lockKey(traversalId)).void

  override def extendLock(traversalId: TraversalId, ttl: FiniteDuration): F[Boolean] =
    redis.expire(lockKey(traversalId), ttl)

object RedisTraversalStateRepository:
  def resource[F[_]: Async](
    redis: RedisCommands[F, String, String],
    defaultTtl: FiniteDuration
  ): Resource[F, TraversalStateRepository[F]] =
    Resource.pure(new RedisTraversalStateRepository[F](redis, defaultTtl = defaultTtl))
