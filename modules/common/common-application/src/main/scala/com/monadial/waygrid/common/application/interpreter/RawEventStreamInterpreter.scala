package com.monadial.waygrid.common.application.interpreter

import com.monadial.waygrid.common.application.algebra.RawEventStream
import com.monadial.waygrid.common.application.model.event.{ EventStream, RawEvent }

import cats.effect.Resource
import fs2.Stream

object RawEventStreamInterpreter:

  def noop[F[+_]]: RawEventStream[F] =
    new RawEventStream[F]:
      override def open(stream: EventStream): Resource[F, Stream[F, RawEvent]] =
        Resource.pure(Stream.empty)
