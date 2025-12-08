package com.monadial.waygrid.common.domain.model.fsm

import com.monadial.waygrid.common.domain.algebra.fsm.{ TraversalResult, FSM as FSMAlgebra }

final case class FSM[F[+_], S, I, E, O](run: (S, I) => F[TraversalResult[S, E, O]]) extends FSMAlgebra[F, S, I, E, O]:
  override def transition(state: S, input: I): F[TraversalResult[S, E, O]] = run(state, input)
