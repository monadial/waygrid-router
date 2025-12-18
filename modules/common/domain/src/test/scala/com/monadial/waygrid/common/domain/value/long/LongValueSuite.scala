package com.monadial.waygrid.common.domain.value.long

import cats.{ Eq, Order, Show }
import com.monadial.waygrid.common.domain.algebra.value.codec.{ Base64Codec, BytesCodec }
import com.monadial.waygrid.common.domain.algebra.value.long.LongValue
import io.circe.syntax.*
import org.scalacheck.Gen
import scodec.Codec as SCodec
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers

/**
 * Tests for LongValue abstraction using a dummy value object.
 * Verifies that all type class instances are correctly derived.
 */
object LongValueSuite extends SimpleIOSuite with Checkers:

  // ---------------------------------------------------------------------------
  // Dummy LongValue for testing
  // ---------------------------------------------------------------------------

  type DummyLongId = DummyLongId.Type
  object DummyLongId extends LongValue

  // ---------------------------------------------------------------------------
  // Generators
  // ---------------------------------------------------------------------------

  given Show[DummyLongId] = Show.fromToString

  private val genDummyLongId: Gen[DummyLongId] =
    Gen.choose(Long.MinValue, Long.MaxValue).map(DummyLongId(_))

  private val genDummyLongIdPair: Gen[(DummyLongId, DummyLongId)] =
    Gen.zip(genDummyLongId, genDummyLongId)

  // ---------------------------------------------------------------------------
  // Codec roundtrip tests
  // ---------------------------------------------------------------------------

  test("LongValue scodec roundtrip"):
    forall(genDummyLongId) { v =>
      val codec  = summon[SCodec[DummyLongId]]
      val result = codec.encode(v).flatMap(codec.decode)
      expect(result.toOption.exists(r => r.value == v && r.remainder.isEmpty))
    }

  test("LongValue circe roundtrip"):
    forall(genDummyLongId) { v =>
      val json    = v.asJson
      val decoded = json.as[DummyLongId]
      expect(decoded.toOption.contains(v))
    }

  test("LongValue BytesCodec roundtrip"):
    forall(genDummyLongId) { v =>
      val encoded = BytesCodec[DummyLongId].encodeToScalar(v)
      val decoded = BytesCodec[DummyLongId].decodeFromScalar(encoded)
      expect(decoded.toOption.contains(v))
    }

  test("LongValue Base64Codec roundtrip"):
    forall(genDummyLongId) { v =>
      val encoded = Base64Codec[DummyLongId].encode(v)
      val decoded = Base64Codec[DummyLongId].decode(encoded)
      expect(decoded.toOption.contains(v))
    }

  // ---------------------------------------------------------------------------
  // Type class instance tests
  // ---------------------------------------------------------------------------

  test("LongValue Eq instance"):
    forall(genDummyLongIdPair) { case (a, b) =>
      val eqInstance = summon[Eq[DummyLongId]]
      expect(eqInstance.eqv(a, a)) and
        expect(eqInstance.eqv(b, b)) and
        expect(eqInstance.eqv(a, b) == (a.unwrap == b.unwrap))
    }

  test("LongValue Order instance"):
    forall(genDummyLongIdPair) { case (a, b) =>
      val orderInstance = summon[Order[DummyLongId]]
      val cmp           = orderInstance.compare(a, b)
      expect(orderInstance.compare(a, a) == 0) and
        expect(cmp == java.lang.Long.compare(a.unwrap, b.unwrap))
    }

  test("LongValue Show instance"):
    forall(genDummyLongId) { v =>
      val showInstance = summon[Show[DummyLongId]]
      expect(showInstance.show(v) == v.unwrap.toString)
    }

  test("LongValue Ordering instance"):
    forall(genDummyLongIdPair) { case (a, b) =>
      val orderingInstance = summon[Ordering[DummyLongId]]
      expect(orderingInstance.compare(a, b) == java.lang.Long.compare(a.unwrap, b.unwrap))
    }

  // ---------------------------------------------------------------------------
  // Unwrap test
  // ---------------------------------------------------------------------------

  test("LongValue unwrap returns underlying value"):
    forall(genDummyLongId) { v =>
      expect(v.unwrap.isInstanceOf[Long])
    }

  // ---------------------------------------------------------------------------
  // Edge case tests - boundary values
  // ---------------------------------------------------------------------------

  pureTest("LongValue handles Long.MinValue") {
    val v       = DummyLongId(Long.MinValue)
    val codec   = summon[SCodec[DummyLongId]]
    val encoded = codec.encode(v)
    val decoded = encoded.flatMap(codec.decode)
    expect(decoded.toOption.exists(_.value == v)) and
      expect(v.unwrap == Long.MinValue)
  }

  pureTest("LongValue handles Long.MaxValue") {
    val v       = DummyLongId(Long.MaxValue)
    val codec   = summon[SCodec[DummyLongId]]
    val encoded = codec.encode(v)
    val decoded = encoded.flatMap(codec.decode)
    expect(decoded.toOption.exists(_.value == v)) and
      expect(v.unwrap == Long.MaxValue)
  }

  pureTest("LongValue handles zero") {
    val v       = DummyLongId(0L)
    val encoded = BytesCodec[DummyLongId].encodeToScalar(v)
    val decoded = BytesCodec[DummyLongId].decodeFromScalar(encoded)
    expect(decoded.toOption.contains(v)) and
      expect(v.unwrap == 0L)
  }

  pureTest("LongValue handles negative one") {
    val v       = DummyLongId(-1L)
    val json    = v.asJson
    val decoded = json.as[DummyLongId]
    expect(decoded.toOption.contains(v)) and
      expect(v.unwrap == -1L)
  }

  pureTest("LongValue handles values beyond Int range") {
    val beyondInt = Int.MaxValue.toLong + 1000L
    val v         = DummyLongId(beyondInt)
    val encoded   = BytesCodec[DummyLongId].encodeToScalar(v)
    val decoded   = BytesCodec[DummyLongId].decodeFromScalar(encoded)
    expect(decoded.toOption.contains(v)) and
      expect(v.unwrap == beyondInt)
  }

  pureTest("LongValue Eq correctly compares identical values") {
    val v1 = DummyLongId(42L)
    val v2 = DummyLongId(42L)
    expect(summon[Eq[DummyLongId]].eqv(v1, v2))
  }

  pureTest("LongValue Eq correctly compares different values") {
    val v1 = DummyLongId(42L)
    val v2 = DummyLongId(43L)
    expect(!summon[Eq[DummyLongId]].eqv(v1, v2))
  }

  pureTest("LongValue Order is numeric") {
    val a = DummyLongId(-100L)
    val b = DummyLongId(100L)
    expect(summon[Order[DummyLongId]].compare(a, b) < 0) and
      expect(summon[Order[DummyLongId]].compare(b, a) > 0)
  }

  pureTest("LongValue BytesCodec encodes to 8 bytes") {
    val v       = DummyLongId(12345L)
    val encoded = BytesCodec[DummyLongId].encodeToScalar(v)
    expect(encoded.size == 8)
  }

  pureTest("LongValue Base64Codec roundtrip for boundary values") {
    val minVal  = DummyLongId(Long.MinValue)
    val maxVal  = DummyLongId(Long.MaxValue)
    val minEnc  = Base64Codec[DummyLongId].encode(minVal)
    val maxEnc  = Base64Codec[DummyLongId].encode(maxVal)
    val minDec  = Base64Codec[DummyLongId].decode(minEnc)
    val maxDec  = Base64Codec[DummyLongId].decode(maxEnc)
    expect(minDec.toOption.contains(minVal)) and
      expect(maxDec.toOption.contains(maxVal))
  }
