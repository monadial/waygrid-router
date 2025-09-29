package com.monadial.waygrid.common.application.http.resource

import cats.effect.Async
import cats.syntax.all.*
import com.monadial.waygrid.common.application.algebra.ThisNode
import com.monadial.waygrid.common.domain.model.node.Value.{NodeAddress, NodeClusterId, NodeComponent, NodeId, NodeRegion, NodeService}
import io.circe.Codec
import org.http4s.circe.*
import org.http4s.dsl.Http4sDsl
import org.http4s.{EntityEncoder, HttpRoutes}

import java.time.Instant

object HomeResource:

  private final case class Response(
    id: NodeId,
    service: NodeService,
    component: NodeComponent,
    clusterId: NodeClusterId,
    region: NodeRegion,
    address: NodeAddress,
    startedAt: Instant,
    uptime: Long,
  ) derives Codec.AsObject

  def resource[F[+_]: {Async, ThisNode}]: HttpRoutes[F] =
    object serverDsl extends Http4sDsl[F]
    import serverDsl.*

    given EntityEncoder[F, Response] = jsonEncoderOf[F, Response]

    HttpRoutes.of[F] {
      case GET -> Root =>
        for
          responseData <- ThisNode[F]
            .get
            .map: node =>
              Response(
                id = node.id,
                service = node.descriptor.service,
                component = node.descriptor.component,
                clusterId = node.clusterId,
                region = node.region,
                address = node.address,
                startedAt = node.startedAt,
                uptime = node.uptime,
              )
          response <- Ok(responseData)
        yield response
    }
