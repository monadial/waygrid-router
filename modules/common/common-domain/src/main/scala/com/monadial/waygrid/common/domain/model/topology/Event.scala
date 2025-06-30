package com.monadial.waygrid.common.domain.model.topology

import com.monadial.waygrid.common.domain.model.event.Event
import com.monadial.waygrid.common.domain.model.node.Node
import com.monadial.waygrid.common.domain.model.topology.Value.ContractId
import io.circe.Codec

sealed trait TopologyEvent                                         extends Event
final case class JoinRequested(contractId: ContractId, node: Node) extends TopologyEvent derives Codec.AsObject
final case class JoinedSuccessfully(contractId: ContractId)        extends TopologyEvent derives Codec.AsObject
final case class LeaveRequested(contractId: ContractId)            extends TopologyEvent derives Codec.AsObject
final case class LeftSuccessfully(contractId: ContractId)          extends TopologyEvent derives Codec.AsObject

final case class NodeJoinAccepted()             extends TopologyEvent derives Codec.AsObject
final case class NodeJoinRejected(test: String) extends TopologyEvent derives Codec.AsObject
