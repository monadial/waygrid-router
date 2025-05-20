package com.monadial.waygrid.common.application.http.resource

import cats.effect.Async
import org.http4s.HttpRoutes

object WellknownResource:
  def v1[F[+_]: Async]: HttpRoutes[F] = HttpRoutes.empty[F]