package com.monadial.waygrid.common.application.util.doobie

import cats.effect.{Async, Resource}
import cats.syntax.all.*
import com.monadial.waygrid.common.application.algebra.Logger
import com.monadial.waygrid.common.application.domain.model.Platform
import com.monadial.waygrid.common.application.domain.model.settings.PostgresSettings
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.util.Credentials
import de.lhns.doobie.flyway.Flyway
import doobie.hikari.HikariTransactor
import doobie.util.log.{LogEvent, LogHandler}


object Doobie:

  def pooled[F[+_]: { Logger, Async}](settings: PostgresSettings): Resource[F, HikariTransactor[F]] =
    for
      config <- Resource
        .pure:
          val config = new HikariConfig()
          config.setJdbcUrl(settings.url)
          config.setCredentials(Credentials(settings.user, settings.password))
          config.setMaximumPoolSize(settings.poolSize.getOrElse(Platform.numberOfAvailableCpuCores))
          config
      logHandler <- Resource
        .pure:
          new LogHandler[F]:
            override def run(logEvent: LogEvent): F[Unit] = Logger[F].info(logEvent.toString)
      xa <- HikariTransactor.fromHikariConfig(config, Some(logHandler))
      // todo for now this is good idea to check migrations on each node run, find different method to handle migrations
      _ <- Flyway(xa):
        flyway =>
          for
            _ <- flyway
              .configure: cfg =>
                cfg
                    .table("migrations")
                    .locations("db/migration")
              .migrate()
          yield ()
    yield xa






