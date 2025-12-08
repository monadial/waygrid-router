package com.monadial.waygrid.common.application.util.circe.codecs

import com.monadial.waygrid.common.application.util.circe.codecs.DomainVectorClockCodecs.given
import com.monadial.waygrid.common.domain.model.traversal.Event.TraversalEvent
import com.monadial.waygrid.common.domain.model.traversal.dag.Value.NodeId
import com.monadial.waygrid.common.domain.model.traversal.state.Event.StateEvent
import com.monadial.waygrid.common.domain.model.traversal.state.TraversalState
import com.monadial.waygrid.common.domain.model.traversal.state.Value.RetryAttempt
import io.circe.{ Codec, Decoder, Encoder }

object DomainTraversalStateCodecs:

  given Codec[Map[NodeId, RetryAttempt]] = Codec.from(
    Decoder[Map[String, RetryAttempt]].map(_.map { case (k, v) => NodeId(k) -> v }),
    Encoder[Map[String, RetryAttempt]].contramap(_.map { case (k, v) => k.unwrap -> v })
  )

  given Encoder[TraversalEvent] = Encoder.derived
  given Decoder[TraversalEvent] = Decoder.derived

  given Encoder[StateEvent] = Encoder.derived
  given Decoder[StateEvent] = Decoder.derived

  given Encoder[TraversalState] = Encoder.derived
  given Decoder[TraversalState] = Decoder.derived
