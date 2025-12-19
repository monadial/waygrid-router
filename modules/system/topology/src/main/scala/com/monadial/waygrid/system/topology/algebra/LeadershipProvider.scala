package com.monadial.waygrid.system.topology.algebra

import scala.concurrent.duration.Duration

import com.monadial.waygrid.common.domain.model.node.Value.NodeId
import com.monadial.waygrid.common.domain.value.Address.NodeAddress
import com.monadial.waygrid.system.topology.domain.model.election.Value.FencingToken

type NodeWon  = FencingToken
type NodeLost = (fencingToken: FencingToken, leader: NodeAddress)

trait LeadershipProvider[F[+_]]:

  def acquire(
    nodeId: NodeId,
    lease: Duration
  ): F[Either[NodeLost, NodeWon]]

  def renew(
    nodeId: NodeId,
    token: FencingToken
  ): F[Unit]

  def release(
    nodeId: NodeId,
    token: FencingToken
  ): F[Unit]
