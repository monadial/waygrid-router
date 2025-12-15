package com.monadial.waygrid.common.domain.model.traversal.condition

import io.circe.Json

/**
 * A small, deterministic condition language for conditional DAG edges.
 *
 * Conditions are evaluated against the output of the upstream node (if provided).
 * The pointer syntax is RFC-6901 JSON Pointer (e.g. `/a/b/0`).
 */
enum Condition:
  case Always
  case JsonExists(pointer: String)
  case JsonEquals(pointer: String, value: Json)
  case Not(cond: Condition)
  case And(all: List[Condition])
  case Or(any: List[Condition])

object Condition:

  def eval(cond: Condition, output: Option[Json]): Boolean =
    cond match
      case Condition.Always =>
        true
      case Condition.JsonExists(pointer) =>
        output.flatMap(atPointer(_, pointer)).isDefined
      case Condition.JsonEquals(pointer, value) =>
        output.flatMap(atPointer(_, pointer)).contains(value)
      case Condition.Not(c) =>
        !eval(c, output)
      case Condition.And(all) =>
        all.forall(eval(_, output))
      case Condition.Or(any) =>
        any.exists(eval(_, output))

  def atPointer(json: Json, pointer: String): Option[Json] =
    val normalized =
      if pointer.isEmpty || pointer == "/" then ""
      else if pointer.startsWith("/") then pointer.drop(1)
      else pointer

    if normalized.isEmpty then Some(json)
    else
      normalized
        .split('/')
        .toList
        .map(unescapePointerToken)
        .foldLeft(Option(json)) { (curr, token) =>
          curr.flatMap { j =>
            j.asObject.flatMap(_.apply(token)).orElse {
              j.asArray.flatMap(arr => token.toIntOption.flatMap(arr.lift))
            }
          }
        }

  private def unescapePointerToken(token: String): String =
    token.replace("~1", "/").replace("~0", "~")
