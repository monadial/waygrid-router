package com.monadial.waygrid.system.blob.store.protocol

import cats.effect.kernel.Async
import cats.implicits.given
import com.monadial.waygrid.common.application.protocol.pb.blob_store.*
import com.monadial.waygrid.common.application.util.otel4s.Htt4sHeadersSyntax.given
import fs2.Stream
import org.http4s.Headers
import org.typelevel.otel4s.trace.Tracer

final class BlobStoreProtocol[F[+_]: {Async, Tracer}] extends BlobStore[F]:
  override def putContent(request: PutContentRequest, ctx: Headers): F[PutContentResponse] =
    Tracer[F].joinOrRoot(ctx):
      Tracer[F]
        .span("blob_store_put_content")
        .surround:
          for
            response <- PutContentResponse.of("1", 1L, "OK").pure[F]
          yield response


  override def getContent(request: GetContentRequest, ctx: Headers): F[GetContentResponse] = ???
  override def exists(request: ExistsRequest, ctx: Headers): F[ExistsResponse] = ???
  override def putContentStream(request: Stream[F, ContentChunk], ctx: Headers): F[PutContentResponse] = ???
  override def getContentStream(request: GetContentRequest, ctx: Headers): Stream[F, ContentChunk] = ???

