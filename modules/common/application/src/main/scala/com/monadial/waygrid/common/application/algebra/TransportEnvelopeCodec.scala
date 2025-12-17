package com.monadial.waygrid.common.application.algebra

import com.monadial.waygrid.common.application.domain.model.envelope.TransportEnvelope
import com.monadial.waygrid.common.domain.algebra.messaging.message.Message
import com.monadial.waygrid.common.domain.model.envelope.DomainEnvelope

trait TransportEnvelopeCodec[F[+_]]:
  def encode[M <: Message](envelope: DomainEnvelope[M]): F[TransportEnvelope]
  def decode(envelope: TransportEnvelope): F[DomainEnvelope[? <: Message]]
