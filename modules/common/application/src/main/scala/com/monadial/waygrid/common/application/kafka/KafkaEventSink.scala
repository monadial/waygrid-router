package com.monadial.waygrid.common.application.kafka

import cats.effect.implicits.*
import cats.effect.{ Async, Clock, Resource }
import cats.implicits.*
import com.monadial.waygrid.common.application.algebra.{ EventSink, Logger, ThisNode }
import com.monadial.waygrid.common.application.domain.model.envelope.TransportEnvelope
import com.monadial.waygrid.common.application.domain.model.settings.Kafka
import com.monadial.waygrid.common.application.interpreter.TransportEnvelopeCodecInterpreter
import com.monadial.waygrid.common.application.kafka.KafkaUtils.toTopic
import com.monadial.waygrid.common.application.kafka.Value.Key
import com.monadial.waygrid.common.application.util.scodec.codecs.ApplicationTransportEnvelopeScodecCodecs.given
import com.monadial.waygrid.common.domain.algebra.messaging.event.Event
import com.monadial.waygrid.common.domain.algebra.messaging.message.Value.{ MessageGroupId, MessageId }
import com.monadial.waygrid.common.domain.algebra.messaging.message.{ Groupable, Message }
import com.monadial.waygrid.common.domain.algebra.value.codec.BytesCodec
import com.monadial.waygrid.common.domain.model.envelope.DomainEnvelope
import com.monadial.waygrid.common.domain.value.Address.Endpoint
import fs2.Stream
import fs2.kafka.*
import scodec.Codec
import org.typelevel.otel4s.context.propagation.TextMapUpdater
import org.typelevel.otel4s.experimental.metrics.InstrumentedQueue
import org.typelevel.otel4s.metrics.{ Counter, Histogram, Meter, UpDownCounter }
import org.typelevel.otel4s.trace.{ SpanContext, Tracer }

import scala.concurrent.duration.*

// -----------------------------------------------------------------------------
// Metrics Definition
// -----------------------------------------------------------------------------
private final case class KafkaEventSinkMetrics[F[+_]](
  realtimeQueueCounter: UpDownCounter[F, Long],
  encodeSuccess: Counter[F, Long],
  encodeFailure: Counter[F, Long],
  encodeLatency: Histogram[F, Long],
  eventDequeued: Counter[F, Long],
  batchSize: Histogram[F, Long],
  inFlightBatches: UpDownCounter[F, Long],
  batchFailure: Counter[F, Long],
  batchLatency: Histogram[F, Long]
)

object KafkaEventSinkMetrics:
  def create[F[+_]: Meter]: Resource[F, KafkaEventSinkMetrics[F]] =
    for
      realtimeQueueCounter <-
        Resource.eval(Meter[F].upDownCounter[Long]("event_sink_queue_realtime").withUnit("1").create)
      encodeSuccess <-
        Resource.eval(Meter[F].counter[Long]("event_sink_encode_success_total").create)
      encodeFailure <-
        Resource.eval(Meter[F].counter[Long]("event_sink_encode_failure_total").create)
      encodeLatency <-
        Resource.eval(Meter[F].histogram[Long]("event_sink_encode_latency_ms").withUnit("ms").create)
      eventDequeued <-
        Resource.eval(Meter[F].counter[Long]("event_sink_event_dequeued_total").create)
      batchSize <-
        Resource.eval(Meter[F].histogram[Long]("event_sink_batch_size").withUnit("events").create)
      inFlightBatches <-
        Resource.eval(Meter[F].upDownCounter[Long]("event_sink_inflight_batches").withUnit("1").create)
      batchFailure <-
        Resource.eval(Meter[F].counter[Long]("event_sink_batch_failure_total").withUnit("events").create)
      batchLatency <-
        Resource.eval(Meter[F].histogram[Long]("event_sink_batch_latency_ms").withUnit("ms").create)
    yield KafkaEventSinkMetrics(
      realtimeQueueCounter,
      encodeSuccess,
      encodeFailure,
      encodeLatency,
      eventDequeued,
      batchSize,
      inFlightBatches,
      batchFailure,
      batchLatency
    )

