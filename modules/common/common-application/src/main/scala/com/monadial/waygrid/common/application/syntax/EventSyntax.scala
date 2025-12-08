package com.monadial.waygrid.common.application.syntax

import cats.effect.Async
import cats.syntax.all.*
import com.monadial.waygrid.common.application.algebra.ThisNode
import com.monadial.waygrid.common.domain.SystemWaygridApp
import com.monadial.waygrid.common.domain.algebra.messaging.event.Event
import com.monadial.waygrid.common.domain.model.envelope.DomainEnvelope
import com.monadial.waygrid.common.domain.model.envelope.Value.EnvelopeId
import com.monadial.waygrid.common.domain.value.Address.Endpoint
import com.monadial.waygrid.common.domain.value.Address.EndpointDirection.Inbound

object EventSyntax:
  extension [E <: Event](evt: E)
    def wrapIntoWaystationEnvelope[F[+_]: {Async, ThisNode}]: F[DomainEnvelope[E]] =
      wrapIntoEnvelope(SystemWaygridApp.Waystation.toServiceAddress.toEndpoint(Inbound))

    def wrapIntoSchedulerEnvelope[F[+_]: {Async, ThisNode}]: F[DomainEnvelope[E]] =
      wrapIntoEnvelope(SystemWaygridApp.Scheduler.toServiceAddress.toEndpoint(Inbound))

    def wrapIntoEnvelope[F[+_]: {Async, ThisNode}](endpoint: Endpoint): F[DomainEnvelope[E]] =
      for
        envelopeId <- EnvelopeId.next[F]
        node       <- ThisNode[F].get
      yield DomainEnvelope(
        envelopeId,
        node.address,
        endpoint,
        evt,
        Map.empty
      )
