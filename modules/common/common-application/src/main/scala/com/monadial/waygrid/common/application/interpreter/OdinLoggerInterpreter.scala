package com.monadial.waygrid.common.application.interpreter

import cats.effect.*
import cats.syntax.all.*
import com.monadial.waygrid.common.application.algebra.{HasNode, Logger, LoggerContext}
import io.odin.formatter.Formatter
import io.odin.meta.Position
import io.odin.{Level, consoleLogger}

object OdinLoggerInterpreter: // todo refactor

  private inline def nodeToContext[F[+_]: {HasNode, Async}]: Map[String, String] =
    HasNode[F]
        .node
        .some
        .map: node =>
          Map(
            "address" -> node.address.show,
            "component" -> node.descriptor.component.show,
            "service"      -> node.descriptor.service.show,
          )
        .getOrElse(Map.empty)

  def default[F[+_]: {HasNode, Async}](minLevel: Level): F[Logger[F]] =
    consoleLogger[F](formatter = Formatter.colorful, minLevel = minLevel)
      .pure
      .map:
        odinLogger =>
          new Logger[F]:
            // Info
            override def info(msg: => String)(using logCtx: LoggerContext): F[Unit] =
              given Position = Position(logCtx.file, logCtx.enclosing, logCtx.pkg, logCtx.line)
              odinLogger
                .info(msg, nodeToContext[F])

            override def info(msg: => String, ctx: Map[String, String])(using logCtx: LoggerContext): F[Unit] =
              given Position = Position(logCtx.file, logCtx.enclosing, logCtx.pkg, logCtx.line)
              odinLogger
                .info(msg, ctx ++ nodeToContext[F])

            override def info(msg: => String, t: Throwable)(using logCtx: LoggerContext): F[Unit] =
              given Position = Position(logCtx.file, logCtx.enclosing, logCtx.pkg, logCtx.line)
              odinLogger
                .info(msg, t)

            override def info(msg: => String, ctx: Map[String, String], t: Throwable)(using logCtx: LoggerContext): F[Unit] =
              given Position = Position(logCtx.file, logCtx.enclosing, logCtx.pkg, logCtx.line)
              odinLogger
                .info(msg, ctx ++ nodeToContext[F], t)

            // Error
            override def error(msg: => String)(using logCtx: LoggerContext): F[Unit] =
              given Position = Position(logCtx.file, logCtx.enclosing, logCtx.pkg, logCtx.line)
              odinLogger
                .error(msg, nodeToContext[F])

            override def error(msg: => String, ctx: Map[String, String])(using logCtx: LoggerContext): F[Unit] =
              given Position = Position(logCtx.file, logCtx.enclosing, logCtx.pkg, logCtx.line)
              odinLogger
                .error(msg, ctx ++ nodeToContext[F])

            override def error(msg: => String, t: Throwable)(using logCtx: LoggerContext): F[Unit] =
              given Position = Position(logCtx.file, logCtx.enclosing, logCtx.pkg, logCtx.line)
              odinLogger
                .error(msg, t)

            override def error(msg: => String, ctx: Map[String, String], t: Throwable)(using logCtx: LoggerContext): F[Unit] =
              given Position = Position(logCtx.file, logCtx.enclosing, logCtx.pkg, logCtx.line)
              odinLogger
                .error(msg, ctx ++ nodeToContext[F], t)

            // Debug
            override def debug(msg: => String)(using logCtx: LoggerContext): F[Unit] =
              given Position = Position(logCtx.file, logCtx.enclosing, logCtx.pkg, logCtx.line)
              odinLogger
                .debug(msg, nodeToContext[F])

            override def debug(msg: => String, ctx: Map[String, String])(using logCtx: LoggerContext): F[Unit] =
              given Position = Position(logCtx.file, logCtx.enclosing, logCtx.pkg, logCtx.line)
              odinLogger
                .debug(msg, ctx ++ nodeToContext[F])

            override def debug(msg: => String, t: Throwable)(using logCtx: LoggerContext): F[Unit] =
              given Position = Position(logCtx.file, logCtx.enclosing, logCtx.pkg, logCtx.line)
              odinLogger
                .debug(msg, t)

            override def debug(msg: => String, ctx: Map[String, String], t: Throwable)(using logCtx: LoggerContext): F[Unit] =
              given Position = Position(logCtx.file, logCtx.enclosing, logCtx.pkg, logCtx.line)
              odinLogger
                .debug(msg, ctx ++ nodeToContext[F], t)

            // Warn
            override def warn(msg: => String)(using logCtx: LoggerContext): F[Unit] =
              given Position = Position(logCtx.file, logCtx.enclosing, logCtx.pkg, logCtx.line)
              odinLogger
                .warn(msg, nodeToContext[F])

            override def warn(msg: => String, ctx: Map[String, String])(using logCtx: LoggerContext): F[Unit] =
              given Position = Position(logCtx.file, logCtx.enclosing, logCtx.pkg, logCtx.line)
              odinLogger
                .warn(msg, ctx ++ nodeToContext[F])

            override def warn(msg: => String, t: Throwable)(using logCtx: LoggerContext): F[Unit] =
              given Position = Position(logCtx.file, logCtx.enclosing, logCtx.pkg, logCtx.line)
              odinLogger
                .warn(msg, t)

            override def warn(msg: => String, ctx: Map[String, String], t: Throwable)(using logCtx: LoggerContext): F[Unit] =
              given Position = Position(logCtx.file, logCtx.enclosing, logCtx.pkg, logCtx.line)
              odinLogger
               .warn(msg, ctx ++ nodeToContext[F], t)

            // Trace
            override def trace(msg: => String)(using logCtx: LoggerContext): F[Unit] =
              given Position = Position(logCtx.file, logCtx.enclosing, logCtx.pkg, logCtx.line)
              odinLogger
                .trace(msg, nodeToContext[F])

            override def trace(msg: => String, ctx: Map[String, String])(using logCtx: LoggerContext): F[Unit] =
              given Position = Position(logCtx.file, logCtx.enclosing, logCtx.pkg, logCtx.line)
              odinLogger
                .trace(msg, ctx ++ nodeToContext[F])

            override def trace(msg: => String, t: Throwable)(using logCtx: LoggerContext): F[Unit] =
              given Position = Position(logCtx.file, logCtx.enclosing, logCtx.pkg, logCtx.line)
              odinLogger
                .trace(msg, t)

            override def trace(msg: => String, ctx: Map[String, String], t: Throwable)(using logCtx: LoggerContext): F[Unit] =
              given Position = Position(logCtx.file, logCtx.enclosing, logCtx.pkg, logCtx.line)
              odinLogger
                .trace(msg, ctx ++ nodeToContext[F], t)

  def resource[F[+_]: {HasNode, Async}](minLevel: Level): Resource[F, Logger[F]] =
    Resource
      .eval(default(minLevel))
