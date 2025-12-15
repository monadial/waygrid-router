package com.monadial.waygrid.common.domain.algebra

import cats.effect.IO
import com.monadial.waygrid.common.domain.model.resiliency.RetryPolicy
import com.monadial.waygrid.common.domain.model.routing.Value.{ DeliveryStrategy, RepeatPolicy, RouteSalt }
import com.monadial.waygrid.common.domain.model.traversal.condition.Condition
import com.monadial.waygrid.common.domain.model.traversal.dag.NodeType
import com.monadial.waygrid.common.domain.model.traversal.dag.Value.EdgeGuard
import com.monadial.waygrid.common.domain.model.traversal.spec.{ Node, Spec }
import com.monadial.waygrid.common.domain.value.Address.ServiceAddress
import io.circe.Json
import org.http4s.Uri
import weaver.SimpleIOSuite

object DagCompilerSuite extends SimpleIOSuite:

  private val svcEntry1 = ServiceAddress(Uri.unsafeFromString("waygrid://origin/http"))
  private val svcEntry2 = ServiceAddress(Uri.unsafeFromString("waygrid://origin/kafka"))
  private val svcFork   = ServiceAddress(Uri.unsafeFromString("waygrid://processor/fork"))
  private val svcB      = ServiceAddress(Uri.unsafeFromString("waygrid://processor/b"))
  private val svcC      = ServiceAddress(Uri.unsafeFromString("waygrid://processor/c"))
  private val svcJoin   = ServiceAddress(Uri.unsafeFromString("waygrid://processor/join"))
  private val svcAfter  = ServiceAddress(Uri.unsafeFromString("waygrid://destination/out"))

  test("DagCompiler compiles multi-entry fork/join spec with matching forkId"):
    val joinNodeId = "join-1"

    val after = Node.Standard(
      address = svcAfter,
      retryPolicy = RetryPolicy.None,
      deliveryStrategy = DeliveryStrategy.Immediate,
      onSuccess = None,
      onFailure = None,
      label = Some("after")
    )

    val join = Node.Join(
      address = svcJoin,
      retryPolicy = RetryPolicy.None,
      deliveryStrategy = DeliveryStrategy.Immediate,
      joinNodeId = joinNodeId,
      strategy = com.monadial.waygrid.common.domain.model.traversal.dag.JoinStrategy.And,
      timeout = None,
      onSuccess = Some(after),
      onFailure = None,
      onTimeout = None,
      label = Some("join")
    )

    val b = Node.Standard(
      address = svcB,
      retryPolicy = RetryPolicy.None,
      deliveryStrategy = DeliveryStrategy.Immediate,
      onSuccess = Some(join),
      onFailure = None,
      label = Some("b")
    )

    val c = Node.Standard(
      address = svcC,
      retryPolicy = RetryPolicy.None,
      deliveryStrategy = DeliveryStrategy.Immediate,
      onSuccess = Some(join),
      onFailure = None,
      label = Some("c")
    )

    val fork = Node.Fork(
      address = svcFork,
      retryPolicy = RetryPolicy.None,
      deliveryStrategy = DeliveryStrategy.Immediate,
      branches = Map("b" -> b, "c" -> c),
      joinNodeId = joinNodeId,
      label = Some("fork")
    )

    val entry1 = Node.Standard(
      address = svcEntry1,
      retryPolicy = RetryPolicy.None,
      deliveryStrategy = DeliveryStrategy.Immediate,
      onSuccess = Some(fork),
      onFailure = None,
      label = Some("entry1")
    )

    val entry2 = Node.Standard(
      address = svcEntry2,
      retryPolicy = RetryPolicy.None,
      deliveryStrategy = DeliveryStrategy.Immediate,
      onSuccess = Some(join),
      onFailure = None,
      label = Some("entry2")
    )

    val spec = Spec.multiple(entry1, entry2)(RepeatPolicy.NoRepeat)

    DagCompiler.default[IO].use { compiler =>
      compiler.compile(spec, RouteSalt("test-salt")).map { dag =>
        val forkNode = dag.nodes.values.find(_.address == svcFork)
        val joinNode = dag.nodes.values.find(_.address == svcJoin)
        val entryNodes = dag.nodes.values.filter(n => n.address == svcEntry1 || n.address == svcEntry2).map(_.id).toList

        (forkNode, joinNode) match
          case (Some(f), Some(j)) =>
            val forkId = f.nodeType match
              case NodeType.Fork(fid) => Some(fid)
              case _                  => None
            val joinForkId = j.nodeType match
              case NodeType.Join(fid, _, _) => Some(fid)
              case _                        => None

            expect.same(forkId, joinForkId) &&
              expect(entryNodes.forall(dag.entryPoints.toList.contains))
          case other =>
            failure(s"Expected both fork and join nodes, got: $other")
      }
    }

  test("DagCompiler preserves entry point order"):
    val entry1 = Node.standard(svcEntry1, label = Some("entry1"))
    val entry2 = Node.standard(svcEntry2, label = Some("entry2"))
    val spec   = Spec.multiple(entry1, entry2)(RepeatPolicy.NoRepeat)

    DagCompiler.default[IO].use { compiler =>
      compiler.compile(spec, RouteSalt("entry-order")).map { dag =>
        val entryAddresses =
          dag.entryPoints.toList.flatMap(id => dag.nodes.get(id).map(_.address))
        expect.same(entryAddresses, List(svcEntry1, svcEntry2))
      }
    }

  test("DagCompiler emits Conditional edges from Spec.Standard.onConditions"):
    val svcEntry  = ServiceAddress(Uri.unsafeFromString("waygrid://origin/entry"))
    val svcRoute  = ServiceAddress(Uri.unsafeFromString("waygrid://processor/router"))
    val svcYes    = ServiceAddress(Uri.unsafeFromString("waygrid://processor/yes"))
    val svcNo     = ServiceAddress(Uri.unsafeFromString("waygrid://processor/no"))
    val svcAfter  = ServiceAddress(Uri.unsafeFromString("waygrid://destination/after"))

    val after = Node.standard(svcAfter)
    val yes   = Node.standard(svcYes, onSuccess = Some(after))
    val no    = Node.standard(svcNo, onSuccess = Some(after))

    val router = Node.Standard(
      address = svcRoute,
      retryPolicy = RetryPolicy.None,
      deliveryStrategy = DeliveryStrategy.Immediate,
      onSuccess = Some(no),
      onFailure = None,
      onConditions = List(
        Node.when(Condition.JsonEquals("/approved", Json.fromBoolean(true)), yes)
      ),
      label = Some("router")
    )

    val entry = Node.standard(svcEntry, onSuccess = Some(router))
    val spec  = Spec.single(entry, RepeatPolicy.NoRepeat)

    DagCompiler.default[IO].use { compiler =>
      compiler.compile(spec, RouteSalt("cond-salt")).map { dag =>
        val routerId = dag.nodes.values.find(_.address == svcRoute).map(_.id)
        val yesId    = dag.nodes.values.find(_.address == svcYes).map(_.id)
        val condEdge =
          (routerId, yesId) match
            case (Some(r), Some(y)) =>
              dag.edges.exists(e => e.from == r && e.to == y && (e.guard match
                case EdgeGuard.Conditional(_) => true
                case _                        => false
              ))
            case _ => false

        expect(condEdge)
      }
    }

  test("DagCompiler is deterministic for fork branch map ordering (same salt)"):
    val joinNodeId = "join-1"

    val join = Node.Join(
      address = svcJoin,
      retryPolicy = RetryPolicy.None,
      deliveryStrategy = DeliveryStrategy.Immediate,
      joinNodeId = joinNodeId,
      strategy = com.monadial.waygrid.common.domain.model.traversal.dag.JoinStrategy.And,
      timeout = None,
      onSuccess = None,
      onFailure = None,
      onTimeout = None
    )

    val b = Node.standard(svcB, onSuccess = Some(join))
    val c = Node.standard(svcC, onSuccess = Some(join))

    val fork1 = Node.fork(
      address = svcFork,
      branches = Map("b" -> b, "c" -> c),
      joinNodeId = joinNodeId
    )

    val fork2 = Node.fork(
      address = svcFork,
      branches = Map("c" -> c, "b" -> b),
      joinNodeId = joinNodeId
    )

    val spec1 = Spec.single(Node.standard(svcEntry1, onSuccess = Some(fork1)), RepeatPolicy.NoRepeat)
    val spec2 = Spec.single(Node.standard(svcEntry1, onSuccess = Some(fork2)), RepeatPolicy.NoRepeat)

    DagCompiler.default[IO].use { compiler =>
      for
        dag1 <- compiler.compile(spec1, RouteSalt("det-salt"))
        dag2 <- compiler.compile(spec2, RouteSalt("det-salt"))
      yield expect.same(dag1.hash, dag2.hash) && expect.same(dag1.nodes.keySet, dag2.nodes.keySet) && expect.same(dag1.edges, dag2.edges)
    }

  test("DagCompiler produces different hashes for different salts"):
    val spec = Spec.single(Node.standard(svcEntry1), RepeatPolicy.NoRepeat)
    DagCompiler.default[IO].use { compiler =>
      for
        dag1 <- compiler.compile(spec, RouteSalt("salt-1"))
        dag2 <- compiler.compile(spec, RouteSalt("salt-2"))
      yield expect(dag1.hash != dag2.hash)
    }
