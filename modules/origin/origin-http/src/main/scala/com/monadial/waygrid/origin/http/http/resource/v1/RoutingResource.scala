package com.monadial.waygrid.origin.http.http.resource.v1

import cats.effect.Async
import cats.implicits.*
import com.monadial.waygrid.common.domain.model.routing.Value.RouteId
import com.monadial.waygrid.common.domain.model.routing.RouteGraph
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl

object RoutingResource:

  final case class RoutingRequest(graph: RouteGraph)
  final case class RoutingResponse()

  def ingest[F[+_]: Async]: HttpRoutes[F] =
    object serverDsl extends Http4sDsl[F]
    import serverDsl.*

    HttpRoutes.of[F] {
      case req @ POST -> Root / "routing" / "ingest" =>
        for
          routeId <- RouteId.next[F]
          
          
          


          res <- Ok("Ingest data endpoint is not yet implemented.")
        yield res
    }
