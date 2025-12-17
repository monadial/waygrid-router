package com.monadial.waygrid.common.application.util.circe.codecs

import cats.data.NonEmptyList
import com.monadial.waygrid.common.application.instances.DurationInstances.given
import com.monadial.waygrid.common.application.util.circe.DerivationConfiguration.given
import com.monadial.waygrid.common.application.util.circe.codecs.DomainRoutingCodecs.given
import com.monadial.waygrid.common.domain.model.traversal.condition.Condition
import com.monadial.waygrid.common.domain.model.traversal.dag.{ Dag, Edge, JoinStrategy, Node, NodeType }
import com.monadial.waygrid.common.domain.model.traversal.dag.Value.{ BranchId, EdgeGuard, ForkId, NodeId }
import io.circe.{ Codec, Decoder, Encoder }

object DomainRoutingDagCodecs:

  // ---------------------------------------------------------------------------
  // ULID-based value codecs
  // ---------------------------------------------------------------------------

  given Codec[ForkId] = Codec.from(
    Decoder[String].map(ForkId.unsafeFrom),
    Encoder[String].contramap(_.unwrap.toString)
  )

  given Codec[BranchId] = Codec.from(
    Decoder[String].map(BranchId.fromStringUnsafe[cats.Id](_)),
    Encoder[String].contramap(_.unwrap.toString)
  )

  // ---------------------------------------------------------------------------
  // Enum codecs
  // ---------------------------------------------------------------------------

  given Codec[Condition]    = Codec.derived[Condition]
  given Codec[JoinStrategy] = Codec.derived[JoinStrategy]
  given Codec[NodeType]     = Codec.derived[NodeType]

  // ---------------------------------------------------------------------------
  // DAG node and structure codecs
  // ---------------------------------------------------------------------------

  given Codec[Node] = Codec.derived[Node]

  /**
   * Provides a custom Circe codec for Map[NodeId, Node].
   *
   * Circe is unable to automatically derive codecs for Map keys that are opaque types,
   * such as NodeId, because it does not know how to encode/decode them as JSON object keys.
   * This manual implementation encodes NodeId keys as their underlying String representation,
   * and decodes them back to NodeId, ensuring stable and reliable serialization.
   */
  private given Codec[Map[NodeId, Node]] = Codec.from(
    Decoder[Map[String, Node]].map(_.map { case (k, v) => NodeId(k) -> v }),
    Encoder[Map[String, Node]].contramap(_.map { case (k, v) => k.unwrap -> v })
  )

  given Codec[EdgeGuard] = Codec.derived[EdgeGuard]
  given Codec[Edge]      = Codec.derived[Edge]

  given [A: Decoder]: Decoder[NonEmptyList[A]] = Decoder.decodeNonEmptyList[A]
  given [A: Encoder]: Encoder[NonEmptyList[A]] = Encoder.encodeNonEmptyList[A]

  given Codec[Dag] = Codec.derived[Dag]
