package com.monadial.waygrid.common.domain.model.node

import com.monadial.waygrid.common.domain.value.string.{StringValue, StringValueRefined}
import eu.timepit.refined.predicates.all.NonEmpty
import io.circe.Codec

type NodeComponent = NodeComponent.Type
object NodeComponent extends StringValue

type NodeService = NodeService.Type
object NodeService extends StringValue

type NodeClusterId = NodeClusterId.Type
object NodeClusterId extends StringValue

type NodeAddress = NodeAddress.Type
object NodeAddress extends StringValue

type NodeSettingsPath = NodeSettingsPath.Type
object NodeSettingsPath extends StringValue

type NodeClientId = NodeClientId.Type
object NodeClientId extends StringValue

type NodeReceiveAddress = NodeReceiveAddress.Type
object NodeReceiveAddress extends StringValue


type NodeNameTest = NodeNameTest.Type
object NodeNameTest extends StringValueRefined[NonEmpty]

enum NodeDescriptor(
  val component: NodeComponent,
  val service: NodeService
) derives Codec.AsObject:
  case Destination(override val service: NodeService)
      extends NodeDescriptor(NodeComponent("destination"), service)
  case Origin(override val service: NodeService)
      extends NodeDescriptor(NodeComponent("origin"), service)
  case Processor(override val service: NodeService)
      extends NodeDescriptor(NodeComponent("processor"), service)
  case System(override val service: NodeService)
      extends NodeDescriptor(NodeComponent("system"), service)

final case class NodeRuntime() derives Codec.AsObject
