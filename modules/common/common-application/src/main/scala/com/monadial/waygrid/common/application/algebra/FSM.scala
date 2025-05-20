package com.monadial.waygrid.common.application.algebra

import cats.Functor
import cats.syntax.all.*

type FSMResult[S, O]                = (state: S, output: O)
type FSMTransformer[F[+_], S, I, O] = FSM[F, S, I, O] => FSM[F, S, I, O]

/**
 * A final tagless, effectful Finite State Machine definition.
 *
 * This trait models a stateful domain process, where each input (event) causes a transition
 * from one state to another, producing an output. Transitions may include effects, validations,
 * side-effectful computations, or domain-specific decisions.
 *
 * This FSM Trait is designed to live in the domain layer, and can be interpreted by a stateful executor
 * such as FSM Algebra in the application layer. It is fully composable and can be decorated with
 * logging, metrics, tracing, or history tracking via pure functional decorators.
 *
 * @tparam F effect type (e.g. IO, Task)
 * @tparam S state type
 * @tparam I input/event type
 * @tparam O output produced by a transition
 */
trait FSM[F[+_], S, I, O]:
  def transition(state: S, input: I): F[FSMResult[S, O]]

object FSM:
  def unit[F[+_]: Functor, S, I](run: (S, I) => F[FSMResult[S, Unit]]): FSM[F, S, I, Unit] =
    (state: S, input: I) =>
      run(state, input)
        .map(newState => (newState.state, ()))
