//package com.monadial.waygrid.common.domain.model.routing.traversal
//
//import cats.syntax.all.*
//import com.monadial.waygrid.common.domain.model.routing.Value.*
//import com.monadial.waygrid.common.domain.model.routing.dag.*
//import com.monadial.waygrid.common.domain.model.routing.dag.Value.*
//import com.monadial.waygrid.common.domain.value.Address.{ NodeAddress, ServiceAddress }
//import org.http4s.Uri
//import weaver.SimpleIOSuite
//import weaver.scalacheck.Checkers
//
//object TraversalStateSuite extends SimpleIOSuite with Checkers:
//
//  private val node1 = NodeId("waygrid://node1/service1")
//  private val node2 = NodeId("waygrid://node2/service1")
//  private val node3 = NodeId("waygrid://node3/service2")
//
//  private val svc1 = ServiceAddress(Uri.unsafeFromString("waygrid://node1/service1"))
//  private val svc2 = ServiceAddress(Uri.unsafeFromString("waygrid://node2/service1"))
//  private val svc3 = ServiceAddress(Uri.unsafeFromString("waygrid://node3/service2"))
//
//  private val waystation = NodeAddress(Uri.unsafeFromString("waygrid://waystation/main"))
//
//  private val dag = Dag(
//    hash = DagHash("test-dag"),
//    entry = node1,
//    repeatPolicy = RepeatPolicy.NoRepeat,
//    nodes = Map(
//      node1 -> Node(node1, None, RetryPolicy.NoRetry, DeliveryStrategy.Immediate, svc1),
//      node2 -> Node(node2, None, RetryPolicy.NoRetry, DeliveryStrategy.Immediate, svc2),
//      node3 -> Node(node3, None, RetryPolicy.NoRetry, DeliveryStrategy.Immediate, svc3)
//    ),
//    edges = List(
//      Edge(node1, node2, EdgeGuard.OnSuccess),
//      Edge(node2, node3, EdgeGuard.OnSuccess),
//      Edge(node1, node3, EdgeGuard.OnFailure)
//    )
//  )
//
//  test("initial state is empty and consistent"):
//    for
//      id    <- TraversalId.next
//      state <- TraversalState.initial(id, dag, waystation).pure
//    yield expect.same(state.remainingCount, dag.nodes.size) and
//        expect(state.current.isEmpty) and
//        expect(state.completed.isEmpty) and
//        expect(state.failed.isEmpty) and
//        expect(state.verifyRemainingConsistency(dag))
//
//  test("start marks node as current and ticks vector clock"):
//    for
//      id   <- TraversalId.next
//      base <- TraversalState.initial(id, dag, waystation).pure
//      next <- base.start(node1, waystation).pure
//    yield expect(next.current.contains(node1)) and
//        expect(next.vectorClock.isAfter(base.vectorClock)) and
//        expect.same(next.history.last.node, node1)
//
//  test("markSuccess decrements remainingCount and adds to completed"):
//    for
//      id   <- TraversalId.next
//      base <- TraversalState.initial(id, dag, waystation).pure
//      s1   <- base.start(node1, waystation).pure
//      s2   <- s1.markSuccess(node1, waystation).pure
//    yield expect(s2.completed.contains(node1)) and
//        expect(!s2.current.contains(node1)) and
//        expect(s2.remainingCount < base.remainingCount) and
//        expect(s2.verifyRemainingConsistency(dag))
//
//  test("markFailure adds node to failed, decreases remainingCount, and records reason"):
//    for
//      id   <- TraversalId.next
//      base <- TraversalState.initial(id, dag, waystation).pure
//      s1   <- base.start(node1, waystation).pure
//      s2   <- s1.markFailure(node1, waystation, Some("boom")).pure
//      last = s2.history.last
//    yield expect(s2.failed.contains(node1)) and
//        expect(!s2.current.contains(node1)) and
//        expect(s2.remainingCount < base.remainingCount) and
//        expect(last.isInstanceOf[TraversalEvent.Failed]) and
//        expect.same(last.asInstanceOf[TraversalEvent.Failed].reason, Some("boom"))
//
//  test("markRetry increments retry count and appends retry event"):
//    for
//      id   <- TraversalId.next
//      base <- TraversalState.initial(id, dag, waystation).pure
//      s1   <- base.start(node1, waystation).pure
//      s2   <- s1.markRetry(node1, waystation, attempt = 2).pure
//    yield expect.same(s2.retryCount(node1), 2) and
//        expect(s2.history.last.isInstanceOf[TraversalEvent.Retried])
//
//  test("nextNodes(OnSuccess) follows DAG edges"):
//    for
//      id   <- TraversalId.next
//      base <- TraversalState.initial(id, dag, waystation).pure
//      s1   <- base.start(node1, waystation).markSuccess(node1, waystation).pure
//      next <- s1.nextNodes(EdgeGuard.OnSuccess, dag).pure
//    yield expect(next.contains(node2)) and expect.same(next.size, 1)
//
//  test("nextNodes(OnFailure) follows DAG failure edges"):
//    for
//      id   <- TraversalId.next
//      base <- TraversalState.initial(id, dag, waystation).pure
//      s1   <- base.start(node1, waystation).markFailure(node1, waystation, Some("fail")).pure
//      next <- s1.nextNodes(EdgeGuard.OnFailure, dag).pure
//    yield expect(next.contains(node3)) and expect.same(next.size, 1)
//
//  test("isTraversalComplete becomes true when all nodes done"):
//    for
//      id   <- TraversalId.next
//      base <- TraversalState.initial(id, dag, waystation).pure
//      s1   <- base.start(node1, waystation).markSuccess(node1, waystation).pure
//      s2   <- s1.start(node2, waystation).markSuccess(node2, waystation).pure
//      s3   <- s2.start(node3, waystation).markSuccess(node3, waystation).pure
//    yield expect(s3.isTraversalComplete)
//
//  test("verifyRemainingConsistency returns false if manually corrupted"):
//    for
//      id   <- TraversalId.next
//      base <- TraversalState.initial(id, dag, waystation).pure
//      bad  <- base.copy(remainingCount = 999).pure
//    yield expect(!bad.verifyRemainingConsistency(dag))
//
//  test("mergeClock keeps highest version per node"):
//    for
//      id     <- TraversalId.next
//      base   <- TraversalState.initial(id, dag, waystation).pure
//      vc2    <- base.vectorClock.tick(waystation).pure
//      merged <- base.mergeClock(vc2).pure
//    yield expect(merged.vectorClock.entries(waystation) >= base.vectorClock.entries(waystation))
//
//  test("isFinished returns true for completed or failed nodes"):
//    for
//      id   <- TraversalId.next
//      base <- TraversalState.initial(id, dag, waystation).pure
//      s1   <- base.start(node1, waystation).markSuccess(node1, waystation).pure
//    yield expect(s1.isFinished(node1))
//
//  test("hasActiveWork and hasProgress reflect state correctly"):
//    for
//      id   <- TraversalId.next
//      base <- TraversalState.initial(id, dag, waystation).pure
//      s1   <- base.start(node1, waystation).pure
//      s2   <- s1.markSuccess(node1, waystation).pure
//    yield expect(s1.hasActiveWork) and expect(!s2.hasActiveWork) and
//        expect(s2.hasProgress)
