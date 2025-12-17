package com.monadial.waygrid.common.application.util.doobie

import cats.effect.{ Async, Resource }
import cats.syntax.all.*
import com.monadial.waygrid.common.application.algebra.Logger
import com.monadial.waygrid.common.application.domain.model.Platform
import com.monadial.waygrid.common.application.domain.model.settings.{ ClickHouseSettings, PostgresSettings }
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.util.Credentials
import de.lhns.doobie.flyway.Flyway
import doobie.hikari.HikariTransactor
import doobie.util.log
import doobie.util.log.{ ExecFailure, LogHandler, ProcessingFailure, Success }

import java.util.Properties

object Doobie:

  private object DoobieLogHandler:
    def default[F[+_]: Logger]: LogHandler[F] =
      case Success(sql, params, label, exec, processing) =>
        Logger[F].info(s"Success: $sql, params: $params, label: $label, exec: $exec, processing: $processing")

      case ProcessingFailure(sql, params, label, exec, processing, failure) =>
        Logger[F].error(
          s"ProcessingFailure: $sql, params: $params, label: $label, exec: $exec, processing: $processing, failure: $failure"
        )

      case ExecFailure(sql, params, label, exec, failure) =>
        Logger[F].error(s"ExecFailure: $sql, params: $params, label: $label, exec: $exec, failure: $failure")

  def postgres[F[+_]: {Logger, Async}](settings: PostgresSettings): Resource[F, HikariTransactor[F]] =
    for
      config <- Resource
        .pure:
          val config = new HikariConfig()
          config.setJdbcUrl(settings.url)
          config.setCredentials(Credentials(settings.user, settings.password))
          config.setMaximumPoolSize(settings.poolSize.getOrElse(Platform.numberOfAvailableCpuCores))
          config
      xa <- HikariTransactor.fromHikariConfig(config, Some(DoobieLogHandler.default[F]))
      // todo for now this is good idea to check migrations on each node run, find different method to handle migrations
      _ <- Flyway(xa): flyway =>
        for
          _ <- flyway
            .configure: cfg =>
              cfg
                .table("migrations")
                .locations("db/migration")
            .migrate()
        yield ()
    yield xa

  def clickHouse[F[+_]: {Logger, Async}](settings: ClickHouseSettings): Resource[F, HikariTransactor[F]] =
    for
      config <- Resource
        .pure:
          val properties = new Properties()
          properties.put("user", settings.user)
          properties.put("password", settings.password)
          properties.put("database", settings.database)
          val config = new HikariConfig()
          config.setJdbcUrl(settings.url)
          config.setConnectionTimeout(settings.getConnectionTimeout.toMillis);
          config.setMaximumPoolSize(settings.getPoolSize);
          config.setMaxLifetime(settings.getConnectionTimeout.toMillis);
          config
      xa <- HikariTransactor.fromHikariConfig(config, Some(DoobieLogHandler.default[F]))
    yield xa
