package com.monadial.waygrid.common.domain.value.long

import cats.{ Eq, Order, Show }
import com.monadial.waygrid.common.domain.algebra.value.codec.{ Base64Codec, BytesCodec }
import com.monadial.waygrid.common.domain.algebra.value.long.LongValueRefined
import eu.timepit.refined.numeric.Positive
import io.circe.syntax.*
import org.scalacheck.Gen
import scodec.Codec as SCodec
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers

/**
 * Tests for LongValueRefined abstraction using a dummy refined type.
 * Verifies that all type class instances are correctly derived for refined long values.
 */
object LongValueRefinedSuite extends SimpleIOSuite with Checkers:

  // ---------------------------------------------------------------------------
  // Dummy LongValueRefined for testing (Positive long)
  // ---------------------------------------------------------------------------

  type DummyPositiveLong = DummyPositiveLong.Type
  object DummyPositiveLong extends LongValueRefined[Positive]

  // ---------------------------------------------------------------------------
  // Generators
  // ---------------------------------------------------------------------------

  given Show[DummyPositiveLong] = Show.fromToString

  private val genDummyPositiveLong: Gen[DummyPositiveLong] =
    Gen.choose(1L, Long.MaxValue).map(DummyPositiveLong.unsafeFrom)

  private val genDummyPositiveLongPair: Gen[(DummyPositiveLong, DummyPositiveLong)] =
    Gen.zip(genDummyPositiveLong, genDummyPositiveLong)

  // ---------------------------------------------------------------------------
  // Codec roundtrip tests
  // ---------------------------------------------------------------------------

  test("LongValueRefined scodec roundtrip"):
    forall(genDummyPositiveLong) { v =>
      val codec  = summon[SCodec[DummyPositiveLong]]
      val result = codec.encode(v).flatMap(codec.decode)
      expect(result.toOption.exists(r => r.value == v && r.remainder.isEmpty))
    }

  test("LongValueRefined circe roundtrip"):
    forall(genDummyPositiveLong) { v =>
      val json    = v.asJson
      val decoded = json.as[DummyPositiveLong]
      expect(decoded.toOption.contains(v))
    }

  test("LongValueRefined BytesCodec roundtrip"):
    forall(genDummyPositiveLong) { v =>
      val encoded = BytesCodec[DummyPositiveLong].encodeToScalar(v)
      val decoded = BytesCodec[DummyPositiveLong].decodeFromScalar(encoded)
      expect(decoded.toOption.contains(v))
    }

  test("LongValueRefined Base64Codec roundtrip"):
    forall(genDummyPositiveLong) { v =>
      val encoded = Base64Codec[DummyPositiveLong].encode(v)
      val decoded = Base64Codec[DummyPositiveLong].decode(encoded)
      expect(decoded.toOption.contains(v))
    }

  // ---------------------------------------------------------------------------
  // Type class instance tests
  // ---------------------------------------------------------------------------

  test("LongValueRefined Eq instance"):
    forall(genDummyPositiveLongPair) { case (a, b) =>
      val eqInstance = summon[Eq[DummyPositiveLong]]
      expect(eqInstance.eqv(a, a)) and
        expect(eqInstance.eqv(b, b)) and
        expect(eqInstance.eqv(a, b) == (a.unwrap == b.unwrap))
    }

  test("LongValueRefined Order instance"):
    forall(genDummyPositiveLongPair) { case (a, b) =>
      val orderInstance = summon[Order[DummyPositiveLong]]
      val cmp           = orderInstance.compare(a, b)
      expect(orderInstance.compare(a, a) == 0) and
        expect(cmp == java.lang.Long.compare(a.unwrap, b.unwrap))
    }

  test("LongValueRefined Show instance"):
    forall(genDummyPositiveLong) { v =>
      val showInstance = summon[Show[DummyPositiveLong]]
      expect(showInstance.show(v) == v.unwrap.toString)
    }

  test("LongValueRefined Ordering instance"):
    forall(genDummyPositiveLongPair) { case (a, b) =>
      val orderingInstance = summon[Ordering[DummyPositiveLong]]
      expect(orderingInstance.compare(a, b) == java.lang.Long.compare(a.unwrap, b.unwrap))
    }

  // ---------------------------------------------------------------------------
  // Unwrap test
  // ---------------------------------------------------------------------------

  test("LongValueRefined unwrap returns underlying value"):
    forall(genDummyPositiveLong) { v =>
      expect(v.unwrap.isInstanceOf[Long]) and
        expect(v.unwrap > 0L)
    }

  // ---------------------------------------------------------------------------
  // Refinement validation tests
  // ---------------------------------------------------------------------------

  pureTest("LongValueRefined.from rejects zero") {
    val result = DummyPositiveLong.from(0L)
    expect(result.isLeft)
  }

  pureTest("LongValueRefined.from rejects negative") {
    val result = DummyPositiveLong.from(-100L)
    expect(result.isLeft)
  }

  pureTest("LongValueRefined.from accepts positive long") {
    val result = DummyPositiveLong.from(999L)
    expect(result.isRight) and
      expect(result.toOption.map(_.unwrap).contains(999L))
  }

  // ---------------------------------------------------------------------------
  // Edge case tests - boundary values
  // ---------------------------------------------------------------------------

  pureTest("LongValueRefined handles Long.MaxValue") {
    val v       = DummyPositiveLong.unsafeFrom(Long.MaxValue)
    val codec   = summon[SCodec[DummyPositiveLong]]
    val encoded = codec.encode(v)
    val decoded = encoded.flatMap(codec.decode)
    expect(decoded.toOption.exists(_.value == v)) and
      expect(v.unwrap == Long.MaxValue)
  }

  pureTest("LongValueRefined handles minimum positive (1)") {
    val v       = DummyPositiveLong.unsafeFrom(1L)
    val encoded = BytesCodec[DummyPositiveLong].encodeToScalar(v)
    val decoded = BytesCodec[DummyPositiveLong].decodeFromScalar(encoded)
    expect(decoded.toOption.contains(v)) and
      expect(v.unwrap == 1L)
  }

  pureTest("LongValueRefined handles values beyond Int range") {
    val beyondInt = Int.MaxValue.toLong + 1000L
    val v         = DummyPositiveLong.unsafeFrom(beyondInt)
    val encoded   = BytesCodec[DummyPositiveLong].encodeToScalar(v)
    val decoded   = BytesCodec[DummyPositiveLong].decodeFromScalar(encoded)
    expect(decoded.toOption.contains(v)) and
      expect(v.unwrap == beyondInt)
  }

  pureTest("LongValueRefined scodec decode fails for zero (refinement)") {
    val codec   = summon[SCodec[DummyPositiveLong]]
    val encoded = scodec.codecs.int64.encode(0L)
    val decoded = encoded.flatMap(codec.decode)
    expect(decoded.isFailure)
  }

  pureTest("LongValueRefined scodec decode fails for negative (refinement)") {
    val codec   = summon[SCodec[DummyPositiveLong]]
    val encoded = scodec.codecs.int64.encode(-100L)
    val decoded = encoded.flatMap(codec.decode)
    expect(decoded.isFailure)
  }

  pureTest("LongValueRefined Eq correctly compares identical values") {
    val v1 = DummyPositiveLong.unsafeFrom(42L)
    val v2 = DummyPositiveLong.unsafeFrom(42L)
    expect(summon[Eq[DummyPositiveLong]].eqv(v1, v2))
  }

  pureTest("LongValueRefined Order is numeric") {
    val a = DummyPositiveLong.unsafeFrom(1L)
    val b = DummyPositiveLong.unsafeFrom(100L)
    expect(summon[Order[DummyPositiveLong]].compare(a, b) < 0) and
      expect(summon[Order[DummyPositiveLong]].compare(b, a) > 0)
  }

  pureTest("LongValueRefined BytesCodec encodes to 8 bytes") {
    val v       = DummyPositiveLong.unsafeFrom(12345L)
    val encoded = BytesCodec[DummyPositiveLong].encodeToScalar(v)
    expect(encoded.size == 8)
  }

  pureTest("LongValueRefined Base64Codec roundtrip for boundary value") {
    val maxVal  = DummyPositiveLong.unsafeFrom(Long.MaxValue)
    val encoded = Base64Codec[DummyPositiveLong].encode(maxVal)
    val decoded = Base64Codec[DummyPositiveLong].decode(encoded)
    expect(decoded.toOption.contains(maxVal))
  }
