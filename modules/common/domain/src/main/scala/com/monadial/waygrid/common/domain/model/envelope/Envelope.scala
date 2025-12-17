package com.monadial.waygrid.common.domain.model.envelope

import com.monadial.waygrid.common.domain.algebra.messaging.message.Message
import com.monadial.waygrid.common.domain.model.envelope.Value.{ EnvelopeId, Stamp }
import com.monadial.waygrid.common.domain.value.Address.{ Endpoint, NodeAddress }

import scala.reflect.ClassTag

type EnvelopeStamps = Map[Class[? <: Stamp], List[Stamp]]

trait Envelope[M]:
  def id: EnvelopeId
  def sender: NodeAddress
  def endpoint: Endpoint
  def message: M
  def stamps: EnvelopeStamps

final case class DomainEnvelope[M <: Message](
  id: EnvelopeId,
  sender: NodeAddress,
  endpoint: Endpoint,
  message: M,
  stamps: EnvelopeStamps
) extends Envelope[M]:

  def addStamp(stamp: Stamp): DomainEnvelope[M] =
    copy(stamps = stamps + (stamp.getClass -> (stamp :: stamps.getOrElse(stamp.getClass, Nil))))

  def findStamp[T <: Stamp](using tag: ClassTag[T]): Option[T] =
    stamps
      .get(tag.runtimeClass.asInstanceOf[Class[? <: Stamp]])
      .flatMap(_.collectFirst { case s: T => s })
