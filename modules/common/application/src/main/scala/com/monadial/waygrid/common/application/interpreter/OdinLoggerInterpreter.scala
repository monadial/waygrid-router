package com.monadial.waygrid.common.application.interpreter

import cats.effect.*
import cats.syntax.all.*
import com.monadial.waygrid.common.application.algebra.{ Logger, LoggerContext, ThisNode }
import io.odin.formatter.Formatter
import io.odin.meta.Position
import io.odin.syntax.*
import io.odin.{ Level, consoleLogger }

object OdinLoggerInterpreter: // todo refactor

  def default[F[+_]: {ThisNode, Async}](minLevel: Level): Resource[F, Logger[F]] =
    for
      nodeContext <- Resource
        .eval(ThisNode[F].get)
        .map: thisNode =>
          Map(
            "clusterId" -> thisNode.clusterId.show,
            "nodeId"    -> thisNode.id.show,
            "region"    -> thisNode.region.show,
            "component" -> thisNode.descriptor.component.show,
            "service"   -> thisNode.descriptor.service.show
          )

      odinLogger <-
        consoleLogger(formatter = Formatter.colorful, minLevel = minLevel)
          .withConstContext(nodeContext)
          .withAsync()
    yield new Logger[F]:
      // Info
      override def info(msg: => String)(using
        logCtx: LoggerContext
      ): F[Unit] =
        given Position =
          Position(logCtx.file, logCtx.enclosing, logCtx.pkg, logCtx.line)
        odinLogger
          .info(msg)

      override def info(msg: => String, ctx: Map[String, String])(using
        logCtx: LoggerContext
      ): F[Unit] =
        given Position =
          Position(logCtx.file, logCtx.enclosing, logCtx.pkg, logCtx.line)
        odinLogger
          .info(msg, ctx)

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
          .info(msg, ctx, t)

      // Error
      override def error(msg: => String)(using
        logCtx: LoggerContext
      ): F[Unit] =
        given Position =
          Position(logCtx.file, logCtx.enclosing, logCtx.pkg, logCtx.line)
        odinLogger
          .error(msg)

      override def error(msg: => String, ctx: Map[String, String])(using
        logCtx: LoggerContext
      ): F[Unit] =
        given Position =
          Position(logCtx.file, logCtx.enclosing, logCtx.pkg, logCtx.line)
        odinLogger
          .error(msg, ctx)

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
          .error(msg, ctx, t)

      // Debug
      override def debug(msg: => String)(using
        logCtx: LoggerContext
      ): F[Unit] =
        given Position =
          Position(logCtx.file, logCtx.enclosing, logCtx.pkg, logCtx.line)
        odinLogger
          .debug(msg)

      override def debug(msg: => String, ctx: Map[String, String])(using
        logCtx: LoggerContext
      ): F[Unit] =
        given Position =
          Position(logCtx.file, logCtx.enclosing, logCtx.pkg, logCtx.line)
        odinLogger
          .debug(msg, ctx)

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
          .debug(msg, ctx, t)

      // Warn
      override def warn(msg: => String)(using
        logCtx: LoggerContext
      ): F[Unit] =
        given Position =
          Position(logCtx.file, logCtx.enclosing, logCtx.pkg, logCtx.line)
        odinLogger
          .warn(msg)

      override def warn(msg: => String, ctx: Map[String, String])(using
        logCtx: LoggerContext
      ): F[Unit] =
        given Position =
          Position(logCtx.file, logCtx.enclosing, logCtx.pkg, logCtx.line)
        odinLogger
          .warn(msg, ctx)

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
          .warn(msg, ctx, t)

      // Trace
      override def trace(msg: => String)(using
        logCtx: LoggerContext
      ): F[Unit] =
        given Position =
          Position(logCtx.file, logCtx.enclosing, logCtx.pkg, logCtx.line)
        odinLogger
          .trace(msg)

      override def trace(msg: => String, ctx: Map[String, String])(using
        logCtx: LoggerContext
      ): F[Unit] =
        given Position =
          Position(logCtx.file, logCtx.enclosing, logCtx.pkg, logCtx.line)
        odinLogger
          .trace(msg, ctx)

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
          .trace(msg, ctx, t)
