package com.monadial.waygrid.common.application.domain.model.fsm

import com.monadial.waygrid.common.application.algebra.FSM as FSMAlg
import com.monadial.waygrid.common.application.algebra.FSMResult as FSMRes

/**
 * A functional, tagless wrapper for defining finite state machines in the domain layer.
 *
 * FSM is a convenience implementation of [[FSM]] that expresses transition logic
 * as a curried function `(S, I) => F[(state: S, output: O)]`, where:
 *   - `S` is the current state,
 *   - `I` is the input or domain event,
 *   - `O` is the output (e.g. emitted message, log entry, command),
 *   - and the effect type `F[_]` can capture computations, validations, or side effects.
 *
 * This wrapper allows domain FSMs to be:
 *   - pure or effectful (`Sync`, `Clock`, `HasNode`, etc.),
 *   - easily decorated with logging, metrics, or tracing,
 *   - reused across interpreters (e.g., in-memory, persistent, distributed),
 *   - cleanly tested by mocking `F[_]` (e.g., using `IO`, `StateT`, or `EitherT`).
 *
 * Example usage:
 * {{{
 *   FSM[IO, TopologyState, TopologyEvent, Unit] { (state, event) =>
 *     event match {
 *       case BeginRegistration => Sync[IO].pure(FSMResult(TopologyState.Registering, ()))
 *       case _                 => Sync[IO].raiseError(...)
 *     }
 *   }
 * }}}
 *
 * @param run the transition function
 * @tparam F effect type
 * @tparam S state
 * @tparam I input (event)
 * @tparam O | Unit output
 */
final case class FSM[F[+_], S, I, O](run: (S, I) => F[FSMRes[S, O]]) extends FSMAlg[F, S, I, O]:
  def transition(state: S, input: I): F[FSMRes[S, O]] = run(state, input)
