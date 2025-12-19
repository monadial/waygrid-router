package com.monadial.waygrid.common.domain.value.bytes

import cats.{ Eq, Order, Show }
import com.monadial.waygrid.common.domain.algebra.value.bytes.BytesValueRefined
import com.monadial.waygrid.common.domain.algebra.value.codec.{ Base64Codec, BytesCodec }
import eu.timepit.refined.api.Validate
import eu.timepit.refined.boolean.Not
import eu.timepit.refined.collection.Empty
import io.circe.syntax.*
import org.scalacheck.Gen
import scodec.Codec as SCodec
import scodec.bits.ByteVector
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers

/**
 * Tests for BytesValueRefined abstraction using a dummy refined type.
 * Verifies that all type class instances are correctly derived for refined bytes values.
 */
object BytesValueRefinedSuite extends SimpleIOSuite with Checkers:

  // ---------------------------------------------------------------------------
  // Custom predicate for non-empty ByteVector
  // ---------------------------------------------------------------------------

  // Define NonEmpty as Not[Empty] for ByteVector
  type NonEmptyBytes = Not[Empty]

  // Provide Validate instance for ByteVector with Not[Empty]
  given Validate[ByteVector, NonEmptyBytes] =
    Validate.fromPredicate(
      bv => bv.nonEmpty,
      bv => s"ByteVector must be non-empty, got ${bv.size} bytes",
      Not(Empty())
    )

  // ---------------------------------------------------------------------------
  // Dummy BytesValueRefined for testing (NonEmpty bytes)
  // ---------------------------------------------------------------------------

  type DummyNonEmptyBytes = DummyNonEmptyBytes.Type
  object DummyNonEmptyBytes extends BytesValueRefined[NonEmptyBytes]

  // ---------------------------------------------------------------------------
  // Generators
  // ---------------------------------------------------------------------------

  given Show[DummyNonEmptyBytes] = Show.fromToString

  private val genDummyNonEmptyBytes: Gen[DummyNonEmptyBytes] =
    Gen.nonEmptyListOf(Gen.choose(Byte.MinValue, Byte.MaxValue))
      .map(bytes => DummyNonEmptyBytes.unsafeFrom(ByteVector(bytes.toArray)))

  private val genDummyNonEmptyBytesPair: Gen[(DummyNonEmptyBytes, DummyNonEmptyBytes)] =
    Gen.zip(genDummyNonEmptyBytes, genDummyNonEmptyBytes)

  // ---------------------------------------------------------------------------
  // Codec roundtrip tests
  // ---------------------------------------------------------------------------

  test("BytesValueRefined scodec roundtrip"):
      forall(genDummyNonEmptyBytes) { v =>
        val codec  = summon[SCodec[DummyNonEmptyBytes]]
        val result = codec.encode(v).flatMap(codec.decode)
        expect(result.toOption.exists(r => r.value == v && r.remainder.isEmpty))
      }

  test("BytesValueRefined circe roundtrip"):
      forall(genDummyNonEmptyBytes) { v =>
        val json    = v.asJson
        val decoded = json.as[DummyNonEmptyBytes]
        expect(decoded.toOption.contains(v))
      }

  test("BytesValueRefined BytesCodec roundtrip"):
      forall(genDummyNonEmptyBytes) { v =>
        val encoded = BytesCodec[DummyNonEmptyBytes].encodeToScalar(v)
        val decoded = BytesCodec[DummyNonEmptyBytes].decodeFromScalar(encoded)
        expect(decoded.toOption.contains(v))
      }

  test("BytesValueRefined Base64Codec roundtrip"):
      forall(genDummyNonEmptyBytes) { v =>
        val encoded = Base64Codec[DummyNonEmptyBytes].encode(v)
        val decoded = Base64Codec[DummyNonEmptyBytes].decode(encoded)
        expect(decoded.toOption.contains(v))
      }

  // ---------------------------------------------------------------------------
  // Type class instance tests
  // ---------------------------------------------------------------------------

  test("BytesValueRefined Eq instance"):
      forall(genDummyNonEmptyBytesPair) { case (a, b) =>
        val eqInstance = summon[Eq[DummyNonEmptyBytes]]
        expect(eqInstance.eqv(a, a)) and
          expect(eqInstance.eqv(b, b)) and
          expect(eqInstance.eqv(a, b) == (a.unwrap == b.unwrap))
      }

  test("BytesValueRefined Order instance"):
      forall(genDummyNonEmptyBytesPair) { case (a, b) =>
        val orderInstance = summon[Order[DummyNonEmptyBytes]]
        val cmp           = orderInstance.compare(a, b)
        expect(orderInstance.compare(a, a) == 0) and
          expect(cmp == a.unwrap.compareTo(b.unwrap))
      }

  test("BytesValueRefined Show instance"):
      forall(genDummyNonEmptyBytes) { v =>
        val showInstance = summon[Show[DummyNonEmptyBytes]]
        expect(showInstance.show(v).nonEmpty)
      }

  test("BytesValueRefined Ordering instance"):
      forall(genDummyNonEmptyBytesPair) { case (a, b) =>
        val orderingInstance = summon[Ordering[DummyNonEmptyBytes]]
        expect(orderingInstance.compare(a, b) == a.unwrap.compareTo(b.unwrap))
      }

  // ---------------------------------------------------------------------------
  // Unwrap test
  // ---------------------------------------------------------------------------

  test("BytesValueRefined unwrap returns underlying value"):
      forall(genDummyNonEmptyBytes) { v =>
        expect(v.unwrap.isInstanceOf[ByteVector]) and
          expect(v.unwrap.nonEmpty)
      }

  // ---------------------------------------------------------------------------
  // Refinement validation tests
  // ---------------------------------------------------------------------------

  pureTest("BytesValueRefined.from rejects empty ByteVector") {
    val result = DummyNonEmptyBytes.from(ByteVector.empty)
    expect(result.isLeft)
  }

  pureTest("BytesValueRefined.from accepts non-empty ByteVector") {
    val bytes  = ByteVector(1, 2, 3)
    val result = DummyNonEmptyBytes.from(bytes)
    expect(result.isRight) and
      expect(result.toOption.map(_.unwrap).contains(bytes))
  }

  // ---------------------------------------------------------------------------
  // Edge case tests - ByteVector specific
  // ---------------------------------------------------------------------------

  pureTest("BytesValueRefined handles single byte") {
    val v       = DummyNonEmptyBytes.unsafeFrom(ByteVector(0x42))
    val codec   = summon[SCodec[DummyNonEmptyBytes]]
    val encoded = codec.encode(v)
    val decoded = encoded.flatMap(codec.decode)
    expect(decoded.toOption.exists(_.value == v)) and
      expect(v.unwrap.size == 1)
  }

  pureTest("BytesValueRefined handles all zero bytes") {
    val bytes   = ByteVector.fill(16)(0)
    val v       = DummyNonEmptyBytes.unsafeFrom(bytes)
    val encoded = BytesCodec[DummyNonEmptyBytes].encodeToScalar(v)
    val decoded = BytesCodec[DummyNonEmptyBytes].decodeFromScalar(encoded)
    expect(decoded.toOption.contains(v)) and
      expect(v.unwrap.toArray.forall(_ == 0))
  }

  pureTest("BytesValueRefined handles all 0xFF bytes") {
    val bytes   = ByteVector.fill(16)(0xff.toByte)
    val v       = DummyNonEmptyBytes.unsafeFrom(bytes)
    val json    = v.asJson
    val decoded = json.as[DummyNonEmptyBytes]
    expect(decoded.toOption.contains(v)) and
      expect(v.unwrap.toArray.forall(_ == 0xff.toByte))
  }

  pureTest("BytesValueRefined handles large ByteVector") {
    val bytes   = ByteVector.fill(1024)(0x55.toByte)
    val v       = DummyNonEmptyBytes.unsafeFrom(bytes)
    val encoded = Base64Codec[DummyNonEmptyBytes].encode(v)
    val decoded = Base64Codec[DummyNonEmptyBytes].decode(encoded)
    expect(decoded.toOption.contains(v)) and
      expect(v.unwrap.size == 1024)
  }

  pureTest("BytesValueRefined handles byte boundary values") {
    val bytes   = ByteVector(Byte.MinValue, -1, 0, 1, Byte.MaxValue)
    val v       = DummyNonEmptyBytes.unsafeFrom(bytes)
    val codec   = summon[SCodec[DummyNonEmptyBytes]]
    val encoded = codec.encode(v)
    val decoded = encoded.flatMap(codec.decode)
    expect(decoded.toOption.exists(_.value == v))
  }

  pureTest("BytesValueRefined Eq correctly compares identical values") {
    val bytes = ByteVector(1, 2, 3)
    val v1    = DummyNonEmptyBytes.unsafeFrom(bytes)
    val v2    = DummyNonEmptyBytes.unsafeFrom(bytes)
    expect(summon[Eq[DummyNonEmptyBytes]].eqv(v1, v2))
  }

  pureTest("BytesValueRefined Eq correctly compares different values") {
    val v1 = DummyNonEmptyBytes.unsafeFrom(ByteVector(1, 2, 3))
    val v2 = DummyNonEmptyBytes.unsafeFrom(ByteVector(1, 2, 4))
    expect(!summon[Eq[DummyNonEmptyBytes]].eqv(v1, v2))
  }

  pureTest("BytesValueRefined Order compares lexicographically") {
    val a = DummyNonEmptyBytes.unsafeFrom(ByteVector(1, 2, 3))
    val b = DummyNonEmptyBytes.unsafeFrom(ByteVector(1, 2, 4))
    expect(summon[Order[DummyNonEmptyBytes]].compare(a, b) < 0) and
      expect(summon[Order[DummyNonEmptyBytes]].compare(b, a) > 0)
  }

  pureTest("BytesValueRefined Order handles different lengths") {
    val shorter = DummyNonEmptyBytes.unsafeFrom(ByteVector(1, 2))
    val longer  = DummyNonEmptyBytes.unsafeFrom(ByteVector(1, 2, 3))
    expect(summon[Order[DummyNonEmptyBytes]].compare(shorter, longer) < 0)
  }

  pureTest("BytesValueRefined scodec decode fails for empty (refinement)") {
    val codec   = summon[SCodec[DummyNonEmptyBytes]]
    val encoded = scodec.codecs.bytes.encode(ByteVector.empty)
    val decoded = encoded.flatMap(codec.decode)
    expect(decoded.isFailure)
  }

  pureTest("BytesValueRefined circe encodes to base64 string") {
    val v    = DummyNonEmptyBytes.unsafeFrom(ByteVector(0xde, 0xad, 0xbe, 0xef))
    val json = v.asJson
    expect(json.isString) and
      expect(json.asString.exists(_.nonEmpty))
  }

  pureTest("BytesValueRefined Base64Codec preserves byte order") {
    val bytes   = ByteVector((0 until 256).map(_.toByte)*)
    val v       = DummyNonEmptyBytes.unsafeFrom(bytes)
    val encoded = Base64Codec[DummyNonEmptyBytes].encode(v)
    val decoded = Base64Codec[DummyNonEmptyBytes].decode(encoded)
    expect(decoded.toOption.contains(v)) and
      expect(decoded.toOption.map(_.unwrap.toArray.toSeq).contains(bytes.toArray.toSeq))
  }

  pureTest("BytesValueRefined BytesCodec is identity (no length prefix)") {
    val bytes   = ByteVector(1, 2, 3, 4)
    val v       = DummyNonEmptyBytes.unsafeFrom(bytes)
    val encoded = BytesCodec[DummyNonEmptyBytes].encodeToScalar(v)
    expect(encoded == bytes)
  }
