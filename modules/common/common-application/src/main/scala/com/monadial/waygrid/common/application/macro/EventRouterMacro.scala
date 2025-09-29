package com.monadial.waygrid.common.application.`macro`

import com.monadial.waygrid.common.application.algebra.EventRouter
import cats.effect.Concurrent
import cats.implicits.*
import com.monadial.waygrid.common.application.domain.model.envelope.Envelope
import com.monadial.waygrid.common.application.domain.model.event.Event
import com.monadial.waygrid.common.domain.model.event.Event as DomainEvent
import shapeless3.typeable.Typeable

import scala.quoted.*

object EventRouterMacro:

  /**
   * Build an EventRouter at compile time from a block of `handle` / `handleWithPriority` calls.
   *
   * Inside your block you bring DSL methods into scope:
   * {{{
   *   handle[MyEvent]       { ev => ... }
   *   handleWithPriority(5) { ev => ... }
   * }}}
   *
   * @tparam F   the effect type (e.g. IO)
   * @param block  a context‐function that invokes `handle` / `handleWithPriority`
   * @param A      a Concurrent[F] instance to allow fiber‐forking
   * @return       an EventRouter[F] that will dispatch and fork each matching handler
   */
  transparent inline def build[F[+_]](
    inline block: Builder[F] ?=> Unit
  )(using A: Concurrent[F]): EventRouter[F] =
    ${ buildImpl[F]('block, 'A) }

  /** Implementation: summon a fresh Builder, run the user DSL, then emit its router */
  private def buildImpl[F[+_]: Type](
    block: Expr[Builder[F] ?=> Unit],
    concr: Expr[Concurrent[F]]
  )(using Quotes): Expr[EventRouter[F]] =
    '{
      // 1) make a new Builder[F]
      val b = new Builder[F](using $concr)
      // 2) bring it into scope so `handle` can call b.handle(...)
      given Builder[F] = b
      // 3) execute the user’s DSL block, accumulating clauses
      $block
      // 4) finally, emit the assembled router
      b.toRouter
    }

  /**
   * Internal accumulator of handler clauses.
   *
   * Each clause is a (priority, PartialFunction) pair.  When we build the router,
   * we sort descending by priority and fork each matching handler.
   */
  class Builder[F[+_]](using A: Concurrent[F]):

    private val clauses =
      scala.collection.mutable.ListBuffer.empty[
        (Int, PartialFunction[Envelope[? <: DomainEvent], F[Unit]])
      ]

    /**
     * Register one handler at the given priority.
     *
     * Uses a shapeless Typeable[E] to safely cast the raw domain‐event to E.
     *
     * @param f         your handler function from `Event[E]` to `F[Unit]`
     * @param priority  higher values run **before** lower ones
     * @param tpe        Typeable instance to test-and-cast at runtime
     */
    def handle[E <: DomainEvent](
      f: Envelope[E] => F[Unit],
      priority: Int
    )(using tpe: Typeable[E]): Unit =
      clauses += (priority -> new PartialFunction[Envelope[? <: DomainEvent], F[Unit]]:
        override def isDefinedAt(evt: Envelope[? <: DomainEvent]): Boolean = tpe.cast(evt.message).isDefined
        override def apply(evt: Envelope[? <: DomainEvent]): F[Unit]       = f(evt.asInstanceOf[Envelope[E]]))
//
//    /**
//     * Turn all accumulated clauses into one EventRouter[F].
//     *
//     * For a given incoming event, it:
//     *  1. sorts handlers by descending priority
//     *  2. filters only those whose PF matches the event
//     *  3. forks each matched handler into its own fiber
//     *  4. sequences all forks as one `F[Unit]`
//     */
//    def toRouter: EventRouter[F] =
//      // sort _once_ when you build the router
//      val sortedPFs = clauses.sortBy { case (prio, _) => -prio }.map(_._2).toList
//      new EventRouter[F]:
//        def route(evt: Event[? <: DomainEvent]): F[Unit] =
//          sortedPFs
//            .iterator
//            .filter(_.isDefinedAt(evt))
//            .map(_.apply(evt))
//            .toList
//            .traverse_(h => A.start(h).void)
//
//    // inside EventRouterMacro.Builder[F]

    /**
     * Combine all handlers into one super‐fast EventRouter.
     * We sort once and then dispatch via a tight while‐loop.
     */
    def toRouter: EventRouter[F] =
      // pre‐sort your handlers once
      val sortedPFs: Array[PartialFunction[Envelope[? <: DomainEvent], F[Unit]]] =
        clauses
          .sortBy { case (prio, _) => -prio }
          .map(_._2)
          .toArray

      (evt: Envelope[? <: DomainEvent]) =>
        sortedPFs.foldLeft(A.unit) { (acc, pf) =>
          acc.flatMap { _ =>
            if pf.isDefinedAt(evt) then pf(evt)
            else A.unit
          }
        }
