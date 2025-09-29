package com.monadial.waygrid.system.scheduler.instances

import cats.Show
import cats.data.Validated
import cats.syntax.all.*
import com.monadial.waygrid.common.domain.instances.StringInstances.given
import com.monadial.waygrid.common.domain.value.codec.{ BytesCodec, BytesDecodingError }
import com.monadial.waygrid.system.scheduler.model.schedule.Value.SchedulerShard

object ScheduleInstances:

  given Show[SchedulerShard] with
    inline def show(value: SchedulerShard): String = SchedulerShard.format(value)

  given BytesCodec[SchedulerShard] with
    inline def encode(value: SchedulerShard): Array[Byte] =
      BytesCodec[String]
        .encode(value.show)

    inline def decode(value: Array[Byte]): Validated[BytesDecodingError, SchedulerShard] =
      BytesCodec[String]
        .decode(value)
        .andThen: shard =>
          SchedulerShard
            .fromString(shard)
            .toValid(BytesDecodingError(s"Invalid SchedulerShard format: $shard"))
