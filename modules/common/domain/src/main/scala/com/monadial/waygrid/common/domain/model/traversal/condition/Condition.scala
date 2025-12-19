package com.monadial.waygrid.common.domain.model.traversal.condition

import io.circe.{ Decoder, Encoder, Json }

/**
 * A small, deterministic condition language for conditional DAG edges.
 *
 * Conditions are evaluated to determine which edge to follow during traversal.
 * Currently supports basic boolean logic (Always, Not, And, Or).
 *
 * Note: JSON pointer-based conditions (JsonExists, JsonEquals) will be added
 * in a future version to support content-based routing.
 */
enum Condition:
  case Always
  case Not(cond: Condition)
  case And(all: List[Condition])
  case Or(any: List[Condition])

object Condition:

  def eval(cond: Condition): Boolean =
    cond match
      case Condition.Always =>
        true
      case Condition.Not(c) =>
        !eval(c)
      case Condition.And(all) =>
        all.forall(eval)
      case Condition.Or(any) =>
        any.exists(eval)

  // ---------------------------------------------------------------------------
  // Circe JSON codecs for recursive Condition type
  // ---------------------------------------------------------------------------

  given Encoder[Condition] = Encoder.instance {
    case Condition.Always =>
      Json.obj("type" -> Json.fromString("Always"))
    case Condition.Not(cond) =>
      Json.obj(
        "type" -> Json.fromString("Not"),
        "cond" -> Encoder[Condition].apply(cond)
      )
    case Condition.And(all) =>
      Json.obj(
        "type" -> Json.fromString("And"),
        "all"  -> Json.arr(all.map(Encoder[Condition].apply)*)
      )
    case Condition.Or(any) =>
      Json.obj(
        "type" -> Json.fromString("Or"),
        "any"  -> Json.arr(any.map(Encoder[Condition].apply)*)
      )
  }

  given Decoder[Condition] = Decoder.instance { cursor =>
    cursor.get[String]("type").flatMap {
      case "Always" => Right(Condition.Always)
      case "Not" =>
        cursor.get[Condition]("cond").map(Condition.Not.apply)
      case "And" =>
        cursor.get[List[Condition]]("all").map(Condition.And.apply)
      case "Or" =>
        cursor.get[List[Condition]]("any").map(Condition.Or.apply)
      case other =>
        Left(io.circe.DecodingFailure(s"Unknown Condition type: $other", cursor.history))
    }
  }
