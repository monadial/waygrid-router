package com.monadial.waygrid.common.application.util.circe.codecs

import com.monadial.waygrid.common.application.util.circe.DerivationConfiguration.given
import com.monadial.waygrid.common.application.util.circe.codecs.DomainRoutingDagCodecs.given
import com.monadial.waygrid.common.application.util.circe.codecs.DomainVectorClockCodecs.given
import com.monadial.waygrid.common.domain.model.traversal.Event.TraversalEvent
import com.monadial.waygrid.common.domain.model.traversal.dag.JoinStrategy
import com.monadial.waygrid.common.domain.model.traversal.dag.Value.{ BranchId, ForkId, NodeId }
import com.monadial.waygrid.common.domain.model.traversal.state.Event.StateEvent
import com.monadial.waygrid.common.domain.model.traversal.state.{
  BranchResult,
  BranchState,
  BranchStatus,
  ForkScope,
  PendingJoin,
  TraversalState
}
import com.monadial.waygrid.common.domain.model.traversal.state.Value.{ RemainingNodes, RetryAttempt, StateVersion }
import io.circe.{ Codec, Decoder, Encoder, KeyDecoder, KeyEncoder }

import java.time.Instant

object DomainTraversalStateCodecs:

  // ---------------------------------------------------------------------------
  // Key Encoders/Decoders for Map types
  // ---------------------------------------------------------------------------

  given KeyEncoder[NodeId] = KeyEncoder.instance(_.unwrap)
  given KeyDecoder[NodeId] = KeyDecoder.instance(s => Some(NodeId(s)))

  given KeyEncoder[ForkId] = KeyEncoder.instance(_.unwrap.toString)
  given KeyDecoder[ForkId] = KeyDecoder.instance(s => Some(ForkId.unsafeFrom(s)))

  given KeyEncoder[BranchId] = KeyEncoder.instance(_.unwrap.toString)
  given KeyDecoder[BranchId] = KeyDecoder.instance(s => Some(BranchId.fromStringUnsafe[cats.Id](s)))

  // ---------------------------------------------------------------------------
  // Value Codecs
  // ---------------------------------------------------------------------------

  given Codec[RetryAttempt] = Codec.from(
    Decoder[Int].map(RetryAttempt(_)),
    Encoder[Int].contramap(_.unwrap)
  )

  given Codec[RemainingNodes] = Codec.from(
    Decoder[Int].map(RemainingNodes(_)),
    Encoder[Int].contramap(_.unwrap)
  )

  given Codec[StateVersion] = Codec.from(
    Decoder[Long].map(StateVersion(_)),
    Encoder[Long].contramap(_.unwrap)
  )

  given Codec[Instant] = Codec.from(
    Decoder[String].map(Instant.parse),
    Encoder[String].contramap(_.toString)
  )

  // ---------------------------------------------------------------------------
  // Fork/Join Type Codecs
  // ---------------------------------------------------------------------------

  given Codec[JoinStrategy] = Codec.derived[JoinStrategy]
  given Codec[BranchStatus] = Codec.derived[BranchStatus]
  given Codec[BranchResult] = Codec.derived[BranchResult]
  given Codec[ForkScope]    = Codec.derived[ForkScope]
  given Codec[BranchState]  = Codec.derived[BranchState]
  given Codec[PendingJoin]  = Codec.derived[PendingJoin]

  // ---------------------------------------------------------------------------
  // Map Codecs
  // ---------------------------------------------------------------------------

  given retriesMapCodec: Codec[Map[NodeId, RetryAttempt]] = Codec.from(
    Decoder[Map[String, RetryAttempt]].map(_.map { case (k, v) => NodeId(k) -> v }),
    Encoder[Map[String, RetryAttempt]].contramap(_.map { case (k, v) => k.unwrap -> v })
  )

  given forkScopesMapCodec: Codec[Map[ForkId, ForkScope]] = Codec.from(
    Decoder[Map[String, ForkScope]].map(_.map { case (k, v) => ForkId.unsafeFrom(k) -> v }),
    Encoder[Map[String, ForkScope]].contramap(_.map { case (k, v) => k.unwrap.toString -> v })
  )

  given branchStatesMapCodec: Codec[Map[BranchId, BranchState]] = Codec.from(
    Decoder[Map[String, BranchState]].map(_.map { case (k, v) => BranchId.fromStringUnsafe[cats.Id](k) -> v }),
    Encoder[Map[String, BranchState]].contramap(_.map { case (k, v) => k.unwrap.toString -> v })
  )

  given pendingJoinsMapCodec: Codec[Map[NodeId, PendingJoin]] = Codec.from(
    Decoder[Map[String, PendingJoin]].map(_.map { case (k, v) => NodeId(k) -> v }),
    Encoder[Map[String, PendingJoin]].contramap(_.map { case (k, v) => k.unwrap -> v })
  )

  // ---------------------------------------------------------------------------
  // Event and State Codecs
  // ---------------------------------------------------------------------------

  given Encoder[TraversalEvent] = Encoder.derived
  given Decoder[TraversalEvent] = Decoder.derived

  given Encoder[StateEvent] = Encoder.derived
  given Decoder[StateEvent] = Decoder.derived

  given Encoder[TraversalState] = Encoder.derived
  given Decoder[TraversalState] = Decoder.derived
