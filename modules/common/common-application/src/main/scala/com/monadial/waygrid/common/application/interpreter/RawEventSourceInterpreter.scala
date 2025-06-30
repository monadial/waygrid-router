package com.monadial.waygrid.common.application.interpreter

import com.monadial.waygrid.common.application.algebra.{ Logger, RawEventSource }
import com.monadial.waygrid.common.application.instances.CirceInstances.given
import com.monadial.waygrid.common.domain.value.codec.BytesCodec

import cats.effect.{ Async, Resource }
import cats.syntax.all.*
import com.monadial.waygrid.common.application.domain.model.event.{EventId, EventStream, RawEvent}
import com.monadial.waygrid.common.application.domain.model.settings.Kafka
import fs2.Stream
import fs2.kafka.*
import io.circe.Json

object RawEventSourceInterpreter:

  def kafka[F[+_]: {Async, Logger}](settings: Kafka.Settings): Resource[F, RawEventSource[F]] =
    for
      given KeyDeserializer[F, EventId] <- Resource.pure(Deserializer.lift(x =>
        BytesCodec[EventId].decode(x).fold(
          err => Async[F].raiseError(err),
          id => Async[F].pure(id)
        )
      ))
      given ValueDeserializer[F, RawEvent] <- Resource.pure(Deserializer.lift(x =>
        BytesCodec[Json].decode(x).fold(
          err => Async[F].raiseError(err),
          json =>
            json.as[RawEvent].fold(
              err => Async[F].raiseError(err),
              rawEvent => Async[F].pure(rawEvent)
            )
        )
      ))
      _ <- Resource.eval(Logger[F].info("Creating Kafka consumer"))
      consumerSettings <- Resource.pure(ConsumerSettings[F, EventId, RawEvent]
        .withBootstrapServers(settings.bootstrapServers.mkString(","))
        .withEnableAutoCommit(true)
        .withAutoOffsetReset(AutoOffsetReset.Earliest)
        .withMaxPollRecords(1_000)
        .withGroupId("waygrid-consumer"))
      consumer <- KafkaConsumer.resource[F, EventId, RawEvent](consumerSettings)
      _ <- Resource.onFinalize:
          consumer.stopConsuming *> Logger[F].info("Kafka consumer stopped")
    yield new RawEventSource[F]:
      override def open(stream: EventStream): Resource[F, Stream[F, RawEvent]] =
        for
          _ <- Resource.eval(Logger[F].info(s"Subscribing to stream: ${stream.show}"))
          _ <- Resource.eval(consumer.subscribeTo(stream.show))
        yield consumer
          .stream
          .map(_.record)
          .map(_.value)

  def noop[F[+_]]: RawEventSource[F] =
    new RawEventSource[F]:
      override def open(stream: EventStream): Resource[F, Stream[F, RawEvent]] =
        Resource.pure(Stream.empty)
