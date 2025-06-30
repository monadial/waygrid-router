package com.monadial.waygrid.common.application.syntax

import com.monadial.waygrid.common.application.algebra.FSM as FSMAlg
import com.monadial.waygrid.common.application.algebra.FSMResult as FSMRes
import com.monadial.waygrid.common.application.algebra.FSMTransformer as FSMDec
import cats.Monad
import cats.implicits.*
import com.monadial.waygrid.common.application.domain.model.fsm.FSM as FSMDom

object FSMSyntax:

  extension [F[+_], S, I, O](fsm: FSMAlg[F, S, I, O])
    /**
     * Applies a functional decorator to this FSM instance.
     *
     * A decorator is a higher-order transformation that wraps an FSM with additional behavior
     * such as logging, metrics, tracing, or history tracking. This method enables fluent
     * composition of FSM behaviors without modifying the core FSM logic.
     *
     * Example:
     * {{{
     *   fsm
     *     .transform(logTransitions(logger))
     *     .transform(trackMetrics(metrics))
     * }}}
     *
     * @param decorator a function that takes an FSM and returns an FSM with additional behavior
     * @return the decorated FSM
     */
    def transform(decorator: FSMDec[F, S, I, O]): FSMAlg[F, S, I, O] = decorator(fsm)

  inline given [F[+_]: Monad, S, I]: Monad[[A] =>> FSMAlg[F, S, I, A]] with
    def pure[A](a: A): FSMAlg[F, S, I, A]                                                     = FSMDom((s, _) => Monad[F].pure(s, a))
    def flatMap[A, B](fa: FSMAlg[F, S, I, A])(f: A => FSMAlg[F, S, I, B]): FSMAlg[F, S, I, B] = fa.flatMap(f)
    def tailRecM[A, B](a: A)(f: A => FSMAlg[F, S, I, Either[A, B]]): FSMAlg[F, S, I, B] =
      FSMDom: (state, input) =>
        def loop(curr: A, state: S): F[FSMRes[S, B]] =
          f(curr)
            .transition(state, input)
            .flatMap:
              case (nextS, Left(nextA)) => loop(nextA, nextS)
              case (nextS, Right(b))    => Monad[F].pure(nextS, b)
        loop(a, state)
