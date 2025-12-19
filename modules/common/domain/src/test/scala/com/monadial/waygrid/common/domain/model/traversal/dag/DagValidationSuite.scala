package com.monadial.waygrid.common.domain.model.traversal.dag

import scala.concurrent.duration.*

import cats.data.NonEmptyList
import cats.effect.IO
import com.monadial.waygrid.common.domain.model.resiliency.RetryPolicy
import com.monadial.waygrid.common.domain.model.routing.Value.{ DeliveryStrategy, RepeatPolicy }
import com.monadial.waygrid.common.domain.model.traversal.dag.Value.{ DagHash, EdgeGuard, ForkId, NodeId }
import com.monadial.waygrid.common.domain.value.Address.ServiceAddress
import org.http4s.Uri
import weaver.SimpleIOSuite

object DagValidationSuite extends SimpleIOSuite:

  // Common test fixtures
  private val svcA = ServiceAddress(Uri.unsafeFromString("waygrid://processor/a"))
  private val svcB = ServiceAddress(Uri.unsafeFromString("waygrid://processor/b"))
  private val svcC = ServiceAddress(Uri.unsafeFromString("waygrid://processor/c"))
  private val svcD = ServiceAddress(Uri.unsafeFromString("waygrid://processor/d"))

  private val nodeA = NodeId("A")
  private val nodeB = NodeId("B")
  private val nodeC = NodeId("C")
  private val nodeD = NodeId("D")

  // ---------------------------------------------------------------------------
  // isLinear Tests
  // ---------------------------------------------------------------------------

  test("isLinear returns true for simple linear DAG"):
      val linearDag = Dag(
        hash = DagHash("linear-dag"),
        entryPoints = NonEmptyList.one(nodeA),
        repeatPolicy = RepeatPolicy.NoRepeat,
        nodes = Map(
          nodeA -> Node(nodeA, Some("A"), RetryPolicy.None, DeliveryStrategy.Immediate, svcA),
          nodeB -> Node(nodeB, Some("B"), RetryPolicy.None, DeliveryStrategy.Immediate, svcB),
          nodeC -> Node(nodeC, Some("C"), RetryPolicy.None, DeliveryStrategy.Immediate, svcC)
        ),
        edges = List(
          Edge(nodeA, nodeB, EdgeGuard.OnSuccess),
          Edge(nodeB, nodeC, EdgeGuard.OnSuccess)
        )
      )

      IO.pure {
        expect(linearDag.isLinear) &&
        expect(linearDag.isValid)
      }

  test("isLinear returns false for DAG with fork"):
      val forkId   = ForkId.unsafeFrom("fork-linear")
      val forkNode = NodeId("F")
      val joinNode = NodeId("J")

      val forkDag = Dag(
        hash = DagHash("fork-dag"),
        entryPoints = NonEmptyList.one(nodeA),
        repeatPolicy = RepeatPolicy.NoRepeat,
        nodes = Map(
          nodeA -> Node(nodeA, Some("A"), RetryPolicy.None, DeliveryStrategy.Immediate, svcA),
          forkNode -> Node(
            forkNode,
            Some("Fork"),
            RetryPolicy.None,
            DeliveryStrategy.Immediate,
            svcA,
            NodeType.Fork(forkId)
          ),
          nodeB -> Node(nodeB, Some("B"), RetryPolicy.None, DeliveryStrategy.Immediate, svcB),
          nodeC -> Node(nodeC, Some("C"), RetryPolicy.None, DeliveryStrategy.Immediate, svcC),
          joinNode -> Node(
            joinNode,
            Some("Join"),
            RetryPolicy.None,
            DeliveryStrategy.Immediate,
            svcA,
            NodeType.Join(forkId, JoinStrategy.And, None)
          ),
          nodeD -> Node(nodeD, Some("D"), RetryPolicy.None, DeliveryStrategy.Immediate, svcD)
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

      IO.pure {
        expect(!forkDag.isLinear)
      }

  test("isLinear returns false for DAG with join"):
      val forkId   = ForkId.unsafeFrom("fork-join-only")
      val joinNode = NodeId("J")

      // Just a join without fork (for testing isLinear detection)
      val joinOnlyDag = Dag(
        hash = DagHash("join-only-dag"),
        entryPoints = NonEmptyList.one(nodeA),
        repeatPolicy = RepeatPolicy.NoRepeat,
        nodes = Map(
          nodeA -> Node(nodeA, Some("A"), RetryPolicy.None, DeliveryStrategy.Immediate, svcA),
          joinNode -> Node(
            joinNode,
            Some("Join"),
            RetryPolicy.None,
            DeliveryStrategy.Immediate,
            svcA,
            NodeType.Join(forkId, JoinStrategy.And, None)
          )
        ),
        edges = List(
          Edge(nodeA, joinNode, EdgeGuard.OnSuccess)
        )
      )

      IO.pure {
        expect(!joinOnlyDag.isLinear)
      }

  test("isLinear returns false for DAG with fan-out (multiple successors)"):
      // A -> B and A -> C (fan-out without fork)
      val fanOutDag = Dag(
        hash = DagHash("fan-out-dag"),
        entryPoints = NonEmptyList.one(nodeA),
        repeatPolicy = RepeatPolicy.NoRepeat,
        nodes = Map(
          nodeA -> Node(nodeA, Some("A"), RetryPolicy.None, DeliveryStrategy.Immediate, svcA),
          nodeB -> Node(nodeB, Some("B"), RetryPolicy.None, DeliveryStrategy.Immediate, svcB),
          nodeC -> Node(nodeC, Some("C"), RetryPolicy.None, DeliveryStrategy.Immediate, svcC)
        ),
        edges = List(
          Edge(nodeA, nodeB, EdgeGuard.OnSuccess),
          Edge(nodeA, nodeC, EdgeGuard.OnSuccess) // Same guard, same from = fan-out
        )
      )

      IO.pure {
        expect(!fanOutDag.isLinear)
      }

  test("isLinear returns false for DAG with conditional edge"):
      import com.monadial.waygrid.common.domain.model.traversal.condition.Condition

      val condDag = Dag(
        hash = DagHash("cond-dag"),
        entryPoints = NonEmptyList.one(nodeA),
        repeatPolicy = RepeatPolicy.NoRepeat,
        nodes = Map(
          nodeA -> Node(nodeA, Some("A"), RetryPolicy.None, DeliveryStrategy.Immediate, svcA),
          nodeB -> Node(nodeB, Some("B"), RetryPolicy.None, DeliveryStrategy.Immediate, svcB)
        ),
        edges = List(
          Edge(nodeA, nodeB, EdgeGuard.Conditional(Condition.Always))
        )
      )

      IO.pure {
        expect(!condDag.isLinear)
      }

  test("isLinear returns true for single node DAG"):
      val singleNodeDag = Dag(
        hash = DagHash("single-node-dag"),
        entryPoints = NonEmptyList.one(nodeA),
        repeatPolicy = RepeatPolicy.NoRepeat,
        nodes = Map(
          nodeA -> Node(nodeA, Some("A"), RetryPolicy.None, DeliveryStrategy.Immediate, svcA)
        ),
        edges = List.empty
      )

      IO.pure {
        expect(singleNodeDag.isLinear) &&
        expect(singleNodeDag.isValid)
      }

  // ---------------------------------------------------------------------------
  // Edge Validation Error Tests
  // ---------------------------------------------------------------------------

  test("validate detects edge target not found"):
      val nonExistentTarget = NodeId("NonExistent")

      val invalidDag = Dag(
        hash = DagHash("invalid-target-dag"),
        entryPoints = NonEmptyList.one(nodeA),
        repeatPolicy = RepeatPolicy.NoRepeat,
        nodes = Map(
          nodeA -> Node(nodeA, Some("A"), RetryPolicy.None, DeliveryStrategy.Immediate, svcA)
        ),
        edges = List(
          Edge(nodeA, nonExistentTarget, EdgeGuard.OnSuccess) // Target doesn't exist
        )
      )

      IO.pure {
        val errors = invalidDag.validate
        val hasTargetError = errors.exists {
          case DagValidationError.EdgeTargetNotFound(from, to, _) =>
            from == nodeA && to == nonExistentTarget
          case _ => false
        }
        expect(hasTargetError) && expect(!invalidDag.isValid)
      }

  test("validate detects edge source not found"):
      val nonExistentSource = NodeId("NonExistent")

      val invalidDag = Dag(
        hash = DagHash("invalid-source-dag"),
        entryPoints = NonEmptyList.one(nodeA),
        repeatPolicy = RepeatPolicy.NoRepeat,
        nodes = Map(
          nodeA -> Node(nodeA, Some("A"), RetryPolicy.None, DeliveryStrategy.Immediate, svcA),
          nodeB -> Node(nodeB, Some("B"), RetryPolicy.None, DeliveryStrategy.Immediate, svcB)
        ),
        edges = List(
          Edge(nodeA, nodeB, EdgeGuard.OnSuccess),
          Edge(nonExistentSource, nodeB, EdgeGuard.OnSuccess) // Source doesn't exist
        )
      )

      IO.pure {
        val errors = invalidDag.validate
        val hasSourceError = errors.exists {
          case DagValidationError.EdgeSourceNotFound(from, to, _) =>
            from == nonExistentSource && to == nodeB
          case _ => false
        }
        expect(hasSourceError) && expect(!invalidDag.isValid)
      }

  test("validate detects join without matching fork"):
      val orphanForkId = ForkId.unsafeFrom("orphan-fork-1")
      val joinNode     = NodeId("J")

      val invalidDag = Dag(
        hash = DagHash("orphan-join-dag"),
        entryPoints = NonEmptyList.one(nodeA),
        repeatPolicy = RepeatPolicy.NoRepeat,
        nodes = Map(
          nodeA -> Node(nodeA, Some("A"), RetryPolicy.None, DeliveryStrategy.Immediate, svcA),
          // Join references a forkId that doesn't exist
          joinNode -> Node(
            joinNode,
            Some("Join"),
            RetryPolicy.None,
            DeliveryStrategy.Immediate,
            svcA,
            NodeType.Join(orphanForkId, JoinStrategy.And, None)
          )
        ),
        edges = List(
          Edge(nodeA, joinNode, EdgeGuard.OnSuccess)
        )
      )

      IO.pure {
        val errors = invalidDag.validate
        val hasJoinError = errors.exists {
          case DagValidationError.JoinWithoutMatchingFork(fid, nid) =>
            fid == orphanForkId && nid == joinNode
          case _ => false
        }
        expect(hasJoinError) && expect(!invalidDag.isValid)
      }

  test("validate collects multiple errors at once"):
      val orphanForkId      = ForkId.unsafeFrom("orphan-fork-2")
      val nonExistentTarget = NodeId("NonExistent")
      val joinNode          = NodeId("J")

      val invalidDag = Dag(
        hash = DagHash("multi-error-dag"),
        entryPoints = NonEmptyList.one(nodeA),
        repeatPolicy = RepeatPolicy.NoRepeat,
        nodes = Map(
          nodeA -> Node(nodeA, Some("A"), RetryPolicy.None, DeliveryStrategy.Immediate, svcA),
          joinNode -> Node(
            joinNode,
            Some("Join"),
            RetryPolicy.None,
            DeliveryStrategy.Immediate,
            svcA,
            NodeType.Join(orphanForkId, JoinStrategy.And, None)
          )
        ),
        edges = List(
          Edge(nodeA, nonExistentTarget, EdgeGuard.OnSuccess), // EdgeTargetNotFound
          Edge(nodeA, joinNode, EdgeGuard.OnSuccess)
        )
      )

      IO.pure {
        val errors = invalidDag.validate
        // Should have at least 2 errors
        expect(errors.size >= 2) && expect(!invalidDag.isValid)
      }

  // ---------------------------------------------------------------------------
  // Helper Method Tests
  // ---------------------------------------------------------------------------

  test("nodeOf returns correct node for existing ID"):
      val testDag = Dag(
        hash = DagHash("nodeOf-dag"),
        entryPoints = NonEmptyList.one(nodeA),
        repeatPolicy = RepeatPolicy.NoRepeat,
        nodes = Map(
          nodeA -> Node(nodeA, Some("A"), RetryPolicy.None, DeliveryStrategy.Immediate, svcA),
          nodeB -> Node(nodeB, Some("B"), RetryPolicy.None, DeliveryStrategy.Immediate, svcB)
        ),
        edges = List(Edge(nodeA, nodeB, EdgeGuard.OnSuccess))
      )

      IO.pure {
        val nodeOpt = testDag.nodeOf(nodeA)
        expect(nodeOpt.isDefined) &&
        expect(nodeOpt.get.id == nodeA) &&
        expect(nodeOpt.get.label.contains("A"))
      }

  test("nodeOf returns None for non-existing ID"):
      val testDag = Dag(
        hash = DagHash("nodeOf-missing-dag"),
        entryPoints = NonEmptyList.one(nodeA),
        repeatPolicy = RepeatPolicy.NoRepeat,
        nodes = Map(
          nodeA -> Node(nodeA, Some("A"), RetryPolicy.None, DeliveryStrategy.Immediate, svcA)
        ),
        edges = List.empty
      )

      IO.pure {
        expect(testDag.nodeOf(nodeB).isEmpty)
      }

  test("outgoingEdges returns all edges from a node"):
      val testDag = Dag(
        hash = DagHash("outgoing-dag"),
        entryPoints = NonEmptyList.one(nodeA),
        repeatPolicy = RepeatPolicy.NoRepeat,
        nodes = Map(
          nodeA -> Node(nodeA, Some("A"), RetryPolicy.None, DeliveryStrategy.Immediate, svcA),
          nodeB -> Node(nodeB, Some("B"), RetryPolicy.None, DeliveryStrategy.Immediate, svcB),
          nodeC -> Node(nodeC, Some("C"), RetryPolicy.None, DeliveryStrategy.Immediate, svcC)
        ),
        edges = List(
          Edge(nodeA, nodeB, EdgeGuard.OnSuccess),
          Edge(nodeA, nodeC, EdgeGuard.OnFailure),
          Edge(nodeB, nodeC, EdgeGuard.OnSuccess)
        )
      )

      IO.pure {
        val outgoing = testDag.outgoingEdges(nodeA)
        expect(outgoing.size == 2) &&
        expect(outgoing.exists(_.to == nodeB)) &&
        expect(outgoing.exists(_.to == nodeC))
      }

  test("outgoingEdges with guard filters correctly"):
      val testDag = Dag(
        hash = DagHash("outgoing-filter-dag"),
        entryPoints = NonEmptyList.one(nodeA),
        repeatPolicy = RepeatPolicy.NoRepeat,
        nodes = Map(
          nodeA -> Node(nodeA, Some("A"), RetryPolicy.None, DeliveryStrategy.Immediate, svcA),
          nodeB -> Node(nodeB, Some("B"), RetryPolicy.None, DeliveryStrategy.Immediate, svcB),
          nodeC -> Node(nodeC, Some("C"), RetryPolicy.None, DeliveryStrategy.Immediate, svcC)
        ),
        edges = List(
          Edge(nodeA, nodeB, EdgeGuard.OnSuccess),
          Edge(nodeA, nodeC, EdgeGuard.OnFailure)
        )
      )

      IO.pure {
        val successEdges = testDag.outgoingEdges(nodeA, EdgeGuard.OnSuccess)
        val failureEdges = testDag.outgoingEdges(nodeA, EdgeGuard.OnFailure)
        expect(successEdges.size == 1) &&
        expect(successEdges.head.to == nodeB) &&
        expect(failureEdges.size == 1) &&
        expect(failureEdges.head.to == nodeC)
      }

  test("nextNodeIds returns correct node IDs"):
      val testDag = Dag(
        hash = DagHash("nextNodeIds-dag"),
        entryPoints = NonEmptyList.one(nodeA),
        repeatPolicy = RepeatPolicy.NoRepeat,
        nodes = Map(
          nodeA -> Node(nodeA, Some("A"), RetryPolicy.None, DeliveryStrategy.Immediate, svcA),
          nodeB -> Node(nodeB, Some("B"), RetryPolicy.None, DeliveryStrategy.Immediate, svcB),
          nodeC -> Node(nodeC, Some("C"), RetryPolicy.None, DeliveryStrategy.Immediate, svcC)
        ),
        edges = List(
          Edge(nodeA, nodeB, EdgeGuard.OnSuccess),
          Edge(nodeA, nodeC, EdgeGuard.OnSuccess)
        )
      )

      IO.pure {
        val nextIds = testDag.nextNodeIds(nodeA, EdgeGuard.OnSuccess)
        expect(nextIds.size == 2) &&
        expect(nextIds.contains(nodeB)) &&
        expect(nextIds.contains(nodeC))
      }

  test("nextNodes returns correct nodes"):
      val testDag = Dag(
        hash = DagHash("nextNodes-dag"),
        entryPoints = NonEmptyList.one(nodeA),
        repeatPolicy = RepeatPolicy.NoRepeat,
        nodes = Map(
          nodeA -> Node(nodeA, Some("A"), RetryPolicy.None, DeliveryStrategy.Immediate, svcA),
          nodeB -> Node(nodeB, Some("B"), RetryPolicy.None, DeliveryStrategy.Immediate, svcB)
        ),
        edges = List(Edge(nodeA, nodeB, EdgeGuard.OnSuccess))
      )

      IO.pure {
        val nextList = testDag.nextNodes(nodeA, EdgeGuard.OnSuccess)
        expect(nextList.size == 1) &&
        expect(nextList.head.id == nodeB)
      }

  test("isTerminalNode returns true for nodes with no outgoing edges"):
      val testDag = Dag(
        hash = DagHash("terminal-dag"),
        entryPoints = NonEmptyList.one(nodeA),
        repeatPolicy = RepeatPolicy.NoRepeat,
        nodes = Map(
          nodeA -> Node(nodeA, Some("A"), RetryPolicy.None, DeliveryStrategy.Immediate, svcA),
          nodeB -> Node(nodeB, Some("B"), RetryPolicy.None, DeliveryStrategy.Immediate, svcB)
        ),
        edges = List(Edge(nodeA, nodeB, EdgeGuard.OnSuccess))
      )

      IO.pure {
        expect(!testDag.isTerminalNode(nodeA)) && // A has outgoing edge
        expect(testDag.isTerminalNode(nodeB))     // B has no outgoing edges
      }

  test("entryNode returns first entry point node"):
      val testDag = Dag(
        hash = DagHash("entryNode-dag"),
        entryPoints = NonEmptyList.one(nodeA),
        repeatPolicy = RepeatPolicy.NoRepeat,
        nodes = Map(
          nodeA -> Node(nodeA, Some("A"), RetryPolicy.None, DeliveryStrategy.Immediate, svcA)
        ),
        edges = List.empty
      )

      IO.pure {
        val entry = testDag.entryNode
        expect(entry.isDefined) &&
        expect(entry.get.id == nodeA)
      }

  test("isEntryPoint correctly identifies entry points"):
      val testDag = Dag(
        hash = DagHash("isEntryPoint-dag"),
        entryPoints = NonEmptyList.of(nodeA, nodeB),
        repeatPolicy = RepeatPolicy.NoRepeat,
        nodes = Map(
          nodeA -> Node(nodeA, Some("A"), RetryPolicy.None, DeliveryStrategy.Immediate, svcA),
          nodeB -> Node(nodeB, Some("B"), RetryPolicy.None, DeliveryStrategy.Immediate, svcB),
          nodeC -> Node(nodeC, Some("C"), RetryPolicy.None, DeliveryStrategy.Immediate, svcC)
        ),
        edges = List(
          Edge(nodeA, nodeC, EdgeGuard.OnSuccess),
          Edge(nodeB, nodeC, EdgeGuard.OnSuccess)
        )
      )

      IO.pure {
        expect(testDag.isEntryPoint(nodeA)) &&
        expect(testDag.isEntryPoint(nodeB)) &&
        expect(!testDag.isEntryPoint(nodeC))
      }

  test("joinNodeIdFor returns correct join for fork"):
      val forkId   = ForkId.unsafeFrom("query-fork")
      val forkNode = NodeId("F")
      val joinNode = NodeId("J")

      val testDag = Dag(
        hash = DagHash("joinFor-dag"),
        entryPoints = NonEmptyList.one(nodeA),
        repeatPolicy = RepeatPolicy.NoRepeat,
        nodes = Map(
          nodeA -> Node(nodeA, Some("A"), RetryPolicy.None, DeliveryStrategy.Immediate, svcA),
          forkNode -> Node(
            forkNode,
            Some("Fork"),
            RetryPolicy.None,
            DeliveryStrategy.Immediate,
            svcA,
            NodeType.Fork(forkId)
          ),
          nodeB -> Node(nodeB, Some("B"), RetryPolicy.None, DeliveryStrategy.Immediate, svcB),
          nodeC -> Node(nodeC, Some("C"), RetryPolicy.None, DeliveryStrategy.Immediate, svcC),
          joinNode -> Node(
            joinNode,
            Some("Join"),
            RetryPolicy.None,
            DeliveryStrategy.Immediate,
            svcA,
            NodeType.Join(forkId, JoinStrategy.And, None)
          )
        ),
        edges = List(
          Edge(nodeA, forkNode, EdgeGuard.OnSuccess),
          Edge(forkNode, nodeB, EdgeGuard.OnSuccess),
          Edge(forkNode, nodeC, EdgeGuard.OnSuccess),
          Edge(nodeB, joinNode, EdgeGuard.OnSuccess),
          Edge(nodeC, joinNode, EdgeGuard.OnSuccess)
        )
      )

      IO.pure {
        val foundJoin = testDag.joinNodeIdFor(forkId)
        expect(foundJoin.isDefined) &&
        expect(foundJoin.contains(joinNode))
      }

  test("joinNodeIdFor returns None for unknown fork"):
      val unknownForkId = ForkId.unsafeFrom("unknown-fork")

      val testDag = Dag(
        hash = DagHash("joinFor-missing-dag"),
        entryPoints = NonEmptyList.one(nodeA),
        repeatPolicy = RepeatPolicy.NoRepeat,
        nodes = Map(
          nodeA -> Node(nodeA, Some("A"), RetryPolicy.None, DeliveryStrategy.Immediate, svcA)
        ),
        edges = List.empty
      )

      IO.pure {
        expect(testDag.joinNodeIdFor(unknownForkId).isEmpty)
      }

  // ---------------------------------------------------------------------------
  // DAG Timeout Tests
  // ---------------------------------------------------------------------------

  test("DAG with timeout preserves timeout value"):
      val timeoutDag = Dag(
        hash = DagHash("timeout-dag"),
        entryPoints = NonEmptyList.one(nodeA),
        repeatPolicy = RepeatPolicy.NoRepeat,
        nodes = Map(
          nodeA -> Node(nodeA, Some("A"), RetryPolicy.None, DeliveryStrategy.Immediate, svcA)
        ),
        edges = List.empty,
        timeout = Some(30.seconds)
      )

      IO.pure {
        expect(timeoutDag.timeout.isDefined) &&
        expect(timeoutDag.timeout.contains(30.seconds))
      }

  test("DAG without timeout has None"):
      val noTimeoutDag = Dag(
        hash = DagHash("no-timeout-dag"),
        entryPoints = NonEmptyList.one(nodeA),
        repeatPolicy = RepeatPolicy.NoRepeat,
        nodes = Map(
          nodeA -> Node(nodeA, Some("A"), RetryPolicy.None, DeliveryStrategy.Immediate, svcA)
        ),
        edges = List.empty
      )

      IO.pure {
        expect(noTimeoutDag.timeout.isEmpty)
      }
