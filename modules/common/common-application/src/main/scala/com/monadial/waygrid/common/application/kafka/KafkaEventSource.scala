package com.monadial.waygrid.common.application.kafka

import cats.effect.kernel.Ref
import cats.effect.syntax.all.*
import cats.effect.{ Async, Concurrent, Resource }
import cats.implicits.*
import com.monadial.waygrid.common.application.algebra.EventSource.{ RebalanceEvent, RebalanceHandler }
import com.monadial.waygrid.common.application.algebra.{ EventSource, Logger, ThisNode }
import com.monadial.waygrid.common.application.domain.model.envelope.TransportEnvelope
import com.monadial.waygrid.common.application.domain.model.settings.Kafka
import com.monadial.waygrid.common.application.instances.CirceInstances.given
import com.monadial.waygrid.common.application.interpreter.TransportEnvelopeCodecInterpreter
import com.monadial.waygrid.common.application.kafka.KafkaUtils.{ toEndpoint, toTopic }
import com.monadial.waygrid.common.application.kafka.Tags.{ Consumer, RebalanceListener }
import com.monadial.waygrid.common.application.kafka.Value.Key
import com.monadial.waygrid.common.application.util.cats.effect.{ FiberT, FiberType }
import com.monadial.waygrid.common.application.util.circe.codecs.ApplicationTransportEnvelopeCodecs.given
import com.monadial.waygrid.common.domain.algebra.messaging.event.Event
import com.monadial.waygrid.common.domain.algebra.value.codec.BytesCodec
import com.monadial.waygrid.common.domain.model.envelope.DomainEnvelope
import com.monadial.waygrid.common.domain.value.Address.Endpoint
import fs2.kafka.*
import io.circe.Json
import org.typelevel.otel4s.context.propagation.TextMapGetter
import org.typelevel.otel4s.trace.Tracer
import scodec.bits.ByteVector

import java.nio.charset.Charset
import scala.concurrent.duration.*

object Tags:
  trait Consumer          extends FiberType
  trait RebalanceListener extends FiberType

