package com.monadial.waygrid.common.application.algebra

import com.monadial.waygrid.common.domain.algebra.fsm.TraversalResult

trait FSMDecorator[F[_], S, I, E, O]:
  def around(
    state: S,
    input: I
  )(
    next: => F[TraversalResult[S, E, O]]
  ): F[TraversalResult[S, E, O]]