object KafkaEventSink:

  private def spawnProducer[F[+_]: Async](
    settings: Kafka.Settings
  ): Resource[F, KafkaProducer[F, Key, TransportEnvelope]] =
    for
      given KeySerializer[F, Key] <- Resource.pure(Serializer.lift[F, Key](_.unwrap.toArrayUnsafe.pure[F]))
      given ValueSerializer[F, TransportEnvelope] <- Resource.pure(Serializer.lift[F, TransportEnvelope] { envelope =>
        Codec[TransportEnvelope].encode(envelope) match
          case scodec.Attempt.Successful(bits) => bits.toByteArray.pure[F]
          case scodec.Attempt.Failure(err)     => Async[F].raiseError(new RuntimeException(s"Failed to encode TransportEnvelope: ${err.messageWithContext}"))
      })
      producerSettings = ProducerSettings[F, Key, TransportEnvelope]
        .withBootstrapServers(settings.bootstrapServers.mkString(","))
        .withAcks(settings.sink.acks.getOrElse(Acks.All))
        .withRequestTimeout(settings.sink.requestTimeout)
        .withBatchSize(64 * 1024)
        .withLinger(settings.sink.linger)
        .withClientId(settings.clientId)
        .withEnableIdempotence(true)
        .withRetries(Int.MaxValue)
        .withMaxInFlightRequestsPerConnection(5)
        .withProperty("compression.type", "zstd")
        .withProperty("delivery.timeout.ms", "120000")
      producer <- KafkaProducer.resource(producerSettings)
    yield producer

  private def resolveKey[F[+_]: Async, M <: Message](envelope: DomainEnvelope[M]): F[Key] =
    envelope.message match
      case m: Groupable => BytesCodec[MessageGroupId].encodeToValue[Key](m.groupId).pure[F]
      case m            => BytesCodec[MessageId].encodeToValue[Key](m.id).pure[F]

  // ---------------------------------------------------------------------------
  // Main Logic
  // ---------------------------------------------------------------------------
  def default[F[+_]: {Async, ThisNode, Meter, Tracer, Logger}](
    settings: Kafka.Settings
  ): Resource[F, EventSink[F]] =
    given TextMapUpdater[Headers] = (carrier: Headers, key: String, value: String) => carrier.append(key, value)

    for
      _        <- Resource.eval(Logger[F].info("[event-sink] Starting Kafka sink"))
      metrics  <- KafkaEventSinkMetrics.create[F]
      codec    <- Resource.pure(TransportEnvelopeCodecInterpreter.default[F])
      producer <- spawnProducer[F](settings)

      internalQueue <- Resource.eval(
        InstrumentedQueue.bounded[F, Option[(
          Endpoint,
          DomainEnvelope[? <: Event],
          Option[SpanContext]
        )]](settings.batch.maxEvents * 2)
      )

      sinkFiber <- Resource.make(
        Stream
          .fromQueueNoneTerminated(internalQueue)
          .mapAsync(1024): (endpoint, domainEnvelope, spanCtx) =>
            Tracer[F].childOrContinue(spanCtx):
                Tracer[F].span("encode_event").surround:
                    for
                      _             <- metrics.eventDequeued.inc()
                      _             <- metrics.realtimeQueueCounter.add(-1)
                      _             <- Logger[F].trace(s"[event-sink] Encoding event: ${domainEnvelope.id}")
                      startEncoding <- Clock[F].monotonic
                      maybeEnvelope <- codec.encode(domainEnvelope).attempt
                      endEncoding   <- Clock[F].monotonic
                      _             <- metrics.encodeLatency.record((endEncoding - startEncoding).toMillis)
                      result <- maybeEnvelope match
                        case Left(ex) =>
                          Logger[F].error(s"[event-sink] Encoding failed for ${domainEnvelope.id}: ${ex.getMessage}") *>
                            metrics.encodeFailure.inc().as(None)
                        case Right(transportEnvelope) =>
                          metrics.encodeSuccess.inc() *>
                            endpoint.toTopic[F].flatMap: topic =>
                                resolveKey(domainEnvelope).flatMap: key =>
                                    Tracer[F].propagate(Headers.empty).map: traceHeaders =>
                                        Some(ProducerRecord(topic, key, transportEnvelope).withHeaders(traceHeaders))
                    yield result
          .unNone
          .groupWithin(settings.batch.maxEvents, settings.batch.maxDuration)
          .evalMap: chunk =>
            for
              now <- Clock[F].monotonic
              batchSize = chunk.size.toLong
              _         <- Logger[F].debug(s"[event-sink] Sending batch of $batchSize events to Kafka driver")
              _         <- metrics.batchSize.record(batchSize)
              ackEffect <- producer.produce(chunk)
            yield (ackEffect, now, batchSize)
          .mapAsync(500): (ackEffect, startTime, batchSize) =>
            metrics.inFlightBatches.add(1) *>
              ackEffect
                .flatMap: _ =>
                  Clock[F].monotonic.flatMap: endTime =>
                      val latency = (endTime - startTime).toMillis
                      Logger[F].debug(s"[event-sink] Batch of $batchSize acknowledged in ${latency}ms") *>
                        metrics.batchLatency.record(latency)
                .handleErrorWith: err =>
                  Logger[F].error(s"[event-sink] Batch failed permanently: ${err.getMessage}") *>
                    metrics.batchFailure.add(batchSize)
                .guarantee(metrics.inFlightBatches.add(-1))
          .compile
          .drain
          .start
      )(fiber =>
        Logger[F].info("[event-sink] Stopping... sealing queue.") *>
          internalQueue.offer(None) *>
          Logger[F].info("[event-sink] Queue sealed. Waiting for drain...") *>
          fiber.join.timeoutTo(15.seconds, Logger[F].warn("[event-sink] Drain timed out! Force stopping.")) *>
          Logger[F].info("[event-sink] Stopped.")
      )
    yield new EventSink[F]:
      override def send(endpoint: Endpoint, event: DomainEnvelope[? <: Event], spanCtx: Option[SpanContext]): F[Unit] =
        metrics.realtimeQueueCounter.add(1) *>
          internalQueue.offer(Some(endpoint, event, spanCtx))

      override def sendBatch(events: List[(Endpoint, DomainEnvelope[? <: Event], Option[SpanContext])]): F[Unit] =
        metrics.realtimeQueueCounter.add(events.size.toLong) *>
          events.traverse_(e => internalQueue.offer(Some(e)))
