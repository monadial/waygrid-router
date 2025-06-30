package com.monadial.waygrid.common.application.domain.model.settings

import com.monadial.waygrid.common.application.instances.DurationInstances.given

import fs2.kafka.Acks
import io.circe.{ Codec, Decoder, Encoder }

import scala.concurrent.duration.FiniteDuration

trait SinkSettings
trait SourceSettings

given Decoder[Acks] = Decoder.decodeString.emap {
  case "all"  => Right(Acks.All)
  case "zero" => Right(Acks.Zero)
  case "one"  => Right(Acks.One)
  case other  => Left(s"Invalid Acks value: ${other} allowed values are 'all', 'zero', 'one'")
}

given Encoder[Acks] = Encoder.encodeString.contramap[Acks] {
  case Acks.All  => "all"
  case Acks.Zero => "zero"
  case Acks.One  => "one"
  case other     => throw new IllegalArgumentException(s"Invalid Acks value: ${other}")
}

object Kafka:
  final case class Batch(
    maxEvents: Int,
    maxDuration: FiniteDuration,
    parallelism: Int,
    maxParallelBatchConcurrency: Int
  ) derives Codec.AsObject

  final case class Sink(
    linger: FiniteDuration,
    requestTimeout: FiniteDuration,
    acks: Option[Acks]
  ) extends SinkSettings derives Codec.AsObject

  final case class Source(
  ) extends SourceSettings derives Codec.AsObject

  final case class Settings(
    bootstrapServers: List[String],
    clientId: String,
    batch: Batch,
    sink: Sink,
    source: Source
  ) derives Codec.AsObject

final case class EventStreamSettings(
  kafka: Kafka.Settings
) derives Codec.AsObject
