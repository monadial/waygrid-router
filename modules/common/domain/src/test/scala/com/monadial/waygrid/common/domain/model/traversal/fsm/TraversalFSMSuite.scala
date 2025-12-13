package com.monadial.waygrid.common.domain.model.traversal.fsm

import cats.effect.IO
import com.monadial.waygrid.common.domain.model.resiliency.RetryPolicy
import com.monadial.waygrid.common.domain.model.routing.Value.{RepeatPolicy, TraversalId, DeliveryStrategy}
import com.monadial.waygrid.common.domain.model.traversal.dag.{Dag, Edge, Node}
import com.monadial.waygrid.common.domain.model.traversal.dag.Value.{DagHash, EdgeGuard, NodeId}
import com.monadial.waygrid.common.domain.model.traversal.state.TraversalState
import com.monadial.waygrid.common.domain.value.Address.{NodeAddress, ServiceAddress}
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
    entry = nodeA,
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
    entry = nodeA,
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
    entry = nodeA,
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
    entry = nodeA,
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
    entry = nodeA,
    repeatPolicy = RepeatPolicy.NoRepeat,
    nodes = Map.empty,
    edges = List.empty
  )

  /** Single node DAG */
  private val singleNodeDag = Dag(
    hash = DagHash("single-node-dag"),
    entry = nodeA,
    repeatPolicy = RepeatPolicy.NoRepeat,
    nodes = Map(
      nodeA -> Node(nodeA, Some("Only Node"), RetryPolicy.None, DeliveryStrategy.Immediate, svcA)
    ),
    edges = List.empty
  )

  private def mkTraversalId: IO[TraversalId] = TraversalId.next[IO]

  private def mkState(dag: Dag): IO[TraversalState] =
    mkTraversalId.map(id => TraversalState.initial(id, waystation, dag))

  private def runFsm(dag: Dag, state: TraversalState, signal: TraversalSignal): IO[com.monadial.waygrid.common.domain.model.fsm.Value.Result[TraversalState, TraversalError, TraversalEffect]] =
    IO.pure(TraversalFSM.stateless[IO](dag, waystation).transition(state, signal)).flatten

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
        case Right(TraversalEffect.DispatchNode(_, nid)) =>
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
        case Right(TraversalEffect.DispatchNode(_, nid)) =>
          expect.same(nid, nodeB) and
            expect(result.state.isCompleted(nodeA)) and
            expect(result.state.isStarted(nodeB))
        case other => failure(s"Expected DispatchNode for nodeB, got: $other")

  test("NodeSuccess on last node completes traversal"):
    for
      state   <- mkState(singleNodeDag)
      started <- runFsm(singleNodeDag, state, TraversalSignal.Begin(state.traversalId))
      result  <- runFsm(singleNodeDag, started.state, TraversalSignal.NodeSuccess(state.traversalId, nodeA))
    yield
      result.output match
        case Right(TraversalEffect.Complete(_)) =>
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
        case Right(TraversalEffect.DispatchNode(_, nid)) =>
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
        case Right(TraversalEffect.Fail(_)) =>
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
        case Right(TraversalEffect.DispatchNode(_, nid)) =>
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
        case Right(TraversalEffect.DispatchNode(_, nid)) =>
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
        case Right(TraversalEffect.Cancel(_)) =>
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
          case Right(TraversalEffect.Complete(_)) => success
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
          case Right(TraversalEffect.Complete(_)) => success
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
          case Right(TraversalEffect.Complete(_)) => success
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
        case Right(TraversalEffect.Fail(_)) => success
        case other                          => failure(s"Expected Fail, got: $other")

  test("Completed signal on incomplete traversal returns InvalidNodeState"):
    for
      state   <- mkState(linearDag)
      started <- runFsm(linearDag, state, TraversalSignal.Begin(state.traversalId))
      result  <- runFsm(linearDag, started.state, TraversalSignal.Completed(state.traversalId))
    yield
      result.output match
        case Left(_: InvalidNodeState) => success
        case other                     => failure(s"Expected InvalidNodeState, got: $other")
