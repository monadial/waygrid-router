package com.monadial.waygrid.common.application.actor

import com.monadial.waygrid.common.application.actor.HttpServerActorCommand.{
  RegisterRoute,
  RegisterWsRoute,
  UnregisterRoute,
  UnregisterWsRoute
}
import com.monadial.waygrid.common.application.algebra.*
import com.monadial.waygrid.common.application.algebra.SupervisedRequest.{ Restart, Start, Stop }
import com.monadial.waygrid.common.application.http.resource.WellknownResource
import com.monadial.waygrid.common.application.util.logging.LoggerLog4CatsWrapper

import cats.Parallel
import cats.data.Kleisli
import cats.effect.implicits.*
import cats.effect.{ Async, Fiber, Ref, Resource }
import cats.syntax.all.*
import com.monadial.waygrid.common.application.domain.model.settings.HttpServerSettings
import com.suprnation.actor.Actor.ReplyingReceive
import fs2.io.net.Network
import org.http4s.{ HttpRoutes, Request }
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.given
import org.http4s.otel4s.middleware.metrics.OtelMetrics
import org.http4s.server.Server
import org.http4s.server.middleware.Metrics
import org.http4s.server.websocket.WebSocketBuilder2
import org.typelevel.otel4s.metrics.MeterProvider

sealed trait ServerState[F[+_]]
object ServerState:
  case class Stopped[F[+_]]()                                                  extends ServerState[F]
  case class PreStart[F[+_]]()                                                 extends ServerState[F]
  case class Failed[F[+_]](error: Throwable)                                   extends ServerState[F]
  case class Restarting[F[+_]](server: Fiber[F, Throwable, (Server, F[Unit])]) extends ServerState[F]
  case class Running[F[+_]](server: Fiber[F, Throwable, (Server, F[Unit])])    extends ServerState[F]

sealed trait HttpServerActorCommand[F[+_]]
object HttpServerActorCommand:
  final case class RegisterRoute[F[+_]](key: String, route: HttpRoutes[F]) extends HttpServerActorCommand[F]
  final case class RegisterWsRoute[F[+_]](key: String, route: WsRoutes[F]) extends HttpServerActorCommand[F]
  final case class UnregisterRoute[F[+_]](key: String)                     extends HttpServerActorCommand[F]
  final case class UnregisterWsRoute[F[+_]](key: String)                   extends HttpServerActorCommand[F]

type HttpServerActor[F[+_]]    = SupervisedActor[F, HttpServerActorCommand[F]]
type HttpServerActorRef[F[+_]] = SupervisedActorRef[F, HttpServerActorCommand[F]]

type WsRoutes[F[+_]] = WebSocketBuilder2[F] => HttpRoutes[F]

type RouteMap[F[+_]]   = Map[String, HttpRoutes[F]]
type WsRouteMap[F[+_]] = Map[String, WsRoutes[F]]

