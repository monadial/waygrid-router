package com.monadial.waygrid.common.domain.algebra.ddd

import com.monadial.waygrid.common.domain.algebra.messaging.event.Event

final case class AggregateHandlerResult[I, S <: AggregateRootState[I, S], E <: Event](
  state: S,
  events: Seq[E]
)

object AggregateHandlerResult:
  def withEvent[I, S <: AggregateRootState[I, S], E <: Event](
    state: S,
    event: E
  ): AggregateHandlerResult[I, S, E] =
    AggregateHandlerResult[I, S, E](state.bump, Seq(event))

  def combine[I, S <: AggregateRootState[I, S], E <: Event](
    left: AggregateHandlerResult[I, S, E],
    right: AggregateHandlerResult[I, S, E]
  ): AggregateHandlerResult[I, S, E] =
    right.copy(events = left.events ++ right.events)
