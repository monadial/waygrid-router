package com.monadial.waygrid.common.application.syntax

import cats.effect.Temporal
import cats.syntax.all.*
import com.monadial.waygrid.common.domain.model.resiliency.Backoff.*
import com.monadial.waygrid.common.domain.model.resiliency.Value.HashKey
import com.monadial.waygrid.common.domain.model.resiliency.{ Backoff, RetryPolicy }

object BackoffSyntax:

  extension [F[+_]: {Temporal}, A](fa: F[A])

    def retryOnFailure[P <: RetryPolicy](
      policy: P,
      hashKey: Option[HashKey] = None
    )(using Backoff[P]): F[A] =
      def loop(attempt: Int): F[A] =
        fa
          .handleErrorWith: err =>
            policy.nextDelay(attempt, hashKey) match
              case None =>
                Temporal[F].raiseError(err)
              case Some(delay) =>
                Temporal[F].sleep(delay) *> loop(attempt + 1)
      loop(1)

    def retryOnResult[P <: RetryPolicy](
      policy: P,
      hashKey: Option[HashKey] = None
    )(isBad: A => Boolean)(using Backoff[P]): F[A] =
      def loop(attempt: Int): F[A] =
        fa
          .flatMap: a =>
            if !isBad(a) then a.pure[F]
            else
              policy.nextDelay(attempt, hashKey) match
                case None =>
                  a.pure[F]
                case Some(delay) =>
                  Temporal[F].sleep(delay) *> loop(attempt + 1)
      loop(1)
