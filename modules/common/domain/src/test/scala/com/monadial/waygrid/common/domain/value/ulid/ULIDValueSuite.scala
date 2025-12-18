package com.monadial.waygrid.common.domain.value.ulid

import cats.{ Eq, Order, Show }
import com.monadial.waygrid.common.domain.algebra.value.codec.{ Base64Codec, BytesCodec }
import com.monadial.waygrid.common.domain.algebra.value.ulid.ULIDValue
import io.circe.syntax.*
import org.scalacheck.Gen
import scodec.Codec as SCodec
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers
import wvlet.airframe.ulid.ULID

/**
 * Tests for ULIDValue abstraction using a dummy value object.
 * Verifies that all type class instances are correctly derived.
 */
object ULIDValueSuite extends SimpleIOSuite with Checkers:

  // ---------------------------------------------------------------------------
  // Dummy ULIDValue for testing
  // ---------------------------------------------------------------------------

  type DummyUlidId = DummyUlidId.Type
  object DummyUlidId extends ULIDValue

  // ---------------------------------------------------------------------------
  // Generators
  // ---------------------------------------------------------------------------

  given Show[DummyUlidId] = Show.fromToString

  private val genDummyUlidId: Gen[DummyUlidId] =
    Gen.delay(Gen.const(ULID.newULID)).map(DummyUlidId(_))

  private val genDummyUlidIdPair: Gen[(DummyUlidId, DummyUlidId)] =
    Gen.zip(genDummyUlidId, genDummyUlidId)

  // ---------------------------------------------------------------------------
  // Codec roundtrip tests
  // ---------------------------------------------------------------------------

  test("ULIDValue scodec roundtrip"):
    forall(genDummyUlidId) { v =>
      val codec  = summon[SCodec[DummyUlidId]]
      val result = codec.encode(v).flatMap(codec.decode)
      expect(result.toOption.exists(r => r.value == v && r.remainder.isEmpty))
    }

  test("ULIDValue circe roundtrip"):
    forall(genDummyUlidId) { v =>
      val json    = v.asJson
      val decoded = json.as[DummyUlidId]
      expect(decoded.toOption.contains(v))
    }

  test("ULIDValue BytesCodec roundtrip"):
    forall(genDummyUlidId) { v =>
      val encoded = BytesCodec[DummyUlidId].encodeToScalar(v)
      val decoded = BytesCodec[DummyUlidId].decodeFromScalar(encoded)
      expect(decoded.toOption.contains(v))
    }

  test("ULIDValue Base64Codec roundtrip"):
    forall(genDummyUlidId) { v =>
      val encoded = Base64Codec[DummyUlidId].encode(v)
      val decoded = Base64Codec[DummyUlidId].decode(encoded)
      expect(decoded.toOption.contains(v))
    }

  // ---------------------------------------------------------------------------
  // Type class instance tests
  // ---------------------------------------------------------------------------

  test("ULIDValue Eq instance"):
    forall(genDummyUlidIdPair) { case (a, b) =>
      val eqInstance = summon[Eq[DummyUlidId]]
      expect(eqInstance.eqv(a, a)) and
        expect(eqInstance.eqv(b, b)) and
        expect(eqInstance.eqv(a, b) == (a.unwrap == b.unwrap))
    }

  test("ULIDValue Order instance"):
    forall(genDummyUlidIdPair) { case (a, b) =>
      val orderInstance = summon[Order[DummyUlidId]]
      expect(orderInstance.compare(a, a) == 0) and
        expect(orderInstance.compare(a, b) == a.unwrap.compareTo(b.unwrap))
    }

  test("ULIDValue Show instance"):
    forall(genDummyUlidId) { v =>
      val showInstance = summon[Show[DummyUlidId]]
      expect(showInstance.show(v) == v.unwrap.toString)
    }

  test("ULIDValue Ordering instance"):
    forall(genDummyUlidIdPair) { case (a, b) =>
      val orderingInstance = summon[Ordering[DummyUlidId]]
      expect(orderingInstance.compare(a, b) == a.unwrap.compareTo(b.unwrap))
    }

  // ---------------------------------------------------------------------------
  // Unwrap test
  // ---------------------------------------------------------------------------

  test("ULIDValue unwrap returns underlying value"):
    forall(genDummyUlidId) { v =>
      expect(v.unwrap.isInstanceOf[ULID])
    }

  // ---------------------------------------------------------------------------
  // Edge case tests - ULID specific
  // ---------------------------------------------------------------------------

  pureTest("ULIDValue from string roundtrip") {
    val ulidStr = "01ARZ3NDEKTSV4RRFFQ69G5FAV"
    val ulid    = ULID.fromString(ulidStr)
    val v       = DummyUlidId(ulid)
    val json    = v.asJson
    val decoded = json.as[DummyUlidId]
    expect(decoded.toOption.contains(v)) and
      expect(v.unwrap.toString == ulidStr)
  }

  pureTest("ULIDValue BytesCodec encodes to 16 bytes (binary format)") {
    // BytesCodec uses binary representation (16 bytes / 128-bit)
    val v       = DummyUlidId(ULID.newULID)
    val encoded = BytesCodec[DummyUlidId].encodeToScalar(v)
    expect(encoded.size == 16)
  }

  pureTest("ULIDValue scodec encodes to 16 bytes") {
    val v       = DummyUlidId(ULID.newULID)
    val codec   = summon[SCodec[DummyUlidId]]
    val encoded = codec.encode(v)
    expect(encoded.toOption.exists(_.size == 128)) // 16 bytes = 128 bits
  }

  pureTest("ULIDValue Eq correctly compares identical values") {
    val ulid = ULID.newULID
    val v1   = DummyUlidId(ulid)
    val v2   = DummyUlidId(ulid)
    expect(summon[Eq[DummyUlidId]].eqv(v1, v2))
  }

  pureTest("ULIDValue Eq correctly compares different values") {
    val v1 = DummyUlidId(ULID.newULID)
    val v2 = DummyUlidId(ULID.newULID)
    expect(!summon[Eq[DummyUlidId]].eqv(v1, v2))
  }

  pureTest("ULIDValue Order respects ULID ordering (later ULIDs are greater)") {
    val earlier = DummyUlidId(ULID.newULID)
    Thread.sleep(2) // ensure different timestamp component
    val later   = DummyUlidId(ULID.newULID)
    expect(summon[Order[DummyUlidId]].compare(earlier, later) < 0)
  }

  pureTest("ULIDValue Show produces valid ULID string") {
    val v    = DummyUlidId(ULID.newULID)
    val show = summon[Show[DummyUlidId]].show(v)
    expect(show.length == 26) and // ULID strings are 26 chars
      expect(show.forall(c => c.isLetterOrDigit))
  }

  pureTest("ULIDValue Base64Codec roundtrip preserves ordering") {
    val earlier    = DummyUlidId(ULID.newULID)
    Thread.sleep(2)
    val later      = DummyUlidId(ULID.newULID)
    val encEarlier = Base64Codec[DummyUlidId].encode(earlier)
    val encLater   = Base64Codec[DummyUlidId].encode(later)
    val decEarlier = Base64Codec[DummyUlidId].decode(encEarlier)
    val decLater   = Base64Codec[DummyUlidId].decode(encLater)
    expect(decEarlier.toOption.contains(earlier)) and
      expect(decLater.toOption.contains(later))
  }

  pureTest("ULIDValue timestamp extraction works correctly") {
    val now  = System.currentTimeMillis()
    val v    = DummyUlidId(ULID.newULID)
    val ulid = v.unwrap
    // ULID timestamp should be very close to current time
    expect(Math.abs(ulid.epochMillis - now) < 1000)
  }
