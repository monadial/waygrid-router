package com.monadial.waygrid.common.application.util.logging

import cats.Applicative
import cats.implicits.*
import com.monadial.waygrid.common.application.algebra.Logger
import org.typelevel.log4cats.{ LoggerFactory as Log4CatsLoggerFactory, SelfAwareStructuredLogger }

private final class LoggerLog4CatsWrapper[F[+_]: Applicative](using
  logger: Logger[F]
) extends SelfAwareStructuredLogger[F]:

  override def isTraceEnabled: F[Boolean] = true.pure[F]
  override def isDebugEnabled: F[Boolean] = true.pure[F]
  override def isInfoEnabled: F[Boolean]  = true.pure[F]
  override def isWarnEnabled: F[Boolean]  = true.pure[F]
  override def isErrorEnabled: F[Boolean] = true.pure[F]

  override def trace(msg: => String): F[Unit] = logger.trace(msg)
  override def debug(msg: => String): F[Unit] = logger.debug(msg)
  override def info(msg: => String): F[Unit]  = logger.info(msg)
  override def warn(msg: => String): F[Unit]  = logger.warn(msg)
  override def error(msg: => String): F[Unit] = logger.error(msg)

  override def trace(t: Throwable)(msg: => String): F[Unit] =
    logger.trace(msg, t)
  override def debug(t: Throwable)(msg: => String): F[Unit] =
    logger.debug(msg, t)
  override def info(t: Throwable)(msg: => String): F[Unit] = logger.info(msg, t)
  override def warn(t: Throwable)(msg: => String): F[Unit] = logger.warn(msg, t)
  override def error(t: Throwable)(msg: => String): F[Unit] =
    logger.error(msg, t)

  override def trace(ctx: Map[String, String])(msg: => String): F[Unit] =
    logger.trace(msg, ctx)
  override def debug(ctx: Map[String, String])(msg: => String): F[Unit] =
    logger.debug(msg, ctx)
  override def info(ctx: Map[String, String])(msg: => String): F[Unit] =
    logger.info(msg, ctx)
  override def warn(ctx: Map[String, String])(msg: => String): F[Unit] =
    logger.warn(msg, ctx)
  override def error(ctx: Map[String, String])(msg: => String): F[Unit] =
    logger.error(msg, ctx)

  override def trace(
    ctx: Map[String, String],
    t: Throwable
  )(msg: => String): F[Unit] = logger.trace(msg, ctx, t)
  override def debug(
    ctx: Map[String, String],
    t: Throwable
  )(msg: => String): F[Unit] = logger.debug(msg, ctx, t)
  override def info(
    ctx: Map[String, String],
    t: Throwable
  )(msg: => String): F[Unit] = logger.info(msg, ctx, t)
  override def warn(
    ctx: Map[String, String],
    t: Throwable
  )(msg: => String): F[Unit] = logger.warn(msg, ctx, t)
  override def error(
    ctx: Map[String, String],
    t: Throwable
  )(msg: => String): F[Unit] = logger.error(msg, ctx, t)

object LoggerLog4CatsWrapper:
  def factory[F[+_]: Applicative](using
    Logger[F]
  ): Log4CatsLoggerFactory[F] = new Log4CatsLoggerFactory[F]:
    override def getLoggerFromName(name: String): SelfAwareStructuredLogger[F] =
      LoggerLog4CatsWrapper[F]
    override def fromName(name: String): F[SelfAwareStructuredLogger[F]] =
      LoggerLog4CatsWrapper[F].pure