object HttpServerActor:

  private inline def defaultRoutes[F[+_]: {Async}] = WellknownResource.v1[F]

  def behavior[F[+_]: {Async, Parallel, Logger,
    MeterProvider}](settings: HttpServerSettings): Resource[F, HttpServerActor[F]] =
    for
      given Network[F]    <- Resource.pure(Network.forAsync[F])
      logger              <- Resource.pure(LoggerLog4CatsWrapper.factory[F].getLogger)
      metricsOps          <- Resource.eval(OtelMetrics.serverMetricsOps())
      routeMapRef         <- Resource.eval(Ref.of[F, RouteMap[F]](Map.empty))
      wsRoutesMapRef      <- Resource.eval(Ref.of[F, WsRouteMap[F]](Map.empty))
      combinedRoutesRef   <- Resource.eval(Ref.of[F, HttpRoutes[F]](defaultRoutes[F]))
      combinedWsRoutesRef <- Resource.eval(Ref.of[F, WsRoutes[F]](_ => HttpRoutes.empty[F]))
      stateRef            <- Resource.eval(Ref.of[F, ServerState[F]](ServerState.Stopped()))
    yield new SupervisedActor[F, HttpServerActorCommand[F]]:
      override def receive: ReplyingReceive[F, HttpServerActorCommand[F] | SupervisedRequest, Any] =
        case RegisterRoute(key, route)   => handleRouteRegistration(key, route)
        case RegisterWsRoute(key, route) => handleWsRouteRegistration(key, route)
        case UnregisterRoute(key)        => handleRouteUnregistration(key)
        case UnregisterWsRoute(key)      => handleWsRouteUnregistration(key)
        case Start                       => handleServerStart
        case Stop                        => handleServerStop
        case Restart                     => receive(Stop) *> receive(Start)

      private def handleServerStart: F[Unit] =
        Logger[F].info("HTTP Server actor Start signal received...") *>
          stateRef
            .get
            .flatMap:
              case ServerState.Running(_) =>
                Logger[F].warn("HTTP Server actor is already running...")

              case ServerState.Stopped() =>
                for
                  _ <- Logger[F].info(s"Starting HTTP server on ${settings.host}:${settings.port}")
                  fiber <- EmberServerBuilder
                    .default[F]
                    .withLogger(logger)
                    .withHost(settings.host)
                    .withPort(settings.port)
                    .withMaxConnections(65536)
                    .withHttpWebSocketApp: ws =>
                      Kleisli: req =>
                        for
                          httpRoutes <- combinedRoutesRef.get
                          wsRoutes   <- combinedWsRoutesRef.get.map(_(ws))
                          app <- (Metrics(
                            metricsOps,
                            classifierF = requestClassifier
                          )(httpRoutes) <+> wsRoutes).orNotFound.pure[F]
                          result <- app(req)
                        yield result
                    .build
                    .allocated
                    .start
                    .flatTap(fiber =>
                      Logger[F].info("HTTP server started.") *> stateRef.set(ServerState.Running(fiber))
                    )
                    .handleErrorWith(e =>
                      Logger[F].error(s"Failed to start HTTP server on ${settings.host}:${settings.port}", e) *>
                        stateRef.set(ServerState.Failed(e))
                    )
                yield ()

              case _ =>
                Logger[F].warn("HTTP Server actor is in an unknown state, cannot start.")

      private def handleServerStop: F[Unit] =
        Logger[F].info("HTTP Server actor Stop signal received...") *>
          stateRef
            .get
            .flatMap:
              case ServerState.Running(fiber) =>
                for
                  _ <- Logger[F].info("Stopping HTTP server...")
                  _ <- fiber.cancel
                  _ <- stateRef.set(ServerState.Stopped())
                yield ()

              case ServerState.Stopped() =>
                Logger[F].warn("HTTP Server actor is already stopped...")

              case ServerState.Failed(e) =>
                Logger[F].error("HTTP Server actor is in failed state, cannot stop.", e)

              case _ =>
                Logger[F].warn("HTTP Server actor is in an unknown state, cannot stop.")

      private def handleRouteRegistration(key: String, route: HttpRoutes[F]): F[Unit] =
        Logger[F].info(s"Registering HTTP route with key: $key") *>
          routeMapRef.update(_ + (key -> route)) *> updateCombinedRoutes()

      private def handleRouteUnregistration(key: String): F[Unit] =
        Logger[F].info(s"Unregistering HTTP route with key: $key") *>
          routeMapRef.update(_ - key) *> updateCombinedRoutes()

      private def handleWsRouteRegistration(key: String, route: WsRoutes[F]): F[Unit] =
        Logger[F].info(s"Registering WS route with key: $key") *>
          wsRoutesMapRef.update(_ + (key -> route)) *> updateCombinedWsRoutes()

      private def handleWsRouteUnregistration(key: String): F[Unit] =
        Logger[F].info(s"Unregistering WS route with key: $key") *>
          wsRoutesMapRef.update(_ - key) *> updateCombinedWsRoutes()

      private def updateCombinedRoutes(): F[Unit] =
        for
          routes <- routeMapRef.get
          _ <- combinedRoutesRef.set(
            routes.values.toList.reduceOption(_ <+> _).getOrElse(defaultRoutes[F])
          )
        yield ()

      private def updateCombinedWsRoutes(): F[Unit] =
        for
          routes <- wsRoutesMapRef.get
          combined = (wsBuilder: WebSocketBuilder2[F]) =>
            routes.values.toList.map(_(wsBuilder)).reduceOption(_ <+> _).getOrElse(HttpRoutes.empty[F])
          _ <- combinedWsRoutesRef.set(combined)
        yield ()

      private def requestClassifier: Request[F] => Option[String] = (req) =>
        req.uri.toString.some // todo use a better classifier
