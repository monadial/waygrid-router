package com.monadial.waygrid.common.application.interpreter

import cats.effect.{Async, Concurrent, Resource}
import cats.implicits.*
import com.monadial.waygrid.common.application.algebra.{EventSource, HasNode, Logger}
import com.monadial.waygrid.common.application.model.event.{Event, EventId, EventTopic}
import com.monadial.waygrid.common.application.model.settings.Kafka.Settings as KafkaEventStreamSettings
import com.monadial.waygrid.common.domain.instances.StringInstances.given
import com.monadial.waygrid.common.domain.instances.ULIDInstances.given
import com.monadial.waygrid.common.domain.syntax.StringSyntax.toDomain
import com.monadial.waygrid.common.domain.syntax.ULIDSyntax.toDomainF
import com.monadial.waygrid.common.domain.value.codec.BytesCodec
import fs2.Stream
import fs2.kafka.*
import io.circe.*
import io.circe.parser.*
import wvlet.airframe.ulid.ULID

object EventSourceInterpreter:

  def kafka[F[+_] : {HasNode, Logger, Async, Concurrent}](settings: KafkaEventStreamSettings): Resource[F, EventSource[F]] =
    for
      given KeyDeserializer[F, EventId] <- Resource.pure:
        Deserializer.lift:
          bts =>
            BytesCodec[ULID]
              .decode(bts)
              .map(_.toDomainF[F, EventId])
              .toEither match
                case Right(eventId) =>
                  eventId
                case Left(err) =>
                  Logger[F].error(s"Failed to decode eventId: ${err.message}") *>
                      new RuntimeException(s"Failed to decode eventId: $err")
                          .raiseError[F, EventId]
      given ValueDeserializer[F, Event] <- Resource.pure:
        Deserializer.lift: bts =>
          BytesCodec[String]
            .decode(bts)
            .toEither
            .leftMap(err => new RuntimeException(s"Failed to decode event bytes: ${err.message}"))
            .liftTo[F]
            .flatMap: str =>
              decode[Event](str)
                .leftMap(err => new RuntimeException(s"Failed to decode Event JSON: $err"))
                .liftTo[F]
      consumerConfig <- Resource.pure(ConsumerSettings[F, EventId, Event]
          .withBootstrapServers(settings.bootstrapServers.mkString(","))
          .withGroupId(HasNode[F].node.clientId.show)
          .withProperty("auto.offset.reset", "earliest"))
      consumer <- KafkaConsumer.resource(consumerConfig)
    yield new EventSource[F]:
      private def internalStream(topic: EventTopic): Stream[F, CommittableConsumerRecord[F, EventId, Event]] =
        Stream
          .eval(consumer.subscribeTo(topic.path.show)) *>
          consumer
              .records

      override def stream(topic: EventTopic): Stream[F, Event] =
        internalStream(topic)
            .map(_.record.value)

  def loggableEventSource[F[+_] : {HasNode, Logger}](eventSource: EventSource[F]): Resource[F, EventSource[F]] =
    Resource
        .pure:
          (topic: EventTopic) => eventSource
              .stream(topic)


  def metricsCollectableEventSource[F[+_] : {HasNode, Logger}](eventSource: EventSource[F]): Resource[F, EventSource[F]] =
    Resource
        .pure:
          (topic: EventTopic) =>
            eventSource
                .stream(topic)
