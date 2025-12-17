package com.monadial.waygrid.common.application.http.resource

import cats.effect.Async
import cats.syntax.all.*
import io.circe.Codec
import org.http4s.circe.*
import org.http4s.dsl.Http4sDsl
import org.http4s.{ EntityEncoder, HttpRoutes }

object HealthCheckResource:
  private final case class Response() derives Codec.AsObject

  def resource[F[+_]: {Async}]: HttpRoutes[F] =
    object serverDsl extends Http4sDsl[F]
    import serverDsl.*

    given EntityEncoder[F, Response] = jsonEncoderOf[F, Response]

    HttpRoutes.of[F] {
      case GET -> Root / "health-check" =>
        for
          res <- Ok(Response())
        yield res
    }
