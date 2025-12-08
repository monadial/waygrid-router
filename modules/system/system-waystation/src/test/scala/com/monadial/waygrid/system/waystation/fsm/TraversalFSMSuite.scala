package com.monadial.waygrid.system.waystation.fsm


import cats.effect.IO
import cats.implicits.*
import com.monadial.waygrid.common.application.util.circe.codecs.DomainRoutingDagCodecs.given
import com.monadial.waygrid.common.domain.model.resiliency.RetryPolicy
import com.monadial.waygrid.common.domain.model.routing.Value.*
import com.monadial.waygrid.common.domain.model.traversal.dag.Value.{DagHash, EdgeGuard, NodeId}
import com.monadial.waygrid.common.domain.model.routing.traversal.TraversalState
import com.monadial.waygrid.common.domain.model.routing.traversal.Value.TraversalInput.{Continue, Failure, Start, Success}
import com.monadial.waygrid.common.domain.model.traversal.dag.{Dag, Edge, Node}
import com.monadial.waygrid.common.domain.value.Address.{NodeAddress, ServiceAddress}
import io.circe.syntax.given
import org.http4s.Uri
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers

import scala.concurrent.duration.*

object TraversalFSMSuite extends SimpleIOSuite with Checkers:

  private val node1 = NodeId("A")
  private val node2 = NodeId("B")
  private val node3 = NodeId("C")
  private val node4 = NodeId("D")

  private val svc1 = ServiceAddress(Uri.unsafeFromString("waygrid://node1/service1"))
  private val svc2 = ServiceAddress(Uri.unsafeFromString("waygrid://node2/service2"))
  private val svc3 = ServiceAddress(Uri.unsafeFromString("waygrid://node3/service3"))
  private val svc4 = ServiceAddress(Uri.unsafeFromString("waygrid://node4/service4"))

  private val dag = Dag(
    hash = DagHash("test-dag"),
    entry = node1,
    repeatPolicy = RepeatPolicy.NoRepeat,
    nodes = Map(
      node1 -> Node(node1, None, RetryPolicy.Exponential(2.second, 2),DeliveryStrategy.Immediate, svc1),
      node2 -> Node(node2, None, RetryPolicy.None, DeliveryStrategy.ScheduleAfter(1.minute), svc2),
      node3 -> Node(node3, None, RetryPolicy.None, DeliveryStrategy.Immediate, svc3),
      node4 -> Node(node4, None, RetryPolicy.None, DeliveryStrategy.Immediate, svc4)
    ),
    edges = List(
      Edge(node1, node2, EdgeGuard.OnSuccess),
      Edge(node2, node3, EdgeGuard.OnSuccess),
      Edge(node1, node4, EdgeGuard.OnFailure)
    )
  )

  test("traversal"):
    for
      _ <- IO(println(dag.asJson.spaces2))
      traversalId <- TraversalId.next[IO]
      thisNodeAddress <- NodeAddress(Uri.unsafeFromString("waygrid://node1/service1?nodeId=1")).pure
      state <- TraversalState.initial(
        traversalId,
        dag,
        thisNodeAddress,
      ).pure
      res <- TraversalFSM.stateless[F].run(state, Start(traversalId, thisNodeAddress, dag))
      _ <- IO(println(res.output))

      suc <- TraversalFSM.stateless[F].run(res.state, Failure(traversalId, thisNodeAddress, dag))
      _ <- IO(println(suc.output))

      suc2 <- TraversalFSM.stateless[F].run(suc.state, Continue(traversalId, thisNodeAddress, dag))
      _ <- IO(println(suc2.output))

      suc3 <- TraversalFSM.stateless[F].run(suc2.state, Failure(traversalId, thisNodeAddress, dag))
      _ <- IO(println(suc3.output))

      suc4 <- TraversalFSM.stateless[F].run(suc3.state, Continue(traversalId, thisNodeAddress, dag))
      _ <- IO(println(suc4.output))

      suc5 <- TraversalFSM.stateless[F].run(suc4.state, Success(traversalId, thisNodeAddress, dag))
      _ <- IO(println(suc5.output))

      suc6 <- TraversalFSM.stateless[F].run(suc5.state, Success(traversalId, thisNodeAddress, dag))
      _ <- IO(println(suc6.output))

      suc7 <- TraversalFSM.stateless[F].run(suc6.state, Success(traversalId, thisNodeAddress, dag))
      _ <- IO(println(suc7.output))
      _ <- IO(println(suc7.state.history))
    yield expect(1 == 1)











