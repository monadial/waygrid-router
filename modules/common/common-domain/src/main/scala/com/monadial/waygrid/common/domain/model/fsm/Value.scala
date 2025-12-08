package com.monadial.waygrid.common.domain.model.fsm

object Value:

  final case class Result[S, E, O](state: S, output: Either[E, O])
