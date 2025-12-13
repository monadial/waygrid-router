package com.monadial.waygrid.common.application.kafka

import cats.effect.kernel.Ref
import cats.effect.syntax.all.*
import cats.effect.{Async, Clock, Concurrent, Resource}
import cats.implicits.*
import com.monadial.waygrid.common.application.algebra.EventSource.{RebalanceEvent, RebalanceHandler}
import com.monadial.waygrid.common.application.algebra.{EventSource, Logger, ThisNode, TransportEnvelopeCodec}
import com.monadial.waygrid.common.application.domain.model.envelope.TransportEnvelope
import com.monadial.waygrid.common.application.domain.model.settings.Kafka
import com.monadial.waygrid.common.application.instances.CirceInstances.given
import com.monadial.waygrid.common.application.interpreter.TransportEnvelopeCodecInterpreter
import com.monadial.waygrid.common.application.kafka.KafkaUtils.{toEndpoint, toTopic}
import com.monadial.waygrid.common.application.kafka.Tags.{Consumer, RebalanceListener}
import com.monadial.waygrid.common.application.kafka.Value.Key
import com.monadial.waygrid.common.application.util.cats.effect.{FiberT, FiberType}
import com.monadial.waygrid.common.application.util.circe.codecs.ApplicationTransportEnvelopeCodecs.given
import com.monadial.waygrid.common.domain.algebra.messaging.event.Event
import com.monadial.waygrid.common.domain.algebra.value.codec.BytesCodec
import com.monadial.waygrid.common.domain.model.envelope.DomainEnvelope
import com.monadial.waygrid.common.domain.value.Address.Endpoint
import fs2.kafka.*
import io.circe.Json
import org.typelevel.otel4s.context.propagation.TextMapGetter
import org.typelevel.otel4s.metrics.{Counter, Histogram, Meter, UpDownCounter}
import org.typelevel.otel4s.trace.{SpanContext, Tracer}
import scodec.bits.ByteVector

import java.nio.charset.Charset

object Tags:
  trait Consumer          extends FiberType
  trait RebalanceListener extends FiberType

// -----------------------------------------------------------------------------
// Metrics Definition
// -----------------------------------------------------------------------------
final case class KafkaEventSourceMetrics[F[+_]](
  messagesReceived: Counter[F, Long],
  messagesProcessed: Counter[F, Long],
  messagesFailed: Counter[F, Long],
  messagesSkipped: Counter[F, Long],
  deserializeErrors: Counter[F, Long],
  commitsTotal: Counter[F, Long],
  commitsFailed: Counter[F, Long],
  inFlightMessages: UpDownCounter[F, Long],
  assignedPartitions: UpDownCounter[F, Long],
  rebalanceEvents: Counter[F, Long],
  decodeLatency: Histogram[F, Long],
  processLatency: Histogram[F, Long],
  commitLatency: Histogram[F, Long],
  endToEndLatency: Histogram[F, Long]
)

object KafkaEventSourceMetrics:
  def create[F[+_]: Meter]: Resource[F, KafkaEventSourceMetrics[F]] =
    for
      messagesReceived  <- Resource.eval(Meter[F].counter[Long]("kafka_event_source_messages_received_total").create)
      messagesProcessed <- Resource.eval(Meter[F].counter[Long]("kafka_event_source_messages_processed_total").create)
      messagesFailed    <- Resource.eval(Meter[F].counter[Long]("kafka_event_source_messages_failed_total").create)
      messagesSkipped   <- Resource.eval(Meter[F].counter[Long]("kafka_event_source_messages_skipped_total").create)
      deserializeErrors <- Resource.eval(Meter[F].counter[Long]("kafka_event_source_deserialize_errors_total").create)
      commitsTotal      <- Resource.eval(Meter[F].counter[Long]("kafka_event_source_commits_total").create)
      commitsFailed     <- Resource.eval(Meter[F].counter[Long]("kafka_event_source_commits_failed_total").create)
      inFlightMessages <-
        Resource.eval(Meter[F].upDownCounter[Long]("kafka_event_source_inflight_messages").withUnit("1").create)
      assignedPartitions <-
        Resource.eval(Meter[F].upDownCounter[Long]("kafka_event_source_assigned_partitions").withUnit("1").create)
      rebalanceEvents <- Resource.eval(Meter[F].counter[Long]("kafka_event_source_rebalance_events_total").create)
      decodeLatency <-
        Resource.eval(Meter[F].histogram[Long]("kafka_event_source_decode_latency_ms").withUnit("ms").create)
      processLatency <-
        Resource.eval(Meter[F].histogram[Long]("kafka_event_source_process_latency_ms").withUnit("ms").create)
      commitLatency <-
        Resource.eval(Meter[F].histogram[Long]("kafka_event_source_commit_latency_ms").withUnit("ms").create)
      endToEndLatency <-
        Resource.eval(Meter[F].histogram[Long]("kafka_event_source_end_to_end_latency_ms").withUnit("ms").create)
    yield KafkaEventSourceMetrics(
      messagesReceived,
      messagesProcessed,
      messagesFailed,
      messagesSkipped,
      deserializeErrors,
      commitsTotal,
      commitsFailed,
      inFlightMessages,
      assignedPartitions,
      rebalanceEvents,
      decodeLatency,
      processLatency,
      commitLatency,
      endToEndLatency
    )

