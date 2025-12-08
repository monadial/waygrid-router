package com.monadial.waygrid.common.domain.model.node

import cats.Show
import cats.implicits.*
import com.monadial.waygrid.common.domain.algebra.value.string.{ StringValue, StringValueRefined }
import com.monadial.waygrid.common.domain.algebra.value.ulid.ULIDValue
import com.monadial.waygrid.common.domain.value.Address.{ Endpoint, EndpointDirection, ServiceAddress }
import eu.timepit.refined.boolean.And
import eu.timepit.refined.collection.MaxSize
import eu.timepit.refined.string.MatchesRegex
import io.circe.Codec

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

  type NodeSettingsPath = NodeSettingsPath.Type
  object NodeSettingsPath extends StringValue

  type NodeClientId = NodeClientId.Type
  object NodeClientId extends StringValue

  type NodeReceiveAddress = NodeReceiveAddress.Type
  object NodeReceiveAddress extends StringValue

  enum NodeCapability:
    case TopologyJoinable
    case ExposesApi

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

    extension (descriptor: NodeDescriptor)
      def toServiceAddress: ServiceAddress = ServiceAddress(descriptor)
      def toEndpoint(direction: EndpointDirection): Endpoint =
        descriptor
          .toServiceAddress
          .toEndpoint(direction)

  final case class NodeRuntime() derives Codec.AsObject
