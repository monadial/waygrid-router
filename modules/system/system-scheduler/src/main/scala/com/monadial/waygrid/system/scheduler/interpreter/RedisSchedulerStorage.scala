//package com.monadial.waygrid.system.scheduler.interpreter
//
//import cats.effect.Resource
//import cats.effect.Async
//import cats.syntax.all.*
//import com.monadial.waygrid.common.application.algebra.Logger
//import com.monadial.waygrid.common.application.util.redis4cats.{ Redis, RedisClient }
//import com.monadial.waygrid.common.domain.algebra.value.codec.BytesCodec
//import com.monadial.waygrid.system.scheduler.algebra.SchedulerStorage
//import com.monadial.waygrid.system.scheduler.instances.ScheduleInstances.given
//import com.monadial.waygrid.system.scheduler.model.schedule.Value
//import com.monadial.waygrid.system.scheduler.model.schedule.Value.{
//  Schedule,
//  ScheduleTime,
//  ScheduleToken,
//  SchedulerShard
//}
//import com.monadial.waygrid.system.scheduler.settings.SchedulerServiceSettings
//import dev.profunktor.redis4cats.codecs.Codecs
//import dev.profunktor.redis4cats.codecs.splits.SplitEpi
//import dev.profunktor.redis4cats.data.RedisCodec
//import dev.profunktor.redis4cats.effects.{ Score, ScoreWithValue }
//import io.lettuce.core.ZAddArgs
//
///**
// * Redis-backed implementation of the SchedulerStorage algebra.
// *
// * Stores scheduling tokens with associated timestamps in a sorted set
// * keyed by a logical shard, enabling efficient range queries by score.
// *
// * @param shard the logical partition of the scheduler
// * @param client the Redis connection (wrapped by Redis4Cats)
// */
//final case class RedisSchedulerStorage[F[+_]: {Async, Logger}](
//  shard: SchedulerShard,
//  client: Redis[F, SchedulerShard, ScheduleToken]
//) extends SchedulerStorage[F]:
//  override def put(schedule: Schedule): F[Unit] =
//    for
//      _     <- Logger[F].debug(s"Putting token ${schedule.token} with time ${schedule.time} in shard $shard")
//      score <- Score(schedule.time.toEpochMillis).pure[F]
//      _     <- client.zAdd(shard, Option(ZAddArgs().nx()), ScoreWithValue(score, schedule.token))
//    yield ()
//
//  override def remove(token: ScheduleToken): F[Unit] =
//    for
//      _ <- Logger[F].debug(s"Removing token $token from shard $shard")
//      _ <- client.zRem(shard, token)
//    yield ()
//
//  override def find(token: ScheduleToken): F[Option[ScheduleTime]] =
//    for
//      _        <- Logger[F].debug(s"Finding token $token in shard $shard")
//      scoreOpt <- client.zScore(shard, token)
//      time     <- scoreOpt.traverse(ScheduleTime.fromDouble)
//    yield time
//
//  override def update(newSchedule: Schedule): F[Unit] =
//    for
//      _ <-
//        Logger[F].debug(s"Updating token ${newSchedule.token} to new time ${newSchedule.time} in shard $shard")
//      newScore <- Score(newSchedule.time.toEpochMillis).pure[F]
//      _        <- client.zAdd(shard, Option(ZAddArgs().xx()), ScoreWithValue(newScore, newSchedule.token))
//    yield ()
//
//object RedisSchedulerStorage:
//
//  /**
//   * Redis codec for encoding and decoding SchedulerShard and ScheduleToken.
//   * Uses BytesCodec and wraps it with SplitEpi to conform to RedisCodec.
//   */
//  private given RedisCodec[SchedulerShard, ScheduleToken] = Codecs.derive(
//    RedisCodec.Bytes,
//    SplitEpi[Array[Byte], SchedulerShard](
//      bytes => BytesCodec[SchedulerShard].decodeFromScalar(bytes).fold(throw _, identity),
//      value => BytesCodec[SchedulerShard].encodeToScalar(value)
//    ),
//    SplitEpi[Array[Byte], ScheduleToken](
//      bytes => BytesCodec[ScheduleToken].decodeFromScalar(bytes).fold(throw _, identity),
//      value => BytesCodec[ScheduleToken].encodeToScalar(value)
//    )
//  )
//
//  /**
//   * Internal factory for constructing a RedisSchedulerStorage from an active client.
//   *
//   * @param shard the logical shard key to bind to
//   * @param client Redis connection mapped to that key type
//   */
//  private def fromClient[F[+_]: {Async, Logger}](
//    shard: SchedulerShard,
//    client: RedisClient[F, SchedulerShard, ScheduleToken]
//  ): Resource[F, RedisSchedulerStorage[F]] =
//    client
//      .map(new RedisSchedulerStorage[F](shard, _))
//
//  /**
//   * Entry point for constructing a scheduler storage using Redis cluster mode.
//   *
//   * @param shard the logical scheduler shard to store entries in
//   * @param settings system-level Redis scheduler configuration
//   * @return a managed instance of RedisSchedulerStorage
//   */
//  def default[F[+_]: {Async, Logger}](
//    shard: SchedulerShard,
//    settings: SchedulerServiceSettings
//  ): Resource[F, RedisSchedulerStorage[F]] =
//    Redis
//      .cluster(settings.redis)
//      .flatMap(fromClient(shard, _))
