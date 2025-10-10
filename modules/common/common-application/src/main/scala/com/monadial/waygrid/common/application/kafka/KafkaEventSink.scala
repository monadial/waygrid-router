package com.monadial.waygrid.common.application.kafka

import cats.effect.Async
import cats.effect.Resource
import cats.effect.implicits.*
import cats.implicits.*
import cats.effect.std.Queue
import com.monadial.waygrid.common.application.algebra.EventSink
import com.monadial.waygrid.common.application.domain.model.envelope.Envelope
import com.monadial.waygrid.common.application.domain.model.envelope.Value.EnvelopeId
import com.monadial.waygrid.common.application.domain.model.settings.Kafka
import com.monadial.waygrid.common.application.kafka.model.KafkaTransportEnvelope
import com.monadial.waygrid.common.application.instances.CirceInstances.given
import com.monadial.waygrid.common.domain.model.event.Event
import com.monadial.waygrid.common.domain.value.codec.BytesCodec
import fs2.Stream
import fs2.kafka.{Acks, KafkaProducer, KeySerializer, ProducerSettings, Serializer, ValueSerializer}
import io.circe.Json
import io.circe.syntax.given

final case class KafkaEventSinkMetrics[F[+_]]()

object KafkaEventSinkMetrics:
  def create[F[+_]]: Resource[F, KafkaEventSinkMetrics[F]] = ???

object KafkaEventSink:

  private def spawnProducer[F[+_]: Async](
    settings: Kafka.Settings
  ): Resource[F, KafkaProducer[F, EnvelopeId, KafkaTransportEnvelope]] =
    for
      given KeySerializer[F, EnvelopeId] <-
        Resource
          .pure(Serializer
            .lift[F, EnvelopeId](BytesCodec[EnvelopeId].encode(_).pure[F]))
      given ValueSerializer[F, KafkaTransportEnvelope] <-
        Resource
          .pure(Serializer
            .lift[F, KafkaTransportEnvelope](x => BytesCodec[Json].encode(x.asJson).pure[F]))
      producerSettings <- Resource.pure(
        ProducerSettings[F, EnvelopeId, KafkaTransportEnvelope]
          .withBootstrapServers(settings.bootstrapServers.mkString(","))
          .withAcks(settings.sink.acks.getOrElse(Acks.All))
          .withRequestTimeout(settings.sink.requestTimeout)
          .withBatchSize(settings.batch.maxEvents)
          .withLinger(settings.sink.linger)
          .withClientId(settings.clientId)
      )
      producer <- KafkaProducer.resource(producerSettings)
    yield producer

  def default[F[+_]: Async](
    settings: Kafka.Settings
  ): Resource[F, EventSink[F]] =
    for
      metrics  <- KafkaEventSinkMetrics.create[F]
      producer <- spawnProducer(settings)
      internalQueue <- Resource.eval(
        Queue.bounded[F, Option[Envelope[? <: Event]]](settings.batch.maxEvents)
      )

      eventSink <- Resource
        .eval:
          Stream
            .fromQueueNoneTerminated(internalQueue)
            .groupWithin(settings.batch.maxEvents, settings.batch.maxDuration)
            .filter(_.nonEmpty)
            .compile
            .drain
            .onCancel:
              internalQueue.offer(None)
            .start

      _ <- Resource
        .onFinalize:
          eventSink.cancel
    yield new EventSink[F]:
      override def send(event: Evt): F[Unit] =
        internalQueue
          .offer(Some(event))

      override def sendBatch(events: List[Evt]): F[Unit] =
        events
          .traverse_(send)
