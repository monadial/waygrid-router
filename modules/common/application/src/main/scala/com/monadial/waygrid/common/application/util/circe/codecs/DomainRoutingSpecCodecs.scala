package com.monadial.waygrid.common.application.util.circe.codecs

import cats.data.NonEmptyList
import com.monadial.waygrid.common.application.instances.DurationInstances.given
import com.monadial.waygrid.common.application.util.circe.DerivationConfiguration.given
import com.monadial.waygrid.common.application.util.circe.codecs.DomainParameterCodecs.given
import com.monadial.waygrid.common.application.util.circe.codecs.DomainRoutingCodecs.given
import com.monadial.waygrid.common.domain.model.routing.Value.DeliveryStrategy
import com.monadial.waygrid.common.domain.model.traversal.condition.Condition
import com.monadial.waygrid.common.domain.model.traversal.dag.JoinStrategy
import com.monadial.waygrid.common.domain.model.traversal.spec.{ Node, Spec }
import io.circe.{ Codec, Decoder, Encoder, Json }
import io.circe.syntax.*

object DomainRoutingSpecCodecs:

  given Codec[DeliveryStrategy] = Codec.derived[DeliveryStrategy]
  given Codec[JoinStrategy] = Codec.derived[JoinStrategy]
  given Codec[Condition] = Codec.derived[Condition]

  // Codecs for each Node variant
  given Codec[Node.ConditionalEdge] = Codec.derived[Node.ConditionalEdge]
  given Codec[Node.Standard] = Codec.derived[Node.Standard]
  given Codec[Node.Fork] = Codec.derived[Node.Fork]
  given Codec[Node.Join] = Codec.derived[Node.Join]

  // Sealed trait codec with discriminator
  given Encoder[Node] = Encoder.instance {
    case n: Node.Standard =>
      n.asJson.deepMerge(Json.obj("type" -> Json.fromString("standard")))
    case n: Node.Fork =>
      n.asJson.deepMerge(Json.obj("type" -> Json.fromString("fork")))
    case n: Node.Join =>
      n.asJson.deepMerge(Json.obj("type" -> Json.fromString("join")))
  }

  given Decoder[Node] = Decoder.instance { cursor =>
    cursor.downField("type").as[String].flatMap {
      case "standard" => cursor.as[Node.Standard]
      case "fork"     => cursor.as[Node.Fork]
      case "join"     => cursor.as[Node.Join]
      case other      => Left(io.circe.DecodingFailure(s"Unknown node type: $other", cursor.history))
    }
  }

  given Codec[Node] = Codec.from(summon[Decoder[Node]], summon[Encoder[Node]])

  // NonEmptyList codec
  given [A: Decoder]: Decoder[NonEmptyList[A]] = Decoder.decodeNonEmptyList[A]
  given [A: Encoder]: Encoder[NonEmptyList[A]] = Encoder.encodeNonEmptyList[A]

  given Codec[Spec] = Codec.derived[Spec]
