package com.monadial.waygrid.common.application.interpreter

import cats.effect.*
import cats.implicits.catsSyntaxApplicativeId
import cats.syntax.all.*
import com.monadial.waygrid.common.application.algebra.{ HasNode, Logger, LoggerContext }
import io.odin.formatter.Formatter
import io.odin.meta.Position
import io.odin.{ Level, consoleLogger }

object OdinLoggerInterpreter: // todo refactor

  def default[F[+_]: {HasNode, Async}](minLevel: Level): F[Logger[F]] =
    for
      thisNode <- HasNode[F].get
      nodeContext <- Map(
//        "address"   -> thisNode.address.show,
        "component" -> thisNode.descriptor.component.show,
        "service"   -> thisNode.descriptor.service.show
      ).pure[F]
      odinLogger <- consoleLogger(formatter = Formatter.colorful, minLevel = minLevel).pure[F]
    yield new Logger[F]:
      // Info
      override def info(msg: => String)(using
        logCtx: LoggerContext
      ): F[Unit] =
        given Position =
          Position(logCtx.file, logCtx.enclosing, logCtx.pkg, logCtx.line)
        odinLogger
          .info(msg, nodeContext)

      override def info(msg: => String, ctx: Map[String, String])(using
        logCtx: LoggerContext
      ): F[Unit] =
        given Position =
          Position(logCtx.file, logCtx.enclosing, logCtx.pkg, logCtx.line)
        odinLogger
          .info(msg, ctx ++ nodeContext)

      override def info(msg: => String, t: Throwable)(using
        logCtx: LoggerContext
      ): F[Unit] =
        given Position =
          Position(logCtx.file, logCtx.enclosing, logCtx.pkg, logCtx.line)
        odinLogger
          .info(msg, t)

      override def info(
        msg: => String,
        ctx: Map[String, String],
        t: Throwable
      )(using logCtx: LoggerContext): F[Unit] =
        given Position =
          Position(logCtx.file, logCtx.enclosing, logCtx.pkg, logCtx.line)
        odinLogger
          .info(msg, ctx ++ nodeContext, t)

      // Error
      override def error(msg: => String)(using
        logCtx: LoggerContext
      ): F[Unit] =
        given Position =
          Position(logCtx.file, logCtx.enclosing, logCtx.pkg, logCtx.line)
        odinLogger
          .error(msg, nodeContext)

      override def error(msg: => String, ctx: Map[String, String])(using
        logCtx: LoggerContext
      ): F[Unit] =
        given Position =
          Position(logCtx.file, logCtx.enclosing, logCtx.pkg, logCtx.line)
        odinLogger
          .error(msg, ctx ++ nodeContext)

      override def error(msg: => String, t: Throwable)(using
        logCtx: LoggerContext
      ): F[Unit] =
        given Position =
          Position(logCtx.file, logCtx.enclosing, logCtx.pkg, logCtx.line)
        odinLogger
          .error(msg, t)

      override def error(
        msg: => String,
        ctx: Map[String, String],
        t: Throwable
      )(using logCtx: LoggerContext): F[Unit] =
        given Position =
          Position(logCtx.file, logCtx.enclosing, logCtx.pkg, logCtx.line)
        odinLogger
          .error(msg, ctx ++ nodeContext, t)

      // Debug
      override def debug(msg: => String)(using
        logCtx: LoggerContext
      ): F[Unit] =
        given Position =
          Position(logCtx.file, logCtx.enclosing, logCtx.pkg, logCtx.line)
        odinLogger
          .debug(msg, nodeContext)

      override def debug(msg: => String, ctx: Map[String, String])(using
        logCtx: LoggerContext
      ): F[Unit] =
        given Position =
          Position(logCtx.file, logCtx.enclosing, logCtx.pkg, logCtx.line)
        odinLogger
          .debug(msg, ctx ++ nodeContext)

      override def debug(msg: => String, t: Throwable)(using
        logCtx: LoggerContext
      ): F[Unit] =
        given Position =
          Position(logCtx.file, logCtx.enclosing, logCtx.pkg, logCtx.line)
        odinLogger
          .debug(msg, t)

      override def debug(
        msg: => String,
        ctx: Map[String, String],
        t: Throwable
      )(using logCtx: LoggerContext): F[Unit] =
        given Position =
          Position(logCtx.file, logCtx.enclosing, logCtx.pkg, logCtx.line)
        odinLogger
          .debug(msg, ctx ++ nodeContext, t)

      // Warn
      override def warn(msg: => String)(using
        logCtx: LoggerContext
      ): F[Unit] =
        given Position =
          Position(logCtx.file, logCtx.enclosing, logCtx.pkg, logCtx.line)
        odinLogger
          .warn(msg, nodeContext)

      override def warn(msg: => String, ctx: Map[String, String])(using
        logCtx: LoggerContext
      ): F[Unit] =
        given Position =
          Position(logCtx.file, logCtx.enclosing, logCtx.pkg, logCtx.line)
        odinLogger
          .warn(msg, ctx ++ nodeContext)

      override def warn(msg: => String, t: Throwable)(using
        logCtx: LoggerContext
      ): F[Unit] =
        given Position =
          Position(logCtx.file, logCtx.enclosing, logCtx.pkg, logCtx.line)
        odinLogger
          .warn(msg, t)

      override def warn(
        msg: => String,
        ctx: Map[String, String],
        t: Throwable
      )(using logCtx: LoggerContext): F[Unit] =
        given Position =
          Position(logCtx.file, logCtx.enclosing, logCtx.pkg, logCtx.line)
        odinLogger
          .warn(msg, ctx ++ nodeContext, t)

      // Trace
      override def trace(msg: => String)(using
        logCtx: LoggerContext
      ): F[Unit] =
        given Position =
          Position(logCtx.file, logCtx.enclosing, logCtx.pkg, logCtx.line)
        odinLogger
          .trace(msg, nodeContext)

      override def trace(msg: => String, ctx: Map[String, String])(using
        logCtx: LoggerContext
      ): F[Unit] =
        given Position =
          Position(logCtx.file, logCtx.enclosing, logCtx.pkg, logCtx.line)
        odinLogger
          .trace(msg, ctx ++ nodeContext)

      override def trace(msg: => String, t: Throwable)(using
        logCtx: LoggerContext
      ): F[Unit] =
        given Position =
          Position(logCtx.file, logCtx.enclosing, logCtx.pkg, logCtx.line)
        odinLogger
          .trace(msg, t)

      override def trace(
        msg: => String,
        ctx: Map[String, String],
        t: Throwable
      )(using logCtx: LoggerContext): F[Unit] =
        given Position =
          Position(logCtx.file, logCtx.enclosing, logCtx.pkg, logCtx.line)
        odinLogger
          .trace(msg, ctx ++ nodeContext, t)

  def resource[F[+_]: {HasNode,
    Async}](minLevel: Level): Resource[F, Logger[F]] =
    Resource
      .eval(default(minLevel))
