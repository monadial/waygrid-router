package com.monadial.waygrid.common.application.model.settings

import io.circe.Codec

object Kafka:
  final case class Settings(
    bootstrapServers: List[String],
  ) derives Codec.AsObject

final case class EventStreamSettings(
  kafka: Kafka.Settings
) derives Codec.AsObject
