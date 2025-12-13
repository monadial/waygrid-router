package com.monadial.waygrid.common.domain.model.envelope

import com.monadial.waygrid.common.domain.algebra.value.string.StringValue
import com.monadial.waygrid.common.domain.algebra.value.ulid.ULIDValue
import com.monadial.waygrid.common.domain.model.routing.Value.TraversalId
import com.monadial.waygrid.common.domain.model.traversal.dag.Dag
import com.monadial.waygrid.common.domain.model.traversal.state.TraversalState
import com.monadial.waygrid.common.domain.value.Address.NodeAddress

object Value:
  type EnvelopeId = EnvelopeId.Type
  object EnvelopeId extends ULIDValue

  type GroupId = GroupId.Type
  object GroupId extends StringValue

  sealed trait Stamp
  final case class TraversalStamp(state: TraversalState, dag: Dag) extends Stamp:
    def update(fn: TraversalState => TraversalState): TraversalStamp =
      copy(state = fn(state))

  object TraversalStamp:
    def initial(traversalId: TraversalId, actor: NodeAddress, dag: Dag): TraversalStamp =
      TraversalStamp(
        TraversalState.initial(traversalId, actor, dag),
        dag
      )
