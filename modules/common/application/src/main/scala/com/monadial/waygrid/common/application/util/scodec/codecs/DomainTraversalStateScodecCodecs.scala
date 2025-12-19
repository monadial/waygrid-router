package com.monadial.waygrid.common.application.util.scodec.codecs

import com.monadial.waygrid.common.application.util.scodec.ScodecUtils.*
import com.monadial.waygrid.common.application.util.scodec.codecs.DomainRoutingDagScodecCodecs.given
import com.monadial.waygrid.common.application.util.scodec.codecs.DomainVectorClockScodecCodecs.given
import com.monadial.waygrid.common.domain.model.routing.Value.TraversalId
import com.monadial.waygrid.common.domain.model.traversal.dag.JoinStrategy
import com.monadial.waygrid.common.domain.model.traversal.dag.Value.{ BranchId, ForkId, NodeId }
import com.monadial.waygrid.common.domain.model.traversal.state.*
import com.monadial.waygrid.common.domain.model.traversal.state.Event.*
import com.monadial.waygrid.common.domain.model.traversal.state.Value.{ RemainingNodes, RetryAttempt, StateVersion }
import com.monadial.waygrid.common.domain.model.vectorclock.VectorClock
import com.monadial.waygrid.common.domain.value.Address.NodeAddress
import io.circe.Json
import io.circe.parser.parse
import org.http4s.Uri
import scodec.*
import scodec.bits.*
import scodec.codecs.*
import wvlet.airframe.ulid.ULID

import java.time.Instant

/**
 * Scodec binary codecs for TraversalState and related types.
 *
 * Uses ScodecUtils helpers for collections and enums.
 */
