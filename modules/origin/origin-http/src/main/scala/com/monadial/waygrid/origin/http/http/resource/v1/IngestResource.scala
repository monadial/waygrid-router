package com.monadial.waygrid.origin.http.http.resource.v1

import cats.effect.Async
import cats.implicits.*
import com.monadial.waygrid.common.application.algebra.{EventSink, Logger, ThisNode}
import com.monadial.waygrid.common.application.syntax.EnvelopeSyntax.send
import com.monadial.waygrid.common.application.syntax.EventSyntax.wrapIntoWaystationEnvelope
import com.monadial.waygrid.common.application.util.circe.codecs.DomainRoutingSpecCodecs.given
import com.monadial.waygrid.common.domain.algebra.DagCompiler
import com.monadial.waygrid.common.domain.algebra.messaging.message.Value.MessageId
import com.monadial.waygrid.common.domain.model.envelope.Value.TraversalStamp
import com.monadial.waygrid.common.domain.model.routing.Value.{RouteSalt, TraversalId}
import com.monadial.waygrid.common.domain.model.traversal.Event.TraversalRequested
import com.monadial.waygrid.common.domain.model.traversal.dag.Value.DagHash
import com.monadial.waygrid.common.domain.model.traversal.spec.Spec
import io.circe.{Codec, Json}
import org.http4s.*
import org.http4s.circe.*
import org.http4s.dsl.Http4sDsl
import org.typelevel.otel4s.trace.Tracer

import scala.annotation.nowarn

object IngestResource:

  private final case class EndpointRequest(
    graph: Spec
  ) derives Codec.AsObject

  private final case class EndpointResponse(
    traversalId: TraversalId,
    dagHash: DagHash
  ) derives Codec.AsObject

  @nowarn("msg=unused implicit parameter")
  def ingest[F[+_]: {Async, ThisNode, EventSink, Tracer, Logger}](compiler: DagCompiler[F]): HttpRoutes[F] =
    object serverDsl extends Http4sDsl[F]
    import serverDsl.*

    given EntityDecoder[F, EndpointRequest]  = jsonOf[F, EndpointRequest]
    given EntityEncoder[F, EndpointResponse] = jsonEncoderOf[F, EndpointResponse]

    HttpRoutes
      .of[F]:
        case req @ POST -> Root / "v1" / "ingest" =>
          Tracer[F]
            .span("ingesting_message")
            .surround {
              for
                request     <- Tracer[F].span("request_deserialization").surround(req.as[EndpointRequest])
                traversalId <- TraversalId.next[F]
                messageId   <- MessageId.next[F]
                thisNode    <- ThisNode[F].get
                compiledDag <-
                  Tracer[F].span("compiling_dag").surround(compiler.compile(request.graph, RouteSalt("test")))
                _ <- Tracer[F].span("dispatching_signal").use: span =>
                    TraversalRequested(messageId, traversalId)
                      .pure[F]
                      .flatMap(_.wrapIntoWaystationEnvelope)
                      .map(_.addStamp(TraversalStamp.initial(traversalId, thisNode.address, compiledDag)))
                      .flatMap(_.send(Some(span.context)))
                response <- EndpointResponse(traversalId, compiledDag.hash)
                  .pure[F]
                  .flatMap(Ok(_))
              yield response
            }
            .handleErrorWith: e =>
              InternalServerError(Json.obj("error" -> Json.fromString(e.toString)))
