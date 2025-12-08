package com.monadial.waygrid.system.scheduler.model.schedule

import cats.implicits.toShow
import com.monadial.waygrid.common.domain.algebra.value.instant.InstantValue
import com.monadial.waygrid.common.domain.algebra.value.integer.IntegerValue
import com.monadial.waygrid.common.domain.algebra.value.ulid.ULIDValue

object Value:

  final case class SchedulerShard(shard: ScheduleShard, virtualShard: ScheduleVirtualShard)
  object SchedulerShard:
    private val ShardPatternPrefix = "waygrid:scheduler:task:shard"
    private val Pattern            = raw"""$ShardPatternPrefix:(\d+)-(\d+)""".r

    def format(value: SchedulerShard): String =
      s"$ShardPatternPrefix:${value.shard.show}-${value.virtualShard.show}"

    def fromString(string: String): Option[SchedulerShard] =
      string match
        case Pattern(shardStr, virtualShardStr) =>
          (shardStr.toIntOption, virtualShardStr.toIntOption) match
            case (Some(shard), Some(virtualShard)) =>
              Some(SchedulerShard(ScheduleShard(shard), ScheduleVirtualShard(virtualShard)))
            case _ => None
        case _ => None

  final case class Schedule(token: ScheduleToken, time: ScheduleTime)

  type ScheduleVirtualShard = ScheduleVirtualShard.Type
  object ScheduleVirtualShard extends IntegerValue

  type ScheduleShard = ScheduleShard.Type
  object ScheduleShard extends IntegerValue

  type ScheduleToken = ScheduleToken.Type
  object ScheduleToken extends ULIDValue

  type ScheduleTime = ScheduleTime.Type
  object ScheduleTime extends InstantValue:
    extension (self: ScheduleTime)
      def toEpochMillis: Double = self
        .unwrap
        .toEpochMilli
        .toDouble
