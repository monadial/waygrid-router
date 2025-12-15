package com.monadial.waygrid.common.domain.model.traversal.fsm

import cats.effect.IO
import cats.data.NonEmptyList
import com.monadial.waygrid.common.domain.model.resiliency.RetryPolicy
import com.monadial.waygrid.common.domain.model.routing.Value.{RepeatPolicy, TraversalId, DeliveryStrategy}
import com.monadial.waygrid.common.domain.model.traversal.condition.Condition
import com.monadial.waygrid.common.domain.model.traversal.dag.{Dag, Edge, JoinStrategy, Node, NodeType}
import com.monadial.waygrid.common.domain.model.traversal.dag.Value.{DagHash, EdgeGuard, ForkId, NodeId}
import com.monadial.waygrid.common.domain.model.traversal.state.{Event, TraversalState}
import com.monadial.waygrid.common.domain.value.Address.{NodeAddress, ServiceAddress}
import io.circe.Json
import org.http4s.Uri
import weaver.SimpleIOSuite

import scala.concurrent.duration.*

object TraversalFSMSuite extends SimpleIOSuite:

  // ---------------------------------------------------------------------------
  // Test Fixtures
  // ---------------------------------------------------------------------------

  private val nodeA = NodeId("A")
  private val nodeB = NodeId("B")
  private val nodeC = NodeId("C")
  private val nodeD = NodeId("D")

  private val svcA = ServiceAddress(Uri.unsafeFromString("waygrid://destination/webhook"))
  private val svcB = ServiceAddress(Uri.unsafeFromString("waygrid://processor/openai"))
  private val svcC = ServiceAddress(Uri.unsafeFromString("waygrid://destination/websocket"))
  private val svcD = ServiceAddress(Uri.unsafeFromString("waygrid://destination/blackhole"))

  private val waystation = NodeAddress(Uri.unsafeFromString("waygrid://system/waystation?nodeId=test-1"))

  /** Simple linear DAG: A -> B -> C */
  private val linearDag = Dag(
    hash = DagHash("linear-dag"),
    entryPoints = NonEmptyList.one(nodeA),
    repeatPolicy = RepeatPolicy.NoRepeat,
    nodes = Map(
      nodeA -> Node(nodeA, Some("Entry"), RetryPolicy.None, DeliveryStrategy.Immediate, svcA),
      nodeB -> Node(nodeB, Some("Middle"), RetryPolicy.None, DeliveryStrategy.Immediate, svcB),
      nodeC -> Node(nodeC, Some("Exit"), RetryPolicy.None, DeliveryStrategy.Immediate, svcC)
    ),
    edges = List(
      Edge(nodeA, nodeB, EdgeGuard.OnSuccess),
      Edge(nodeB, nodeC, EdgeGuard.OnSuccess)
    )
  )

  /** DAG with retry policy: A (with retries) -> B */
  private val retryDag = Dag(
    hash = DagHash("retry-dag"),
    entryPoints = NonEmptyList.one(nodeA),
    repeatPolicy = RepeatPolicy.NoRepeat,
    nodes = Map(
      nodeA -> Node(nodeA, Some("Retryable"), RetryPolicy.Linear(1.second, maxRetries = 2), DeliveryStrategy.Immediate, svcA),
      nodeB -> Node(nodeB, Some("Success"), RetryPolicy.None, DeliveryStrategy.Immediate, svcB)
    ),
    edges = List(
      Edge(nodeA, nodeB, EdgeGuard.OnSuccess)
    )
  )

  /** DAG with failure edge: A --(success)--> B, A --(failure)--> D */
  private val failureDag = Dag(
    hash = DagHash("failure-dag"),
    entryPoints = NonEmptyList.one(nodeA),
    repeatPolicy = RepeatPolicy.NoRepeat,
    nodes = Map(
      nodeA -> Node(nodeA, Some("Entry"), RetryPolicy.None, DeliveryStrategy.Immediate, svcA),
      nodeB -> Node(nodeB, Some("Success Path"), RetryPolicy.None, DeliveryStrategy.Immediate, svcB),
      nodeD -> Node(nodeD, Some("Failure Path"), RetryPolicy.None, DeliveryStrategy.Immediate, svcD)
    ),
    edges = List(
      Edge(nodeA, nodeB, EdgeGuard.OnSuccess),
      Edge(nodeA, nodeD, EdgeGuard.OnFailure)
    )
  )

  /** DAG with scheduled delivery */
  private val scheduledDag = Dag(
    hash = DagHash("scheduled-dag"),
    entryPoints = NonEmptyList.one(nodeA),
    repeatPolicy = RepeatPolicy.NoRepeat,
    nodes = Map(
      nodeA -> Node(nodeA, Some("Scheduled Entry"), RetryPolicy.None, DeliveryStrategy.ScheduleAfter(5.minutes), svcA),
      nodeB -> Node(nodeB, Some("After Scheduled"), RetryPolicy.None, DeliveryStrategy.Immediate, svcB)
    ),
    edges = List(
      Edge(nodeA, nodeB, EdgeGuard.OnSuccess)
    )
  )

  /** Empty DAG for error testing */
  private val emptyDag = Dag(
    hash = DagHash("empty-dag"),
    entryPoints = NonEmptyList.one(nodeA),
    repeatPolicy = RepeatPolicy.NoRepeat,
    nodes = Map.empty,
    edges = List.empty
  )

  /** Single node DAG */
  private val singleNodeDag = Dag(
    hash = DagHash("single-node-dag"),
    entryPoints = NonEmptyList.one(nodeA),
    repeatPolicy = RepeatPolicy.NoRepeat,
    nodes = Map(
      nodeA -> Node(nodeA, Some("Only Node"), RetryPolicy.None, DeliveryStrategy.Immediate, svcA)
    ),
    edges = List.empty
  )

  private val forkId: ForkId =
    ForkId.fromStringUnsafe[cats.Id]("01ARZ3NDEKTSV4RRFFQ69G5FAV")

  private val forkNode = NodeId("F")
  private val joinNode = NodeId("J")

  /** Conditional DAG: A -> B(router) -> (if /approved==true) C else D */
  private val conditionalDag = Dag(
    hash = DagHash("conditional-dag"),
    entryPoints = NonEmptyList.one(nodeA),
    repeatPolicy = RepeatPolicy.NoRepeat,
    nodes = Map(
      nodeA -> Node(nodeA, Some("Entry"), RetryPolicy.None, DeliveryStrategy.Immediate, svcA),
      nodeB -> Node(nodeB, Some("Router"), RetryPolicy.None, DeliveryStrategy.Immediate, svcB),
      nodeC -> Node(nodeC, Some("Approved"), RetryPolicy.None, DeliveryStrategy.Immediate, svcC),
      nodeD -> Node(nodeD, Some("Default"), RetryPolicy.None, DeliveryStrategy.Immediate, svcD)
    ),
    edges = List(
      Edge(nodeA, nodeB, EdgeGuard.OnSuccess),
      Edge(nodeB, nodeD, EdgeGuard.OnSuccess),
      Edge(nodeB, nodeC, EdgeGuard.Conditional(Condition.JsonEquals("/approved", Json.fromBoolean(true))))
    )
  )

  /** Fork/Join DAG: A -> F(fork) -> {B,C} -> J(join AND) -> D */
  private val forkJoinDag = Dag(
    hash = DagHash("fork-join-dag"),
    entryPoints = NonEmptyList.one(nodeA),
    repeatPolicy = RepeatPolicy.NoRepeat,
    nodes = Map(
      nodeA -> Node(nodeA, Some("Start"), RetryPolicy.None, DeliveryStrategy.Immediate, svcA),
      nodeB -> Node(nodeB, Some("B"), RetryPolicy.None, DeliveryStrategy.Immediate, svcB),
      nodeC -> Node(nodeC, Some("C"), RetryPolicy.None, DeliveryStrategy.Immediate, svcC),
      nodeD -> Node(nodeD, Some("After Join"), RetryPolicy.None, DeliveryStrategy.Immediate, svcD),
      forkNode -> Node(forkNode, Some("Fork"), RetryPolicy.None, DeliveryStrategy.Immediate, svcA, NodeType.Fork(forkId)),
      joinNode -> Node(joinNode, Some("Join"), RetryPolicy.None, DeliveryStrategy.Immediate, svcA, NodeType.Join(forkId, JoinStrategy.And, None))
    ),
    edges = List(
      Edge(nodeA, forkNode, EdgeGuard.OnSuccess),
      Edge(forkNode, nodeB, EdgeGuard.OnSuccess),
      Edge(forkNode, nodeC, EdgeGuard.OnSuccess),
      Edge(nodeB, joinNode, EdgeGuard.OnSuccess),
      Edge(nodeC, joinNode, EdgeGuard.OnSuccess),
      Edge(joinNode, nodeD, EdgeGuard.OnSuccess)
    )
  )

  /** Fork/Join OR DAG: A -> F(fork) -> {B,C} -> J(join OR) -> D */
  private val forkJoinOrDag =
    forkJoinDag.copy(
      hash = DagHash("fork-join-or-dag"),
      nodes = forkJoinDag.nodes.updated(joinNode, forkJoinDag.nodes(joinNode).copy(nodeType = NodeType.Join(forkId, JoinStrategy.Or, None)))
    )

  /** Fork/Join AND timeout DAG: join has OnTimeout edge to D */
  private val forkJoinTimeoutDag =
    forkJoinDag.copy(
      hash = DagHash("fork-join-timeout-dag"),
      nodes = forkJoinDag.nodes.updated(joinNode, forkJoinDag.nodes(joinNode).copy(nodeType = NodeType.Join(forkId, JoinStrategy.And, Some(1.second)))),
      edges = forkJoinDag.edges :+ Edge(joinNode, nodeD, EdgeGuard.OnTimeout)
    )

  /** Linear DAG where next node is scheduled after delay: A -> B(scheduled) */
  private val scheduleNextDag = Dag(
    hash = DagHash("schedule-next-dag"),
    entryPoints = NonEmptyList.one(nodeA),
    repeatPolicy = RepeatPolicy.NoRepeat,
    nodes = Map(
      nodeA -> Node(nodeA, Some("A"), RetryPolicy.None, DeliveryStrategy.Immediate, svcA),
      nodeB -> Node(nodeB, Some("B"), RetryPolicy.None, DeliveryStrategy.ScheduleAfter(10.minutes), svcB)
    ),
    edges = List(
      Edge(nodeA, nodeB, EdgeGuard.OnSuccess)
    )
  )

  /** Multi-origin DAG: A and B are both valid entry points */
  private val multiEntryDag = Dag(
    hash = DagHash("multi-entry-dag"),
    entryPoints = NonEmptyList.of(nodeA, nodeB),
    repeatPolicy = RepeatPolicy.NoRepeat,
    nodes = Map(
      nodeA -> Node(nodeA, Some("Entry A"), RetryPolicy.None, DeliveryStrategy.Immediate, svcA),
      nodeB -> Node(nodeB, Some("Entry B"), RetryPolicy.None, DeliveryStrategy.Immediate, svcB),
      nodeC -> Node(nodeC, Some("Common"), RetryPolicy.None, DeliveryStrategy.Immediate, svcC)
    ),
    edges = List(
      Edge(nodeA, nodeC, EdgeGuard.OnSuccess),
      Edge(nodeB, nodeC, EdgeGuard.OnSuccess)
    )
  )

  private def mkTraversalId: IO[TraversalId] = TraversalId.next[IO]

  private def mkState(dag: Dag): IO[TraversalState] =
    mkTraversalId.map(id => TraversalState.initial(id, waystation, dag))

  /** Default test time - using a fixed instant for deterministic tests */
  private val testNow: java.time.Instant = java.time.Instant.parse("2025-01-15T10:00:00Z")

  private def runFsm(dag: Dag, state: TraversalState, signal: TraversalSignal, now: java.time.Instant = testNow): IO[com.monadial.waygrid.common.domain.model.fsm.Value.Result[TraversalState, TraversalError, TraversalEffect]] =
    IO.pure(TraversalFSM.stateless[IO](dag, waystation, now).transition(state, signal)).flatten

  // ---------------------------------------------------------------------------
  // Begin Signal Tests
  // ---------------------------------------------------------------------------

  test("Begin on empty DAG returns EmptyDag error"):
    for
      state  <- mkState(emptyDag)
      result <- runFsm(emptyDag, state, TraversalSignal.Begin(state.traversalId))
    yield
      result.output match
        case Left(_: EmptyDag) => success
        case other             => failure(s"Expected EmptyDag error, got: $other")

  test("Begin on linear DAG dispatches entry node"):
    for
      state  <- mkState(linearDag)
      result <- runFsm(linearDag, state, TraversalSignal.Begin(state.traversalId))
    yield
      result.output match
        case Right(TraversalEffect.DispatchNode(_, nid, _, _)) =>
          expect.same(nid, nodeA) and
            expect(result.state.isStarted(nodeA))
        case other => failure(s"Expected DispatchNode, got: $other")

  test("Begin on scheduled DAG returns Schedule effect"):
    for
      state  <- mkState(scheduledDag)
      result <- runFsm(scheduledDag, state, TraversalSignal.Begin(state.traversalId))
    yield
      result.output match
        case Right(TraversalEffect.Schedule(_, _, nid)) =>
          expect.same(nid, nodeA) and
            expect(!result.state.isStarted(nodeA))
        case other => failure(s"Expected Schedule effect, got: $other")

  test("Begin on ScheduleAt in the past dispatches immediately"):
    // Schedule time is before testNow, so should dispatch immediately
    val pastTime = testNow.minusSeconds(5)
    val dag = Dag(
      hash = DagHash("schedule-at-past"),
      entryPoints = NonEmptyList.one(nodeA),
      repeatPolicy = RepeatPolicy.NoRepeat,
      nodes = Map(
        nodeA -> Node(nodeA, Some("A"), RetryPolicy.None, DeliveryStrategy.ScheduleAt(pastTime), svcA)
      ),
      edges = List.empty
    )
    for
      state  <- mkState(dag)
      result <- runFsm(dag, state, TraversalSignal.Begin(state.traversalId))
    yield
      result.output match
        case Right(TraversalEffect.DispatchNode(_, nid, _, _)) =>
          expect.same(nid, nodeA)
        case other => failure(s"Expected DispatchNode, got: $other")

  test("Begin when traversal already in progress returns AlreadyInProgress error"):
    for
      state   <- mkState(linearDag)
      result1 <- runFsm(linearDag, state, TraversalSignal.Begin(state.traversalId))
      result2 <- runFsm(linearDag, result1.state, TraversalSignal.Begin(state.traversalId))
    yield
      result2.output match
        case Left(_: AlreadyInProgress) => success
        case other                      => failure(s"Expected AlreadyInProgress error, got: $other")

  // ---------------------------------------------------------------------------
  // NodeSuccess Signal Tests
  // ---------------------------------------------------------------------------

  test("NodeSuccess transitions to next node in linear DAG"):
    for
      state   <- mkState(linearDag)
      started <- runFsm(linearDag, state, TraversalSignal.Begin(state.traversalId))
      result  <- runFsm(linearDag, started.state, TraversalSignal.NodeSuccess(state.traversalId, nodeA))
    yield
      result.output match
        case Right(TraversalEffect.DispatchNode(_, nid, _, _)) =>
          expect.same(nid, nodeB) and
            expect(result.state.isCompleted(nodeA)) and
            expect(result.state.isStarted(nodeB))
        case other => failure(s"Expected DispatchNode for nodeB, got: $other")

  test("NodeSuccess schedules next node when its delivery strategy is scheduled"):
    for
      state   <- mkState(scheduleNextDag)
      started <- runFsm(scheduleNextDag, state, TraversalSignal.Begin(state.traversalId))
      result  <- runFsm(scheduleNextDag, started.state, TraversalSignal.NodeSuccess(state.traversalId, nodeA))
    yield
      result.output match
        case Right(TraversalEffect.Schedule(_, _, nid)) =>
          expect.same(nid, nodeB) and expect(!result.state.isStarted(nodeB))
        case other => failure(s"Expected Schedule for nodeB, got: $other")

  test("NodeSuccess selects Conditional edge when condition matches"):
    for
      state   <- mkState(conditionalDag)
      started <- runFsm(conditionalDag, state, TraversalSignal.Begin(state.traversalId))
      r1      <- runFsm(conditionalDag, started.state, TraversalSignal.NodeSuccess(state.traversalId, nodeA))
      r2 <- runFsm(
        conditionalDag,
        r1.state,
        TraversalSignal.NodeSuccess(
          state.traversalId,
          nodeB,
          output = Some(Json.obj("approved" -> Json.fromBoolean(true)))
        )
      )
    yield
      r2.output match
        case Right(TraversalEffect.DispatchNode(_, nid, _, _)) =>
          expect.same(nid, nodeC) and expect(r2.state.isStarted(nodeC))
        case other => failure(s"Expected DispatchNode for nodeC, got: $other")

  test("NodeSuccess falls back to OnSuccess edge when no Conditional matches"):
    for
      state   <- mkState(conditionalDag)
      started <- runFsm(conditionalDag, state, TraversalSignal.Begin(state.traversalId))
      r1      <- runFsm(conditionalDag, started.state, TraversalSignal.NodeSuccess(state.traversalId, nodeA))
      r2 <- runFsm(
        conditionalDag,
        r1.state,
        TraversalSignal.NodeSuccess(
          state.traversalId,
          nodeB,
          output = Some(Json.obj("approved" -> Json.fromBoolean(false)))
        )
      )
    yield
      r2.output match
        case Right(TraversalEffect.DispatchNode(_, nid, _, _)) =>
          expect.same(nid, nodeD) and expect(r2.state.isStarted(nodeD))
        case other => failure(s"Expected DispatchNode for nodeD, got: $other")

  test("NodeSuccess on last node completes traversal"):
    for
      state   <- mkState(singleNodeDag)
      started <- runFsm(singleNodeDag, state, TraversalSignal.Begin(state.traversalId))
      result  <- runFsm(singleNodeDag, started.state, TraversalSignal.NodeSuccess(state.traversalId, nodeA))
    yield
      result.output match
        case Right(TraversalEffect.Complete(_, _)) =>
          expect(result.state.isCompleted(nodeA)) and
            expect(result.state.isTraversalComplete)
        case other => failure(s"Expected Complete, got: $other")

  test("NodeSuccess for unknown node returns NodeNotFound error"):
    for
      state   <- mkState(linearDag)
      started <- runFsm(linearDag, state, TraversalSignal.Begin(state.traversalId))
      result  <- runFsm(linearDag, started.state, TraversalSignal.NodeSuccess(state.traversalId, NodeId("unknown")))
    yield
      result.output match
        case Left(_: NodeNotFound) => success
        case other                 => failure(s"Expected NodeNotFound, got: $other")

  test("NodeSuccess for node not started returns InvalidNodeState error"):
    for
      state  <- mkState(linearDag)
      result <- runFsm(linearDag, state, TraversalSignal.NodeSuccess(state.traversalId, nodeA))
    yield
      result.output match
        case Left(_: InvalidNodeState) => success
        case other                     => failure(s"Expected InvalidNodeState, got: $other")

  // ---------------------------------------------------------------------------
  // NodeFailure Signal Tests
  // ---------------------------------------------------------------------------

  test("NodeFailure with retry policy schedules retry"):
    for
      state   <- mkState(retryDag)
      started <- runFsm(retryDag, state, TraversalSignal.Begin(state.traversalId))
      failed  <- runFsm(retryDag, started.state, TraversalSignal.NodeFailure(state.traversalId, nodeA, Some("timeout")))
    yield
      failed.output match
        case Right(TraversalEffect.ScheduleRetry(_, _, attempt, nid)) =>
          expect.same(nid, nodeA) and
            expect.same(attempt.unwrap, 1) and
            expect(failed.state.isFailed(nodeA))
        case other => failure(s"Expected ScheduleRetry, got: $other")

  test("NodeFailure with failure edge transitions via OnFailure"):
    for
      state   <- mkState(failureDag)
      started <- runFsm(failureDag, state, TraversalSignal.Begin(state.traversalId))
      failed  <- runFsm(failureDag, started.state, TraversalSignal.NodeFailure(state.traversalId, nodeA, Some("error")))
    yield
      failed.output match
        case Right(TraversalEffect.DispatchNode(_, nid, _, _)) =>
          expect.same(nid, nodeD) and
            expect(failed.state.isFailed(nodeA)) and
            expect(failed.state.isStarted(nodeD))
        case other => failure(s"Expected DispatchNode for nodeD, got: $other")

  test("NodeFailure without retry or failure edge fails traversal"):
    for
      state   <- mkState(singleNodeDag)
      started <- runFsm(singleNodeDag, state, TraversalSignal.Begin(state.traversalId))
      failed  <- runFsm(singleNodeDag, started.state, TraversalSignal.NodeFailure(state.traversalId, nodeA, Some("fatal")))
    yield
      failed.output match
        case Right(TraversalEffect.Fail(_, _)) =>
          expect(failed.state.isFailed(nodeA))
        case other => failure(s"Expected Fail, got: $other")

  // ---------------------------------------------------------------------------
  // Retry Signal Tests
  // ---------------------------------------------------------------------------

  test("Retry restarts a failed node"):
    for
      state   <- mkState(retryDag)
      started <- runFsm(retryDag, state, TraversalSignal.Begin(state.traversalId))
      failed  <- runFsm(retryDag, started.state, TraversalSignal.NodeFailure(state.traversalId, nodeA, None))
      // Manually mark the node as ready for retry (simulate timer fire)
      retried <- runFsm(retryDag, failed.state, TraversalSignal.Retry(state.traversalId, nodeA))
    yield
      retried.output match
        case Right(TraversalEffect.DispatchNode(_, nid, _, _)) =>
          expect.same(nid, nodeA) and
            expect(retried.state.isStarted(nodeA)) and
            expect(retried.state.retryCount(nodeA) >= 1)
        case other => failure(s"Expected DispatchNode, got: $other")

  test("Retry on non-failed node returns InvalidNodeState error"):
    for
      state   <- mkState(linearDag)
      started <- runFsm(linearDag, state, TraversalSignal.Begin(state.traversalId))
      result  <- runFsm(linearDag, started.state, TraversalSignal.Retry(state.traversalId, nodeA))
    yield
      result.output match
        case Left(_: InvalidNodeState) => success
        case other                     => failure(s"Expected InvalidNodeState, got: $other")

  // ---------------------------------------------------------------------------
  // Resume Signal Tests
  // ---------------------------------------------------------------------------

  test("Resume starts a scheduled node"):
    for
      state     <- mkState(scheduledDag)
      scheduled <- runFsm(scheduledDag, state, TraversalSignal.Begin(state.traversalId))
      resumed   <- runFsm(scheduledDag, scheduled.state, TraversalSignal.Resume(state.traversalId, nodeA))
    yield
      resumed.output match
        case Right(TraversalEffect.DispatchNode(_, nid, _, _)) =>
          expect.same(nid, nodeA) and
            expect(resumed.state.current.contains(nodeA))
        case other => failure(s"Expected DispatchNode, got: $other")

  test("Resume on unknown node returns NodeNotFound error"):
    for
      state  <- mkState(linearDag)
      result <- runFsm(linearDag, state, TraversalSignal.Resume(state.traversalId, NodeId("unknown")))
    yield
      result.output match
        case Left(_: NodeNotFound) => success
        case other                 => failure(s"Expected NodeNotFound, got: $other")

  test("Resume on already finished node returns NoOp"):
    for
      state    <- mkState(singleNodeDag)
      started  <- runFsm(singleNodeDag, state, TraversalSignal.Begin(state.traversalId))
      result   <- runFsm(singleNodeDag, started.state, TraversalSignal.NodeSuccess(state.traversalId, nodeA))
      resumed  <- runFsm(singleNodeDag, result.state, TraversalSignal.Resume(state.traversalId, nodeA))
    yield
      resumed.output match
        case Right(TraversalEffect.NoOp(_)) => success
        case other                          => failure(s"Expected NoOp, got: $other")

  // ---------------------------------------------------------------------------
  // Cancel Signal Tests
  // ---------------------------------------------------------------------------

  test("Cancel cancels the active traversal"):
    for
      state    <- mkState(linearDag)
      started  <- runFsm(linearDag, state, TraversalSignal.Begin(state.traversalId))
      canceled <- runFsm(linearDag, started.state, TraversalSignal.Cancel(state.traversalId))
    yield
      canceled.output match
        case Right(TraversalEffect.Cancel(_, _)) =>
          expect(canceled.state.current.isEmpty)
        case other => failure(s"Expected Cancel effect, got: $other")

  // ---------------------------------------------------------------------------
  // Complete Workflow Tests
  // ---------------------------------------------------------------------------

  test("Complete linear DAG traversal: A -> B -> C"):
    for
      state <- mkState(linearDag)
      // Begin -> dispatch A
      r1 <- runFsm(linearDag, state, TraversalSignal.Begin(state.traversalId))
      // A succeeds -> dispatch B
      r2 <- runFsm(linearDag, r1.state, TraversalSignal.NodeSuccess(state.traversalId, nodeA))
      // B succeeds -> dispatch C
      r3 <- runFsm(linearDag, r2.state, TraversalSignal.NodeSuccess(state.traversalId, nodeB))
      // C succeeds -> complete
      r4 <- runFsm(linearDag, r3.state, TraversalSignal.NodeSuccess(state.traversalId, nodeC))
    yield
      val finalState = r4.state
      expect(finalState.isCompleted(nodeA)) and
        expect(finalState.isCompleted(nodeB)) and
        expect(finalState.isCompleted(nodeC)) and
        expect(finalState.isTraversalComplete) and
        (r4.output match
          case Right(TraversalEffect.Complete(_, _)) => success
          case other                              => failure(s"Expected Complete, got: $other"))

  test("Traversal with retry then success"):
    for
      state <- mkState(retryDag)
      // Begin -> dispatch A
      r1 <- runFsm(retryDag, state, TraversalSignal.Begin(state.traversalId))
      // A fails first time -> schedule retry
      r2 <- runFsm(retryDag, r1.state, TraversalSignal.NodeFailure(state.traversalId, nodeA, Some("first fail")))
      // Retry fires -> dispatch A again
      r3 <- runFsm(retryDag, r2.state, TraversalSignal.Retry(state.traversalId, nodeA))
      // A succeeds this time -> dispatch B
      r4 <- runFsm(retryDag, r3.state, TraversalSignal.NodeSuccess(state.traversalId, nodeA))
      // B succeeds -> complete
      r5 <- runFsm(retryDag, r4.state, TraversalSignal.NodeSuccess(state.traversalId, nodeB))
    yield
      val finalState = r5.state
      expect(finalState.isCompleted(nodeA)) and
        expect(finalState.isCompleted(nodeB)) and
        expect(finalState.retryCount(nodeA) >= 1) and
        (r5.output match
          case Right(TraversalEffect.Complete(_, _)) => success
          case other                              => failure(s"Expected Complete, got: $other"))

  test("Traversal with failure edge transition"):
    for
      state <- mkState(failureDag)
      // Begin -> dispatch A
      r1 <- runFsm(failureDag, state, TraversalSignal.Begin(state.traversalId))
      // A fails -> dispatch D (failure edge)
      r2 <- runFsm(failureDag, r1.state, TraversalSignal.NodeFailure(state.traversalId, nodeA, Some("error")))
      // D succeeds -> complete
      r3 <- runFsm(failureDag, r2.state, TraversalSignal.NodeSuccess(state.traversalId, nodeD))
    yield
      val finalState = r3.state
      expect(finalState.isFailed(nodeA)) and
        expect(finalState.isCompleted(nodeD)) and
        expect(finalState.isTraversalComplete) and
        (r3.output match
          case Right(TraversalEffect.Complete(_, _)) => success
          case other                              => failure(s"Expected Complete, got: $other"))

  // ---------------------------------------------------------------------------
  // Vector Clock Tests
  // ---------------------------------------------------------------------------

  test("Vector clock advances on each state transition"):
    for
      state <- mkState(linearDag)
      v0    <- IO.pure(state.version)
      r1    <- runFsm(linearDag, state, TraversalSignal.Begin(state.traversalId))
      v1    <- IO.pure(r1.state.version)
      r2    <- runFsm(linearDag, r1.state, TraversalSignal.NodeSuccess(state.traversalId, nodeA))
      v2    <- IO.pure(r2.state.version)
    yield
      expect(v1 > v0) and expect(v2 > v1)

  test("History records all events"):
    for
      state <- mkState(singleNodeDag)
      r1    <- runFsm(singleNodeDag, state, TraversalSignal.Begin(state.traversalId))
      r2    <- runFsm(singleNodeDag, r1.state, TraversalSignal.NodeSuccess(state.traversalId, nodeA))
    yield
      val history = r2.state.history
      expect(history.size >= 2)

  // ---------------------------------------------------------------------------
  // Edge Cases
  // ---------------------------------------------------------------------------

  test("NodeStart is idempotent"):
    for
      state   <- mkState(linearDag)
      started <- runFsm(linearDag, state, TraversalSignal.Begin(state.traversalId))
      ack1    <- runFsm(linearDag, started.state, TraversalSignal.NodeStart(state.traversalId, nodeA))
      ack2    <- runFsm(linearDag, ack1.state, TraversalSignal.NodeStart(state.traversalId, nodeA))
    yield
      (ack1.output, ack2.output) match
        case (Right(TraversalEffect.NoOp(_)), Right(TraversalEffect.NoOp(_))) => success
        case other => failure(s"Expected NoOp for both, got: $other")

  test("Failed signal marks traversal as failed"):
    for
      state   <- mkState(linearDag)
      started <- runFsm(linearDag, state, TraversalSignal.Begin(state.traversalId))
      failed  <- runFsm(linearDag, started.state, TraversalSignal.Failed(state.traversalId, Some("external failure")))
    yield
      failed.output match
        case Right(TraversalEffect.Fail(_, _)) => success
        case other                             => failure(s"Expected Fail, got: $other")

  test("Completed signal on incomplete traversal returns InvalidNodeState"):
    for
      state   <- mkState(linearDag)
      started <- runFsm(linearDag, state, TraversalSignal.Begin(state.traversalId))
      result  <- runFsm(linearDag, started.state, TraversalSignal.Completed(state.traversalId))
    yield
      result.output match
        case Left(_: InvalidNodeState) => success
        case other                     => failure(s"Expected InvalidNodeState, got: $other")

  // ---------------------------------------------------------------------------
  // Fork/Join Tests
  // ---------------------------------------------------------------------------

  test("Fork fans out to multiple branches and join waits until all complete (AND)"):
    val forkNode = NodeId("F")
    val joinNode = NodeId("J")
    for
      state0 <- mkState(forkJoinDag)
      // Begin -> dispatch A
      r1 <- runFsm(forkJoinDag, state0, TraversalSignal.Begin(state0.traversalId))
      // A succeeds -> Fork is handled immediately as control flow, produces DispatchNodes
      r2 <- runFsm(forkJoinDag, r1.state, TraversalSignal.NodeSuccess(state0.traversalId, nodeA))
      // Acknowledge branch nodes started
      r3 <- runFsm(forkJoinDag, r2.state, TraversalSignal.NodeStart(state0.traversalId, nodeB))
      r4 <- runFsm(forkJoinDag, r3.state, TraversalSignal.NodeStart(state0.traversalId, nodeC))
      // First branch completes -> join not satisfied yet -> NoOp
      r5 <- runFsm(forkJoinDag, r4.state, TraversalSignal.NodeSuccess(state0.traversalId, nodeB))
      // Second branch completes -> join satisfied -> dispatch nodeD
      r6 <- runFsm(forkJoinDag, r5.state, TraversalSignal.NodeSuccess(state0.traversalId, nodeC))
      // nodeD completes -> traversal complete
      r7 <- runFsm(forkJoinDag, r6.state, TraversalSignal.NodeSuccess(state0.traversalId, nodeD))
    yield
      // Fork is now handled immediately when transitioning TO it (not dispatched as work)
      val fanoutOk =
        r2.output match
          case Right(TraversalEffect.DispatchNodes(_, nodes, _, _)) =>
            val ids = nodes.map(_._1).toSet
            expect(ids == Set(nodeB, nodeC)) &&
              expect(r2.state.hasActiveForks) &&
              expect(r2.state.branchStates.size == 2) &&
              expect(r2.state.isCompleted(forkNode)) // Fork marked as completed (control flow node)
          case other => failure(s"Expected DispatchNodes, got: $other")

      val joinWaitOk =
        r5.output match
          case Right(TraversalEffect.NoOp(_)) =>
            expect(r5.state.pendingJoins.contains(joinNode))
          case other => failure(s"Expected NoOp while waiting for join, got: $other")

      val joinCompleteOk =
        r6.output match
          case Right(TraversalEffect.DispatchNode(_, nid, _, _)) =>
            expect.same(nid, nodeD) &&
              expect(!r6.state.hasActiveForks) &&
              expect(r6.state.branchStates.isEmpty) &&
              expect(r6.state.isCompleted(joinNode))
          case other => failure(s"Expected DispatchNode(nodeD) after join, got: $other")

      val completedOk =
        r7.output match
          case Right(TraversalEffect.Complete(_, _)) =>
            expect(r7.state.isTraversalComplete)
          case other => failure(s"Expected Complete, got: $other")

      fanoutOk and joinWaitOk and joinCompleteOk and completedOk

  test("Fork/Join OR completes on first branch and cancels the other branch"):
    for
      state0 <- mkState(forkJoinOrDag)
      r1     <- runFsm(forkJoinOrDag, state0, TraversalSignal.Begin(state0.traversalId))
      // A succeeds -> Fork is handled immediately, produces DispatchNodes
      r2     <- runFsm(forkJoinOrDag, r1.state, TraversalSignal.NodeSuccess(state0.traversalId, nodeA))
      // First branch completes -> OR join satisfied -> dispatch nodeD and cancel other branch
      r3     <- runFsm(forkJoinOrDag, r2.state, TraversalSignal.NodeSuccess(state0.traversalId, nodeB))
    yield
      val canceledRecorded =
        r3.state.history.exists {
          case Event.BranchCanceled(_, _, _, _, _, _) => true
          case _                                     => false
        }

      val dispatchAfterJoin =
        r3.output match
          case Right(TraversalEffect.DispatchNode(_, nid, branchesToCancel, _)) =>
            expect.same(nid, nodeD) &&
              expect(branchesToCancel.nonEmpty) // Should include the branch to cancel
          case other => failure(s"Expected DispatchNode(nodeD) after OR join, got: $other")

      dispatchAfterJoin and expect(!r3.state.hasActiveForks) and expect(r3.state.branchStates.isEmpty) and expect(canceledRecorded)

  test("Fork/Join Quorum waits until required branches complete"):
    val nodeE     = NodeId("E")
    val forkNodeQ = NodeId("FQ")
    val joinNodeQ = NodeId("JQ")

    val dag = Dag(
      hash = DagHash("fork-join-quorum-dag"),
      entryPoints = NonEmptyList.one(nodeA),
      repeatPolicy = RepeatPolicy.NoRepeat,
      nodes = Map(
        nodeA -> Node(nodeA, Some("Start"), RetryPolicy.None, DeliveryStrategy.Immediate, svcA),
        nodeB -> Node(nodeB, Some("B"), RetryPolicy.None, DeliveryStrategy.Immediate, svcB),
        nodeC -> Node(nodeC, Some("C"), RetryPolicy.None, DeliveryStrategy.Immediate, svcC),
        nodeE -> Node(nodeE, Some("E"), RetryPolicy.None, DeliveryStrategy.Immediate, svcC),
        nodeD -> Node(nodeD, Some("After Join"), RetryPolicy.None, DeliveryStrategy.Immediate, svcD),
        forkNodeQ -> Node(forkNodeQ, Some("Fork"), RetryPolicy.None, DeliveryStrategy.Immediate, svcA, NodeType.Fork(forkId)),
        joinNodeQ -> Node(
          joinNodeQ,
          Some("Join"),
          RetryPolicy.None,
          DeliveryStrategy.Immediate,
          svcA,
          NodeType.Join(forkId, JoinStrategy.Quorum(2), None)
        )
      ),
      edges = List(
        Edge(nodeA, forkNodeQ, EdgeGuard.OnSuccess),
        Edge(forkNodeQ, nodeB, EdgeGuard.OnSuccess),
        Edge(forkNodeQ, nodeC, EdgeGuard.OnSuccess),
        Edge(forkNodeQ, nodeE, EdgeGuard.OnSuccess),
        Edge(nodeB, joinNodeQ, EdgeGuard.OnSuccess),
        Edge(nodeC, joinNodeQ, EdgeGuard.OnSuccess),
        Edge(nodeE, joinNodeQ, EdgeGuard.OnSuccess),
        Edge(joinNodeQ, nodeD, EdgeGuard.OnSuccess)
      )
    )

    for
      state0 <- mkState(dag)
      r1     <- runFsm(dag, state0, TraversalSignal.Begin(state0.traversalId))
      // A succeeds -> Fork is handled immediately, produces DispatchNodes
      r2     <- runFsm(dag, r1.state, TraversalSignal.NodeSuccess(state0.traversalId, nodeA))
      // First branch completes -> quorum(2) not satisfied yet
      r3     <- runFsm(dag, r2.state, TraversalSignal.NodeSuccess(state0.traversalId, nodeB))
      // Second branch completes -> quorum(2) satisfied
      r4     <- runFsm(dag, r3.state, TraversalSignal.NodeSuccess(state0.traversalId, nodeC))
    yield
      val waitsAfterOne =
        r3.output match
          case Right(TraversalEffect.NoOp(_)) => success
          case other                          => failure(s"Expected NoOp after 1 branch for quorum(2), got: $other")

      val completesOnSecond =
        r4.output match
          case Right(TraversalEffect.DispatchNode(_, nid, _, _)) => expect.same(nid, nodeD)
          case other => failure(s"Expected DispatchNode after quorum reached, got: $other")

      waitsAfterOne and completesOnSecond

  test("Join Timeout dispatches OnTimeout edge"):
    for
      state0 <- mkState(forkJoinTimeoutDag)
      r1     <- runFsm(forkJoinTimeoutDag, state0, TraversalSignal.Begin(state0.traversalId))
      // A succeeds -> Fork is handled immediately
      r2     <- runFsm(forkJoinTimeoutDag, r1.state, TraversalSignal.NodeSuccess(state0.traversalId, nodeA))
      // First branch completes
      r3     <- runFsm(forkJoinTimeoutDag, r2.state, TraversalSignal.NodeSuccess(state0.traversalId, nodeB))
      // Timeout occurs before second branch completes
      r4     <- runFsm(forkJoinTimeoutDag, r3.state, TraversalSignal.Timeout(state0.traversalId, joinNode, None))
    yield
      r4.output match
        case Right(TraversalEffect.DispatchNode(_, nid, _, _)) => expect.same(nid, nodeD)
        case other => failure(s"Expected DispatchNode via OnTimeout edge, got: $other")

  // ---------------------------------------------------------------------------
  // Multiple Entry Tests
  // ---------------------------------------------------------------------------

  test("Begin can start from a non-primary entry point in a multi-origin DAG"):
    for
      state0 <- mkState(multiEntryDag)
      r1 <- runFsm(multiEntryDag, state0, TraversalSignal.Begin(state0.traversalId, entryNodeId = Some(nodeB)))
    yield
      r1.output match
        case Right(TraversalEffect.DispatchNode(_, nid, _, _)) =>
          expect.same(nid, nodeB) && expect(r1.state.isStarted(nodeB)) && expect(!r1.state.isStarted(nodeA))
        case other => failure(s"Expected DispatchNode(nodeB), got: $other")

  test("Begin without entryNodeId uses the first entry point"):
    for
      state0 <- mkState(multiEntryDag)
      r1     <- runFsm(multiEntryDag, state0, TraversalSignal.Begin(state0.traversalId))
    yield
      r1.output match
        case Right(TraversalEffect.DispatchNode(_, nid, _, _)) =>
          expect.same(nid, nodeA) && expect(r1.state.isStarted(nodeA)) && expect(!r1.state.isStarted(nodeB))
        case other => failure(s"Expected DispatchNode(nodeA), got: $other")

  test("Begin with an unknown entryNodeId returns InvalidNodeState"):
    for
      state0 <- mkState(multiEntryDag)
      r1     <- runFsm(multiEntryDag, state0, TraversalSignal.Begin(state0.traversalId, entryNodeId = Some(nodeC)))
    yield
      r1.output match
        case Left(_: InvalidNodeState) => success
        case other                     => failure(s"Expected InvalidNodeState, got: $other")

  // ---------------------------------------------------------------------------
  // Traversal-Level Timeout Tests
  // ---------------------------------------------------------------------------

  private val dagWithTimeout = Dag(
    hash = DagHash("timeout-dag"),
    entryPoints = NonEmptyList.one(nodeA),
    repeatPolicy = RepeatPolicy.NoRepeat,
    nodes = Map(
      nodeA -> Node(nodeA, Some("A"), RetryPolicy.None, DeliveryStrategy.Immediate, svcA),
      nodeB -> Node(nodeB, Some("B"), RetryPolicy.None, DeliveryStrategy.Immediate, svcB)
    ),
    edges = List(
      Edge(nodeA, nodeB, EdgeGuard.OnSuccess)
    ),
    timeout = Some(scala.concurrent.duration.FiniteDuration(30, "seconds"))
  )

  test("Begin with DAG timeout schedules traversal timeout"):
    for
      state0 <- mkState(dagWithTimeout)
      r1     <- runFsm(dagWithTimeout, state0, TraversalSignal.Begin(state0.traversalId))
    yield
      r1.output match
        case Right(TraversalEffect.DispatchNode(_, nid, _, Some((timeoutId, deadline)))) =>
          expect.same(nid, nodeA) &&
            expect(timeoutId.startsWith("traversal-timeout-")) &&
            expect(r1.state.traversalTimeoutId.contains(timeoutId)) &&
            expect(deadline.isAfter(testNow))
        case other => failure(s"Expected DispatchNode with timeout info, got: $other")

  test("Complete effect includes timeout ID to cancel"):
    for
      state0   <- mkState(dagWithTimeout)
      r1       <- runFsm(dagWithTimeout, state0, TraversalSignal.Begin(state0.traversalId))
      r2       <- runFsm(dagWithTimeout, r1.state, TraversalSignal.NodeSuccess(state0.traversalId, nodeA))
      r3       <- runFsm(dagWithTimeout, r2.state, TraversalSignal.NodeSuccess(state0.traversalId, nodeB))
    yield
      val timeoutIdFromStart = r1.output match
        case Right(TraversalEffect.DispatchNode(_, _, _, Some((tid, _)))) => Some(tid)
        case _ => None

      r3.output match
        case Right(TraversalEffect.Complete(_, cancelTimeoutId)) =>
          expect(cancelTimeoutId == timeoutIdFromStart) &&
            expect(r3.state.traversalTimeoutId.isEmpty) // Cleared after completion
        case other => failure(s"Expected Complete with cancelTimeoutId, got: $other")

  test("TraversalTimeout fails traversal when still active"):
    for
      state0 <- mkState(dagWithTimeout)
      r1     <- runFsm(dagWithTimeout, state0, TraversalSignal.Begin(state0.traversalId))
      // Simulate timeout firing while traversal is still in progress
      r2     <- runFsm(dagWithTimeout, r1.state, TraversalSignal.TraversalTimeout(state0.traversalId))
    yield
      r2.output match
        case Right(TraversalEffect.Fail(_, _)) =>
          expect(r2.state.active.isEmpty) && // All active work canceled
            expect(r2.state.traversalTimeoutId.isEmpty) && // Timeout cleared
            expect(r2.state.history.exists {
              case Event.TraversalTimedOut(_, _, _, _, _) => true
              case _ => false
            })
        case other => failure(s"Expected Fail after traversal timeout, got: $other")

  test("TraversalTimeout is ignored when traversal already complete (race condition)"):
    for
      state0 <- mkState(dagWithTimeout)
      r1     <- runFsm(dagWithTimeout, state0, TraversalSignal.Begin(state0.traversalId))
      r2     <- runFsm(dagWithTimeout, r1.state, TraversalSignal.NodeSuccess(state0.traversalId, nodeA))
      r3     <- runFsm(dagWithTimeout, r2.state, TraversalSignal.NodeSuccess(state0.traversalId, nodeB))
      // Now traversal is complete - timeout arrives late (race condition)
      r4     <- runFsm(dagWithTimeout, r3.state, TraversalSignal.TraversalTimeout(state0.traversalId))
    yield
      r4.output match
        case Right(TraversalEffect.NoOp(_)) => success // Timeout ignored gracefully
        case other => failure(s"Expected NoOp for late timeout, got: $other")

  test("DAG without timeout does not schedule timeout"):
    for
      state0 <- mkState(linearDag) // linearDag has no timeout
      r1     <- runFsm(linearDag, state0, TraversalSignal.Begin(state0.traversalId))
    yield
      r1.output match
        case Right(TraversalEffect.DispatchNode(_, nid, _, scheduleTimeout)) =>
          expect.same(nid, nodeA) &&
            expect(scheduleTimeout.isEmpty) &&
            expect(r1.state.traversalTimeoutId.isEmpty)
        case other => failure(s"Expected DispatchNode without timeout, got: $other")

  // ---------------------------------------------------------------------------
  // Nested Fork Tests
  // ---------------------------------------------------------------------------

  private val forkId2: ForkId =
    ForkId.fromStringUnsafe[cats.Id]("01ARZ3NDEKTSV4RRFFQ69G5FAW")

  private val nodeE = NodeId("E")
  private val nodeH = NodeId("H") // Renamed from nodeF to avoid collision with forkNode (NodeId("F"))
  private val nodeG = NodeId("G")
  private val forkNode2 = NodeId("F2")
  private val joinNode2 = NodeId("J2")

  /**
   * Nested Fork DAG:
   * A -> Fork1 -> [B -> Fork2 -> [D, E] -> Join2 -> H, C] -> Join1 -> G
   *
   * Structure:
   * - Fork1 creates two branches: one with nested Fork2, one simple (C)
   * - Branch 1: B -> Fork2 -> (D and E in parallel) -> Join2 -> H
   * - Branch 2: C (simple)
   * - Both must complete for Join1 (AND strategy)
   */
  private val nestedForkDag = Dag(
    hash = DagHash("nested-fork-dag"),
    entryPoints = NonEmptyList.one(nodeA),
    repeatPolicy = RepeatPolicy.NoRepeat,
    nodes = Map(
      nodeA -> Node(nodeA, Some("Start"), RetryPolicy.None, DeliveryStrategy.Immediate, svcA),
      nodeB -> Node(nodeB, Some("B"), RetryPolicy.None, DeliveryStrategy.Immediate, svcB),
      nodeC -> Node(nodeC, Some("C"), RetryPolicy.None, DeliveryStrategy.Immediate, svcC),
      nodeD -> Node(nodeD, Some("D"), RetryPolicy.None, DeliveryStrategy.Immediate, svcD),
      nodeE -> Node(nodeE, Some("E"), RetryPolicy.None, DeliveryStrategy.Immediate, svcA),
      nodeH -> Node(nodeH, Some("H"), RetryPolicy.None, DeliveryStrategy.Immediate, svcB),
      nodeG -> Node(nodeG, Some("End"), RetryPolicy.None, DeliveryStrategy.Immediate, svcC),
      forkNode -> Node(forkNode, Some("Fork1"), RetryPolicy.None, DeliveryStrategy.Immediate, svcA, NodeType.Fork(forkId)),
      joinNode -> Node(joinNode, Some("Join1"), RetryPolicy.None, DeliveryStrategy.Immediate, svcA, NodeType.Join(forkId, JoinStrategy.And, None)),
      forkNode2 -> Node(forkNode2, Some("Fork2"), RetryPolicy.None, DeliveryStrategy.Immediate, svcA, NodeType.Fork(forkId2)),
      joinNode2 -> Node(joinNode2, Some("Join2"), RetryPolicy.None, DeliveryStrategy.Immediate, svcA, NodeType.Join(forkId2, JoinStrategy.And, None))
    ),
    edges = List(
      // Main flow to first fork
      Edge(nodeA, forkNode, EdgeGuard.OnSuccess),
      // Fork1 branches
      Edge(forkNode, nodeB, EdgeGuard.OnSuccess), // Branch 1: leads to nested fork
      Edge(forkNode, nodeC, EdgeGuard.OnSuccess), // Branch 2: simple
      // Nested fork in branch 1
      Edge(nodeB, forkNode2, EdgeGuard.OnSuccess),
      Edge(forkNode2, nodeD, EdgeGuard.OnSuccess),
      Edge(forkNode2, nodeE, EdgeGuard.OnSuccess),
      Edge(nodeD, joinNode2, EdgeGuard.OnSuccess),
      Edge(nodeE, joinNode2, EdgeGuard.OnSuccess),
      Edge(joinNode2, nodeH, EdgeGuard.OnSuccess),
      // Both branches merge at Join1
      Edge(nodeH, joinNode, EdgeGuard.OnSuccess),
      Edge(nodeC, joinNode, EdgeGuard.OnSuccess),
      // Final node after Join1
      Edge(joinNode, nodeG, EdgeGuard.OnSuccess)
    )
  )

  test("Nested forks: inner fork completes before outer join"):
    for
      state0 <- mkState(nestedForkDag)
      // Begin -> dispatch A
      r1 <- runFsm(nestedForkDag, state0, TraversalSignal.Begin(state0.traversalId))
      // A succeeds -> Fork1 triggers, dispatches B and C
      r2 <- runFsm(nestedForkDag, r1.state, TraversalSignal.NodeSuccess(state0.traversalId, nodeA))
    yield
      // After A succeeds, Fork1 should trigger and dispatch both B and C
      val fork1Ok =
        r2.output match
          case Right(TraversalEffect.DispatchNodes(_, nodes, _, _)) =>
            val ids = nodes.map(_._1).toSet
            expect(ids == Set(nodeB, nodeC)) &&
              expect(r2.state.hasActiveForks) &&
              expect(r2.state.forkScopes.contains(forkId)) &&
              expect(r2.state.branchStates.size == 2)
          case other => failure(s"Expected DispatchNodes for B and C, got: $other")

      fork1Ok

  test("Nested forks: complete traversal through both fork levels"):
    for
      state0 <- mkState(nestedForkDag)
      // Begin -> A
      r1 <- runFsm(nestedForkDag, state0, TraversalSignal.Begin(state0.traversalId))
      // A succeeds -> Fork1 -> {B, C}
      r2 <- runFsm(nestedForkDag, r1.state, TraversalSignal.NodeSuccess(state0.traversalId, nodeA))
      // B succeeds -> Fork2 -> {D, E}
      r3 <- runFsm(nestedForkDag, r2.state, TraversalSignal.NodeSuccess(state0.traversalId, nodeB))
    yield
      // After B succeeds, Fork2 should trigger and dispatch D and E (inner fork)
      val fork2Ok =
        r3.output match
          case Right(TraversalEffect.DispatchNodes(_, nodes, _, _)) =>
            val ids = nodes.map(_._1).toSet
            expect(ids == Set(nodeD, nodeE)) &&
              expect(r3.state.forkScopes.contains(forkId)) && // Outer fork still active
              expect(r3.state.forkScopes.contains(forkId2)) && // Inner fork now active
              expect(r3.state.forkDepth == 2) // Nested depth is 2
          case other => failure(s"Expected DispatchNodes for D and E, got: $other")

      fork2Ok

  test("Nested forks: full traversal to completion"):
    for
      state0 <- mkState(nestedForkDag)
      // Begin -> A
      r1 <- runFsm(nestedForkDag, state0, TraversalSignal.Begin(state0.traversalId))
      // A succeeds -> Fork1 -> {B, C}
      r2 <- runFsm(nestedForkDag, r1.state, TraversalSignal.NodeSuccess(state0.traversalId, nodeA))
      // B succeeds -> Fork2 -> {D, E}
      r3 <- runFsm(nestedForkDag, r2.state, TraversalSignal.NodeSuccess(state0.traversalId, nodeB))
      // C succeeds -> waiting at Join1
      r4 <- runFsm(nestedForkDag, r3.state, TraversalSignal.NodeSuccess(state0.traversalId, nodeC))
      // D succeeds -> waiting at Join2
      r5 <- runFsm(nestedForkDag, r4.state, TraversalSignal.NodeSuccess(state0.traversalId, nodeD))
      // E succeeds -> Join2 satisfied -> H dispatched
      r6 <- runFsm(nestedForkDag, r5.state, TraversalSignal.NodeSuccess(state0.traversalId, nodeE))
      // H succeeds -> Join1 satisfied (both branches done) -> G dispatched
      r7 <- runFsm(nestedForkDag, r6.state, TraversalSignal.NodeSuccess(state0.traversalId, nodeH))
      // G succeeds -> traversal complete
      r8 <- runFsm(nestedForkDag, r7.state, TraversalSignal.NodeSuccess(state0.traversalId, nodeG))
    yield
      // After D: Join2 not satisfied (waiting for E)
      val afterD = r5.output match
        case Right(TraversalEffect.NoOp(_)) => success
        case other => failure(s"Expected NoOp after D (waiting for E), got: $other")

      // After E: Join2 satisfied, dispatch H
      val afterE = r6.output match
        case Right(TraversalEffect.DispatchNode(_, nid, _, _)) =>
          expect.same(nid, nodeH) &&
            expect(!r6.state.forkScopes.contains(forkId2)) // Inner fork cleaned up
        case other => failure(s"Expected DispatchNode(H) after Join2, got: $other")

      // After H: Join1 satisfied (C was already done), dispatch G
      val afterH = r7.output match
        case Right(TraversalEffect.DispatchNode(_, nid, _, _)) =>
          expect.same(nid, nodeG) &&
            expect(!r7.state.forkScopes.contains(forkId)) // Outer fork cleaned up
        case other => failure(s"Expected DispatchNode(G) after Join1, got: $other")

      // After G: Complete
      val afterG = r8.output match
        case Right(TraversalEffect.Complete(_, _)) =>
          expect(r8.state.isTraversalComplete) &&
            expect(r8.state.forkScopes.isEmpty) &&
            expect(r8.state.branchStates.isEmpty)
        case other => failure(s"Expected Complete, got: $other")

      afterD and afterE and afterH and afterG
