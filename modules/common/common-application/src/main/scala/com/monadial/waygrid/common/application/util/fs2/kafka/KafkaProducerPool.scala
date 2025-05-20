package com.monadial.waygrid.common.application.util.fs2.kafka

import cats.effect.*
import cats.effect.std.Semaphore
import cats.syntax.all.*
import com.monadial.waygrid.common.application.algebra.Logger
import fs2.kafka.*

trait KafkaProducerPool[F[+_], K, V]:
  def lease[A](use: KafkaProducer[F, K, V] => F[A]): F[A]

object KafkaProducerPool:
  def make[F[+_]: {Async, Logger}, K, V](
    settings: ProducerSettings[F, K, V],
    poolSize: Int
  ): Resource[F, KafkaProducerPool[F, K, V]] =
    for
      _         <- Resource.eval(Logger[F].info(s"Creating KafkaProducerPool with size $poolSize"))
      semaphore <- Resource.eval(Semaphore[F](poolSize.toLong))
      nextIndex <- Resource.eval(Ref.of[F, Int](0))
      slots <- Resource.eval((0 until poolSize).toVector.traverse { _ =>
        Ref.of[F, Option[Resource[F, KafkaProducer[F, K, V]]]](None)
      })
    yield new KafkaProducerPool[F, K, V]:
      override def lease[A](use: KafkaProducer[F, K, V] => F[A]): F[A] =
        semaphore.permit.use { _ =>
          for
            idx <- nextIndex.modify(i => ((i + 1) % poolSize, i))
            slot = slots(idx)
            resourceOpt <- slot.get
            resource <- resourceOpt match
              case Some(res) => res.pure[F]
              case None =>
                for
                  _ <- Logger[F].debug(s"Creating producer at slot $idx")
                  res = KafkaProducer.resource(settings)
                  _ <- slot.set(Some(res))
                yield res
            result <- resource.use(use)
          yield result
        }