object DomainTraversalStateScodecCodecs:

  // ---------------------------------------------------------------------------
  // Primitive value type codecs
  // ---------------------------------------------------------------------------

  given Codec[TraversalId] =
    bytes(16).xmap(b => TraversalId(ULID.fromBytes(b.toArray)), id => ByteVector(id.unwrap.toBytes))

  given Codec[RemainingNodes] = int32.xmap(RemainingNodes(_), _.unwrap)
  given Codec[RetryAttempt]   = int32.xmap(RetryAttempt(_), _.unwrap)
  given Codec[StateVersion]   = int64.xmap(StateVersion(_), _.unwrap)
  given Codec[NodeAddress] =
    variableSizeBytes(int32, utf8).xmap(s => NodeAddress(Uri.unsafeFromString(s)), _.unwrap.renderString)

  private given jsonCodec: Codec[Json] =
    variableSizeBytes(int32, utf8).xmap(s => parse(s).getOrElse(Json.Null), _.noSpaces)
  private given instantCodec: Codec[Instant] = int64.xmap(Instant.ofEpochMilli, _.toEpochMilli)
  private given stringCodec: Codec[String]   = variableSizeBytes(int32, utf8)

  // ---------------------------------------------------------------------------
  // Simple enum codecs (using enumCodec helper - much more concise!)
  // ---------------------------------------------------------------------------

  given Codec[BranchStatus] = enumCodec[BranchStatus](
    BranchStatus.Pending   -> 0,
    BranchStatus.Running   -> 1,
    BranchStatus.Completed -> 2,
    BranchStatus.Failed    -> 3,
    BranchStatus.Canceled  -> 4,
    BranchStatus.TimedOut  -> 5
  )

  // ---------------------------------------------------------------------------
  // BranchResult codec (ADT with payloads - explicit is clearer here)
  // ---------------------------------------------------------------------------

  given Codec[BranchResult] = Codec[BranchResult](
    {
      case BranchResult.Success(output) =>
        uint8.encode(0).flatMap(d => optional(bool, jsonCodec).encode(output).map(d ++ _))
      case BranchResult.Failure(reason) => uint8.encode(1).flatMap(d => stringCodec.encode(reason).map(d ++ _))
      case BranchResult.Timeout         => uint8.encode(2)
    },
    bits =>
      uint8.decode(bits).flatMap { r =>
        r.value match
          case 0 => optional(bool, jsonCodec).decode(r.remainder).map(_.map(BranchResult.Success(_)))
          case 1 => stringCodec.decode(r.remainder).map(_.map(BranchResult.Failure(_)))
          case 2 => Attempt.successful(DecodeResult(BranchResult.Timeout, r.remainder))
          case n => Attempt.failure(Err(s"Unknown BranchResult: $n"))
      }
  )

  // ---------------------------------------------------------------------------
  // Collection codecs (using helpers - MUCH more concise than manual!)
  // ---------------------------------------------------------------------------

  given setNodeIdCodec: Codec[Set[NodeId]]     = setCodec[NodeId]
  given setBranchIdCodec: Codec[Set[BranchId]] = setCodec[BranchId]

  given mapNodeIdRetryAttemptCodec: Codec[Map[NodeId, RetryAttempt]] = mapCodec[NodeId, RetryAttempt]
  given mapNodeIdBranchIdCodec: Codec[Map[NodeId, BranchId]]         = mapCodec[NodeId, BranchId]

  private given vectorNodeIdCodec: Codec[Vector[NodeId]] = vectorCodec[NodeId]
  // Note: vectorStateEventCodec moved after stateEventCodec to avoid forward reference

  // ---------------------------------------------------------------------------
  // Composite type codecs (using xmap - more verbose but reliable)
  // ---------------------------------------------------------------------------

  given forkScopeCodec: Codec[ForkScope] =
    (summon[Codec[ForkId]] :: summon[Codec[NodeId]] :: setBranchIdCodec ::
      optional(bool, summon[Codec[ForkId]]) :: optional(bool, summon[Codec[BranchId]]) ::
      instantCodec :: optional(bool, instantCodec)).xmap(
      { case (forkId, forkNodeId, branches, parentScope, parentBranchId, startedAt, timeout) =>
        ForkScope(forkId, forkNodeId, branches, parentScope, parentBranchId, startedAt, timeout)
      },
      fs => (fs.forkId, fs.forkNodeId, fs.branches, fs.parentScope, fs.parentBranchId, fs.startedAt, fs.timeout)
    )

  given mapForkIdForkScopeCodec: Codec[Map[ForkId, ForkScope]] = mapCodec[ForkId, ForkScope]

  given branchStateCodec: Codec[BranchState] =
    (summon[Codec[BranchId]] :: summon[Codec[ForkId]] :: summon[Codec[NodeId]] ::
      optional(bool, summon[Codec[NodeId]]) :: summon[Codec[BranchStatus]] ::
      optional(bool, summon[Codec[BranchResult]]) :: vectorNodeIdCodec).xmap(
      { case (branchId, forkId, entryNode, currentNode, status, result, history) =>
        BranchState(branchId, forkId, entryNode, currentNode, status, result, history)
      },
      bs => (bs.branchId, bs.forkId, bs.entryNode, bs.currentNode, bs.status, bs.result, bs.history)
    )

  given mapBranchIdBranchStateCodec: Codec[Map[BranchId, BranchState]] = mapCodec[BranchId, BranchState]

  given pendingJoinCodec: Codec[PendingJoin] =
    (summon[Codec[NodeId]] :: summon[Codec[ForkId]] :: summon[Codec[JoinStrategy]] ::
      setBranchIdCodec :: setBranchIdCodec :: setBranchIdCodec :: setBranchIdCodec ::
      optional(bool, instantCodec)).xmap(
      { case (joinNodeId, forkId, strategy, required, completed, failed, canceled, timeout) =>
        PendingJoin(joinNodeId, forkId, strategy, required, completed, failed, canceled, timeout)
      },
      pj =>
        (
          pj.joinNodeId,
          pj.forkId,
          pj.strategy,
          pj.requiredBranches,
          pj.completedBranches,
          pj.failedBranches,
          pj.canceledBranches,
          pj.timeout
        )
    )

  given mapNodeIdPendingJoinCodec: Codec[Map[NodeId, PendingJoin]] = mapCodec[NodeId, PendingJoin]

  // ---------------------------------------------------------------------------
  // StateEvent codec (discriminated works well for case classes)
  // ---------------------------------------------------------------------------

  private val traversalStartedCodec: Codec[TraversalStarted] =
    (summon[Codec[NodeId]] :: summon[Codec[NodeAddress]] :: summon[Codec[VectorClock]]).xmap(
      { case (node, actor, vc) => TraversalStarted(node, actor, vc) },
      e => (e.node, e.actor, e.vectorClock)
    )

  private val traversalScheduledCodec: Codec[TraversalScheduled] =
    (summon[Codec[NodeId]] :: summon[Codec[NodeAddress]] :: instantCodec :: summon[Codec[VectorClock]]).xmap(
      { case (node, actor, scheduledAt, vc) => TraversalScheduled(node, actor, scheduledAt, vc) },
      e => (e.node, e.actor, e.scheduledAt, e.vectorClock)
    )

  private val traversalResumedCodec: Codec[TraversalResumed] =
    (summon[Codec[NodeId]] :: summon[Codec[NodeAddress]] :: summon[Codec[VectorClock]]).xmap(
      { case (node, actor, vc) => TraversalResumed(node, actor, vc) },
      e => (e.node, e.actor, e.vectorClock)
    )

  private val traversalCompletedCodec: Codec[TraversalCompleted] =
    (summon[Codec[NodeId]] :: summon[Codec[NodeAddress]] :: summon[Codec[VectorClock]]).xmap(
      { case (node, actor, vc) => TraversalCompleted(node, actor, vc) },
      e => (e.node, e.actor, e.vectorClock)
    )

  private val traversalFailedCodec: Codec[TraversalFailed] =
    (summon[Codec[NodeId]] :: summon[Codec[NodeAddress]] :: summon[Codec[VectorClock]] :: optional(
      bool,
      stringCodec
    )).xmap(
      { case (node, actor, vc, reason) => TraversalFailed(node, actor, vc, reason) },
      e => (e.node, e.actor, e.vectorClock, e.reason)
    )

  private val traversalCanceledCodec: Codec[TraversalCanceled] =
    (summon[Codec[NodeId]] :: summon[Codec[NodeAddress]] :: summon[Codec[VectorClock]]).xmap(
      { case (node, actor, vc) => TraversalCanceled(node, actor, vc) },
      e => (e.node, e.actor, e.vectorClock)
    )

  private val nodeTraversalRetriedCodec: Codec[NodeTraversalRetried] =
    (summon[
      Codec[NodeId]
    ] :: summon[Codec[NodeAddress]] :: summon[Codec[RetryAttempt]] :: summon[Codec[VectorClock]]).xmap(
      { case (node, actor, attempt, vc) => NodeTraversalRetried(node, actor, attempt, vc) },
      e => (e.node, e.actor, e.attempt, e.vectorClock)
    )

  private val nodeTraversalSucceededCodec: Codec[NodeTraversalSucceeded] =
    (summon[Codec[NodeId]] :: summon[Codec[NodeAddress]] :: summon[Codec[VectorClock]]).xmap(
      { case (node, actor, vc) => NodeTraversalSucceeded(node, actor, vc) },
      e => (e.node, e.actor, e.vectorClock)
    )

  private val nodeTraversalFailedCodec: Codec[NodeTraversalFailed] =
    (summon[Codec[NodeId]] :: summon[Codec[NodeAddress]] :: summon[Codec[VectorClock]] :: optional(
      bool,
      stringCodec
    )).xmap(
      { case (node, actor, vc, reason) => NodeTraversalFailed(node, actor, vc, reason) },
      e => (e.node, e.actor, e.vectorClock, e.reason)
    )

  private val forkStartedCodec: Codec[ForkStarted] =
    (summon[
      Codec[NodeId]
    ] :: summon[Codec[ForkId]] :: setBranchIdCodec :: summon[Codec[NodeAddress]] :: summon[Codec[VectorClock]]).xmap(
      { case (node, forkId, branches, actor, vc) => ForkStarted(node, forkId, branches, actor, vc) },
      e => (e.node, e.forkId, e.branches, e.actor, e.vectorClock)
    )

  private val branchStartedCodec: Codec[BranchStarted] =
    (summon[Codec[NodeId]] :: summon[
      Codec[BranchId]
    ] :: summon[Codec[ForkId]] :: summon[Codec[NodeAddress]] :: summon[Codec[VectorClock]]).xmap(
      { case (node, branchId, forkId, actor, vc) => BranchStarted(node, branchId, forkId, actor, vc) },
      e => (e.node, e.branchId, e.forkId, e.actor, e.vectorClock)
    )

  private val branchAdvancedCodec: Codec[BranchAdvanced] =
    (summon[Codec[NodeId]] :: summon[
      Codec[BranchId]
    ] :: summon[Codec[ForkId]] :: summon[Codec[NodeAddress]] :: summon[Codec[VectorClock]]).xmap(
      { case (node, branchId, forkId, actor, vc) => BranchAdvanced(node, branchId, forkId, actor, vc) },
      e => (e.node, e.branchId, e.forkId, e.actor, e.vectorClock)
    )

  private val branchCompletedCodec: Codec[BranchCompleted] =
    (summon[Codec[NodeId]] :: summon[Codec[BranchId]] :: summon[
      Codec[ForkId]
    ] :: summon[Codec[BranchResult]] :: summon[Codec[NodeAddress]] :: summon[Codec[VectorClock]]).xmap(
      { case (node, branchId, forkId, result, actor, vc) =>
        BranchCompleted(node, branchId, forkId, result, actor, vc)
      },
      e => (e.node, e.branchId, e.forkId, e.result, e.actor, e.vectorClock)
    )

  private val branchCanceledCodec: Codec[BranchCanceled] =
    (summon[Codec[NodeId]] :: summon[
      Codec[BranchId]
    ] :: summon[Codec[ForkId]] :: stringCodec :: summon[Codec[NodeAddress]] :: summon[Codec[VectorClock]]).xmap(
      { case (node, branchId, forkId, reason, actor, vc) => BranchCanceled(node, branchId, forkId, reason, actor, vc) },
      e => (e.node, e.branchId, e.forkId, e.reason, e.actor, e.vectorClock)
    )

  private val branchTimedOutCodec: Codec[BranchTimedOut] =
    (summon[Codec[NodeId]] :: summon[
      Codec[BranchId]
    ] :: summon[Codec[ForkId]] :: summon[Codec[NodeAddress]] :: summon[Codec[VectorClock]]).xmap(
      { case (node, branchId, forkId, actor, vc) => BranchTimedOut(node, branchId, forkId, actor, vc) },
      e => (e.node, e.branchId, e.forkId, e.actor, e.vectorClock)
    )

  private val joinReachedCodec: Codec[JoinReached] =
    (summon[Codec[NodeId]] :: summon[
      Codec[BranchId]
    ] :: summon[Codec[ForkId]] :: summon[Codec[NodeAddress]] :: summon[Codec[VectorClock]]).xmap(
      { case (node, branchId, forkId, actor, vc) => JoinReached(node, branchId, forkId, actor, vc) },
      e => (e.node, e.branchId, e.forkId, e.actor, e.vectorClock)
    )

  private val joinCompletedCodec: Codec[JoinCompleted] =
    (summon[
      Codec[NodeId]
    ] :: summon[Codec[ForkId]] :: setBranchIdCodec :: summon[Codec[NodeAddress]] :: summon[Codec[VectorClock]]).xmap(
      { case (node, forkId, completed, actor, vc) => JoinCompleted(node, forkId, completed, actor, vc) },
      e => (e.node, e.forkId, e.completedBranches, e.actor, e.vectorClock)
    )

  private val joinTimedOutCodec: Codec[JoinTimedOut] =
    (summon[
      Codec[NodeId]
    ] :: summon[Codec[ForkId]] :: setBranchIdCodec :: summon[Codec[NodeAddress]] :: summon[Codec[VectorClock]]).xmap(
      { case (node, forkId, pending, actor, vc) => JoinTimedOut(node, forkId, pending, actor, vc) },
      e => (e.node, e.forkId, e.pendingBranches, e.actor, e.vectorClock)
    )

  private val traversalTimeoutScheduledCodec: Codec[TraversalTimeoutScheduled] =
    (summon[
      Codec[NodeId]
    ] :: stringCodec :: instantCodec :: summon[Codec[NodeAddress]] :: summon[Codec[VectorClock]]).xmap(
      { case (node, timeoutId, deadline, actor, vc) =>
        TraversalTimeoutScheduled(node, timeoutId, deadline, actor, vc)
      },
      e => (e.node, e.timeoutId, e.deadline, e.actor, e.vectorClock)
    )

  private val traversalTimedOutCodec: Codec[TraversalTimedOut] =
    (summon[
      Codec[NodeId]
    ] :: setNodeIdCodec :: setBranchIdCodec :: summon[Codec[NodeAddress]] :: summon[Codec[VectorClock]]).xmap(
      { case (node, activeNodes, activeBranches, actor, vc) =>
        TraversalTimedOut(node, activeNodes, activeBranches, actor, vc)
      },
      e => (e.node, e.activeNodes, e.activeBranches, e.actor, e.vectorClock)
    )

  given stateEventCodec: Codec[StateEvent] = discriminated[StateEvent]
    .by(uint8)
    .typecase(0, traversalStartedCodec)
    .typecase(1, traversalScheduledCodec)
    .typecase(2, traversalResumedCodec)
    .typecase(3, traversalCompletedCodec)
    .typecase(4, traversalFailedCodec)
    .typecase(5, traversalCanceledCodec)
    .typecase(6, nodeTraversalRetriedCodec)
    .typecase(7, nodeTraversalSucceededCodec)
    .typecase(8, nodeTraversalFailedCodec)
    .typecase(9, forkStartedCodec)
    .typecase(10, branchStartedCodec)
    .typecase(11, branchAdvancedCodec)
    .typecase(12, branchCompletedCodec)
    .typecase(13, branchCanceledCodec)
    .typecase(14, branchTimedOutCodec)
    .typecase(15, joinReachedCodec)
    .typecase(16, joinCompletedCodec)
    .typecase(17, joinTimedOutCodec)
    .typecase(18, traversalTimeoutScheduledCodec)
    .typecase(19, traversalTimedOutCodec)

  // Vector of StateEvents (defined after stateEventCodec to avoid forward reference)
  given vectorStateEventCodec: Codec[Vector[StateEvent]] = vectorCodec[StateEvent]

  // ---------------------------------------------------------------------------
  // TraversalState codec
  // ---------------------------------------------------------------------------

  given traversalStateCodec: Codec[TraversalState] =
    (summon[Codec[TraversalId]] ::
      setNodeIdCodec :: setNodeIdCodec :: setNodeIdCodec ::
      mapNodeIdRetryAttemptCodec ::
      summon[Codec[VectorClock]] ::
      vectorStateEventCodec ::
      summon[Codec[RemainingNodes]] ::
      mapForkIdForkScopeCodec ::
      mapBranchIdBranchStateCodec ::
      mapNodeIdPendingJoinCodec ::
      mapNodeIdBranchIdCodec ::
      optional(bool, stringCodec) ::
      summon[Codec[StateVersion]]).xmap(
      {
        case (
              traversalId,
              active,
              completed,
              failed,
              retries,
              vectorClock,
              history,
              remainingNodes,
              forkScopes,
              branchStates,
              pendingJoins,
              nodeToBranch,
              traversalTimeoutId,
              stateVersion
            ) =>
          TraversalState(
            traversalId,
            active,
            completed,
            failed,
            retries,
            vectorClock,
            history,
            remainingNodes,
            forkScopes,
            branchStates,
            pendingJoins,
            nodeToBranch,
            traversalTimeoutId,
            stateVersion
          )
      },
      ts =>
        (
          ts.traversalId,
          ts.active,
          ts.completed,
          ts.failed,
          ts.retries,
          ts.vectorClock,
          ts.history,
          ts.remainingNodes,
          ts.forkScopes,
          ts.branchStates,
          ts.pendingJoins,
          ts.nodeToBranch,
          ts.traversalTimeoutId,
          ts.stateVersion
        )
    )
