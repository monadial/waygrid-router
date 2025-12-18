package com.monadial.waygrid.common.domain.value.instant

import cats.{ Eq, Order, Show }
import com.monadial.waygrid.common.domain.algebra.value.codec.{ Base64Codec, BytesCodec }
import com.monadial.waygrid.common.domain.algebra.value.instant.InstantValue
import io.circe.syntax.*
import org.scalacheck.Gen
import scodec.Codec as SCodec
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers

import java.time.Instant

/**
 * Tests for InstantValue abstraction using a dummy value object.
 * Verifies that all type class instances are correctly derived.
 */
object InstantValueSuite extends SimpleIOSuite with Checkers:

  // ---------------------------------------------------------------------------
  // Dummy InstantValue for testing
  // ---------------------------------------------------------------------------

  type DummyTimestamp = DummyTimestamp.Type
  object DummyTimestamp extends InstantValue

  // ---------------------------------------------------------------------------
  // Generators
  // ---------------------------------------------------------------------------

  given Show[DummyTimestamp] = Show.fromToString

  private val genDummyTimestamp: Gen[DummyTimestamp] =
    Gen.choose(0L, System.currentTimeMillis() * 2).map(ms => DummyTimestamp(Instant.ofEpochMilli(ms)))

  private val genDummyTimestampPair: Gen[(DummyTimestamp, DummyTimestamp)] =
    Gen.zip(genDummyTimestamp, genDummyTimestamp)

  // ---------------------------------------------------------------------------
  // Codec roundtrip tests
  // ---------------------------------------------------------------------------

  test("InstantValue scodec roundtrip"):
    forall(genDummyTimestamp) { v =>
      val codec  = summon[SCodec[DummyTimestamp]]
      val result = codec.encode(v).flatMap(codec.decode)
      expect(result.toOption.exists(r => r.value == v && r.remainder.isEmpty))
    }

  test("InstantValue circe roundtrip"):
    forall(genDummyTimestamp) { v =>
      val json    = v.asJson
      val decoded = json.as[DummyTimestamp]
      expect(decoded.toOption.contains(v))
    }

  test("InstantValue BytesCodec roundtrip"):
    forall(genDummyTimestamp) { v =>
      val encoded = BytesCodec[DummyTimestamp].encodeToScalar(v)
      val decoded = BytesCodec[DummyTimestamp].decodeFromScalar(encoded)
      expect(decoded.toOption.contains(v))
    }

  test("InstantValue Base64Codec roundtrip"):
    forall(genDummyTimestamp) { v =>
      val encoded = Base64Codec[DummyTimestamp].encode(v)
      val decoded = Base64Codec[DummyTimestamp].decode(encoded)
      expect(decoded.toOption.contains(v))
    }

  // ---------------------------------------------------------------------------
  // Type class instance tests
  // ---------------------------------------------------------------------------

  test("InstantValue Eq instance"):
    forall(genDummyTimestampPair) { case (a, b) =>
      val eqInstance = summon[Eq[DummyTimestamp]]
      expect(eqInstance.eqv(a, a)) and
        expect(eqInstance.eqv(b, b)) and
        expect(eqInstance.eqv(a, b) == (a.unwrap == b.unwrap))
    }

  test("InstantValue Order instance"):
    forall(genDummyTimestampPair) { case (a, b) =>
      val orderInstance = summon[Order[DummyTimestamp]]
      expect(orderInstance.compare(a, a) == 0) and
        expect(orderInstance.compare(a, b) == a.unwrap.compareTo(b.unwrap))
    }

  test("InstantValue Show instance"):
    forall(genDummyTimestamp) { v =>
      val showInstance = summon[Show[DummyTimestamp]]
      expect(showInstance.show(v) == v.unwrap.toString)
    }

  test("InstantValue Ordering instance"):
    forall(genDummyTimestampPair) { case (a, b) =>
      val orderingInstance = summon[Ordering[DummyTimestamp]]
      expect(orderingInstance.compare(a, b) == a.unwrap.compareTo(b.unwrap))
    }

  // ---------------------------------------------------------------------------
  // Unwrap test
  // ---------------------------------------------------------------------------

  test("InstantValue unwrap returns underlying value"):
    forall(genDummyTimestamp) { v =>
      expect(v.unwrap.isInstanceOf[Instant])
    }

  // ---------------------------------------------------------------------------
  // Edge case tests - Instant specific
  // ---------------------------------------------------------------------------

  pureTest("InstantValue handles epoch (1970-01-01T00:00:00Z)") {
    val v       = DummyTimestamp(Instant.EPOCH)
    val codec   = summon[SCodec[DummyTimestamp]]
    val encoded = codec.encode(v)
    val decoded = encoded.flatMap(codec.decode)
    expect(decoded.toOption.exists(_.value == v)) and
      expect(v.unwrap == Instant.EPOCH)
  }

  pureTest("InstantValue handles far future dates") {
    // Year 3000
    val future  = Instant.ofEpochMilli(32503680000000L)
    val v       = DummyTimestamp(future)
    val encoded = BytesCodec[DummyTimestamp].encodeToScalar(v)
    val decoded = BytesCodec[DummyTimestamp].decodeFromScalar(encoded)
    expect(decoded.toOption.contains(v))
  }

  pureTest("InstantValue handles negative epoch (before 1970)") {
    // 1960-01-01
    val past    = Instant.ofEpochMilli(-315619200000L)
    val v       = DummyTimestamp(past)
    val json    = v.asJson
    val decoded = json.as[DummyTimestamp]
    expect(decoded.toOption.contains(v)) and
      expect(v.unwrap.toEpochMilli < 0)
  }

  pureTest("InstantValue BytesCodec encodes to 8 bytes") {
    val v       = DummyTimestamp(Instant.now())
    val encoded = BytesCodec[DummyTimestamp].encodeToScalar(v)
    expect(encoded.size == 8) // Long is 8 bytes
  }

  pureTest("InstantValue Eq correctly compares identical values") {
    val instant = Instant.now()
    val v1      = DummyTimestamp(instant)
    val v2      = DummyTimestamp(instant)
    expect(summon[Eq[DummyTimestamp]].eqv(v1, v2))
  }

  pureTest("InstantValue Eq correctly compares different values") {
    val v1 = DummyTimestamp(Instant.now())
    Thread.sleep(2)
    val v2 = DummyTimestamp(Instant.now())
    expect(!summon[Eq[DummyTimestamp]].eqv(v1, v2))
  }

  pureTest("InstantValue Order respects chronological ordering") {
    val earlier = DummyTimestamp(Instant.now())
    Thread.sleep(2)
    val later   = DummyTimestamp(Instant.now())
    expect(summon[Order[DummyTimestamp]].compare(earlier, later) < 0) and
      expect(summon[Order[DummyTimestamp]].compare(later, earlier) > 0)
  }

  pureTest("InstantValue Show produces ISO-8601 format") {
    val v    = DummyTimestamp(Instant.EPOCH)
    val show = summon[Show[DummyTimestamp]].show(v)
    expect(show == "1970-01-01T00:00:00Z")
  }

  pureTest("InstantValue handles millisecond precision") {
    val withMillis = Instant.ofEpochMilli(1234567890123L)
    val v          = DummyTimestamp(withMillis)
    val encoded    = BytesCodec[DummyTimestamp].encodeToScalar(v)
    val decoded    = BytesCodec[DummyTimestamp].decodeFromScalar(encoded)
    expect(decoded.toOption.contains(v)) and
      expect(v.unwrap.toEpochMilli == 1234567890123L)
  }

  pureTest("InstantValue circe roundtrip handles current time") {
    val v       = DummyTimestamp(Instant.now())
    val json    = v.asJson
    val decoded = json.as[DummyTimestamp]
    expect(decoded.toOption.contains(v))
  }

  pureTest("InstantValue Base64Codec roundtrip for boundary values") {
    val epoch  = DummyTimestamp(Instant.EPOCH)
    val future = DummyTimestamp(Instant.ofEpochMilli(Long.MaxValue / 1000)) // Reasonable far future
    val encEpoch  = Base64Codec[DummyTimestamp].encode(epoch)
    val encFuture = Base64Codec[DummyTimestamp].encode(future)
    val decEpoch  = Base64Codec[DummyTimestamp].decode(encEpoch)
    val decFuture = Base64Codec[DummyTimestamp].decode(encFuture)
    expect(decEpoch.toOption.contains(epoch)) and
      expect(decFuture.toOption.contains(future))
  }
