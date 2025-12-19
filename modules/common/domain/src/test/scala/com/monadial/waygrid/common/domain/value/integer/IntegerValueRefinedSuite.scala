package com.monadial.waygrid.common.domain.value.integer

import cats.{ Eq, Order, Show }
import com.monadial.waygrid.common.domain.algebra.value.codec.{ Base64Codec, BytesCodec }
import com.monadial.waygrid.common.domain.algebra.value.integer.IntegerValueRefined
import eu.timepit.refined.numeric.Positive
import io.circe.syntax.*
import org.scalacheck.Gen
import scodec.Codec as SCodec
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers

/**
 * Tests for IntegerValueRefined abstraction using a dummy refined type.
 * Verifies that all type class instances are correctly derived for refined integer values.
 */
object IntegerValueRefinedSuite extends SimpleIOSuite with Checkers:

  // ---------------------------------------------------------------------------
  // Dummy IntegerValueRefined for testing (Positive integer)
  // ---------------------------------------------------------------------------

  type DummyPositiveInt = DummyPositiveInt.Type
  object DummyPositiveInt extends IntegerValueRefined[Positive]

  // ---------------------------------------------------------------------------
  // Generators
  // ---------------------------------------------------------------------------

  given Show[DummyPositiveInt] = Show.fromToString

  private val genDummyPositiveInt: Gen[DummyPositiveInt] =
    Gen.choose(1, Int.MaxValue).map(DummyPositiveInt.unsafeFrom)

  private val genDummyPositiveIntPair: Gen[(DummyPositiveInt, DummyPositiveInt)] =
    Gen.zip(genDummyPositiveInt, genDummyPositiveInt)

  // ---------------------------------------------------------------------------
  // Codec roundtrip tests
  // ---------------------------------------------------------------------------

  test("IntegerValueRefined scodec roundtrip"):
      forall(genDummyPositiveInt) { v =>
        val codec  = summon[SCodec[DummyPositiveInt]]
        val result = codec.encode(v).flatMap(codec.decode)
        expect(result.toOption.exists(r => r.value == v && r.remainder.isEmpty))
      }

  test("IntegerValueRefined circe roundtrip"):
      forall(genDummyPositiveInt) { v =>
        val json    = v.asJson
        val decoded = json.as[DummyPositiveInt]
        expect(decoded.toOption.contains(v))
      }

  test("IntegerValueRefined BytesCodec roundtrip"):
      forall(genDummyPositiveInt) { v =>
        val encoded = BytesCodec[DummyPositiveInt].encodeToScalar(v)
        val decoded = BytesCodec[DummyPositiveInt].decodeFromScalar(encoded)
        expect(decoded.toOption.contains(v))
      }

  test("IntegerValueRefined Base64Codec roundtrip"):
      forall(genDummyPositiveInt) { v =>
        val encoded = Base64Codec[DummyPositiveInt].encode(v)
        val decoded = Base64Codec[DummyPositiveInt].decode(encoded)
        expect(decoded.toOption.contains(v))
      }

  // ---------------------------------------------------------------------------
  // Type class instance tests
  // ---------------------------------------------------------------------------

  test("IntegerValueRefined Eq instance"):
      forall(genDummyPositiveIntPair) { case (a, b) =>
        val eqInstance = summon[Eq[DummyPositiveInt]]
        expect(eqInstance.eqv(a, a)) and
          expect(eqInstance.eqv(b, b)) and
          expect(eqInstance.eqv(a, b) == (a.unwrap == b.unwrap))
      }

  test("IntegerValueRefined Order instance"):
      forall(genDummyPositiveIntPair) { case (a, b) =>
        val orderInstance = summon[Order[DummyPositiveInt]]
        val cmp           = orderInstance.compare(a, b)
        expect(orderInstance.compare(a, a) == 0) and
          expect(cmp == Integer.compare(a.unwrap, b.unwrap))
      }

  test("IntegerValueRefined Show instance"):
      forall(genDummyPositiveInt) { v =>
        val showInstance = summon[Show[DummyPositiveInt]]
        expect(showInstance.show(v) == v.unwrap.toString)
      }

  test("IntegerValueRefined Ordering instance"):
      forall(genDummyPositiveIntPair) { case (a, b) =>
        val orderingInstance = summon[Ordering[DummyPositiveInt]]
        expect(orderingInstance.compare(a, b) == Integer.compare(a.unwrap, b.unwrap))
      }

  // ---------------------------------------------------------------------------
  // Unwrap test
  // ---------------------------------------------------------------------------

  test("IntegerValueRefined unwrap returns underlying value"):
      forall(genDummyPositiveInt) { v =>
        expect(v.unwrap.isInstanceOf[Int]) and
          expect(v.unwrap > 0)
      }

  // ---------------------------------------------------------------------------
  // Refinement validation tests
  // ---------------------------------------------------------------------------

  pureTest("IntegerValueRefined.from rejects zero") {
    val result = DummyPositiveInt.from(0)
    expect(result.isLeft)
  }

  pureTest("IntegerValueRefined.from rejects negative") {
    val result = DummyPositiveInt.from(-5)
    expect(result.isLeft)
  }

  pureTest("IntegerValueRefined.from accepts positive integer") {
    val result = DummyPositiveInt.from(42)
    expect(result.isRight) and
      expect(result.toOption.map(_.unwrap).contains(42))
  }

  // ---------------------------------------------------------------------------
  // Edge case tests - boundary values
  // ---------------------------------------------------------------------------

  pureTest("IntegerValueRefined handles Int.MaxValue") {
    val v       = DummyPositiveInt.unsafeFrom(Int.MaxValue)
    val codec   = summon[SCodec[DummyPositiveInt]]
    val encoded = codec.encode(v)
    val decoded = encoded.flatMap(codec.decode)
    expect(decoded.toOption.exists(_.value == v)) and
      expect(v.unwrap == Int.MaxValue)
  }

  pureTest("IntegerValueRefined handles minimum positive (1)") {
    val v       = DummyPositiveInt.unsafeFrom(1)
    val encoded = BytesCodec[DummyPositiveInt].encodeToScalar(v)
    val decoded = BytesCodec[DummyPositiveInt].decodeFromScalar(encoded)
    expect(decoded.toOption.contains(v)) and
      expect(v.unwrap == 1)
  }

  pureTest("IntegerValueRefined scodec decode fails for zero (refinement)") {
    val codec = summon[SCodec[DummyPositiveInt]]
    // Encode zero as raw int32
    val encoded = scodec.codecs.int32.encode(0)
    val decoded = encoded.flatMap(codec.decode)
    expect(decoded.isFailure)
  }

  pureTest("IntegerValueRefined scodec decode fails for negative (refinement)") {
    val codec   = summon[SCodec[DummyPositiveInt]]
    val encoded = scodec.codecs.int32.encode(-100)
    val decoded = encoded.flatMap(codec.decode)
    expect(decoded.isFailure)
  }

  pureTest("IntegerValueRefined Eq correctly compares identical values") {
    val v1 = DummyPositiveInt.unsafeFrom(42)
    val v2 = DummyPositiveInt.unsafeFrom(42)
    expect(summon[Eq[DummyPositiveInt]].eqv(v1, v2))
  }

  pureTest("IntegerValueRefined Order is numeric") {
    val a = DummyPositiveInt.unsafeFrom(1)
    val b = DummyPositiveInt.unsafeFrom(100)
    expect(summon[Order[DummyPositiveInt]].compare(a, b) < 0) and
      expect(summon[Order[DummyPositiveInt]].compare(b, a) > 0)
  }

  pureTest("IntegerValueRefined BytesCodec encodes to 4 bytes") {
    val v       = DummyPositiveInt.unsafeFrom(12345)
    val encoded = BytesCodec[DummyPositiveInt].encodeToScalar(v)
    expect(encoded.size == 4)
  }

  pureTest("IntegerValueRefined Base64Codec roundtrip for boundary value") {
    val maxVal  = DummyPositiveInt.unsafeFrom(Int.MaxValue)
    val encoded = Base64Codec[DummyPositiveInt].encode(maxVal)
    val decoded = Base64Codec[DummyPositiveInt].decode(encoded)
    expect(decoded.toOption.contains(maxVal))
  }
