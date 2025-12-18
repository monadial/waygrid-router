package com.monadial.waygrid.common.application.util.scodec.codecs

import com.monadial.waygrid.common.application.util.scodec.codecs.DomainRoutingDagScodecCodecs.given
import com.monadial.waygrid.common.application.util.scodec.codecs.DomainTraversalStateScodecCodecs.given
import com.monadial.waygrid.common.domain.model.envelope.Value.{ Stamp, TraversalRefStamp, TraversalStamp }
import com.monadial.waygrid.common.domain.model.traversal.dag.Dag
import com.monadial.waygrid.common.domain.model.traversal.dag.Value.DagHash
import com.monadial.waygrid.common.domain.model.traversal.state.TraversalState
import scodec.*
import scodec.codecs.*

/**
 * Scodec binary codecs for Stamp types.
 *
 * Type discriminators:
 * - 0x01 = TraversalStamp (contains full TraversalState and Dag)
 * - 0x02 = TraversalRefStamp (lightweight reference with just DagHash)
 */
object DomainStampScodecCodecs:

  // ---------------------------------------------------------------------------
  // TraversalStamp codec
  // ---------------------------------------------------------------------------

  private val traversalStampCodec: Codec[TraversalStamp] =
    (summon[Codec[TraversalState]] :: summon[Codec[Dag]]).xmap(
      { case (state, dag) => TraversalStamp(state, dag) },
      ts => (ts.state, ts.dag)
    )

  // ---------------------------------------------------------------------------
  // TraversalRefStamp codec
  // ---------------------------------------------------------------------------

  private val traversalRefStampCodec: Codec[TraversalRefStamp] =
    summon[Codec[DagHash]].xmap(
      dagHash => TraversalRefStamp(dagHash),
      _.dagHash
    )

  // ---------------------------------------------------------------------------
  // Stamp sealed trait codec with type discriminators
  // ---------------------------------------------------------------------------

  given Codec[Stamp] = discriminated[Stamp]
    .by(uint8)
    .typecase(1, traversalStampCodec)
    .typecase(2, traversalRefStampCodec)
