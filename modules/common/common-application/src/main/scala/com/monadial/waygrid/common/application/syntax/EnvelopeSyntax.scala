package com.monadial.waygrid.common.application.syntax

import cats.effect.Async
import cats.implicits.*
import com.monadial.waygrid.common.application.algebra.EventSink
import com.monadial.waygrid.common.domain.algebra.messaging.event.Event
import com.monadial.waygrid.common.domain.model.envelope.DomainEnvelope
import org.typelevel.otel4s.trace.SpanContext

object EnvelopeSyntax:
  extension [Evt <: Event](envelope: DomainEnvelope[Evt])
    def send[F[+_]: {Async, EventSink}](spanCtx: Option[SpanContext]): F[Unit] =
      for
        _ <- EventSink[F].send(envelope.endpoint, envelope, spanCtx)
      yield ()
