package com.monadial.waygrid.common.application.model.envelope

import com.monadial.waygrid.common.domain.model.message.Message

final case class Envelope[M <: Message](message: M)


object Envelope:
  def wrap[M <: Message](message: M): Envelope[M] = Envelope(message)
  def unwrap[M <: Message](envelope: Envelope[M]): M = envelope.message