package com.monadial.waygrid.common.application.http.resource

import cats.effect.kernel.Async
import cats.syntax.all.*
import com.monadial.waygrid.common.application.algebra.Logger
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl

object HealthCheckResource:
  def apply[F[+_]: {Async, Logger}]: HttpRoutes[F] =
    object serverDsl extends Http4sDsl[F]
    import serverDsl.*

    HttpRoutes.of[F] {
      case GET -> Root / "health-check" =>
        for
          _   <- Logger[F].info("Received health-check request", Map("endpoint" -> "/.wellknown/health-check"))
          res <- Ok("1")
        yield res
    }
