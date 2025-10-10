package com.monadial.waygrid.common.application.interpreter

import com.monadial.waygrid.common.application.algebra.{EventSource, Logger}
import com.monadial.waygrid.common.application.instances.CirceInstances.given
import com.monadial.waygrid.common.domain.value.codec.BytesCodec
import cats.effect.*
import cats.effect.implicits.*
import cats.syntax.all.*
import com.monadial.waygrid.common.application.domain.model.event.{EventId, EventStream, RawEvent}
import com.monadial.waygrid.common.application.domain.model.settings.Kafka
import fs2.kafka.*
import io.circe.Json
import org.apache.kafka.common.TopicPartition

object EventSourceInterpreter:

  def kafka[F[+_]: {Async, Concurrent, Logger}](settings: Kafka.Settings): Resource[F, EventSource[F]] =
    for
      _ <- Resource.eval(Logger[F].info("Allocating Kafka consumer"))
      given KeyDeserializer[F, EventId] <-
        Resource.pure(Deserializer.lift(x => BytesCodec[EventId].decode(x).liftTo[F]))
      given ValueDeserializer[F, RawEvent] <- Resource.pure(Deserializer.lift(x =>
        for
          json <- BytesCodec[Json].decode(x).liftTo[F]
          raw  <- json.as[RawEvent].liftTo[F]
        yield raw
      ))
      consumerSettings <- Resource.pure(ConsumerSettings[F, EventId, RawEvent]
        .withBootstrapServers(settings.bootstrapServers.mkString(","))
        .withEnableAutoCommit(false)
        .withAutoOffsetReset(AutoOffsetReset.Earliest)
        .withClientId(settings.clientId)
        .withGroupId(settings.clientId))


    yield new EventSource[F]:
      override def subscribe(stream: EventStream)(handler: Handler): Resource[F, Unit] =
        subscribeTo(List(stream))(handler)
      override def subscribeTo(streams: List[EventStream])(handler: Handler): Resource[F, Unit] =
        for
          _ <- Resource.eval(Logger[F].info(s"Subscribing to streams: ${streams.map(_.show).mkString(", ")}"))
          currentAssignment <- Resource.eval(Ref.of[F, Set[TopicPartition]](Set.empty))
          kafkaConsumer <- KafkaConsumer.resource(consumerSettings)
          _ <- Resource.eval(streams.map(_.show) match
            case first :: rest => kafkaConsumer.subscribeTo(first, rest*)
            case Nil           => Concurrent[F].raiseError(new IllegalArgumentException("No streams to subscribe to")))
          assignmentMonitorFiber <- Resource
            .eval:
              kafkaConsumer
                .assignmentStream
                .evalMap: assignment =>
                  if assignment.isEmpty then
                    Logger[F].warn("Kafka consumer assignment is empty, no partitions assigned")
                  else
                    Logger[F].info(s"Kafka consumer assigned partitions: ${assignment.mkString(", ")}")
                .compile
                .drain
                .handleErrorWith: ex =>
                  Logger[F].error(s"Error monitoring Kafka assignment: ${ex.getMessage}") *> ex.raiseError[F, Unit]
                .onCancel {
                  Logger[F].info("✅ Kafka assignment monitor fiber cancelled, stopping...")
                }
                .start
          // consumerFiber <- Resource
          //   .eval:
          //     kafkaConsumer
          //       .partitionedRecords
          //       .map: partitionedStream =>
          //         partitionedStream
          //           .evalMap: record =>
          //             for
          //               _ <- Logger[F].trace(
          //                 s"Received event id: ${record.record.key.show} on partition ${record.offset.topicPartition}"
          //               )
          //               event <- EventCodecInterpreter[F].decode(record.record.value)
          //             yield (record.offset, event)
          //           .evalMap: (offset, event) =>
          //             handler.applyOrElse(
          //               event,
          //               _ => Logger[F].warn(s"Unhandled event: ${event.id.show}")
          //             ) *> offset.pure[F]
          //       .parJoinUnbounded
          //       .through(commitBatchWithin[F](500, 1.seconds))
          //       .compile
          //       .drain
          //       .handleErrorWith: ex =>
          //         Logger[F].error(s"Error in Kafka consumer: ${ex.getMessage}") *> ex.raiseError[F, Unit]
          //       .onCancel {
          //         Logger[F].info("✅ Kafka consumer fiber cancelled, stopping...")
          //       }
          //       .start
          // _ <- Resource.onFinalize:
          //     Logger[F].info("Stopping Kafka consumer") *>
          //       kafkaConsumer.stopConsuming *>
          //       consumerFiber.cancel *>
          //       assignmentMonitorFiber.cancel *>
          //       Logger[F].info("✅ Kafka consumer stopped")
        yield ()
