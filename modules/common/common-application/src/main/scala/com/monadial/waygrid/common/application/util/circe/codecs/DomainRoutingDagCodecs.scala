package com.monadial.waygrid.common.application.util.circe.codecs

import com.monadial.waygrid.common.application.util.circe.codecs.DomainRoutingCodecs.given
import com.monadial.waygrid.common.domain.model.traversal.dag.{Dag, Edge, Node}
import com.monadial.waygrid.common.domain.model.traversal.dag.Value.{EdgeGuard, NodeId}
import io.circe.{Codec, Decoder, Encoder}

object DomainRoutingDagCodecs:

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
  given Codec[Edge] = Codec.derived[Edge]
  given Codec[Dag]  = Codec.derived[Dag]
