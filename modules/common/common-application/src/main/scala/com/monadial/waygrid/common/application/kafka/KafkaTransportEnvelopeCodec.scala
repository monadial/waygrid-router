package com.monadial.waygrid.common.application.kafka

import cats.Applicative
import cats.effect.kernel.Resource
import cats.implicits.*
import com.monadial.waygrid.common.application.`macro`.{ CirceEventCodecRegistryMacro }
import com.monadial.waygrid.common.application.algebra.TransportEnvelopeCodec
import com.monadial.waygrid.common.application.domain.model.envelope.Envelope
import com.monadial.waygrid.common.application.kafka.model.KafkaTransportEnvelope
import com.monadial.waygrid.common.domain.model.message.Message

object KafkaTransportEnvelopeCodec:

  def default[F[+_]]: TransportEnvelopeCodec[F, KafkaTransportEnvelope] =
    new TransportEnvelopeCodec[F, KafkaTransportEnvelope]:


