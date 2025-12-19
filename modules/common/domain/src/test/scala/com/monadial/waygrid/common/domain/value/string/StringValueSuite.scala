package com.monadial.waygrid.common.domain.value.string

import cats.{ Eq, Order, Show }
import com.monadial.waygrid.common.domain.algebra.value.codec.{ Base64Codec, BytesCodec }
import com.monadial.waygrid.common.domain.algebra.value.string.StringValue
import io.circe.syntax.*
import org.scalacheck.Gen
import scodec.Codec as SCodec
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers

/**
 * Tests for StringValue abstraction using a dummy value object.
 * Verifies that all type class instances are correctly derived.
 */
object StringValueSuite extends SimpleIOSuite with Checkers:

  // ---------------------------------------------------------------------------
  // Dummy StringValue for testing
  // ---------------------------------------------------------------------------

  type DummyStringId = DummyStringId.Type
  object DummyStringId extends StringValue

  // ---------------------------------------------------------------------------
  // Generators
  // ---------------------------------------------------------------------------

  given Show[DummyStringId] = Show.fromToString

  private val genDummyStringId: Gen[DummyStringId] =
    Gen.nonEmptyListOf(Gen.alphaNumChar).map(_.take(50).mkString).map(DummyStringId(_))

  private val genDummyStringIdPair: Gen[(DummyStringId, DummyStringId)] =
    for
      a <- genDummyStringId
      b <- genDummyStringId
    yield (a, b)

  // ---------------------------------------------------------------------------
  // Codec roundtrip tests
  // ---------------------------------------------------------------------------

  test("StringValue scodec roundtrip"):
      forall(genDummyStringId) { v =>
        val codec  = summon[SCodec[DummyStringId]]
        val result = codec.encode(v).flatMap(codec.decode)
        expect(result.toOption.exists(r => r.value == v && r.remainder.isEmpty))
      }

  test("StringValue circe roundtrip"):
      forall(genDummyStringId) { v =>
        val json    = v.asJson
        val decoded = json.as[DummyStringId]
        expect(decoded.toOption.contains(v))
      }

  test("StringValue BytesCodec roundtrip"):
      forall(genDummyStringId) { v =>
        val encoded = BytesCodec[DummyStringId].encodeToScalar(v)
        val decoded = BytesCodec[DummyStringId].decodeFromScalar(encoded)
        expect(decoded.toOption.contains(v))
      }

  test("StringValue Base64Codec roundtrip"):
      forall(genDummyStringId) { v =>
        val encoded = Base64Codec[DummyStringId].encode(v)
        val decoded = Base64Codec[DummyStringId].decode(encoded)
        expect(decoded.toOption.contains(v))
      }

  // ---------------------------------------------------------------------------
  // Type class instance tests
  // ---------------------------------------------------------------------------

  test("StringValue Eq instance"):
      forall(genDummyStringIdPair) { case (a, b) =>
        val eqInstance = summon[Eq[DummyStringId]]
        expect(eqInstance.eqv(a, a)) and
          expect(eqInstance.eqv(b, b)) and
          expect(eqInstance.eqv(a, b) == (a.unwrap == b.unwrap))
      }

  test("StringValue Order instance"):
      forall(genDummyStringIdPair) { case (a, b) =>
        val orderInstance = summon[Order[DummyStringId]]
        val cmp           = orderInstance.compare(a, b)
        expect(orderInstance.compare(a, a) == 0) and
          expect(cmp == a.unwrap.compare(b.unwrap))
      }

  test("StringValue Show instance"):
      forall(genDummyStringId) { v =>
        val showInstance = summon[Show[DummyStringId]]
        expect(showInstance.show(v) == v.unwrap)
      }

  test("StringValue Ordering instance"):
      forall(genDummyStringIdPair) { case (a, b) =>
        val orderingInstance = summon[Ordering[DummyStringId]]
        expect(orderingInstance.compare(a, b) == a.unwrap.compare(b.unwrap))
      }

  // ---------------------------------------------------------------------------
  // Unwrap test
  // ---------------------------------------------------------------------------

  test("StringValue unwrap returns underlying value"):
      forall(genDummyStringId) { v =>
        expect(v.unwrap.isInstanceOf[String])
      }

  // ---------------------------------------------------------------------------
  // Edge case tests - special characters
  // ---------------------------------------------------------------------------

  private val genSpecialChars: Gen[DummyStringId] =
    Gen.oneOf(
      "hello\nworld",          // newline
      "tab\there",             // tab
      "quote\"inside",         // double quote
      "back\\slash",           // backslash
      "emoji🎉test",           // emoji
      "unicode™®©",            // special unicode
      "中文字符",                  // Chinese characters
      "Ελληνικά",              // Greek
      "العربية",               // Arabic
      "null\u0000byte",        // null byte
      " leading",              // leading space
      "trailing ",             // trailing space
      "  multiple   spaces  ", // multiple spaces
      "a",                     // single char
      "🔥💧🌍🌬️"              // multiple emojis
    ).map(DummyStringId(_))

  test("StringValue scodec roundtrip with special characters"):
      forall(genSpecialChars) { v =>
        val codec  = summon[SCodec[DummyStringId]]
        val result = codec.encode(v).flatMap(codec.decode)
        expect(result.toOption.exists(r => r.value == v && r.remainder.isEmpty))
      }

  test("StringValue circe roundtrip with special characters"):
      forall(genSpecialChars) { v =>
        val json    = v.asJson
        val decoded = json.as[DummyStringId]
        expect(decoded.toOption.contains(v))
      }

  test("StringValue BytesCodec roundtrip with special characters"):
      forall(genSpecialChars) { v =>
        val encoded = BytesCodec[DummyStringId].encodeToScalar(v)
        val decoded = BytesCodec[DummyStringId].decodeFromScalar(encoded)
        expect(decoded.toOption.contains(v))
      }

  test("StringValue Base64Codec roundtrip with special characters"):
      forall(genSpecialChars) { v =>
        val encoded = Base64Codec[DummyStringId].encode(v)
        val decoded = Base64Codec[DummyStringId].decode(encoded)
        expect(decoded.toOption.contains(v))
      }

  // ---------------------------------------------------------------------------
  // Edge case tests - boundary values
  // ---------------------------------------------------------------------------

  pureTest("StringValue handles empty string") {
    val v       = DummyStringId("")
    val codec   = summon[SCodec[DummyStringId]]
    val encoded = codec.encode(v)
    val decoded = encoded.flatMap(codec.decode)
    expect(decoded.toOption.exists(_.value == v))
  }

  pureTest("StringValue handles very long string") {
    val longStr = "x" * 10000
    val v       = DummyStringId(longStr)
    val encoded = BytesCodec[DummyStringId].encodeToScalar(v)
    val decoded = BytesCodec[DummyStringId].decodeFromScalar(encoded)
    expect(decoded.toOption.contains(v)) and
      expect(v.unwrap.length == 10000)
  }

  pureTest("StringValue Eq correctly compares identical values") {
    val v1 = DummyStringId("test")
    val v2 = DummyStringId("test")
    expect(summon[Eq[DummyStringId]].eqv(v1, v2))
  }

  pureTest("StringValue Eq correctly compares different values") {
    val v1 = DummyStringId("test1")
    val v2 = DummyStringId("test2")
    expect(!summon[Eq[DummyStringId]].eqv(v1, v2))
  }

  pureTest("StringValue Order is lexicographic") {
    val a = DummyStringId("apple")
    val b = DummyStringId("banana")
    expect(summon[Order[DummyStringId]].compare(a, b) < 0) and
      expect(summon[Order[DummyStringId]].compare(b, a) > 0)
  }
