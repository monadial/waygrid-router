package com.monadial.waygrid.common.application.algebra

import sourcecode.*

/**
 * A tagless-final logging algebra for structured and leveled logging in a purely functional context.
 *
 * This type class abstracts over an effect type `F`, enabling logs to be recorded with
 * contextual information, without coupling to a specific logging backend or framework.
 *
 * Implementations may delegate to structured loggers (e.g., Odin, SLF4J, console) and may automatically
 * enrich log output with contextual metadata such as timestamps, correlation IDs, or service labels.
 *
 * ### Example Usage
 * {{{
 * def run[F[+_]: Logger]: F[Unit] =
 *   Logger[F].info("Node started", Map("kind" -> "origin", "name" -> "http"))
 * }}}
 *
 * Each log level supports both simple string messages and optional structured context (key-value pairs),
 * which can be used for log filtering, aggregation, or search in log pipelines.
 *
 * @tparam F the effect type constructor (e.g., `IO`, `Sync`, `ZIO`)
 */
trait Logger[F[+_]]:
  def info(msg: => String)(using LoggerContext): F[Unit]
  def info(msg: => String, ctx: Map[String, String])(using LoggerContext): F[Unit]
  def info(msg: => String, t: Throwable)(using LoggerContext): F[Unit]
  def info(msg: => String, ctx: Map[String, String], t: Throwable)(using LoggerContext): F[Unit]

  def error(msg: => String)(using LoggerContext): F[Unit]
  def error(msg: => String, ctx: Map[String, String])(using LoggerContext): F[Unit]
  def error(msg: => String, t: Throwable)(using LoggerContext): F[Unit]
  def error(msg: => String, ctx: Map[String, String], t: Throwable)(using LoggerContext): F[Unit]

  def debug(msg: => String)(using LoggerContext): F[Unit]
  def debug(msg: => String, ctx: Map[String, String])(using LoggerContext): F[Unit]
  def debug(msg: => String, t: Throwable)(using LoggerContext): F[Unit]
  def debug(msg: => String, ctx: Map[String, String], t: Throwable)(using LoggerContext): F[Unit]

  def warn(msg: => String)(using LoggerContext): F[Unit]
  def warn(msg: => String, ctx: Map[String, String])(using LoggerContext): F[Unit]
  def warn(msg: => String, t: Throwable)(using LoggerContext): F[Unit]
  def warn(msg: => String, ctx: Map[String, String], t: Throwable)(using LoggerContext): F[Unit]

  def trace(msg: => String)(using LoggerContext): F[Unit]
  def trace(msg: => String, ctx: Map[String, String])(using LoggerContext): F[Unit]
  def trace(msg: => String, t: Throwable)(using LoggerContext): F[Unit]
  def trace(msg: => String, ctx: Map[String, String], t: Throwable)(using LoggerContext): F[Unit]

object Logger:
  def apply[F[+_]](using ev: Logger[F]): Logger[F] = ev

final case class LoggerContext(
  enclosing: String,
  name: String,
  file: String,
  line: Int,
  pkg: String,
  category: Option[String] = None,
)

object LoggerContext:
  inline given (using
    enclosing: Enclosing,
    name: Name,
    file: File,
    line: Line,
    pkg: Pkg
  ): LoggerContext = LoggerContext(
    enclosing.value,
    name.value,
    file.value,
    line.value,
    pkg.value
  )
