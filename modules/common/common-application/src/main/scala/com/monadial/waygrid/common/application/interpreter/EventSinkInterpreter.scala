package com.monadial.waygrid.common.application.interpreter

import cats.Applicative
import cats.effect.{Async, Concurrent, Resource}
import cats.implicits.*
import com.monadial.waygrid.common.application.algebra.{EventSink, HasNode, Logger}
import com.monadial.waygrid.common.application.model.event.{Event, EventId}
import com.monadial.waygrid.common.application.model.settings.Kafka.Settings as KafkaEventStreamSettings
import com.monadial.waygrid.common.domain.instances.StringInstances.given
import com.monadial.waygrid.common.domain.value.codec.BytesCodec
import fs2.kafka.*
import fs2.{Pipe, Stream}
import io.circe.*
import io.circe.syntax.*

import scala.concurrent.duration.*

object EventSinkInterpreter:

  def kafka[F[+_] : {HasNode, Logger, Async, Concurrent}](settings: KafkaEventStreamSettings): Resource[F, EventSink[F]] =
    for
      given KeySerializer[F, EventId] <- Resource.pure(Serializer.lift[F, EventId](eventId => BytesCodec[String].encode(eventId.asJson.noSpaces).pure[F]))
      given ValueSerializer[F, Event] <- Resource.pure(Serializer.lift[F, Event](event => BytesCodec[String].encode(event.asJson.noSpaces).pure[F]))
      producerConfig <- Resource.pure(ProducerSettings[F, EventId, Event]
          .withBootstrapServers(settings.bootstrapServers.mkString(","))
          .withClientId(HasNode[F].node.clientId.show)
      )
      producer <- KafkaProducer.resource(producerConfig)
    yield new EventSink[F]:
      override def pipe: Pipe[F, Event, Unit] =
        _
          .groupWithin(100, 1.second)
          .mapAsync(16): events =>
            events
              .traverse:
                event =>
                  Logger[F].debug(s"producing event: ${event}") >>
                      ProducerRecord[EventId, Event](event.topic.path.show, event.id, event).pure[F]
          .evalMap(x => producer.produce(x))
          .void

  def blackhole[F[+_] : {Applicative, Logger}]: Resource[F, EventSink[F]] = Resource.pure(
    new EventSink[F]:
      override def pipe: Pipe[F, Event, Unit] = (events: Stream[F, Event]) =>
        events
            .evalMap(event => Applicative[F].unit)
  )


  def loggableEventSink[F[+_]: {HasNode, Logger}](eventSink: EventSink[F]): Resource[F, EventSink[F]] =
    Resource
      .pure:
        new EventSink[F]:
          override def pipe: Pipe[F, Event, Unit] =
            eventSink.pipe.andThen { stream =>
              stream.evalMap { event =>
                  Logger[F].info(s"Event produced: $event")
              }
            }

  def metricsCollectableEventSink[F[+_]: {HasNode, Logger}](eventSink: EventSink[F]): Resource[F, EventSink[F]] =
    Resource
      .pure:
        new EventSink[F]:
          override def pipe: Pipe[F, Event, Unit] =
            eventSink.pipe.andThen { stream =>
              stream.evalMap { event =>
                Logger[F].info(s"Event produced: $event")
              }
            }

