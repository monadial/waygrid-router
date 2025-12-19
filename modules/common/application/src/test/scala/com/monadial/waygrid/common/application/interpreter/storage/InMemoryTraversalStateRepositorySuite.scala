package com.monadial.waygrid.common.application.interpreter.storage

import scala.concurrent.duration.*

import cats.data.NonEmptyList
import cats.effect.IO
import com.monadial.waygrid.common.domain.model.resiliency.RetryPolicy
import com.monadial.waygrid.common.domain.model.routing.Value.{ DeliveryStrategy, RepeatPolicy, TraversalId }
import com.monadial.waygrid.common.domain.model.traversal.dag.Value.{ DagHash, NodeId }
import com.monadial.waygrid.common.domain.model.traversal.dag.{ Dag, Node }
import com.monadial.waygrid.common.domain.model.traversal.fsm.ConcurrentModification
import com.monadial.waygrid.common.domain.model.traversal.state.TraversalState
import com.monadial.waygrid.common.domain.model.traversal.state.Value.StateVersion
import com.monadial.waygrid.common.domain.value.Address.{ NodeAddress, ServiceAddress }
import org.http4s.Uri
import weaver.SimpleIOSuite

object InMemoryTraversalStateRepositorySuite extends SimpleIOSuite:

  private val nodeA = NodeId("A")
  private val svcA  = ServiceAddress(Uri.unsafeFromString("waygrid://processor/a"))
  private val node  = NodeAddress(Uri.unsafeFromString("waygrid://system/waystation?nodeId=test-1"))

  private val dag = Dag(
    hash = DagHash("repo-dag"),
    entryPoints = NonEmptyList.one(nodeA),
    repeatPolicy = RepeatPolicy.NoRepeat,
    nodes = Map(nodeA -> Node(nodeA, None, RetryPolicy.None, DeliveryStrategy.Immediate, svcA)),
    edges = Nil
  )

  private def mkState: IO[TraversalState] =
    TraversalId.next[IO].map(id => TraversalState.initial(id, node, dag))

  test("save increments stateVersion and load returns saved state"):
      InMemoryTraversalStateRepository.make[IO].flatMap { repo =>
        for
          state0 <- mkState
          saved  <- repo.save(state0)
          loaded <- repo.load(state0.traversalId)
        yield expect(saved.stateVersion == StateVersion(1L)) &&
          expect(loaded.contains(saved))
      }

  test("update enforces expected version and increments"):
      InMemoryTraversalStateRepository.make[IO].flatMap { repo =>
        for
          state0 <- mkState
          saved1 <- repo.save(state0)
          saved2 <- repo.update(saved1.traversalId, saved1.stateVersion, s => s.copy(active = Set(nodeA)))
        yield expect(saved2.stateVersion == StateVersion(2L)) &&
          expect(saved2.active.contains(nodeA))
      }

  test("save detects concurrent modification"):
      InMemoryTraversalStateRepository.make[IO].flatMap { repo =>
        for
          state0 <- mkState
          _      <- repo.save(state0)
          r      <- repo.save(state0).attempt
        yield r match
          case Left(_: ConcurrentModification) => success
          case other                           => failure(s"Expected ConcurrentModification, got: $other")
      }

  test("acquireLock is exclusive until released"):
      InMemoryTraversalStateRepository.make[IO].flatMap { repo =>
        for
          state0 <- mkState
          ok1    <- repo.acquireLock(state0.traversalId, 10.seconds)
          ok2    <- repo.acquireLock(state0.traversalId, 10.seconds)
          _      <- repo.releaseLock(state0.traversalId)
          ok3    <- repo.acquireLock(state0.traversalId, 10.seconds)
        yield expect(ok1) && expect(!ok2) && expect(ok3)
      }
