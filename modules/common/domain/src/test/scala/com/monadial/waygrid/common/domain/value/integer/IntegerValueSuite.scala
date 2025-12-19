package com.monadial.waygrid.common.domain.value.integer

import cats.{ Eq, Order, Show }
import com.monadial.waygrid.common.domain.algebra.value.codec.{ Base64Codec, BytesCodec }
import com.monadial.waygrid.common.domain.algebra.value.integer.IntegerValue
import io.circe.syntax.*
import org.scalacheck.Gen
import scodec.Codec as SCodec
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers

/**
 * Tests for IntegerValue abstraction using a dummy value object.
 * Verifies that all type class instances are correctly derived.
 */
object IntegerValueSuite extends SimpleIOSuite with Checkers:

  // ---------------------------------------------------------------------------
  // Dummy IntegerValue for testing
  // ---------------------------------------------------------------------------

  type DummyIntId = DummyIntId.Type
  object DummyIntId extends IntegerValue

  // ---------------------------------------------------------------------------
  // Generators
  // ---------------------------------------------------------------------------

  given Show[DummyIntId] = Show.fromToString

  private val genDummyIntId: Gen[DummyIntId] =
    Gen.choose(Int.MinValue, Int.MaxValue).map(DummyIntId(_))

  private val genDummyIntIdPair: Gen[(DummyIntId, DummyIntId)] =
    Gen.zip(genDummyIntId, genDummyIntId)

  // ---------------------------------------------------------------------------
  // Codec roundtrip tests
  // ---------------------------------------------------------------------------

  test("IntegerValue scodec roundtrip"):
      forall(genDummyIntId) { v =>
        val codec  = summon[SCodec[DummyIntId]]
        val result = codec.encode(v).flatMap(codec.decode)
        expect(result.toOption.exists(r => r.value == v && r.remainder.isEmpty))
      }

  test("IntegerValue circe roundtrip"):
      forall(genDummyIntId) { v =>
        val json    = v.asJson
        val decoded = json.as[DummyIntId]
        expect(decoded.toOption.contains(v))
      }

  test("IntegerValue BytesCodec roundtrip"):
      forall(genDummyIntId) { v =>
        val encoded = BytesCodec[DummyIntId].encodeToScalar(v)
        val decoded = BytesCodec[DummyIntId].decodeFromScalar(encoded)
        expect(decoded.toOption.contains(v))
      }

  test("IntegerValue Base64Codec roundtrip"):
      forall(genDummyIntId) { v =>
        val encoded = Base64Codec[DummyIntId].encode(v)
        val decoded = Base64Codec[DummyIntId].decode(encoded)
        expect(decoded.toOption.contains(v))
      }

  // ---------------------------------------------------------------------------
  // Type class instance tests
  // ---------------------------------------------------------------------------

  test("IntegerValue Eq instance"):
      forall(genDummyIntIdPair) { case (a, b) =>
        val eqInstance = summon[Eq[DummyIntId]]
        expect(eqInstance.eqv(a, a)) and
          expect(eqInstance.eqv(b, b)) and
          expect(eqInstance.eqv(a, b) == (a.unwrap == b.unwrap))
      }

  test("IntegerValue Order instance"):
      forall(genDummyIntIdPair) { case (a, b) =>
        val orderInstance = summon[Order[DummyIntId]]
        val cmp           = orderInstance.compare(a, b)
        expect(orderInstance.compare(a, a) == 0) and
          expect(cmp == Integer.compare(a.unwrap, b.unwrap))
      }

  test("IntegerValue Show instance"):
      forall(genDummyIntId) { v =>
        val showInstance = summon[Show[DummyIntId]]
        expect(showInstance.show(v) == v.unwrap.toString)
      }

  test("IntegerValue Ordering instance"):
      forall(genDummyIntIdPair) { case (a, b) =>
        val orderingInstance = summon[Ordering[DummyIntId]]
        expect(orderingInstance.compare(a, b) == Integer.compare(a.unwrap, b.unwrap))
      }

  // ---------------------------------------------------------------------------
  // Unwrap test
  // ---------------------------------------------------------------------------

  test("IntegerValue unwrap returns underlying value"):
      forall(genDummyIntId) { v =>
        expect(v.unwrap.isInstanceOf[Int])
      }

  // ---------------------------------------------------------------------------
  // Edge case tests - boundary values
  // ---------------------------------------------------------------------------

  pureTest("IntegerValue handles Int.MinValue") {
    val v       = DummyIntId(Int.MinValue)
    val codec   = summon[SCodec[DummyIntId]]
    val encoded = codec.encode(v)
    val decoded = encoded.flatMap(codec.decode)
    expect(decoded.toOption.exists(_.value == v)) and
      expect(v.unwrap == Int.MinValue)
  }

  pureTest("IntegerValue handles Int.MaxValue") {
    val v       = DummyIntId(Int.MaxValue)
    val codec   = summon[SCodec[DummyIntId]]
    val encoded = codec.encode(v)
    val decoded = encoded.flatMap(codec.decode)
    expect(decoded.toOption.exists(_.value == v)) and
      expect(v.unwrap == Int.MaxValue)
  }

  pureTest("IntegerValue handles zero") {
    val v       = DummyIntId(0)
    val encoded = BytesCodec[DummyIntId].encodeToScalar(v)
    val decoded = BytesCodec[DummyIntId].decodeFromScalar(encoded)
    expect(decoded.toOption.contains(v)) and
      expect(v.unwrap == 0)
  }

  pureTest("IntegerValue handles negative one") {
    val v       = DummyIntId(-1)
    val json    = v.asJson
    val decoded = json.as[DummyIntId]
    expect(decoded.toOption.contains(v)) and
      expect(v.unwrap == -1)
  }

  pureTest("IntegerValue Eq correctly compares identical values") {
    val v1 = DummyIntId(42)
    val v2 = DummyIntId(42)
    expect(summon[Eq[DummyIntId]].eqv(v1, v2))
  }

  pureTest("IntegerValue Eq correctly compares different values") {
    val v1 = DummyIntId(42)
    val v2 = DummyIntId(43)
    expect(!summon[Eq[DummyIntId]].eqv(v1, v2))
  }

  pureTest("IntegerValue Order is numeric") {
    val a = DummyIntId(-100)
    val b = DummyIntId(100)
    expect(summon[Order[DummyIntId]].compare(a, b) < 0) and
      expect(summon[Order[DummyIntId]].compare(b, a) > 0)
  }

  pureTest("IntegerValue BytesCodec encodes to 4 bytes") {
    val v       = DummyIntId(12345)
    val encoded = BytesCodec[DummyIntId].encodeToScalar(v)
    expect(encoded.size == 4)
  }

  pureTest("IntegerValue Base64Codec roundtrip for boundary values") {
    val minVal = DummyIntId(Int.MinValue)
    val maxVal = DummyIntId(Int.MaxValue)
    val minEnc = Base64Codec[DummyIntId].encode(minVal)
    val maxEnc = Base64Codec[DummyIntId].encode(maxVal)
    val minDec = Base64Codec[DummyIntId].decode(minEnc)
    val maxDec = Base64Codec[DummyIntId].decode(maxEnc)
    expect(minDec.toOption.contains(minVal)) and
      expect(maxDec.toOption.contains(maxVal))
  }