object KafkaEventSource:

  private final case class PartitionAssignment(endpoint: Endpoint, partitions: List[Int])

  def default[F[+_]: {Async, Concurrent, ThisNode, Logger, Tracer, Meter}](
    settings: Kafka.Settings
  ): Resource[F, EventSource[F]] =
    for
      _       <- Resource.eval(Logger[F].info("[kafka-event-source] Allocating Kafka consumer"))
      metrics <- KafkaEventSourceMetrics.create[F]

      given KeyDeserializer[F, Key] <- Resource.pure(
        Deserializer.lift(x => Key(ByteVector(x)).pure[F])
      )

      // Deserializer with error handling - returns Either instead of failing
      given ValueDeserializer[F, TransportEnvelope] <- Resource.pure(Deserializer.lift(x =>
        for
          json <- BytesCodec[Json].decodeFromScalar(ByteVector(x)).liftTo[F]
          raw  <- json.as[TransportEnvelope].liftTo[F]
        yield raw
      ))

      consumerSettings <- Resource.pure(
        ConsumerSettings[F, Key, TransportEnvelope]
          .withBootstrapServers(settings.bootstrapServers.mkString(","))
          .withEnableAutoCommit(false)
          .withAutoOffsetReset(AutoOffsetReset.Earliest)
          .withClientId(settings.clientId)
          .withGroupId(settings.clientId)
          // Production settings
          .withMaxPollRecords(settings.batch.maxEvents)
          .withProperty("max.poll.interval.ms", "300000")
          .withProperty("session.timeout.ms", "45000")
          .withProperty("heartbeat.interval.ms", "15000")
          .withProperty("fetch.min.bytes", "1")
          .withProperty("fetch.max.wait.ms", "500")
      )

      codec <- Resource.pure(TransportEnvelopeCodecInterpreter.default[F])
    yield new EventSource[F]:

      override def subscribe(endpoint: Endpoint)(handler: Handler): Resource[F, Unit] =
        subscribeInternal(endpoint, None)(handler)

      override def subscribe(
        endpoint: Endpoint,
        rebalanceHandler: RebalanceHandler[F]
      )(handler: Handler): Resource[F, Unit] =
        subscribeInternal(endpoint, Some(rebalanceHandler))(handler)

      private def subscribeInternal(
        endpoint: Endpoint,
        maybeRebalanceHandler: Option[RebalanceHandler[F]]
      )(handler: Handler): Resource[F, Unit] =
        for
          kafkaConsumer        <- KafkaConsumer.resource(consumerSettings)
          topic                <- Resource.eval(endpoint.toTopic[F])
          partitionAssignments <- Resource.eval(Ref.of[F, Option[PartitionAssignment]](None))
          _                    <- Resource.eval(Logger[F].info(s"[kafka-event-source] Subscribing to topic: $topic"))
          _                    <- Resource.eval(kafkaConsumer.subscribeTo(topic))

          rebalanceListener <- maybeRebalanceHandler.traverse { rebalanceHandler =>
            runRebalanceListener(kafkaConsumer, metrics)(partitionAssignments, rebalanceHandler)
          }

          consumer <- runConsumer(kafkaConsumer, codec, metrics)(handler)

          _ <- Resource.onFinalize:
              Logger[F].info("[kafka-event-source] Stopping consumer...") *>
                kafkaConsumer.stopConsuming *>
                rebalanceListener.traverse_(_.cancel) *>
                consumer.cancel *>
                Logger[F].info("[kafka-event-source] Consumer stopped.")
        yield ()

      private def runConsumer(
        consumer: KafkaConsumer[F, Key, TransportEnvelope],
        codec: TransportEnvelopeCodec[F],
        metrics: KafkaEventSourceMetrics[F]
      )(handler: Handler): Resource[F, FiberT[F, Consumer, Unit]] =
        given TextMapGetter[Headers] = new TextMapGetter[Headers]:
          override def get(carrier: Headers, key: String): Option[String] =
            carrier(key).map(x => String(x.value, Charset.forName("UTF-8")))
          override def keys(carrier: Headers): Iterable[String] =
            carrier.toChain.toIterable.map(_.key)

        Resource.eval:
            consumer
              .partitionedRecords
              .map: partitionedStream =>
                partitionedStream
                  .evalMap: record =>
                    val messageStart = System.currentTimeMillis()
                    metrics.messagesReceived.inc() *>
                      metrics.inFlightMessages.add(1) *>
                      Tracer[F].joinOrRoot(record.record.headers):
                          Tracer[F].span("[kafka-event-source] Processing event").use: span =>
                              processRecord(record, codec, metrics, handler, messageStart, span.context)
                        .guarantee(metrics.inFlightMessages.add(-1))
              .parJoin(settings.batch.maxParallelBatchConcurrency)
              .through(commitBatchWithin[F](settings.batch.maxEvents, settings.batch.maxDuration))
              .evalTap: _ =>
                metrics.commitsTotal.inc()
              .compile
              .drain
              .handleErrorWith: ex =>
                Logger[F].error(s"[kafka-event-source] Fatal error in consumer: ${ex.getMessage}") *>
                  ex.raiseError[F, Unit]
              .onCancel:
                Logger[F].info("[kafka-event-source] Consumer fiber cancelled.")
              .start
              .map(FiberT[F, Consumer, Unit])

      private def processRecord(
        record: CommittableConsumerRecord[F, Key, TransportEnvelope],
        codec: TransportEnvelopeCodec[F],
        metrics: KafkaEventSourceMetrics[F],
        handler: Handler,
        messageStart: Long,
        spanCtx: SpanContext,
      ): F[CommittableOffset[F]] =
            for
              decodeStart    <- Clock[F].monotonic
              decodedEnvelope <- codec.decode(record.record.value).attempt
              domainEnvelope <- decodedEnvelope
                  .fold(fa => fa.raiseError[F, DomainEnvelope[? <: Event]], _.pure[F])
              decodeEnd      <- Clock[F].monotonic
              _              <- metrics.decodeLatency.record((decodeEnd - decodeStart).toMillis)
              _ <- Logger[F].trace(
                s"[kafka-event-source] Processing event ${domainEnvelope.id.show} " +
                  s"from partition ${record.offset.topicPartition.partition}"
              )
              processStart <- Clock[F].monotonic
              processResult <- handler
                .applyOrElse(
                  domainEnvelope.asInstanceOf[DomainEnvelope[? <: Event]],
                  (_: DomainEnvelope[? <: Event]) =>
                    Logger[F].warn(s"[kafka-event-source] Unhandled event: ${domainEnvelope.id.show}")
                )
                .as(true)
                .handleErrorWith: ex =>
                  Logger[F].error(
                    s"[kafka-event-source] Handler failed for ${domainEnvelope.id.show}: ${ex.getMessage}"
                  ) *> false.pure[F]
              processEnd <- Clock[F].monotonic
              _          <- metrics.processLatency.record((processEnd - processStart).toMillis)
              _ <- if processResult then metrics.messagesProcessed.inc()
              else metrics.messagesFailed.inc()
              messageEnd <- Clock[F].realTime
              _          <- metrics.endToEndLatency.record(messageEnd.toMillis - messageStart)
            yield record.offset

      private def runRebalanceListener(
        consumer: KafkaConsumer[F, Key, TransportEnvelope],
        metrics: KafkaEventSourceMetrics[F]
      )(
        partitionAssignments: Ref[F, Option[PartitionAssignment]],
        rebalanceHandler: RebalanceHandler[F]
      ): Resource[F, FiberT[F, RebalanceListener, Unit]] =
        Resource.eval:
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
                  _ <- current.traverse_(c => metrics.assignedPartitions.add(-c.partitions.size.toLong))
                  _ <- assigned.traverse_(a => metrics.assignedPartitions.add(a.partitions.size.toLong))
                  event <- (current, assigned) match
                    case (None, None) =>
                      Logger[F].debug("[kafka-event-source] Rebalance starting") *>
                        RebalanceEvent.Started.pure[F]

                    case (Some(c), None) =>
                      Logger[F].debug(
                        s"[kafka-event-source] Rebalance revoked: lost ${c.partitions.size} partitions"
                      ) *>
                        RebalanceEvent.Revoked(c.endpoint, c.partitions).pure[F]

                    case (None, Some(a)) =>
                      Logger[F].info(
                        s"[kafka-event-source] Initial assignment: ${a.partitions.size} partitions"
                      ) *>
                        partitionAssignments.set(Some(a)) *>
                        RebalanceEvent.Completed(a.endpoint, a.partitions).pure[F]

                    case (Some(c), Some(a)) =>
                      val gained = a.partitions.diff(c.partitions)
                      val lost   = c.partitions.diff(a.partitions)
                      Logger[F].info(
                        s"[kafka-event-source] Rebalance completed: gained ${gained.size}, lost ${lost.size}, " +
                          s"now have ${a.partitions.size} partitions"
                      ) *>
                        partitionAssignments.set(Some(a)) *>
                        RebalanceEvent.Completed(a.endpoint, a.partitions).pure[F]

                  _ <- metrics.rebalanceEvents.inc()
                  _ <- rebalanceHandler(event)
                yield ()
              .compile
              .drain
              .start
              .map(FiberT[F, RebalanceListener, Unit])
