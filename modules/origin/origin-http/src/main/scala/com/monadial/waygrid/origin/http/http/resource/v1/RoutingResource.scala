package com.monadial.waygrid.origin.http.http.resource.v1

import cats.data.EitherT
import cats.effect.{Async, Concurrent}
import cats.implicits.*
import com.monadial.waygrid.common.application.algebra.{EventSink, ThisNode}
import com.monadial.waygrid.common.application.domain.model.envelope.Envelope
import com.monadial.waygrid.common.domain.model.routing.spec.{Node, Spec}
import com.monadial.waygrid.common.domain.model.node.Value.ServiceAddress
import com.monadial.waygrid.common.application.util.circe.codecs.DomainRoutingSpecCodecs.given
import com.monadial.waygrid.common.domain.algebra.DagCompiler
import com.monadial.waygrid.common.domain.model.content.Content
import com.monadial.waygrid.common.domain.model.routing.Event.RoutingWasRequested
import com.monadial.waygrid.common.domain.model.routing.Value.RepeatPolicy.NoRepeat
import com.monadial.waygrid.common.domain.model.routing.Value.RetryPolicy.NoRetry
import com.monadial.waygrid.common.domain.model.routing.Value.{DeliveryStrategy, RouteSalt, TraversalId}
import io.circe.Codec
import org.http4s
import org.http4s.*
import org.http4s.circe.*
import org.http4s.dsl.Http4sDsl

object RoutingResource:

  private final case class Request(
    graph: Spec,
  ) derives Codec.AsObject

  private final case class Response(
    traversalId: TraversalId
  ) derives Codec.AsObject

  def ingest[F[+_]: {Async, ThisNode, EventSink}](compiler: DagCompiler[F]): HttpRoutes[F] =
    object serverDsl extends Http4sDsl[F]
    import serverDsl.*

//    given EntityDecoder[F, Request]  = jsonOf[F, Request]
    given EntityEncoder[F, Response] = jsonEncoderOf[F, Response]

    HttpRoutes.of[F]:
        case req @ POST -> Root / "v1" / "ingest" =>
          {
            for
              request <- mapRequest(req)
              traversalId <- TraversalId.next[F]
              compiledDag <- compiler.compile(request.graph, RouteSalt("test"))
              response <- Response(traversalId).pure[F]
            yield response
          }
            .handleErrorWith: e =>
              InternalServerError(s"Failed to process routing data: ${e}")


  private def mapRequest[F[+_]: Concurrent](req: http4s.Request[F]): EitherT[F, Throwable, Request] =
    for
      req <- EitherT
    yeld ()


