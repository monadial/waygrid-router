package com.monadial.waygrid.common.application.util.vulcan.codecs

import java.time.Instant

import cats.syntax.all.*
import com.monadial.waygrid.common.application.util.vulcan.VulcanUtils.given
import com.monadial.waygrid.common.application.util.vulcan.codecs.DomainPrimitivesVulcanCodecs.given
import com.monadial.waygrid.common.application.util.vulcan.codecs.DomainRoutingDagVulcanCodecs.given
import com.monadial.waygrid.common.domain.model.traversal.dag.JoinStrategy
import com.monadial.waygrid.common.domain.model.traversal.dag.Value.{ BranchId, ForkId, NodeId }
import com.monadial.waygrid.common.domain.model.traversal.state.{
  BranchResult,
  BranchState,
  BranchStatus,
  ForkScope,
  PendingJoin
}
import io.circe.Json
import vulcan.Codec

/**
 * Vulcan Avro codecs for fork/join state types.
 *
 * Handles:
 * - BranchStatus (enum: Pending, Running, Completed, Failed, Canceled, TimedOut)
 * - BranchResult (enum: Success, Failure, Timeout)
 * - BranchState (branch state within a fork)
 * - ForkScope (active fork tracking)
 * - PendingJoin (join waiting for branches)
 *
 * Import `DomainForkJoinVulcanCodecs.given` to bring these codecs into scope.
 */
