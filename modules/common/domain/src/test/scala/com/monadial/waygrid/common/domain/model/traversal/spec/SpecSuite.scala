package com.monadial.waygrid.common.domain.model.traversal.spec

import cats.effect.IO
import com.monadial.waygrid.common.domain.model.resiliency.RetryPolicy
import com.monadial.waygrid.common.domain.model.routing.Value.{ DeliveryStrategy, RepeatPolicy }
import com.monadial.waygrid.common.domain.model.traversal.condition.Condition
import com.monadial.waygrid.common.domain.model.traversal.dag.JoinStrategy
import com.monadial.waygrid.common.domain.value.Address.ServiceAddress
import org.http4s.Uri
import weaver.SimpleIOSuite

object SpecSuite extends SimpleIOSuite:

  private val svcA = ServiceAddress(Uri.unsafeFromString("waygrid://processor/a"))
  private val svcB = ServiceAddress(Uri.unsafeFromString("waygrid://processor/b"))
  private val svcC = ServiceAddress(Uri.unsafeFromString("waygrid://processor/c"))
  private val svcD = ServiceAddress(Uri.unsafeFromString("waygrid://processor/d"))

  test("Spec.single creates a non-empty entryPoints list"):
    val node = Node.Standard(
      address = svcA,
      retryPolicy = RetryPolicy.None,
      deliveryStrategy = DeliveryStrategy.Immediate,
      onSuccess = None,
      onFailure = None
    )
    val spec = Spec.single(node, RepeatPolicy.NoRepeat)
    IO.pure(expect(spec.entryPoints.toList == List(node)))

  test("Spec.multiple preserves entry point order"):
    val node1 = Node.standard(svcA)
    val node2 = Node.standard(svcB)
    val spec  = Spec.multiple(node1, node2)(RepeatPolicy.NoRepeat)
    IO.pure(expect(spec.entryPoints.toList == List(node1, node2)))

  test("Spec supports fan-out (Fork) and fan-in (Join) wiring"):
    val joinId = "join-1"
    val after  = Node.standard(svcD)
    val join   = Node.join(svcC, joinId, JoinStrategy.And, onSuccess = Some(after))
    val b      = Node.standard(svcB, onSuccess = Some(join))
    val a      = Node.fork(svcA, branches = Map("b" -> b), joinNodeId = joinId)

    val spec = Spec.single(a, RepeatPolicy.NoRepeat)
    IO.pure(
      expect(spec.entryPoints.head == a) &&
        expect(a.branches("b") == b) &&
        expect(b.onSuccess.contains(join)) &&
        expect(join.onSuccess.contains(after))
    )

  test("Spec supports conditional edges on Standard nodes"):
    val yes = Node.standard(svcB)
    val no  = Node.standard(svcC)

    val router = Node.Standard(
      address = svcA,
      retryPolicy = RetryPolicy.None,
      deliveryStrategy = DeliveryStrategy.Immediate,
      onSuccess = Some(no),
      onFailure = None,
      onConditions = List(
        Node.when(Condition.Always, yes),
        Node.when(Condition.Not(Condition.Always), yes)
      )
    )

    IO.pure(
      expect(router.onSuccess.contains(no)) &&
        expect(router.onConditions.length == 2)
    )
