package com.monadial.waygrid.common.application.syntax

import com.monadial.waygrid.common.application.`macro`.EventRouterMacro
import com.monadial.waygrid.common.application.algebra.EventSource

import cats.effect.implicits.*
import cats.effect.{ Async, Fiber }
import cats.implicits.*
import com.monadial.waygrid.common.application.domain.model.event.EventStream

object EventSourceSyntax:

  /**
   * Internal helper: lift a total handler into a PartialFunction that is always defined.
   * This avoids creating a fresh anonymous class at each subscribe site.
   *
   * @param handler the total function to lift
   * @tparam F effect type
   * @return a PartialFunction that delegates every event to `handler`
   */
  private def wrapAsPF[F[+_]](
    handler: EventSource[F]#Evt => F[Unit]
  ): PartialFunction[EventSource[F]#Evt, F[Unit]] =
    new PartialFunction[EventSource[F]#Evt, F[Unit]]:
      def isDefinedAt(evt: EventSource[F]#Evt): Boolean = true
      def apply(evt: EventSource[F]#Evt): F[Unit]       = handler(evt)

  extension [F[+_]: Async](es: EventSource[F])

    /**
     * Subscribe with a compile-time routed set of handlers.
     *
     * {{{
     *   es.subscribeHandlers(EventStream("foo")) {
     *     route[UserCreated]   { e => ... }
     *     routeWithPriority { e => ... }
     *   }
     * }}}
     *
     * Under the hood this:
     *  1. Invokes the `EventRouterMacro` to build an `EventRouter[F]` from your `handle[…]` definitions.
     *  2. Wraps `router.route` in a reusable `PartialFunction` so you don't reallocate an anonymous class on each call.
     *  3. Delegates to `es.subscribe(stream)(...)`.
     *
     * @param stream       the logical Kafka topic(s) to subscribe to
     * @param buildRoutes  a context function in which you call `handle[...]` / `handleWithPriority[...]`
     */
    inline def subscribeRoutes(
      stream: EventStream
    )(inline buildRoutes: EventRouterMacro.Builder[F] ?=> Unit): F[Fiber[F, Throwable, Unit]] =
      subscribeRoutesTo(List(stream))(buildRoutes)

    /**
     * Same as `subscribeHandlers`, but for subscribing to *multiple* streams in parallel.
     *
     * @param streams      a list of logical Kafka topics
     * @param buildRoutes  DSL block of `handle[...]` / `handleWithPriority[...]`
     */
    inline def subscribeRoutesTo(
      streams: List[EventStream]
    )(inline buildRoutes: EventRouterMacro.Builder[F] ?=> Unit): F[Fiber[F, Throwable, Unit]] =
      for
        router     <- EventRouterMacro.build[F](buildRoutes).pure[F]
        subscriber <- es.subscribeTo(streams)(wrapAsPF(router.route)).pure[F]
        fiber      <- subscriber.useForever.as(()).start
      yield fiber
