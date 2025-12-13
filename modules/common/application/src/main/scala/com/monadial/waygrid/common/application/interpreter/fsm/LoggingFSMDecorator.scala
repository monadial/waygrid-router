package com.monadial.waygrid.common.application.interpreter.fsm

import cats.Monad
import cats.implicits.*
import com.monadial.waygrid.common.application.algebra.{ FSMDecorator, Logger }
import com.monadial.waygrid.common.domain.algebra.fsm.TraversalResult

object LoggingFSMDecorator:

  def decorator[F[+_]: {Monad, Logger}, S, I, E, O]: FSMDecorator[F, S, I, E, O] =
    new FSMDecorator[F, S, I, E, O]:
      override def around(state: S, input: I)(next: => F[TraversalResult[S, E, O]]): F[TraversalResult[S, E, O]] =
        for
          _      <- Logger[F].debug(s"[FSM] Incoming signal: ${input.getClass.getSimpleName}")
          result <- next
          _ <- result.output match
            case Left(error) => Logger[F].error(s"[FSM] Result error: ${error.getClass.getSimpleName}")
            case Right(_)    => Logger[F].debug(s"[FSM] Result signal: ${result.output.getClass.getSimpleName}")
        yield result
