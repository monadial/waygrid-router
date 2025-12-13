package com.monadial.waygrid.system.topology.domain.model.election

import com.monadial.waygrid.common.domain.algebra.value.long.LongValue
import com.monadial.waygrid.common.domain.value.Address.NodeAddress

object Value:

  type FencingToken = FencingToken.Type
  object FencingToken extends LongValue:

    extension (token: FencingToken)
      def bump: FencingToken = FencingToken(token.unwrap + 1L)

  type ElectionEpoch = ElectionEpoch.Type
  object ElectionEpoch extends LongValue:
    def initial: ElectionEpoch = ElectionEpoch(0L)

    extension (epoch: ElectionEpoch)
      def bump: ElectionEpoch = ElectionEpoch(epoch.unwrap + 1L)

  final case class Leader(
    nodeAddress: NodeAddress,
    fencingToken: FencingToken,
    epoch: ElectionEpoch
  )

  enum ElectionRole:
    case Follower, Leader, Candidate
