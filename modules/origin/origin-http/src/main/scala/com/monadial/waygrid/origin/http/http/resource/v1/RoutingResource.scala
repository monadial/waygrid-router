package com.monadial.waygrid.origin.http.http.resource.v1

import cats.effect.Async
import cats.implicits.*
import com.monadial.waygrid.common.application.algebra.ThisNode
import com.monadial.waygrid.common.domain.model.routing.spec.{Node, Spec}
import com.monadial.waygrid.common.domain.model.node.Value.ServiceAddress
import com.monadial.waygrid.common.application.util.circe.codecs.DomainRoutingSpecCodecs.given
import com.monadial.waygrid.common.domain.model.content.Content
import com.monadial.waygrid.common.domain.model.routing.Value.RepeatPolicy.NoRepeat
import com.monadial.waygrid.common.domain.model.routing.Value.RetryPolicy.NoRetry
import com.monadial.waygrid.common.domain.model.routing.Value.DeliveryStrategy
import io.circe.Codec
import org.http4s.*
import org.http4s.circe.*
import org.http4s.dsl.Http4sDsl

object RoutingResource:

  private final case class Request(
    graph: Spec,
    content: Content
  ) derives Codec.AsObject

  private final case class Response(
    graph: Spec
  ) derives Codec.AsObject

  def ingest[F[+_]: {Async, ThisNode}]: HttpRoutes[F] =
    object serverDsl extends Http4sDsl[F]
    import serverDsl.*

//    given EntityDecoder[F, Request]  = jsonOf[F, Request]
    given EntityEncoder[F, Response] = jsonEncoderOf[F, Response]

    HttpRoutes.of[F]:
        case req @ POST -> Root / "v1" / "ingest" =>
          {
            for
//              request <- req.as[Request]
              test <- Spec(
                Node(
                  ServiceAddress.fromString("waygrid://destination/webhook"),
                  NoRetry,
                  DeliveryStrategy.Immediate,
                  None,
                  Some(
                    Node(
                      ServiceAddress.fromString("waygrid://destination/webhook"),
                      NoRetry,
                      DeliveryStrategy.Immediate,
                      None,
                      Some(
                        Node(
                          ServiceAddress.fromString("waygrid://destination/webhook"),
                          NoRetry,
                          DeliveryStrategy.Immediate,
                          None,
                          None
                        )
                      )
                    )
                  )
                ),
                NoRepeat
              ).pure[F]

              currentNode <- ThisNode[F].get
              response    <- Ok(Response(test))
            yield response
          }
            .handleErrorWith: e =>
              InternalServerError(s"Failed to process routing data: ${e}")
