package com.monadial.waygrid.common.application.domain.model.envelope

import cats.effect.Async
import cats.implicits.*
import com.monadial.waygrid.common.application.algebra.ThisNode
import com.monadial.waygrid.common.application.domain.model.envelope.Value.EnvelopeId
import com.monadial.waygrid.common.domain.SystemWaygridApp
import com.monadial.waygrid.common.domain.model.message.Message
import com.monadial.waygrid.common.domain.model.node.Value.{ NodeAddress, NodeDescriptor, ServiceAddress }

final case class Envelope[M <: Message](
  id: EnvelopeId,
  address: ServiceAddress,
  sender: NodeAddress,
  message: M
)

trait ToService
trait ToNode

object Envelope:
  def toWaystation[F[+_]: {Async, ThisNode}, M <: Message](message: M): F[Envelope[M]] = toService(
    SystemWaygridApp.Waystation.serviceAddress,
    message
  )

  def toService[F[+_]: {Async, ThisNode}, M <: Message](
    address: ServiceAddress,
    message: M
  ): F[Envelope[M]] =
    for
      id       <- EnvelopeId.next[F]
      thisNode <- ThisNode[F].get
    yield Envelope(
      id,
      address,
      thisNode.address,
      message
    )

//  def toNode[F[+_]: {Async, ThisNode}, M <: Message](nodeAddress: NodeAddress, message: M): F[Envelope[M]]

