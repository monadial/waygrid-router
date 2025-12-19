package com.monadial.waygrid.common.domain.model.traversal.dag

import scala.concurrent.duration.*

import cats.Show
import cats.data.NonEmptyList
import com.monadial.waygrid.common.domain.model.resiliency.RetryPolicy
import com.monadial.waygrid.common.domain.model.routing.Value.{ DeliveryStrategy, RepeatPolicy }
import com.monadial.waygrid.common.domain.model.traversal.dag.Value.{ DagHash, EdgeGuard, NodeId }
import com.monadial.waygrid.common.domain.value.Address.ServiceAddress
import org.http4s.Uri
import org.scalacheck.Gen
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers

/**
 * Property-based tests for DAG structures and related value types.
 *
 * These tests verify invariants that should hold for all valid DAG configurations:
 * - Linear DAGs are always valid and have predictable structure
 * - Edge/node consistency: all edge endpoints exist in the node map
 * - Terminal nodes have no outgoing edges
 * - Value types preserve identity through construction
 */
object DagPropertySuite extends SimpleIOSuite with Checkers:

  // Show instances required by weaver-scalacheck
  given Show[Int]    = Show.fromToString
  given Show[Long]   = Show.fromToString
  given Show[String] = Show.fromToString

  // ---------------------------------------------------------------------------
  // DAG Factory
  // ---------------------------------------------------------------------------

  private def makeLinearDag(n: Int): Dag =
    val nodeIds = (1 to n).map(i => NodeId(s"node-$i")).toList
    val svc     = ServiceAddress(Uri.unsafeFromString("waygrid://processor/default"))

    val nodes = nodeIds.map { id =>
      id -> Node(id, Some(id.unwrap), RetryPolicy.None, DeliveryStrategy.Immediate, svc)
    }.toMap

    val edges = nodeIds.sliding(2).toList.flatMap {
      case List(from, to) => Some(Edge(from, to, EdgeGuard.OnSuccess))
      case _              => None
    }

    Dag(
      hash = DagHash(s"linear-dag-$n"),
      entryPoints = NonEmptyList.one(nodeIds.head),
      repeatPolicy = RepeatPolicy.NoRepeat,
      nodes = nodes,
      edges = edges
    )

  // ---------------------------------------------------------------------------
  // DAG Structure Properties
  // ---------------------------------------------------------------------------

  test("linear DAGs are always valid"):
      forall(Gen.choose(1, 5)) { n =>
        val dag = makeLinearDag(n)
        expect(dag.isValid)
      }

  test("linear DAGs have correct node count"):
      forall(Gen.choose(1, 5)) { n =>
        val dag = makeLinearDag(n)
        expect(dag.nodes.size == n)
      }

  test("linear DAGs have n-1 edges"):
      forall(Gen.choose(1, 5)) { n =>
        val dag = makeLinearDag(n)
        expect(dag.edges.size == math.max(0, n - 1))
      }

  test("linear DAGs are always linear"):
      forall(Gen.choose(1, 5)) { n =>
        val dag = makeLinearDag(n)
        expect(dag.isLinear)
      }

  test("entry node is always accessible"):
      forall(Gen.choose(1, 5)) { n =>
        val dag = makeLinearDag(n)
        expect(dag.entryNode.isDefined) &&
        expect(dag.isEntryPoint(dag.entry))
      }

  // ---------------------------------------------------------------------------
  // Edge Consistency Properties
  // ---------------------------------------------------------------------------

  test("all edge sources exist in nodes"):
      forall(Gen.choose(1, 5)) { n =>
        val dag             = makeLinearDag(n)
        val allSourcesExist = dag.edges.forall(e => dag.nodes.contains(e.from))
        expect(allSourcesExist)
      }

  test("all edge targets exist in nodes"):
      forall(Gen.choose(1, 5)) { n =>
        val dag             = makeLinearDag(n)
        val allTargetsExist = dag.edges.forall(e => dag.nodes.contains(e.to))
        expect(allTargetsExist)
      }

  test("terminal nodes have no outgoing edges"):
      forall(Gen.choose(1, 5)) { n =>
        val dag               = makeLinearDag(n)
        val terminalNodes     = dag.nodes.keys.filter(dag.isTerminalNode)
        val allTerminalsValid = terminalNodes.forall(node => dag.outgoingEdges(node).isEmpty)
        expect(allTerminalsValid)
      }

  // ---------------------------------------------------------------------------
  // Node Lookup Properties
  // ---------------------------------------------------------------------------

  test("nodeOf returns Some for all existing nodes"):
      forall(Gen.choose(1, 5)) { n =>
        val dag      = makeLinearDag(n)
        val allFound = dag.nodes.keys.forall(id => dag.nodeOf(id).isDefined)
        expect(allFound)
      }

  test("nodeOf returns None for non-existing nodes"):
      forall(Gen.choose(1, 5)) { n =>
        val dag         = makeLinearDag(n)
        val nonExistent = NodeId("definitely-not-in-dag-12345")
        expect(dag.nodeOf(nonExistent).isEmpty)
      }

  // ---------------------------------------------------------------------------
  // RetryPolicy Properties
  // ---------------------------------------------------------------------------

  test("RetryPolicy.Linear: maxRetries is always positive"):
      forall(Gen.choose(1, 100)) { maxRetries =>
        val policy                     = RetryPolicy.Linear(1.second, maxRetries)
        val RetryPolicy.Linear(_, max) = policy: @unchecked
        expect(max > 0)
      }

  test("RetryPolicy.Exponential: preserves parameters"):
      forall(Gen.choose(100, 10000)) { delayMs =>
        val baseDelay                          = delayMs.millis
        val maxRetries                         = 5
        val policy                             = RetryPolicy.Exponential(baseDelay, maxRetries)
        val RetryPolicy.Exponential(base, max) = policy: @unchecked
        expect(base == baseDelay) && expect(max == maxRetries)
      }

  // ---------------------------------------------------------------------------
  // NodeId Properties
  // ---------------------------------------------------------------------------

  test("NodeId: unwrap returns the underlying value"):
      forall(Gen.alphaNumStr.filter(_.nonEmpty).map(_.take(20))) { s =>
        val nodeId = NodeId(s)
        expect(nodeId.unwrap == s)
      }

  test("NodeId: equality is by value"):
      forall(Gen.alphaNumStr.filter(_.nonEmpty).map(_.take(20))) { s =>
        val id1 = NodeId(s)
        val id2 = NodeId(s)
        expect(id1 == id2)
      }

  // ---------------------------------------------------------------------------
  // Edge Properties
  // ---------------------------------------------------------------------------

  test("Edge: preserves from, to, and guard"):
      forall(Gen.choose(1, 100)) { seed =>
        val from = NodeId(s"from-$seed")
        val to   = NodeId(s"to-$seed")
        val edge = Edge(from, to, EdgeGuard.OnSuccess)
        expect(edge.from == from) &&
        expect(edge.to == to) &&
        expect(edge.guard == EdgeGuard.OnSuccess)
      }

  test("Edge: equality is structural"):
      forall(Gen.choose(1, 100)) { seed =>
        val from  = NodeId(s"from-$seed")
        val to    = NodeId(s"to-$seed")
        val edge1 = Edge(from, to, EdgeGuard.OnSuccess)
        val edge2 = Edge(from, to, EdgeGuard.OnSuccess)
        expect(edge1 == edge2)
      }

  // ---------------------------------------------------------------------------
  // DagHash Properties
  // ---------------------------------------------------------------------------

  test("DagHash: unwrap returns the underlying value"):
      forall(Gen.alphaNumStr.filter(_.nonEmpty).map(_.take(32))) { s =>
        val hash = DagHash(s)
        expect(hash.unwrap == s)
      }

  test("DagHash: equality is by value"):
      forall(Gen.alphaNumStr.filter(_.nonEmpty).map(_.take(32))) { s =>
        val h1 = DagHash(s)
        val h2 = DagHash(s)
        expect(h1 == h2)
      }