object KafkaEventSource:

  final case class PartitionAssignment(endpoint: Endpoint, partitions: List[Int])

  def default[F[+_]: {Async, Concurrent, ThisNode, Logger, Tracer}](
    settings: Kafka.Settings
  ): Resource[F, EventSource[F]] =
    for
      _ <- Resource.eval(Logger[F].info("Allocating Kafka consumer"))
      given KeyDeserializer[F, Key] <-
        Resource.pure(Deserializer.lift(x => Key(ByteVector(x)).pure[F]))
      given ValueDeserializer[F, TransportEnvelope] <- Resource.pure(Deserializer.lift(x =>
        for
          json <- BytesCodec[Json].decodeFromScalar(ByteVector(x)).liftTo[F]
          raw  <- json.as[TransportEnvelope].liftTo[F]
        yield raw
      ))
      consumerSettings <- Resource.pure(ConsumerSettings[F, Key, TransportEnvelope]
        .withBootstrapServers(settings.bootstrapServers.mkString(","))
        .withEnableAutoCommit(false)
        .withAutoOffsetReset(AutoOffsetReset.Earliest)
        .withClientId(settings.clientId)
        .withGroupId(settings.clientId))
      codec <- Resource.pure(TransportEnvelopeCodecInterpreter.default[F])
    yield new EventSource[F]:
      override def subscribe(
        endpoint: Endpoint
      )(handler: Handler): Resource[F, Unit] =
        for
          kafkaConsumer <- KafkaConsumer.resource(consumerSettings)
          topic         <- Resource.eval(endpoint.toTopic[F])
          _             <- Resource.eval(Logger[F].info(s"Subscribing to topic: ${topic}"))
          _             <- Resource.eval(kafkaConsumer.subscribeTo(topic, Nil*))
          consumer      <- runConsumer(kafkaConsumer)(handler)
          _ <- Resource.onFinalize:
              kafkaConsumer.stopConsuming *>
                consumer.cancel *>
                Logger[F].info("Stopping Kafka consumer")
        yield ()

      override def subscribe(
        endpoint: Endpoint,
        rebalanceHandler: RebalanceHandler[F]
      )(handler: Handler): Resource[F, Unit] =
        for
          kafkaConsumer        <- KafkaConsumer.resource(consumerSettings)
          topic                <- Resource.eval(endpoint.toTopic[F])
          partitionAssignments <- Resource.eval(Ref.of[F, Option[PartitionAssignment]](None))
          _                    <- Resource.eval(Logger[F].info(s"Subscribing to topic: ${topic}"))
          _                    <- Resource.eval(kafkaConsumer.subscribeTo(topic, Nil*))
          rebalanceListener    <- runRebalanceListener(kafkaConsumer)(partitionAssignments, rebalanceHandler)
          consumer             <- runConsumer(kafkaConsumer)(handler)
          _ <- Resource.onFinalize:
              kafkaConsumer.stopConsuming *>
                rebalanceListener.cancel *>
                consumer.cancel *>
                Logger[F].info("Stopping Kafka consumer")
        yield ()

      private def runConsumer(consumer: KafkaConsumer[F, Key, TransportEnvelope])(handler: Handler)
        : Resource[F, FiberT[F, Consumer, Unit]] =
        given TextMapGetter[Headers] = new TextMapGetter[Headers]:
          override def get(carrier: Headers, key: String): Option[String] =
            carrier(key)
              .map(x => String(x.value, Charset.forName("UTF-8")))
          override def keys(carrier: Headers): Iterable[String] =
            carrier
              .toChain
              .toIterable
              .map(_.key)

        Resource
          .eval:
            consumer
              .partitionedRecords
              .map: partitionedStream =>
                partitionedStream
                  .evalMap: record =>
                    Tracer[F].joinOrRoot(record.record.headers) {
                      Tracer[F].span(">>>[event-source] Processing event").use {
                        span =>
                          for
                            _ <- Logger[F].trace(
                              s"Received event id: ${record.record.key.unwrap.toString} on partition ${record.offset.topicPartition}"
                            )
                            _ <- record.offset.commit
                            event <- Tracer[F].childScope(span.context):
                                (codec.decode(record.record.value))
                          yield (record.offset, event)
                      }
                    }
                  .evalMap: (offset, event) =>
                    handler.applyOrElse(
                      event.asInstanceOf[DomainEnvelope[? <: Event]],
                      _ => Logger[F].warn(s"Unhandled event: ${event.id.show}")
                    ) *> offset.pure[F]
              .parJoinUnbounded
              .through(commitBatchWithin[F](500, 1.seconds))
              .compile
              .drain
              .handleErrorWith: ex =>
                Logger[F].error(s"Error in Kafka consumer: ${ex.getMessage}") *> ex.raiseError[F, Unit]
              .onCancel {
                Logger[F].info("✅ Kafka consumer fiber cancelled, stopping...")
              }
              .start
              .map(FiberT[F, Consumer, Unit])

      private def runRebalanceListener(consumer: KafkaConsumer[F, Key, TransportEnvelope])(
        partitionAssignments: Ref[F, Option[PartitionAssignment]],
        rebalanceHandler: EventSource.RebalanceEvent => F[Unit]
      ): Resource[F, FiberT[F, RebalanceListener, Unit]] =
        for
          listener <- Resource
            .eval:
              consumer
                .assignmentStream
                .evalTap: assignment =>
                  for
                    current <- partitionAssignments.get
                    assigned <- assignment
                      .toList
                      .traverse(k => k.toEndpoint[F].tupleRight(k.partition))
                      .map(_.groupMap(_._1)(_._2))
                      .map(_.headOption)
                      .map(_.map(PartitionAssignment(_, _)))
                    event <- (current, assigned) match
                      case (None, None) =>
                        for
                          _     <- Logger[F].debug("Rebalance is starting")
                          event <- RebalanceEvent.Started.pure[F]
                        yield event
                      case (Some(c), None) =>
                        for
                          _     <- Logger[F].debug("Rebalance is revoked assignment is cleared")
                          event <- RebalanceEvent.Revoked(c.endpoint, c.partitions).pure[F]
                        yield event
                      case (None, Some(a)) =>
                        for
                          _     <- Logger[F].debug(s"Initial rebalance assignment completed")
                          _     <- partitionAssignments.set(Some(a))
                          event <- RebalanceEvent.Completed(a.endpoint, a.partitions).pure[F]
                        yield event
                      case (Some(c), Some(a)) =>
                        for
                          _     <- Logger[F].debug(s"Rebalance completed")
                          _     <- partitionAssignments.set(Some(a))
                          event <- RebalanceEvent.Completed(a.endpoint, a.partitions).pure[F]
                        yield event
                    _ <- rebalanceHandler(event)
                  yield ()
                .compile
                .drain
                .start
                .map(FiberT[F, RebalanceListener, Unit])
        yield listener
