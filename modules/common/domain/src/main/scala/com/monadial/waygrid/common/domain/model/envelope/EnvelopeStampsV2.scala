package com.monadial.waygrid.common.domain.model.envelope

import com.monadial.waygrid.common.domain.model.envelope.Value.{ Stamp, TraversalRefStamp, TraversalStamp }

/**
 * Avro-compatible envelope stamps container.
 *
 * Unlike the original `EnvelopeStamps` (a `Map[Class[? <: Stamp], List[Stamp]]`),
 * this representation uses explicit typed fields for each stamp type, making it
 * fully compatible with Apache Avro serialization.
 *
 * Avro requires:
 * - Known field names at schema time (not dynamic Class keys)
 * - Homogeneous collections (not heterogeneous List[Stamp])
 * - No Java reflection-based types
 *
 * @param traversalStamps Full traversal stamps containing state and DAG
 * @param traversalRefStamps Lightweight reference stamps for context restoration
 */
final case class EnvelopeStampsV2(
  traversalStamps: List[TraversalStamp] = Nil,
  traversalRefStamps: List[TraversalRefStamp] = Nil
):
  /**
   * Convert to legacy EnvelopeStamps format for backward compatibility.
   *
   * This is useful during migration when some code paths still expect
   * the original Map-based representation.
   */
  def toLegacy: EnvelopeStamps =
    val stamps: List[(Class[? <: Stamp], List[Stamp])] = List(
      classOf[TraversalStamp]    -> traversalStamps,
      classOf[TraversalRefStamp] -> traversalRefStamps
    ).filter(_._2.nonEmpty)
    stamps.toMap

  /**
   * Check if there are any stamps present.
   */
  def isEmpty: Boolean =
    traversalStamps.isEmpty && traversalRefStamps.isEmpty

  /**
   * Check if there are any stamps present.
   */
  def nonEmpty: Boolean = !isEmpty

  /**
   * Get the first traversal stamp if present.
   */
  def headTraversalStamp: Option[TraversalStamp] =
    traversalStamps.headOption

  /**
   * Get the first traversal ref stamp if present.
   */
  def headTraversalRefStamp: Option[TraversalRefStamp] =
    traversalRefStamps.headOption

  /**
   * Add a traversal stamp.
   */
  def addTraversalStamp(stamp: TraversalStamp): EnvelopeStampsV2 =
    copy(traversalStamps = stamp :: traversalStamps)

  /**
   * Add a traversal ref stamp.
   */
  def addTraversalRefStamp(stamp: TraversalRefStamp): EnvelopeStampsV2 =
    copy(traversalRefStamps = stamp :: traversalRefStamps)

object EnvelopeStampsV2:
  /**
   * Empty stamps container.
   */
  val empty: EnvelopeStampsV2 = EnvelopeStampsV2()

  /**
   * Create from a single traversal stamp.
   */
  def fromTraversalStamp(stamp: TraversalStamp): EnvelopeStampsV2 =
    EnvelopeStampsV2(traversalStamps = List(stamp))

  /**
   * Create from a single traversal ref stamp.
   */
  def fromTraversalRefStamp(stamp: TraversalRefStamp): EnvelopeStampsV2 =
    EnvelopeStampsV2(traversalRefStamps = List(stamp))

  /**
   * Convert from legacy EnvelopeStamps format.
   *
   * This extracts stamps by their runtime type from the heterogeneous
   * map structure and places them into the appropriate typed fields.
   */
  def fromLegacy(stamps: EnvelopeStamps): EnvelopeStampsV2 =
    val traversalStamps = stamps
      .getOrElse(classOf[TraversalStamp], Nil)
      .collect { case s: TraversalStamp => s }

    val traversalRefStamps = stamps
      .getOrElse(classOf[TraversalRefStamp], Nil)
      .collect { case s: TraversalRefStamp => s }

    EnvelopeStampsV2(traversalStamps, traversalRefStamps)
