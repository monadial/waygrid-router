package com.monadial.waygrid.common.application.util.vulcan.codecs

import com.monadial.waygrid.common.application.util.vulcan.VulcanUtils.given
import com.monadial.waygrid.common.domain.model.envelope.Value.{ EnvelopeId, GroupId }
import com.monadial.waygrid.common.domain.model.node.Value.{
  NodeClusterId,
  NodeComponent,
  NodeId as ModelNodeId,
  NodeRegion,
  NodeService
}
import com.monadial.waygrid.common.domain.model.routing.Value.{ RepeatTimes, RepeatUntilDate, TraversalId }
import com.monadial.waygrid.common.domain.model.traversal.dag.Value.{
  BranchId,
  DagHash,
  ForkId,
  NodeId as DagNodeId
}
import com.monadial.waygrid.common.domain.model.traversal.state.Value.{ RemainingNodes, RetryAttempt, StateVersion }
import vulcan.Codec
import wvlet.airframe.ulid.ULID

import java.time.Instant

/**
 * Vulcan Avro codecs for primitive domain value types.
 *
 * This file provides codecs for simple value types that wrap basic primitives:
 * - ULID-based types (NodeId, TraversalId, EnvelopeId, BranchId)
 * - String-based types (NodeComponent, NodeService, DagHash, etc.)
 * - Long/Int-based types (RetryAttempt, RemainingNodes, etc.)
 *
 * Import `DomainPrimitivesVulcanCodecs.given` to bring these codecs into scope.
 */
object DomainPrimitivesVulcanCodecs:

  // ---------------------------------------------------------------------------
  // ULID-based value types
  // ---------------------------------------------------------------------------

  /**
   * NodeId from node.Value (ULID-based unique node identifier).
   */
  given Codec[ModelNodeId] = summon[Codec[ULID]].imap(
    ulid => ModelNodeId(ulid)
  )(
    nodeId => nodeId.unwrap
  )

  /**
   * TraversalId (ULID-based traversal identifier).
   */
  given Codec[TraversalId] = summon[Codec[ULID]].imap(
    ulid => TraversalId(ulid)
  )(
    id => id.unwrap
  )

  /**
   * EnvelopeId (ULID-based envelope identifier).
   */
  given Codec[EnvelopeId] = summon[Codec[ULID]].imap(
    ulid => EnvelopeId(ulid)
  )(
    id => id.unwrap
  )

  /**
   * BranchId (ULID-based branch identifier for fork/join).
   */
  given Codec[BranchId] = summon[Codec[ULID]].imap(
    ulid => BranchId(ulid)
  )(
    id => id.unwrap
  )

  // ---------------------------------------------------------------------------
  // String-based value types
  // ---------------------------------------------------------------------------

  /**
   * NodeId from dag.Value (String-based DAG node identifier).
   * Note: This is distinct from node.Value.NodeId which is ULID-based.
   */
  given Codec[DagNodeId] = Codec.string.imap(
    str => DagNodeId(str)
  )(
    id => id.unwrap
  )

  /**
   * DagHash (content-based hash of DAG structure).
   */
  given Codec[DagHash] = Codec.string.imap(
    str => DagHash(str)
  )(
    hash => hash.unwrap
  )

  /**
   * ForkId (String identifier for fork/join correlation, max 16 chars).
   */
  given Codec[ForkId] = Codec.string.imap(
    str => ForkId.unsafeFrom(str)
  )(
    id => id.unwrap
  )

  /**
   * GroupId (envelope grouping identifier).
   */
  given Codec[GroupId] = Codec.string.imap(
    str => GroupId(str)
  )(
    id => id.unwrap
  )

  /**
   * NodeComponent (component type: origin, processor, destination, system).
   */
  given Codec[NodeComponent] = Codec.string.imap(
    str => NodeComponent(str)
  )(
    comp => comp.unwrap
  )

  /**
   * NodeService (service name within a component).
   */
  given Codec[NodeService] = Codec.string.imap(
    str => NodeService(str)
  )(
    svc => svc.unwrap
  )

  /**
   * NodeClusterId (cluster identifier for node groups).
   */
  given Codec[NodeClusterId] = Codec.string.imap(
    str => NodeClusterId(str)
  )(
    id => id.unwrap
  )

  /**
   * NodeRegion (geographic region, e.g., "us-west-1").
   */
  given Codec[NodeRegion] = Codec.string.imap(
    str => NodeRegion.unsafeFrom(str)
  )(
    region => region.unwrap
  )

  // ---------------------------------------------------------------------------
  // Numeric value types
  // ---------------------------------------------------------------------------

  /**
   * RetryAttempt (retry counter for nodes).
   */
  given Codec[RetryAttempt] = Codec.int.imap(
    n => RetryAttempt(n)
  )(
    attempt => attempt.unwrap
  )

  /**
   * RemainingNodes (count of unprocessed nodes).
   */
  given Codec[RemainingNodes] = Codec.int.imap(
    n => RemainingNodes(n)
  )(
    remaining => remaining.unwrap
  )

  /**
   * StateVersion (optimistic locking version).
   */
  given Codec[StateVersion] = Codec.long.imap(
    n => StateVersion(n)
  )(
    version => version.unwrap
  )

  /**
   * RepeatTimes (repeat count for repeat policy).
   */
  given Codec[RepeatTimes] = Codec.long.imap(
    n => RepeatTimes(n)
  )(
    times => times.unwrap
  )

  /**
   * RepeatUntilDate (deadline for repeat policy).
   */
  given Codec[RepeatUntilDate] = summon[Codec[Instant]].imap(
    instant => RepeatUntilDate(instant)
  )(
    date => date.unwrap
  )
