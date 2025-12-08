package com.monadial.waygrid.common.domain.algebra.fsm

import com.monadial.waygrid.common.domain.model.fsm.Value.Result

type TraversalResult[S, E, O] = Result[S, E, O]

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
trait FSM[F[+_], S, I, E, O]:
  def transition(state: S, input: I): F[TraversalResult[S, E, O]]

object FSM:
  def unit[F[+_], S, I, E](run: (S, I) => F[TraversalResult[S, E, Unit]]): FSM[F, S, I, E, Unit] =
    (state: S, input: I) =>
      run(state, input)
