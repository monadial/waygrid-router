package com.monadial.waygrid.common.application.model.settings

import io.circe.Codec

import scala.concurrent.duration.FiniteDuration
import com.monadial.waygrid.common.application.instances.DurationInstances.given

trait SinkSettings
trait SourceSettings

object Kafka:
  final case class Batch(
    maxEvents: Int,
    maxDuration: FiniteDuration,
    parallelism: Int,
    maxParallelBatchConcurrency: Int
  ) derives Codec.AsObject

  final case class Sink(
    poolSize: Int
  ) extends SinkSettings derives Codec.AsObject

  final case class Source(
  ) extends SourceSettings derives Codec.AsObject

  final case class Settings(
    bootstrapServers: List[String],
    batch: Batch,
    sink: Sink,
    source: Source
  ) derives Codec.AsObject

final case class EventStreamSettings(
  kafka: Kafka.Settings
) derives Codec.AsObject
