package com.monadial.waygrid.common.application.kafka

import cats.effect.kernel.Resource
import com.monadial.waygrid.common.application.algebra.{EventSource}
import com.monadial.waygrid.common.application.domain.model.event.EventStream
import cats.Applicative

object KafkaEventSource:
  def default[F[+_]: {Applicative}]: Resource[F, EventSource[F]] =
    for
      codec <- KafkaTransportEnvelopeCodec.default[F]
    yield new EventSource[F]:
      override def subscribe(stream: EventStream)(handler: Handler): Resource[F, Unit] = ???
      override def subscribeTo(streams: List[EventStream])(handler: Handler): Resource[F, Unit] = ???



