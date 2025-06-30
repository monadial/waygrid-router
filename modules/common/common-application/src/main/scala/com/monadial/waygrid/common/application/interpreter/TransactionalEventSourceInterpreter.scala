//package com.monadial.waygrid.common.application.interpreter
//
//import com.monadial.waygrid.common.application.algebra.{ Logger, TransactionalEventSource }
//import com.monadial.waygrid.common.application.instances.CirceInstances.given
//import com.monadial.waygrid.common.application.model.event.{ Event, EventId, EventStream, RawEvent }
//import com.monadial.waygrid.common.application.model.settings.Kafka
//import com.monadial.waygrid.common.domain.model.event.Event as DomainEvent
//import com.monadial.waygrid.common.domain.value.codec.BytesCodec
//
//import cats.effect.implicits.*
//import cats.effect.kernel.Fiber
//import cats.effect.std.Queue
//import cats.effect.{ Async, Concurrent, Resource }
//import cats.implicits.*
//import cats.syntax.all.*
//import fs2.kafka.*
//import io.circe.Json
//import io.circe.syntax.*
//
//object TransactionalEventSourceInterpreter:
//
//  def kafka[F[+_]: {Async, Concurrent, Logger}](settings: Kafka.Settings): Resource[F, TransactionalEventSource[F]] =
//
//    def spawnProducerPool: Resource[F, Queue[F, TransactionalKafkaProducer[F, EventId, RawEvent]]] =
//      for
//        given KeySerializer[F, EventId] <-
//          Resource.pure(Serializer.lift[F, EventId](x => BytesCodec[EventId].encode(x).pure[F]))
//        given ValueSerializer[F, RawEvent] <-
//          Resource.pure(Serializer.lift[F, RawEvent](x => BytesCodec[Json].encode(x.asJson).pure[F]))
//        producerSettings = ProducerSettings[F, EventId, RawEvent]
//          .withBootstrapServers(settings.bootstrapServers.mkString(","))
//          .withEnableIdempotence(true)
//          .withRetries(3)
//        pool <-
//          Resource.eval(Queue.bounded[F, TransactionalKafkaProducer[F, EventId, RawEvent]](settings.sink.poolSize))
//        _ <- (0 until settings.sink.poolSize).toList.traverse_ { i =>
//          val transactionalSettings =
//            TransactionalProducerSettings(s"kafka-producer-$i", producerSettings.withClientId(s"kafka-producer-$i"))
//          TransactionalKafkaProducer
//            .resource[F, EventId, RawEvent](transactionalSettings)
//            .evalTap(producer => pool.offer(producer))
//        }
//      yield pool
//
//    for
//      _ <- Resource.eval(Logger[F].info("Creating Transactional Kafka consumer"))
//      given KeyDeserializer[F, EventId] <-
//        Resource.pure(Deserializer.lift(x => BytesCodec[EventId].decode(x).liftTo[F]))
//      given ValueDeserializer[F, RawEvent] <- Resource.pure(Deserializer.lift(x =>
//        for
//          json <- BytesCodec[Json].decode(x).liftTo[F]
//          raw  <- json.as[RawEvent].liftTo[F]
//        yield raw
//      ))
//
//      consumerSettings <- Resource.pure(ConsumerSettings[F, EventId, RawEvent]
//        .withBootstrapServers(settings.bootstrapServers.mkString(","))
//        .withEnableAutoCommit(false)
//        .withAutoOffsetReset(AutoOffsetReset.Earliest)
//        .withMaxPollRecords(1000)
//        .withClientId("test-12")
//        .withGroupId("waygrid-consumer-123"))
//    yield new TransactionalEventSource[F]:
//      override def subscribe(stream: EventStream, handler: Handler): Resource[F, Unit] =
//        for
//          producerPool <- spawnProducerPool
//          consumer     <- KafkaConsumer.resource(consumerSettings)
//          _            <- Resource.eval(consumer.subscribeTo(stream.show))
//          _ <- Resource
//            .eval:
//              consumer
//                .records
//                .evalTap(r => Logger[F].info(s"Received: ${r.record.value.id}"))
//                .evalMap { committable =>
//                  for
//                    rawEvt <- Async[F].pure(committable.record.value)
//                    evt <- EventCodecInterpreter[F].decode(rawEvt).onError { case e =>
//                      Logger[F].error("Decode failed", e)
//                    }
//                    _  <- Logger[F].info(s"Decoded event: ${evt.id.show}")
//                    _  <- handler.applyOrElse(evt, (_: Event[? <: DomainEvent]) => Logger[F].warn("Unhandled event"))
//                    pr <- ProducerRecord(EventStream("topology.out").show, rawEvt.id, rawEvt).pure[F]
//                    _  <- Logger[F].info("Produced transactional record")
//                  yield CommittableProducerRecords.one(pr, committable.offset)
//                }
//                .evalMap { record =>
//                  for
//                    producer <- producerPool.take
//                    _        <- producer.produce(TransactionalProducerRecords.one(record)).void
//                    _        <- producerPool.offer(producer)
//                    _        <- Logger[F].info(s"Committed offset: ${record.offset}")
//                  yield ()
//                }
//                .compile
//                .drain
//                .onCancel {
//                  Logger[F].info("✅ Kafka consumer fiber cancelled, stopping...") *>
//                    consumer.stopConsuming
//                }
//        yield ()
//
//      override def subscribeTo(
//        streams: List[EventStream],
//        handler: Handler
//      ): F[Fiber[F, Throwable, Unit]] = Logger[F].info("Creating Kafka consumer").start
////        spawnProducerPool.use { pool =>
////          KafkaConsumer.resource(consumerSettings).use { consumer =>
////            streams.map(_.show) match
////              case first :: rest =>
////                Logger[F].info("subscribint to topics") *> consumer.subscribeTo(first, rest*) *>
////                  consumer
////                    .records
////                    .parEvalMapUnordered(settings.batch.parallelism) { committable =>
////                      for
////                        _                 <- Logger[F].info(s"Received event: ${committable.record.value.id.show}")
////                        rawEvt            <- Async[F].pure(committable.record.value)
////                        evt               <- EventCodecInterpreter[F].decode(rawEvt)
////                        _                 <- handler.applyOrElse(evt, (_: Event[? <: DomainEvent]) => Async[F].unit)
////                        committableRecord <- ProducerRecord(EventStream("topology").show, rawEvt.id, rawEvt).pure[F]
////                      yield CommittableProducerRecords.one(committableRecord, committable.offset)
////                    }
////                    .evalMap { record =>
////                      for
////                        producer <- pool.take
////                        _        <- producer.produce(TransactionalProducerRecords.one(record))
////                        _        <- pool.offer(producer)
////                      yield ()
////
////                    }
////                    .compile
////                    .drain
////                    .start
////              case Nil =>
////                Async[F].raiseError(new RuntimeException("Empty stream list"))
////          }
////        }
