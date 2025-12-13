package com.monadial.waygrid.common.application.util.circe.codecs.events

import com.monadial.waygrid.common.domain.model.traversal.Event.{
  NodeTraversalFailed,
  NodeTraversalRequested,
  NodeTraversalSucceeded,
  TraversalCancelled,
  TraversalFailed,
  TraversalRequested,
  TraversalResumed,
  TraversalScheduled
}
import io.circe.Codec
import io.circe.generic.semiauto

object DomainTraversalEvents:
  given Codec[TraversalRequested]     = semiauto.deriveCodec
  given Codec[TraversalScheduled]     = semiauto.deriveCodec
  given Codec[TraversalResumed]       = semiauto.deriveCodec
  given Codec[TraversalFailed]        = semiauto.deriveCodec
  given Codec[TraversalCancelled]     = semiauto.deriveCodec
  given Codec[NodeTraversalRequested] = semiauto.deriveCodec
  given Codec[NodeTraversalSucceeded] = semiauto.deriveCodec
  given Codec[NodeTraversalFailed]    = semiauto.deriveCodec
