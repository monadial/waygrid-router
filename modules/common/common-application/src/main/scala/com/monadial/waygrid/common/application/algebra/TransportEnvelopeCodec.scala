package com.monadial.waygrid.common.application.algebra

import com.monadial.waygrid.common.application.domain.model.envelope.Envelope
import com.monadial.waygrid.common.application.domain.model.transport.TransportEnvelope
import com.monadial.waygrid.common.domain.model.message.Message

trait TransportEnvelopeCodec[F[+_], T <: TransportEnvelope]:
  def encode[M <: Message](envelope: Envelope[M]): F[T]
  def decode(decode: T): F[Envelope[? <: Message]]