object DomainForkJoinVulcanCodecs:

  // ---------------------------------------------------------------------------
  // BranchStatus (enum)
  // ---------------------------------------------------------------------------

  /**
   * BranchStatus encoded as Avro enum.
   */
  given Codec[BranchStatus] =
    Codec.enumeration[BranchStatus](
      name = "BranchStatus",
      namespace = "com.monadial.waygrid.common.domain.model.traversal.state",
      symbols = List("Pending", "Running", "Completed", "Failed", "Canceled", "TimedOut"),
      encode = _.toString,
      decode = {
        case "Pending"   => Right(BranchStatus.Pending)
        case "Running"   => Right(BranchStatus.Running)
        case "Completed" => Right(BranchStatus.Completed)
        case "Failed"    => Right(BranchStatus.Failed)
        case "Canceled"  => Right(BranchStatus.Canceled)
        case "TimedOut"  => Right(BranchStatus.TimedOut)
        case other       => Left(vulcan.AvroError(s"Unknown BranchStatus: $other"))
      }
    )

  // ---------------------------------------------------------------------------
  // BranchResult (sealed trait with 3 variants)
  // ---------------------------------------------------------------------------

  /**
   * Json codec using string representation.
   * Circe Json is serialized as its string form.
   */
  private given Codec[Json] = Codec.string.imapError(str =>
    io.circe.parser
      .parse(str)
      .left
      .map(e => vulcan.AvroError(s"Invalid JSON: ${e.getMessage}"))
  )(_.noSpaces)

  /**
   * BranchResult.Success - branch completed successfully.
   */
  private given Codec[BranchResult.Success] = Codec.record(
    name = "BranchResultSuccess",
    namespace = "com.monadial.waygrid.common.domain.model.traversal.state"
  ) { field =>
    field("output", _.output).map(BranchResult.Success.apply)
  }

  /**
   * BranchResult.Failure - branch failed with reason.
   */
  private given Codec[BranchResult.Failure] = Codec.record(
    name = "BranchResultFailure",
    namespace = "com.monadial.waygrid.common.domain.model.traversal.state"
  ) { field =>
    field("reason", _.reason).map(BranchResult.Failure.apply)
  }

  /**
   * BranchResult.Timeout - branch timed out.
   */
  private given Codec[BranchResult.Timeout.type] = Codec.record(
    name = "BranchResultTimeout",
    namespace = "com.monadial.waygrid.common.domain.model.traversal.state"
  ) { _ =>
    BranchResult.Timeout.pure
  }

  /**
   * BranchResult sealed trait as Avro union.
   * Uses explicit encoding with pattern matching for Scala 3 compatibility.
   */
  given Codec[BranchResult] =
    import scala.annotation.nowarn

    val successCodec = summon[Codec[BranchResult.Success]]
    val failureCodec = summon[Codec[BranchResult.Failure]]
    val timeoutCodec = summon[Codec[BranchResult.Timeout.type]]

    val unionCodec = Codec.union[BranchResult] { alt =>
      alt[BranchResult.Success] |+|
        alt[BranchResult.Failure] |+|
        alt[BranchResult.Timeout.type]
    }

    @nowarn("msg=deprecated")
    val result = Codec.instance(
      unionCodec.schema,
      (br: BranchResult) =>
        br match
          case v: BranchResult.Success => successCodec.encode(v)
          case v: BranchResult.Failure => failureCodec.encode(v)
          case BranchResult.Timeout    => timeoutCodec.encode(BranchResult.Timeout),
      unionCodec.decode
    )
    result

  // ---------------------------------------------------------------------------
  // Set helpers
  // ---------------------------------------------------------------------------

  /**
   * Set[BranchId] codec as list.
   */
  given branchIdSetCodec: Codec[Set[BranchId]] = Codec.list[BranchId].imap(_.toSet)(_.toList)

  /**
   * Set[NodeId] codec as list.
   */
  given nodeIdSetCodec: Codec[Set[NodeId]] = Codec.list[NodeId].imap(_.toSet)(_.toList)

  /**
   * Vector[NodeId] codec as list.
   */
  given nodeIdVectorCodec: Codec[Vector[NodeId]] = Codec.list[NodeId].imap(_.toVector)(_.toList)

  // ---------------------------------------------------------------------------
  // BranchState
  // ---------------------------------------------------------------------------

  /**
   * BranchState encoded as Avro record.
   */
  given Codec[BranchState] = Codec.record(
    name = "BranchState",
    namespace = "com.monadial.waygrid.common.domain.model.traversal.state"
  ) { field =>
    (
      field("branchId", _.branchId),
      field("forkId", _.forkId),
      field("entryNode", _.entryNode),
      field("currentNode", _.currentNode),
      field("status", _.status),
      field("result", _.result),
      field("history", _.history)
    ).mapN(BranchState.apply)
  }

  // ---------------------------------------------------------------------------
  // ForkScope
  // ---------------------------------------------------------------------------

  /**
   * ForkScope encoded as Avro record.
   */
  given Codec[ForkScope] = Codec.record(
    name = "ForkScope",
    namespace = "com.monadial.waygrid.common.domain.model.traversal.state"
  ) { field =>
    (
      field("forkId", _.forkId),
      field("forkNodeId", _.forkNodeId),
      field("branches", _.branches),
      field("parentScope", _.parentScope),
      field("parentBranchId", _.parentBranchId),
      field("startedAt", _.startedAt),
      field("timeout", _.timeout)
    ).mapN(ForkScope.apply)
  }

  // ---------------------------------------------------------------------------
  // PendingJoin
  // ---------------------------------------------------------------------------

  /**
   * PendingJoin encoded as Avro record.
   */
  given Codec[PendingJoin] = Codec.record(
    name = "PendingJoin",
    namespace = "com.monadial.waygrid.common.domain.model.traversal.state"
  ) { field =>
    (
      field("joinNodeId", _.joinNodeId),
      field("forkId", _.forkId),
      field("strategy", _.strategy),
      field("requiredBranches", _.requiredBranches),
      field("completedBranches", _.completedBranches),
      field("failedBranches", _.failedBranches),
      field("canceledBranches", _.canceledBranches),
      field("timeout", _.timeout)
    ).mapN(PendingJoin.apply)
  }

  // ---------------------------------------------------------------------------
  // Map helpers for TraversalState
  // ---------------------------------------------------------------------------

  /**
   * Map[ForkId, ForkScope] codec as list.
   */
  given forkScopeMapCodec: Codec[Map[ForkId, ForkScope]] =
    Codec.list[ForkScope].imap(scopes => scopes.map(s => s.forkId -> s).toMap)(_.values.toList)

  /**
   * Map[BranchId, BranchState] codec as list.
   */
  given branchStateMapCodec: Codec[Map[BranchId, BranchState]] =
    Codec.list[BranchState].imap(states => states.map(s => s.branchId -> s).toMap)(_.values.toList)

  /**
   * Map[NodeId, PendingJoin] codec as list.
   */
  given pendingJoinMapCodec: Codec[Map[NodeId, PendingJoin]] =
    Codec.list[PendingJoin].imap(joins => joins.map(j => j.joinNodeId -> j).toMap)(_.values.toList)

  /**
   * Map[NodeId, BranchId] codec as list of entries.
   */
  private case class NodeToBranchEntry(nodeId: NodeId, branchId: BranchId)
  private given Codec[NodeToBranchEntry] = Codec.record(
    name = "NodeToBranchEntry",
    namespace = "com.monadial.waygrid.common.domain.model.traversal.state"
  ) { field =>
    (
      field("nodeId", _.nodeId),
      field("branchId", _.branchId)
    ).mapN(NodeToBranchEntry.apply)
  }

  given nodeToBranchMapCodec: Codec[Map[NodeId, BranchId]] =
    Codec.list[NodeToBranchEntry].imap(entries => entries.map(e => e.nodeId -> e.branchId).toMap)(_.toList.map {
      case (k, v) => NodeToBranchEntry(k, v)
    })
