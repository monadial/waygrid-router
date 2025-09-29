package com.monadial.waygrid.common.domain.algebra.ddd

import com.monadial.waygrid.common.domain.model.command.Command

sealed trait AggregateHandlerError

object AggregateHandlerError:
  final case class HandlerNotFound[C <: Command](command: C) extends AggregateHandlerError
  final case class AlreadyExists[I](id: I)                   extends AggregateHandlerError
