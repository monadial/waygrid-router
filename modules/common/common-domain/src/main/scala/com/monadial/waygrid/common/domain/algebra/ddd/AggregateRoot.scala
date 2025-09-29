package com.monadial.waygrid.common.domain.algebra.ddd

import cats.data.Kleisli
import cats.syntax.all.*
import com.monadial.waygrid.common.domain.model.command.Command
import com.monadial.waygrid.common.domain.model.event.Event

/**
 * The result of handling a command on an aggregate, producing either an error
 * or a new state along with events.
 */
type AggResult[I, S <: AggregateRootState[I, S], E <: Event] =
  Either[AggregateHandlerError, AggregateHandlerResult[I, S, E]]

/**
 * The effect type for aggregate handlers, constrained to Either with
 * AggregateHandlerError.
 */
type AggEffect[I, S <: AggregateRootState[I, S], E <: Event] =
  [A] =>> Either[AggregateHandlerError, A]

/**
 * Models a handler for commands on an aggregate, implemented as a Kleisli
 * from command and optional state to a handling result.
 */
type Handler[I, S <: AggregateRootState[I, S], C <: Command, E <: Event] =
  Kleisli[AggEffect[I, S, E], (C, Option[S]), AggregateHandlerResult[I, S, E]]

/**
 * Trait for aggregates that define handlers for commands. The `handle` method
 * delegates to the appropriate handler or returns a HandlerNotFound error.
 */
trait AggregateRoot[I, S <: AggregateRootState[I, S], C <: Command, E <: Event]:
  protected def handlers: Seq[Handler[I, S, C, E]]

  final def handle(command: C, state: Option[S]): AggResult[I, S, E] =
    handlers
      .reduceOption(_ <+> _)
      .getOrElse(Handler.notFound)
      .run((command, state))
