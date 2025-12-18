package com.monadial.waygrid.common.domain.value.string

import cats.{ Eq, Order, Show }
import com.monadial.waygrid.common.domain.algebra.value.codec.{ Base64Codec, BytesCodec }
import com.monadial.waygrid.common.domain.algebra.value.string.StringValueRefined
import eu.timepit.refined.collection.NonEmpty
import io.circe.syntax.*
import org.scalacheck.Gen
import scodec.Codec as SCodec
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers

/**
 * Tests for StringValueRefined abstraction using a dummy refined type.
 * Verifies that all type class instances are correctly derived for refined string values.
 */
object StringValueRefinedSuite extends SimpleIOSuite with Checkers:

  // ---------------------------------------------------------------------------
  // Dummy StringValueRefined for testing (NonEmpty string)
  // ---------------------------------------------------------------------------

  type DummyNonEmptyString = DummyNonEmptyString.Type
  object DummyNonEmptyString extends StringValueRefined[NonEmpty]

  // ---------------------------------------------------------------------------
  // Generators
  // ---------------------------------------------------------------------------

  given Show[DummyNonEmptyString] = Show.fromToString

  private val genDummyNonEmptyString: Gen[DummyNonEmptyString] =
    Gen.nonEmptyListOf(Gen.alphaNumChar).map(_.take(50).mkString).map(DummyNonEmptyString.unsafeFrom)

  private val genDummyNonEmptyStringPair: Gen[(DummyNonEmptyString, DummyNonEmptyString)] =
    for
      a <- genDummyNonEmptyString
      b <- genDummyNonEmptyString
    yield (a, b)

  // ---------------------------------------------------------------------------
  // Codec roundtrip tests
  // ---------------------------------------------------------------------------

  test("StringValueRefined scodec roundtrip"):
    forall(genDummyNonEmptyString) { v =>
      val codec  = summon[SCodec[DummyNonEmptyString]]
      val result = codec.encode(v).flatMap(codec.decode)
      expect(result.toOption.exists(r => r.value == v && r.remainder.isEmpty))
    }

  test("StringValueRefined circe roundtrip"):
    forall(genDummyNonEmptyString) { v =>
      val json    = v.asJson
      val decoded = json.as[DummyNonEmptyString]
      expect(decoded.toOption.contains(v))
    }

  test("StringValueRefined BytesCodec roundtrip"):
    forall(genDummyNonEmptyString) { v =>
      val encoded = BytesCodec[DummyNonEmptyString].encodeToScalar(v)
      val decoded = BytesCodec[DummyNonEmptyString].decodeFromScalar(encoded)
      expect(decoded.toOption.contains(v))
    }

  test("StringValueRefined Base64Codec roundtrip"):
    forall(genDummyNonEmptyString) { v =>
      val encoded = Base64Codec[DummyNonEmptyString].encode(v)
      val decoded = Base64Codec[DummyNonEmptyString].decode(encoded)
      expect(decoded.toOption.contains(v))
    }

  // ---------------------------------------------------------------------------
  // Type class instance tests
  // ---------------------------------------------------------------------------

  test("StringValueRefined Eq instance"):
    forall(genDummyNonEmptyStringPair) { case (a, b) =>
      val eqInstance = summon[Eq[DummyNonEmptyString]]
      expect(eqInstance.eqv(a, a)) and
        expect(eqInstance.eqv(b, b)) and
        expect(eqInstance.eqv(a, b) == (a.unwrap == b.unwrap))
    }

  test("StringValueRefined Order instance"):
    forall(genDummyNonEmptyStringPair) { case (a, b) =>
      val orderInstance = summon[Order[DummyNonEmptyString]]
      val cmp           = orderInstance.compare(a, b)
      expect(orderInstance.compare(a, a) == 0) and
        expect(cmp == a.unwrap.compare(b.unwrap))
    }

  test("StringValueRefined Show instance"):
    forall(genDummyNonEmptyString) { v =>
      val showInstance = summon[Show[DummyNonEmptyString]]
      expect(showInstance.show(v) == v.unwrap)
    }

  test("StringValueRefined Ordering instance"):
    forall(genDummyNonEmptyStringPair) { case (a, b) =>
      val orderingInstance = summon[Ordering[DummyNonEmptyString]]
      expect(orderingInstance.compare(a, b) == a.unwrap.compare(b.unwrap))
    }

  // ---------------------------------------------------------------------------
  // Unwrap test
  // ---------------------------------------------------------------------------

  test("StringValueRefined unwrap returns underlying value"):
    forall(genDummyNonEmptyString) { v =>
      expect(v.unwrap.isInstanceOf[String]) and
        expect(v.unwrap.nonEmpty)
    }

  // ---------------------------------------------------------------------------
  // Refinement validation tests
  // ---------------------------------------------------------------------------

  pureTest("StringValueRefined.from rejects empty string") {
    val result = DummyNonEmptyString.from("")
    expect(result.isLeft)
  }

  pureTest("StringValueRefined.from accepts non-empty string") {
    val result = DummyNonEmptyString.from("hello")
    expect(result.isRight) and
      expect(result.toOption.map(_.unwrap).contains("hello"))
  }

  // ---------------------------------------------------------------------------
  // Edge case tests - special characters
  // ---------------------------------------------------------------------------

  private val genSpecialCharsNonEmpty: Gen[DummyNonEmptyString] =
    Gen.oneOf(
      "hello\nworld",           // newline
      "tab\there",              // tab
      "quote\"inside",          // double quote
      "back\\slash",            // backslash
      "emoji🎉test",            // emoji
      "unicode™®©",             // special unicode
      "中文字符",                // Chinese characters
      "Ελληνικά",              // Greek
      "العربية",                // Arabic
      " ",                      // single space (non-empty!)
      " leading",               // leading space
      "trailing ",              // trailing space
      "  multiple   spaces  ",  // multiple spaces
      "a",                      // single char
      "🔥💧🌍🌬️"                 // multiple emojis
    ).map(DummyNonEmptyString.unsafeFrom)

  test("StringValueRefined scodec roundtrip with special characters"):
    forall(genSpecialCharsNonEmpty) { v =>
      val codec  = summon[SCodec[DummyNonEmptyString]]
      val result = codec.encode(v).flatMap(codec.decode)
      expect(result.toOption.exists(r => r.value == v && r.remainder.isEmpty))
    }

  test("StringValueRefined circe roundtrip with special characters"):
    forall(genSpecialCharsNonEmpty) { v =>
      val json    = v.asJson
      val decoded = json.as[DummyNonEmptyString]
      expect(decoded.toOption.contains(v))
    }

  test("StringValueRefined BytesCodec roundtrip with special characters"):
    forall(genSpecialCharsNonEmpty) { v =>
      val encoded = BytesCodec[DummyNonEmptyString].encodeToScalar(v)
      val decoded = BytesCodec[DummyNonEmptyString].decodeFromScalar(encoded)
      expect(decoded.toOption.contains(v))
    }

  test("StringValueRefined Base64Codec roundtrip with special characters"):
    forall(genSpecialCharsNonEmpty) { v =>
      val encoded = Base64Codec[DummyNonEmptyString].encode(v)
      val decoded = Base64Codec[DummyNonEmptyString].decode(encoded)
      expect(decoded.toOption.contains(v))
    }

  // ---------------------------------------------------------------------------
  // Edge case tests - boundary values
  // ---------------------------------------------------------------------------

  pureTest("StringValueRefined handles single character") {
    val v       = DummyNonEmptyString.unsafeFrom("x")
    val codec   = summon[SCodec[DummyNonEmptyString]]
    val encoded = codec.encode(v)
    val decoded = encoded.flatMap(codec.decode)
    expect(decoded.toOption.exists(_.value == v)) and
      expect(v.unwrap.length == 1)
  }

  pureTest("StringValueRefined handles very long string") {
    val longStr = "x" * 10000
    val v       = DummyNonEmptyString.unsafeFrom(longStr)
    val encoded = BytesCodec[DummyNonEmptyString].encodeToScalar(v)
    val decoded = BytesCodec[DummyNonEmptyString].decodeFromScalar(encoded)
    expect(decoded.toOption.contains(v)) and
      expect(v.unwrap.length == 10000)
  }

  pureTest("StringValueRefined scodec decode fails for empty string (refinement)") {
    val codec   = summon[SCodec[DummyNonEmptyString]]
    // Encode an empty string as raw UTF-8 bytes
    val encoded = scodec.codecs.utf8.encode("")
    val decoded = encoded.flatMap(codec.decode)
    expect(decoded.isFailure)
  }

  pureTest("StringValueRefined Eq correctly compares identical values") {
    val v1 = DummyNonEmptyString.unsafeFrom("test")
    val v2 = DummyNonEmptyString.unsafeFrom("test")
    expect(summon[Eq[DummyNonEmptyString]].eqv(v1, v2))
  }

  pureTest("StringValueRefined Order is lexicographic") {
    val a = DummyNonEmptyString.unsafeFrom("apple")
    val b = DummyNonEmptyString.unsafeFrom("banana")
    expect(summon[Order[DummyNonEmptyString]].compare(a, b) < 0) and
      expect(summon[Order[DummyNonEmptyString]].compare(b, a) > 0)
  }

  pureTest("StringValueRefined handles whitespace-only string") {
    val v      = DummyNonEmptyString.unsafeFrom("   ")
    val json   = v.asJson
    val decoded = json.as[DummyNonEmptyString]
    expect(decoded.toOption.contains(v)) and
      expect(v.unwrap == "   ")
  }
