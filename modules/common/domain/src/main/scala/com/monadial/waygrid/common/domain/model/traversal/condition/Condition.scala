package com.monadial.waygrid.common.domain.model.traversal.condition

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
