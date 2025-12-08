package com.monadial.waygrid.common.application.util.redis4cats

import cats.effect.{Async, Resource, Sync}
import cats.syntax.all.*
import com.monadial.waygrid.common.application.algebra.Logger
import com.monadial.waygrid.common.application.domain.model.settings.RedisSettings
import dev.profunktor.redis4cats.algebra.Ping
import dev.profunktor.redis4cats.connection.{RedisClient as Redis4CatsClient, RedisClusterClient as Redis4CatsClusterClient}
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.Log as RedisLog
import dev.profunktor.redis4cats.{RedisCommands, Redis as Redis4CatsRedis}

type Redis[F[+_], K, V]       = RedisCommands[F, K, V]
type RedisClient[F[+_], K, V] = Resource[F, Redis[F, K, V]]

object Redis:
  /**
   * Custom logger integration with Redis4Cats to route logs through your application's logger.
   */
  given [F[+_]: Logger]: RedisLog[F] = new RedisLog[F]:
    def error(message: => String): F[Unit] = Logger[F].error(message)
    def info(message: => String): F[Unit]  = Logger[F].info(message)
    def debug(message: => String): F[Unit] = Logger[F].debug(message)

  def connection[F[+_]: {Async, Logger}, K, V](
    settings: RedisSettings
  )(using codec: RedisCodec[K, V]): Resource[F, RedisClient[F, K, V]] =
    settings.uris match
      case Nil =>
        Resource.eval(Sync[F].raiseError(new IllegalArgumentException("Redis URI list is empty")))
      case head :: Nil =>
        singleNode(settings)
      case _ =>
        cluster(settings)

  private def cluster[F[+_]: {Async, Logger}, K, V](
    settings: RedisSettings
  )(using codec: RedisCodec[K, V]): Resource[F, RedisClient[F, K, V]] =
    for
      _      <- Resource.eval(Logger[F].info("🔌 Connecting to Redis cluster..."))
      client <- Redis4CatsClusterClient[F](settings.uris*)
      redis <- Resource
        .pure:
          Redis4CatsRedis[F]
            .fromClusterClient[K, V](client, codec)(None)
            .evalTap(ping)
      _ <- Resource.onFinalize(Logger[F].info("🔌 Disconnected from Redis cluster"))
    yield redis

  private def singleNode[F[+_]: {Async, Logger}, K, V](
    settings: RedisSettings
  )(using codec: RedisCodec[K, V]): Resource[F, RedisClient[F, K, V]] =
    for
      _      <- Resource.eval(Logger[F].info("🔌 Connecting to Redis single node..."))
      client <- Redis4CatsClient[F].fromUri(settings.uris.head)
      redis <- Resource
        .pure:
          Redis4CatsRedis[F]
            .fromClient[K, V](client, codec)
            .evalTap(ping)
      _ <- Resource.onFinalize(Logger[F].info("🔌 Disconnected from Redis single node"))
    yield redis

  private def ping[F[+_]: {Async, Logger}, K, V](redis: Ping[F]): F[Unit] =
    redis
      .ping
      .flatMap:
        case "PONG" =>
          Logger[F].info("✅ Redis connection validated with PING")
        case other =>
          Logger[F].error(s"❌ Redis ping failed, unexpected response: $other") *>
            Async[F].raiseError(new RuntimeException(s"Invalid Redis ping response: $other"))
      .handleErrorWith: ex =>
        Logger[F].error(s"❌ Redis connection validation failed: ${ex.getMessage}") *>
          Async[F].raiseError(ex)
