package com.monadial.waygrid.common.application.syntax

import cats.effect.Concurrent
import fs2.{Pull, Stream}

object StreamSyntax:


  extension [F[_] : Concurrent, A](self: Stream[F, A])

    /**
     * Applies a given effectful function to the first element of the stream, if it exists, without modifying the stream contents.
     *
     * If the stream is empty, no effect is applied.
     *
     * @param f function to apply to the first element
     * @example
     * {{{
     *   Stream(1, 2, 3)
     *     .evalTapFirst(i => Sync[F].delay(println(s"First element: $i")))
     *     .compile
     *     .toList
     * }}}
     * Prints "First element: 1" and returns List(1, 2, 3)
     */
    def evalTapFirst(f: A => F[Unit]): Stream[F, A] =
      self
        .pull
        .uncons1
        .flatMap:
          case Some((head, tail)) =>
            Pull.eval(f(head)) >> Pull.output1(head) >> tail.pull.echo
          case None =>
            Pull.done
        .stream
