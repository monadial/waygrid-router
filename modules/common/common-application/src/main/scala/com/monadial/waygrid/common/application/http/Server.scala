package com.monadial.waygrid.common.application.http

import cats.Applicative
import cats.effect.Async
import cats.effect.kernel.Resource
import cats.syntax.all.*
import com.comcast.ip4s.{Host, Port}
import com.monadial.waygrid.common.application.algebra
import com.monadial.waygrid.common.application.algebra.{HasNode, Logger}
import com.monadial.waygrid.common.application.http.resource.HealthCheckResource
import fs2.io.net.Network
import org.http4s.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.server.{Router, Server as Http4sServer}
import org.typelevel.log4cats.{SelfAwareStructuredLogger, LoggerFactory as Log4CatsLoggerFactory}

type Server[F[+_]] = Resource[F, Http4sServer]

final case class ServerSettings(
                                   host: Host,
                                   port: Port
)

object Server:

  def serveWellknow[F[+_]: {Async, HasNode, Network, Logger}](settings: ServerSettings): Server[F] =
    mkServer(settings)(wellknownRoutes)(_ => HttpRoutes.empty)

  def serveHttp[F[+_]: {Async, HasNode, Network, Logger}](settings: ServerSettings)(httpRoutes: HttpRoutes[F]): Server[F] =
    mkServer(settings)(wellknownRoutes <+> httpRoutes)(_ => HttpRoutes.empty)

  def serveHttpAndWebsocket[F[+_]: {Async, HasNode, Network, Logger}](settings: ServerSettings)(
    routes: HttpRoutes[F],
    wsRoutes: WebSocketBuilder[F] => HttpRoutes[F]
  ): Server[F] =
    mkServer(settings)(wellknownRoutes <+> routes)(wsRoutes)

  private def mkServer[F[+_]: {Async, HasNode, Network, Logger}](settings: ServerSettings)(httpRoutes: HttpRoutes[F])(
    wsRoutes: WebSocketBuilder[F] => HttpRoutes[F]
  ): Server[F] =
    // log4cats odin logger factory
    given Log4CatsLoggerFactory[F] = OdinLog4CatsWrapper.factory[F]
    EmberServerBuilder
      .default[F]
      .withHost(settings.host)
      .withPort(settings.port)
      .withHttpWebSocketApp(wsApp => (httpRoutes <+> wsRoutes(wsApp)).orNotFound)
      .build

  private def wellknownRoutes[F[+_]: {Async,HasNode, Logger}]: HttpRoutes[F] =
    Router(
      "/.wellknown" -> HealthCheckResource[F]
    )

final class OdinLog4CatsWrapper[F[+_]: {HasNode, Applicative}](using logger: Logger[F])
    extends SelfAwareStructuredLogger[F]:

  override def isTraceEnabled: F[Boolean] = true.pure[F]
  override def isDebugEnabled: F[Boolean] = true.pure[F]
  override def isInfoEnabled:  F[Boolean] = true.pure[F]
  override def isWarnEnabled:  F[Boolean] = true.pure[F]
  override def isErrorEnabled: F[Boolean] = true.pure[F]

  override def trace(msg: => String): F[Unit] = logger.trace(msg)
  override def debug(msg: => String): F[Unit] = logger.debug(msg)
  override def info(msg: => String): F[Unit]  = logger.info(msg)
  override def warn(msg: => String): F[Unit]  = logger.warn(msg)
  override def error(msg: => String): F[Unit] = logger.error(msg)

  override def trace(t: Throwable)(msg: => String): F[Unit] = logger.trace(msg, t)
  override def debug(t: Throwable)(msg: => String): F[Unit] = logger.debug(msg, t)
  override def info(t: Throwable)(msg: => String):  F[Unit] = logger.info(msg, t)
  override def warn(t: Throwable)(msg: => String):  F[Unit] = logger.warn(msg, t)
  override def error(t: Throwable)(msg: => String): F[Unit] = logger.error(msg, t)

  override def trace(ctx: Map[String, String])(msg: => String): F[Unit] = logger.trace(msg, ctx)
  override def debug(ctx: Map[String, String])(msg: => String): F[Unit] = logger.debug(msg, ctx)
  override def info(ctx: Map[String, String])(msg: => String): F[Unit]  = logger.info(msg, ctx)
  override def warn(ctx: Map[String, String])(msg: => String): F[Unit]  = logger.warn(msg, ctx)
  override def error(ctx: Map[String, String])(msg: => String): F[Unit] = logger.error(msg, ctx)

  override def trace(ctx: Map[String, String], t: Throwable)(msg: => String): F[Unit] = logger.trace(msg, ctx, t)
  override def debug(ctx: Map[String, String], t: Throwable)(msg: => String): F[Unit] = logger.debug(msg, ctx, t)
  override def info(ctx: Map[String, String], t: Throwable)(msg: => String): F[Unit]  = logger.info(msg, ctx, t)
  override def warn(ctx: Map[String, String], t: Throwable)(msg: => String): F[Unit]  = logger.warn(msg, ctx, t)
  override def error(ctx: Map[String, String], t: Throwable)(msg: => String): F[Unit] = logger.error(msg, ctx, t)

object OdinLog4CatsWrapper:
  def factory[F[+_]: {HasNode, Applicative}](using Logger[F]): Log4CatsLoggerFactory[F] = new Log4CatsLoggerFactory[F]:
    override def getLoggerFromName(name: String): SelfAwareStructuredLogger[F] = OdinLog4CatsWrapper[F]
    override def fromName(name: String): F[SelfAwareStructuredLogger[F]] = OdinLog4CatsWrapper[F].pure
