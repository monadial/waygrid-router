package com.monadial.waygrid.common.domain.model.vectorclock

import scala.collection.immutable.SortedMap

import com.monadial.waygrid.common.domain.model.vectorclock.VectorClock.Comparison
import com.monadial.waygrid.common.domain.model.vectorclock.VectorClock.Comparison.{ After, Before, Concurrent, Equal }
import com.monadial.waygrid.common.domain.value.Address.NodeAddress

/**
 * A VectorClock is a distributed logical clock for tracking causality across nodes.
 * Each node is assigned a monotonically increasing version (logical counter).
 */
final case class VectorClock(entries: SortedMap[NodeAddress, Long]):

  /**
   * Increments the version for the given node by 1.
   * If the node is not present, it starts from version 1.
   */
  def tick(node: NodeAddress): VectorClock =
    VectorClock(entries.updatedWith(node):
        case Some(value) => Some(value + 1)
        case None        => Some(1L))

  /**
   * Merges this vector clock with another, taking the max version for each node.
   */
  def merge(other: VectorClock): VectorClock =
    VectorClock((entries.keySet ++ other.entries.keySet)
      .map: key =>
        key -> math.max(entries.getOrElse(key, 0L), other.entries.getOrElse(key, 0L))
      .to(SortedMap))

  /**
   * Compares this vector clock with another.
   * Returns Before, After, Equal, or Concurrent depending on causal relationship.
   */
  def compareTo(other: VectorClock): Comparison =
    val allKeys = entries.keySet union other.entries.keySet

    val (lt, gt) = allKeys.foldLeft((false, false)) { case ((ltAcc, gtAcc), key) =>
      val a = entries.getOrElse(key, 0L)
      val b = other.entries.getOrElse(key, 0L)

      (
        ltAcc || a < b,
        gtAcc || a > b
      )
    }

    (lt, gt) match
      case (true, true)   => Concurrent
      case (true, false)  => Before
      case (false, true)  => After
      case (false, false) => Equal

  /**
   * Returns true if this clock happened before the other (is strictly less).
   */
  def isBefore(other: VectorClock): Boolean =
    compareTo(other) == VectorClock.Comparison.Before

  /**
   * Returns true if this clock happened after the other (is strictly greater).
   */
  def isAfter(other: VectorClock): Boolean =
    compareTo(other) == VectorClock.Comparison.After

  /**
   * Returns true if the two clocks are concurrent (neither before nor after).
   */
  def isConcurrentWith(other: VectorClock): Boolean =
    compareTo(other) == VectorClock.Comparison.Concurrent

object VectorClock:
  /**
   * Returns an empty vector clock (no versions recorded).
   */
  val empty: VectorClock = VectorClock(SortedMap.empty)

  /**
   * Creates a new vector clock with a single node at version 1 — the originator.
   */
  def initial(node: NodeAddress): VectorClock = VectorClock(SortedMap(node -> 1L))

  /**
   * Enumeration of possible causal relationships between vector clocks.
   */
  enum Comparison:
    case Before, After, Equal, Concurrent
