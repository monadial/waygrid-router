package com.monadial.waygrid.common.domain.model.topology

import com.monadial.waygrid.common.domain.algebra.messaging.event.Event
import com.monadial.waygrid.common.domain.algebra.messaging.message.Value.MessageId
import com.monadial.waygrid.common.domain.model.node.Node
import com.monadial.waygrid.common.domain.model.topology.Value.ContractId
import io.circe.Codec

sealed trait TopologyEvent extends Event
final case class JoinRequested(
  id: MessageId,
  contractId: ContractId,
  node: Node
) extends TopologyEvent derives Codec.AsObject

final case class JoinedSuccessfully(
  id: MessageId,
  contractId: ContractId
) extends TopologyEvent derives Codec.AsObject
final case class LeaveRequested(
  id: MessageId,
  contractId: ContractId
) extends TopologyEvent derives Codec.AsObject
final case class LeftSuccessfully(
  id: MessageId,
  contractId: ContractId
) extends TopologyEvent derives Codec.AsObject

final case class NodeJoinAccepted(id: MessageId) extends TopologyEvent derives Codec.AsObject
final case class NodeJoinRejected(
  id: MessageId,
  test: String
) extends TopologyEvent derives Codec.AsObject
