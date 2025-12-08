package com.monadial.waygrid.common.domain.algebra.ddd

import cats.data.Kleisli
import com.monadial.waygrid.common.domain.algebra.messaging.command.Command
import com.monadial.waygrid.common.domain.algebra.messaging.event.Event

object Handler:

  /**
   * Returns a handler that always fails with HandlerNotFound for any input.
   *
   * @tparam I Aggregate identifier type
   * @tparam S Aggregate state type
   * @tparam C Command type
   * @tparam E Event type
   * @return Handler that always returns HandlerNotFound error
   */
  def notFound[I, S <: AggregateRootState[I, S], C <: Command, E <: Event]: Handler[I, S, C, E] =
    Kleisli: in =>
      Left(AggregateHandlerError.HandlerNotFound(in._1))

  /**
   * Creates a handler from a PartialFunction, wrapping the result into Either.
   * If the PartialFunction is not defined for the input, returns HandlerNotFound.
   *
   * @param pf PartialFunction from (Command, Option[State]) to handler result
   * @tparam I Aggregate identifier type
   * @tparam S Aggregate state type
   * @tparam C Command type
   * @tparam E Event type
   * @return Handler that wraps the PartialFunction in Either for error handling
   */
  def fromPartial[I, S <: AggregateRootState[I, S], C <: Command, E <: Event](
    pf: PartialFunction[(C, Option[S]), AggregateHandlerResult[I, S, E]]
  ): Handler[I, S, C, E] =
    Kleisli: in =>
      if pf.isDefinedAt(in) then Right(pf(in))
      else Left(AggregateHandlerError.HandlerNotFound(in._1))

  /**
   * Creates a handler from a PartialFunction that already returns Either, allowing explicit error handling.
   * If the PartialFunction is not defined for the input, returns HandlerNotFound.
   *
   * @param pf PartialFunction from (Command, Option[State]) to Either error or handler result
   * @tparam I Aggregate identifier type
   * @tparam S Aggregate state type
   * @tparam C Command type
   * @tparam E Event type
   * @return Handler that uses the provided PartialFunction for explicit error handling
   */
  def fromEither[I, S <: AggregateRootState[I, S], C <: Command, E <: Event](
    pf: PartialFunction[(C, Option[S]), Either[AggregateHandlerError, AggregateHandlerResult[I, S, E]]]
  ): Handler[I, S, C, E] =
    Kleisli: in =>
      if pf.isDefinedAt(in) then pf(in)
      else Left(AggregateHandlerError.HandlerNotFound(in._1))
