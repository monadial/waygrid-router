package com.monadial.waygrid.common.domain.model.node

import cats.Show
import cats.implicits.*
import com.monadial.waygrid.common.domain.model.{ Waygrid, node }
import com.monadial.waygrid.common.domain.value.string.{ StringValue, StringValueRefined }
import com.monadial.waygrid.common.domain.value.ulid.ULIDValue
import com.monadial.waygrid.common.domain.value.uri.URIValue
import eu.timepit.refined.boolean.And
import eu.timepit.refined.collection.MaxSize
import eu.timepit.refined.string
import eu.timepit.refined.string.MatchesRegex
import io.circe.Codec
import org.http4s.Uri
import org.http4s.Uri.Scheme

object Value:
  type NodeComponent = NodeComponent.Type
  object NodeComponent extends StringValue:
    def Origin: NodeComponent      = NodeComponent("origin")
    def Processor: NodeComponent   = NodeComponent("processor")
    def Destination: NodeComponent = NodeComponent("destination")
    def System: NodeComponent      = NodeComponent("system")

  type NodeService = NodeService.Type
  object NodeService extends StringValue

  type NodeClusterId = NodeClusterId.Type
  object NodeClusterId extends StringValue

  type NodeId = NodeId.Type
  object NodeId extends ULIDValue

  type NodeRegion = NodeRegion.Type
  object NodeRegion extends StringValueRefined[MatchesRegex["^[a-z]{2}(?:-[a-z]{1,16})*-[0-9]{1,2}$"] And MaxSize[32]]

  type NodeAddress = NodeAddress.Type
  object NodeAddress extends URIValue:
    def fromNode(node: Node): NodeAddress =
      NodeAddress(
        node.descriptor.serviceAddress().unwrap +? ("region" -> node.region.show) +? ("clusterId" -> node.clusterId.show) +? ("nodeId" -> node.id.show)
      )

    extension (nodeAddress: NodeAddress)
      inline def service: ServiceAddress = ServiceAddress(nodeAddress.unwrap -? "clusterId" -? "nodeId")

  type ServiceAddress = ServiceAddress.Type
  object ServiceAddress extends URIValue:
    private inline def nodeScheme = Scheme.unsafeFromString(Waygrid.appName)

    def fromUri(value: Uri): ServiceAddress =
      if value.scheme.contains(nodeScheme) then ServiceAddress(value)
      else throw IllegalArgumentException(s"NodeAddress must have scheme ${Waygrid.appName}, got: ${value}")

    def fromString(value: String): ServiceAddress =
      fromUri(Uri.unsafeFromString(value))

    def fromNodeDescriptor(nodeDescriptor: NodeDescriptor): ServiceAddress =
      ServiceAddress(
        Uri(
          scheme = Some(nodeScheme),
          authority = Some(Uri.Authority(host = Uri.RegName(nodeDescriptor.component.show)))
        ) / nodeDescriptor.service.show
      )

    extension (serviceAddress: ServiceAddress)
      def toNodeDescriptor: Option[NodeDescriptor] =
        for
          host <- serviceAddress.unwrap.authority.map(_.host)
          component = NodeComponent(host.value)
          service <- serviceAddress.unwrap.path.segments.headOption.map(x => NodeService(x.toString))
        yield NodeDescriptor(component, service)

  type NodeSettingsPath = NodeSettingsPath.Type
  object NodeSettingsPath extends StringValue

  type NodeClientId = NodeClientId.Type
  object NodeClientId extends StringValue

  type NodeReceiveAddress = NodeReceiveAddress.Type
  object NodeReceiveAddress extends StringValue

  final case class NodeDescriptor(
    component: NodeComponent,
    service: NodeService
  ) derives Codec.AsObject

  object NodeDescriptor:
    def Destination(service: NodeService): NodeDescriptor =
      NodeDescriptor(NodeComponent.Destination, service)

    def Origin(service: NodeService): NodeDescriptor =
      NodeDescriptor(NodeComponent.Origin, service)

    def Processor(service: NodeService): NodeDescriptor =
      NodeDescriptor(NodeComponent.Processor, service)

    def System(service: NodeService): NodeDescriptor =
      NodeDescriptor(NodeComponent.System, service)

    given Show[NodeDescriptor] with
      def show(nodeDescriptor: NodeDescriptor): String =
        s"${nodeDescriptor.component.show}.${nodeDescriptor.service.show}"

    extension (nodeDescriptor: NodeDescriptor)
      inline def serviceAddress(): ServiceAddress =
        ServiceAddress.fromNodeDescriptor(nodeDescriptor)

  final case class NodeRuntime() derives Codec.AsObject
