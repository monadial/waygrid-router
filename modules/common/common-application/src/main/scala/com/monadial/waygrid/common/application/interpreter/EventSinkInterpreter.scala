//package com.monadial.waygrid.common.application.interpreter
//
//import cats.effect.implicits.*
//import cats.effect.std.Queue
//import cats.effect.{ Async, Resource }
//import cats.implicits.*
//import com.monadial.waygrid.common.application.algebra.{ EventSink, Logger }
//import com.monadial.waygrid.common.application.domain.model.event.{ Event, EventId, RawEvent }
//import com.monadial.waygrid.common.application.domain.model.settings.Kafka
//import com.monadial.waygrid.common.application.instances.CirceInstances.given
//import com.monadial.waygrid.common.domain.algebra.messaging.event.Event as DomainEvent
//import com.monadial.waygrid.common.domain.algebra.value.codec.BytesCodec
//import fs2.Stream
//import fs2.kafka.*
//import io.circe.Json
//import io.circe.syntax.given
//import org.typelevel.otel4s.metrics.{ Counter, Histogram, Meter, UpDownCounter }
//
//
//
//object EventSinkInterpreter:
//
//  def kafka[F[+_]: {Async, Logger, Meter}](settings: Kafka.Settings): Resource[F, EventSink[F]] =
//    def spawnProducer: Resource[F, KafkaProducer[F, EventId, RawEvent]] =
//      for
//        given KeySerializer[F, EventId] <-
//          Resource.pure(Serializer.lift[F, EventId](BytesCodec[EventId].encodeToScalar(_).toArrayUnsafe.pure[F]))
//        given ValueSerializer[F, RawEvent] <-
//          Resource.pure(Serializer.lift[F, RawEvent](x => BytesCodec[Json].encodeToScalar(x.asJson).toArrayUnsafe.pure[F]))
//        producerSettings <- Resource.pure(
//          ProducerSettings[F, EventId, RawEvent]
//            .withBootstrapServers(settings.bootstrapServers.mkString(","))
//            .withAcks(settings.sink.acks.getOrElse(Acks.All))
//            .withRequestTimeout(settings.sink.requestTimeout)
//            .withBatchSize(settings.batch.maxEvents)
//            .withLinger(settings.sink.linger)
//        )
//        producer <- KafkaProducer.resource(producerSettings.withClientId(settings.clientId))
//      yield producer
//
//    for
//      _ <- Resource.eval(Logger[F].info("[event-sink] Starting Kafka sink"))
//      internalQueue <- Resource.eval(Queue.bounded[
//        F,
//        Option[Event[? <: DomainEvent]]
//      ](settings.batch.maxEvents * settings.batch.parallelism * 2))
//      metrics  <- EventSinkMetrics.create[F]
//      producer <- spawnProducer
//
//      publisher <- Resource.eval:
//          Stream
//            .fromQueueNoneTerminated(internalQueue)
////            .evalMap {
////              event =>
////                for
////                  _ <- metrics.realtimeQueueCounter.add(-1L)
////                  _ <- metrics.eventDequeued.inc()
////                  _ <- Logger[F].trace(
////                    s"[event-sink] Dequeued event: ${event.id} (${event.event.getClass.getSimpleName})"
////                  )
////                  _ <- Logger[F].debug(s"[event-sink] Encoding event: ${event.id}")
////
////                  startEnc <- Temporal[F].monotonic
////                  result   <- TransportEnvelopeCodecInterpreter[F].de
//
////                  endEnc   <- Temporal[F].monotonic
////                  _        <- metrics.encodeLatency.record((endEnc - startEnc).toMillis)
////                  encoded <- result match
////                    case Left(ex) =>
////                      metrics.encodeFailure.inc() *>
////                        Logger[F].error(s"[event-sink] Failed to encode ${event.id.show}: ${ex.getMessage}") *>
////                        ex.raiseError[F, RawEvent]
////                    case Right(e) =>
////                      metrics.encodeSuccess.inc() *>
////                        e.pure[F]
////                yield ProducerRecord(encoded.address.show, event.id, encoded)
////            }
//            .groupWithin(settings.batch.maxEvents, settings.batch.maxDuration)
//            .filter(_.nonEmpty)
////            .evalMap { records =>
////              for
////                batch     <- ProducerRecords(records).pure[F]
////                _         <- metrics.batchSize.record(batch.size)
////                _         <- Logger[F].debug(s"[event-sink] Sending batch of ${batch.size} events")
////                startProd <- Temporal[F].monotonic
////                result    <- producer.produce(batch).attempt
////                endProd   <- Temporal[F].monotonic
////                _         <- metrics.batchLatency.record((endProd - startProd).toMillis)
////                _ <- result match
////                  case Left(ex) =>
////                    metrics.batchFailure.inc() *>
////                      Logger[F].error(s"[event-sink] Failed to send batch: ${ex.getMessage}") *>
////                      ex.raiseError[F, Unit]
////                  case Right(_) =>
////                    Logger[F].trace(s"[event-sink] Batch sent successfully")
////              yield ()
////            }
//            .compile
//            .drain
//            .onCancel {
//              internalQueue.offer(None) *>
//                Logger[F].info("[event-sink] Kafka publisher stopped")
//            }
//            .start
//
//      _ <- Resource.onFinalize(
//        publisher.cancel *>
//          Logger[F].info("[event-sink] Kafka sink stopped")
//      )
//    yield new EventSink[F]:
//      override def send(event: Evt): F[Unit] = ???
//      override def sendBatch(events: Iterable[Evt]): F[Unit] = ???
//
//
//
//
////      override def send(event: Event[? <: DomainEvent]): F[Unit] =
////        Logger[F].trace(s"[event-sink] Enqueuing event: ${event.id}") *>
////          metrics.realtimeQueueCounter.add(1L) *>
////          internalQueue.offer(Some(event))
////
////      override def sendBatch(events: List[Event[? <: DomainEvent]]): F[Unit] =
////        events.traverse_(send)
