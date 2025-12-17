package com.monadial.waygrid.common.domain.value

import cats.Show
import cats.implicits.*
import com.monadial.waygrid.common.domain.algebra.value.uri.URIValue
import com.monadial.waygrid.common.domain.model.Waygrid
import com.monadial.waygrid.common.domain.model.node.Value.{ NodeComponent, NodeDescriptor, NodeId, NodeService }
import com.monadial.waygrid.common.domain.value.Address.AddressError.{
  InvalidScheme,
  MissingAuthority,
  MissingNodeId,
  MissingScheme,
  MissingServiceSegment
}
import com.monadial.waygrid.common.domain.value.Address.EndpointDirection.{ Inbound, Outbound }
import org.http4s.Uri
import org.http4s.Uri.Scheme
import wvlet.airframe.ulid.ULID

object Address:

  private inline def SchemeValue: Scheme = Scheme.unsafeFromString(Waygrid.appName.toLowerCase)

  private inline def ensure(cond: Boolean, err: AddressError): Either[AddressError, Unit] =
    Either.cond(cond, (), err)

  sealed trait AddressError derives CanEqual
  object AddressError:
    case object MissingScheme         extends AddressError
    case object InvalidScheme         extends AddressError
    case object MissingAuthority      extends AddressError
    case object MissingServiceSegment extends AddressError
    case object MissingNodeId         extends AddressError

    given cats.Show[AddressError] with
      def show(e: AddressError): String = e.toString

  type ServiceAddress = ServiceAddress.Type
  object ServiceAddress extends URIValue:
    def apply(descriptor: NodeDescriptor): ServiceAddress =
      ServiceAddress(
        Uri(
          scheme = Some(SchemeValue),
          authority = Some(Uri.Authority(host = Uri.RegName(descriptor.component.show))),
          path = Uri.Path.unsafeFromString(s"/${descriptor.service.show}")
        )
      )

    def fromUri(uri: Uri): Either[AddressError, ServiceAddress] =
      for
        _ <- ensure(uri.scheme.nonEmpty, MissingScheme)
        _ <- ensure(uri.scheme.contains(SchemeValue), InvalidScheme)
        _ <- ensure(uri.authority.nonEmpty, MissingAuthority)
        _ <- ensure(uri.path.segments.nonEmpty, MissingServiceSegment)
      yield ServiceAddress(uri)

    def fromString(value: String): Either[AddressError, ServiceAddress] =
      fromUri(Uri.unsafeFromString(value))

    extension (a: ServiceAddress)
      def component: NodeComponent                                  = NodeComponent(a.unwrap.authority.get.host.value)
      def service: NodeService                                      = NodeService(a.unwrap.path.segments.head.toString)
      def toDescriptor: NodeDescriptor                              = NodeDescriptor(a.component, a.service)
      def toEndpoint(direction: EndpointDirection): LogicalEndpoint = LogicalEndpoint.fromAddress(a, direction)
      def toInboundEndpoint: LogicalEndpoint                        = toEndpoint(Inbound)
      def toOutboundEndpoint: LogicalEndpoint                       = toEndpoint(Outbound)

  type NodeAddress = NodeAddress.Type
  object NodeAddress extends URIValue:
    def apply(descriptor: NodeDescriptor, nodeId: NodeId): NodeAddress =
      NodeAddress(
        Uri(
          scheme = Some(SchemeValue),
          authority = Some(Uri.Authority(host = Uri.RegName(descriptor.component.show))),
          path = Uri.Path.unsafeFromString(s"/${descriptor.service.show}")
        ).withQueryParam("nodeId", nodeId.show)
      )

    def fromUri(uri: Uri): Either[AddressError, NodeAddress] =
      for
        _ <- ensure(uri.scheme.nonEmpty, MissingScheme)
        _ <- ensure(uri.scheme.contains(SchemeValue), InvalidScheme)
        _ <- ensure(uri.authority.nonEmpty, MissingAuthority)
        _ <- ensure(uri.path.segments.nonEmpty, MissingServiceSegment)
        _ <- ensure(uri.query.params.contains("nodeId"), MissingNodeId)
      yield NodeAddress(uri)

    def fromString(value: String): Either[AddressError, NodeAddress] =
      fromUri(Uri.unsafeFromString(value))

    extension (a: NodeAddress)
      def component: NodeComponent                                   = NodeComponent(a.unwrap.authority.get.host.value)
      def service: NodeService                                       = NodeService(a.unwrap.path.segments.head.toString)
      def nodeId: NodeId                                             = NodeId(ULID.fromString(a.unwrap.query.params("nodeId")))
      def toDescriptor: NodeDescriptor                               = NodeDescriptor(a.component, a.service)
      def toEndpoint(direction: EndpointDirection): PhysicalEndpoint = PhysicalEndpoint.fromAddress(a, direction)
      def toInboundEndpoint: PhysicalEndpoint                        = toEndpoint(Inbound)
      def toOutboundEndpoint: PhysicalEndpoint                       = toEndpoint(Outbound)

  sealed trait Endpoint:
    def descriptor: NodeDescriptor
    def direction: EndpointDirection

  object Endpoint:
    given Show[Endpoint] with
      def show(e: Endpoint): String = e match
        case LogicalEndpoint(descriptor, direction) =>
          s"${descriptor.component}.${descriptor.service}-${direction.toString}"
        case PhysicalEndpoint(descriptor, nodeId, direction) =>
          s"${descriptor.component}.${descriptor.service}@${nodeId.show}-${direction.toString}"

  enum EndpointDirection:
    case Inbound, Outbound

    override def toString: String = this match
      case Inbound  => "inbound"
      case Outbound => "outbound"

  final case class LogicalEndpoint(
    descriptor: NodeDescriptor,
    direction: EndpointDirection
  ) extends Endpoint
  object LogicalEndpoint:
    def fromAddress(address: ServiceAddress, direction: EndpointDirection): LogicalEndpoint =
      LogicalEndpoint(
        NodeDescriptor(address.component, address.service),
        direction
      )

  final case class PhysicalEndpoint(
    descriptor: NodeDescriptor,
    nodeId: NodeId,
    direction: EndpointDirection
  ) extends Endpoint
  object PhysicalEndpoint:
    def fromAddress(address: NodeAddress, direction: EndpointDirection): PhysicalEndpoint =
      PhysicalEndpoint(
        NodeDescriptor(address.component, address.service),
        address.nodeId,
        direction
      )
