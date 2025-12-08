package com.monadial.waygrid.common.domain.model.vectorclock

import weaver.SimpleIOSuite
import cats.syntax.all.*
import com.monadial.waygrid.common.domain.model.vectorclock.VectorClock.Comparison.*
import com.monadial.waygrid.common.domain.value.Address.NodeAddress
import org.http4s.Uri

import scala.collection.immutable.SortedMap

object VectorClockSuite extends SimpleIOSuite:

  private val n1 = NodeAddress(Uri.unsafeFromString("waygrid://node1/service1"))
  private val n2 = NodeAddress(Uri.unsafeFromString("waygrid://node2/service1"))
  private val n3 = NodeAddress(Uri.unsafeFromString("waygrid://node3/service1"))

  test("VectorClock.initial creates version 1 for origin node"):
    for
      vc <- VectorClock.initial(n1).pure
    yield expect.same(vc.compareTo(vc), Equal)

  test("tick increments counter monotonically"):
    for
      vc1 <- VectorClock.initial(n1).pure
      vc2 <- vc1.tick(n1).tick(n1).pure
    yield expect.same(vc2.entries(n1), 3L)

  test("tick creates new entry if node missing"):
    for
      vc1 <- VectorClock.initial(n1).pure
      vc2 <- vc1.tick(n2).pure
    yield expect(vc2.entries.contains(n2)) and expect.same(vc2.entries(n2), 1L)

  test("merge keeps max version per node"):
    for
      vc1 <- VectorClock.initial(n1).tick(n1).pure // n1 -> 2
      vc2 <- VectorClock.initial(n1).tick(n2).tick(n2).pure // n1 -> 1, n2 -> 2
      merged <- vc1.merge(vc2).pure
    yield expect.same(merged.entries(n1), 2L) and expect.same(merged.entries(n2), 2L)

  test("merge is commutative and keeps max version per node"):
    for
      vc1 <- VectorClock.initial(n1).tick(n1).pure
      vc2 <- VectorClock.initial(n1).tick(n2).tick(n2).pure
    yield
      val merged1 = vc1.merge(vc2)
      val merged2 = vc2.merge(vc1)
      expect.same(merged1.entries, merged2.entries) and
          expect.same(merged1.entries(n1), 2L) and
          expect.same(merged1.entries(n2), 2L)

  test("compareTo yields Before / After correctly"):
    for
      base <- VectorClock.initial(n1).pure
      vc1  <- base.tick(n1).pure // 2
      vc2  <- base.pure
    yield expect.same(vc2.compareTo(vc1), Before) and
        expect.same(vc1.compareTo(vc2), After)

  test("compareTo yields Concurrent when neither dominates"):
    for
      vc1 <- VectorClock(SortedMap(n1 -> 2L, n2 -> 1L)).pure
      vc2 <- VectorClock(SortedMap(n1 -> 1L, n2 -> 2L)).pure
    yield expect.same(vc1.compareTo(vc2), Concurrent) and
        expect.same(vc2.compareTo(vc1), Concurrent)

  test("merge is associative, commutative, and idempotent"):
    for
      vcA <- VectorClock(SortedMap(n1 -> 2L, n2 -> 1L)).pure
      vcB <- VectorClock(SortedMap(n1 -> 1L, n2 -> 3L)).pure
      vcC <- VectorClock(SortedMap(n3 -> 1L)).pure
      ab  <- vcA.merge(vcB).pure
      ba  <- vcB.merge(vcA).pure
      assoc1 <- vcA.merge(vcB).merge(vcC).pure
      assoc2 <- vcA.merge(vcB.merge(vcC)).pure
      idemp  <- vcA.merge(vcA).pure
    yield expect.same(ab.entries, ba.entries) and
        expect.same(assoc1.entries, assoc2.entries) and
        expect.same(idemp.entries, vcA.entries)

  test("merge with empty clock is safe"):
    for
      vc     <- VectorClock.initial(n1).tick(n1).pure
      merged <- VectorClock.empty.merge(vc).pure
    yield expect.same(merged.entries(n1), 2L)
