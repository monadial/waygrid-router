package com.monadial.waygrid.common.domain.model.vectorclock

import cats.Show
import com.monadial.waygrid.common.domain.value.Address.NodeAddress
import org.http4s.Uri
import org.scalacheck.Gen
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers

/**
 * Property-based tests for VectorClock.
 *
 * These tests verify mathematical properties that should hold for all inputs:
 * - Tick monotonicity: ticking always advances the clock
 * - Merge commutativity: merge(a, b) == merge(b, a)
 * - Reflexivity: a clock is never before or after itself
 * - Empty clock ordering: empty is always before any ticked clock
 */
object VectorClockPropertySuite extends SimpleIOSuite with Checkers:

  // Show instances required by weaver-scalacheck
  given Show[Int] = Show.fromToString

  // ---------------------------------------------------------------------------
  // Helper: Create a NodeAddress for testing
  // ---------------------------------------------------------------------------

  private def makeActor(id: Int): NodeAddress =
    NodeAddress(Uri.unsafeFromString(s"waygrid://processor/test?nodeId=node-$id"))

  // ---------------------------------------------------------------------------
  // Tick Properties
  // ---------------------------------------------------------------------------

  test("tick always advances the clock"):
      forall(Gen.choose(1, 1000)) { actorId =>
        val actor  = makeActor(actorId)
        val clock  = VectorClock.empty
        val ticked = clock.tick(actor)
        expect(ticked.isAfter(clock))
      }

  test("multiple ticks accumulate"):
      forall(Gen.choose(1, 10)) { ticks =>
        val actor    = makeActor(1)
        val initial  = VectorClock.empty
        val advanced = (1 to ticks).foldLeft(initial)((c, _) => c.tick(actor))
        expect(advanced.isAfter(initial))
      }

  // ---------------------------------------------------------------------------
  // Merge Properties
  // ---------------------------------------------------------------------------

  test("merge is commutative"):
      forall(Gen.choose(1, 500)) { seed =>
        val actor1 = makeActor(seed)
        val actor2 = makeActor(seed + 500)
        val clock1 = VectorClock.empty.tick(actor1)
        val clock2 = VectorClock.empty.tick(actor2)
        val merge1 = clock1.merge(clock2)
        val merge2 = clock2.merge(clock1)
        expect(merge1 == merge2)
      }

  // ---------------------------------------------------------------------------
  // Ordering Properties
  // ---------------------------------------------------------------------------

  test("self is never before or after itself (reflexivity)"):
      forall(Gen.choose(1, 1000)) { actorId =>
        val actor = makeActor(actorId)
        val clock = VectorClock.empty.tick(actor).tick(actor)
        expect(!clock.isAfter(clock)) &&
        expect(!clock.isBefore(clock))
      }

  test("empty clock is before any ticked clock"):
      forall(Gen.choose(1, 1000)) { actorId =>
        val actor  = makeActor(actorId)
        val empty  = VectorClock.empty
        val ticked = empty.tick(actor)
        expect(empty.isBefore(ticked))
      }
