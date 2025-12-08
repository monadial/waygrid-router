package com.monadial.waygrid.common.application.syntax

import cats.effect.Async
import cats.effect.implicits.*
import cats.implicits.*
import com.monadial.waygrid.common.application.`macro`.EventRouterMacro
import com.monadial.waygrid.common.application.algebra.EventSource
import com.monadial.waygrid.common.application.algebra.EventSource.RebalanceHandler
import com.monadial.waygrid.common.application.util.cats.effect.{ FiberT, FiberType }
import com.monadial.waygrid.common.domain.SystemWaygridApp
import com.monadial.waygrid.common.domain.value.Address.Endpoint
import com.monadial.waygrid.common.domain.value.Address.EndpointDirection.Inbound

object EventSourceSyntax:

  trait EventSubscriber extends FiberType

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
    inline def subscribeTo(
      endpoint: Endpoint
    )(inline buildRoutes: EventRouterMacro.Builder[F] ?=> Unit): F[FiberT[F, EventSubscriber, Unit]] =
      for
        router     <- EventRouterMacro.build[F](buildRoutes).pure[F]
        subscriber <- es.subscribe(endpoint)(wrapAsPF(router.route)).pure[F]
        fiber      <- subscriber.useForever.as(()).start
      yield FiberT[F, EventSubscriber, Unit](fiber)

    inline def subscribeToWithRebalance(
      endpoint: Endpoint,
      rebalanceHandler: RebalanceHandler[F]
    )(inline buildRoutes: EventRouterMacro.Builder[F] ?=> Unit): F[FiberT[F, EventSubscriber, Unit]] =
      for
        router     <- EventRouterMacro.build[F](buildRoutes).pure[F]
        subscriber <- es.subscribe(endpoint, rebalanceHandler)(wrapAsPF(router.route)).pure[F]
        fiber      <- subscriber.useForever.as(()).start
      yield FiberT[F, EventSubscriber, Unit](fiber)

    inline def subscribeToWaystationInboundEvents(
      inline buildRoutes: EventRouterMacro.Builder[F] ?=> Unit
    ): F[FiberT[F, EventSubscriber, Unit]] =
      subscribeTo(
        SystemWaygridApp.Waystation.toServiceAddress.toEndpoint(Inbound)
      )(buildRoutes)

    inline def subscribeToWaystationInboundEventsWithRebalance(rebalanceHandler: RebalanceHandler[F])(
      inline buildRoutes: EventRouterMacro.Builder[F] ?=> Unit
    ): F[FiberT[F, EventSubscriber, Unit]] =
      subscribeToWithRebalance(
        SystemWaygridApp.Waystation.toServiceAddress.toEndpoint(Inbound),
        rebalanceHandler
      )(buildRoutes)
