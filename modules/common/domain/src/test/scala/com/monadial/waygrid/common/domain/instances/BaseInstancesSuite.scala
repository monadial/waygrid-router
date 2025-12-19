package com.monadial.waygrid.common.domain.instances

import java.time.Instant

import cats.Show
import com.monadial.waygrid.common.domain.algebra.value.codec.{ Base64Codec, BytesCodec }
import com.monadial.waygrid.common.domain.instances.ByteVectorInstances.given
import com.monadial.waygrid.common.domain.instances.InstantInstances.given
import com.monadial.waygrid.common.domain.instances.IntegerInstances.given
import com.monadial.waygrid.common.domain.instances.LongInstances.given
import com.monadial.waygrid.common.domain.instances.StringInstances.given
import com.monadial.waygrid.common.domain.instances.ULIDInstances.given
import com.monadial.waygrid.common.domain.instances.URIInstances.given
import io.circe.syntax.*
import io.circe.{ Decoder as JsonDecoder, Encoder as JsonEncoder }
import org.http4s.Uri
import org.scalacheck.Gen
import scodec.bits.ByteVector
import scodec.{ Codec as SCodec, Decoder as SDecoder, Encoder as SEncoder }
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers
import wvlet.airframe.ulid.ULID

/**
 * Comprehensive tests for base value type instances.
 * Tests scodec, circe, BytesCodec, and Base64Codec for all primitive types.
 */
object BaseInstancesSuite extends SimpleIOSuite with Checkers:

  // ---------------------------------------------------------------------------
  // Show instances for ScalaCheck
  // ---------------------------------------------------------------------------

  given Show[String]     = Show.fromToString
  given Show[Int]        = Show.fromToString
  given Show[Long]       = Show.fromToString
  given Show[ULID]       = Show.fromToString
  given Show[Uri]        = Show.fromToString
  given Show[Instant]    = Show.fromToString
  given Show[ByteVector] = Show.fromToString

  // ---------------------------------------------------------------------------
  // Generators
  // ---------------------------------------------------------------------------

  private val genString: Gen[String] =
    Gen.alphaNumStr.map(_.take(100))

  private val genStringWithSpecialChars: Gen[String] =
    Gen.oneOf(
      Gen.alphaNumStr,
      Gen.const("Hello, 世界! 🌍"),
      Gen.const("Special: !@#$%^&*()_+-=[]{}|;':\",./<>?"),
      Gen.const(""),
      Gen.const("  spaces  ")
    )

  private val genInt: Gen[Int] =
    Gen.choose(Int.MinValue, Int.MaxValue)

  private val genLong: Gen[Long] =
    Gen.choose(Long.MinValue, Long.MaxValue)

  private val genULID: Gen[ULID] =
    Gen.delay(Gen.const(ULID.newULID))

  private val genUri: Gen[Uri] =
    for
      scheme <- Gen.oneOf("http", "https", "waygrid")
      host   <- Gen.alphaLowerStr.map(_.take(20)).suchThat(_.nonEmpty)
      path   <- Gen.alphaNumStr.map(_.take(30))
    yield Uri.unsafeFromString(s"$scheme://$host/$path")

  private val genInstant: Gen[Instant] =
    Gen.choose(0L, System.currentTimeMillis() * 2).map(Instant.ofEpochMilli)

  private val genByteVector: Gen[ByteVector] =
    Gen.listOf(Gen.choose(Byte.MinValue, Byte.MaxValue)).map(bytes => ByteVector(bytes*))

  // ---------------------------------------------------------------------------
  // Helper functions for testing
  // ---------------------------------------------------------------------------

  private def testScodecRoundtrip[A: {SEncoder, SDecoder}](value: A): Boolean =
    val codec = SCodec(summon[SEncoder[A]], summon[SDecoder[A]])
    val result =
      for
        bits    <- codec.encode(value)
        decoded <- codec.decode(bits)
      yield decoded.value == value && decoded.remainder.isEmpty
    result.toOption.getOrElse(false)

  private def testCirceRoundtrip[A: {JsonEncoder, JsonDecoder}](value: A): Boolean =
    val json    = value.asJson
    val decoded = json.as[A]
    decoded.toOption.contains(value)

  private def testBytesCodecRoundtrip[A: BytesCodec](value: A): Boolean =
    val encoded = BytesCodec[A].encodeToScalar(value)
    val decoded = BytesCodec[A].decodeFromScalar(encoded)
    decoded.toOption.contains(value)

  private def testBase64CodecRoundtrip[A: Base64Codec](value: A): Boolean =
    val encoded = Base64Codec[A].encode(value)
    val decoded = Base64Codec[A].decode(encoded)
    decoded.toOption.contains(value)

  // ===========================================================================
  // String tests
  // ===========================================================================

  test("String scodec roundtrip"):
      forall(genString) { s =>
        expect(testScodecRoundtrip(s))
      }

  test("String scodec roundtrip with special characters"):
      forall(genStringWithSpecialChars) { s =>
        expect(testScodecRoundtrip(s))
      }

  test("String circe roundtrip"):
      forall(genString) { s =>
        expect(testCirceRoundtrip(s))
      }

  test("String BytesCodec roundtrip"):
      forall(genString) { s =>
        expect(testBytesCodecRoundtrip(s))
      }

  test("String Base64Codec roundtrip"):
      forall(genString) { s =>
        expect(testBase64CodecRoundtrip(s))
      }

  // ===========================================================================
  // Int tests
  // ===========================================================================

  test("Int scodec roundtrip"):
      forall(genInt) { i =>
        expect(testScodecRoundtrip(i))
      }

  test("Int circe roundtrip"):
      forall(genInt) { i =>
        expect(testCirceRoundtrip(i))
      }

  test("Int BytesCodec roundtrip"):
      forall(genInt) { i =>
        expect(testBytesCodecRoundtrip(i))
      }

  test("Int Base64Codec roundtrip"):
      forall(genInt) { i =>
        expect(testBase64CodecRoundtrip(i))
      }

  // ===========================================================================
  // Long tests
  // ===========================================================================

  test("Long scodec roundtrip"):
      forall(genLong) { l =>
        expect(testScodecRoundtrip(l))
      }

  test("Long circe roundtrip"):
      forall(genLong) { l =>
        expect(testCirceRoundtrip(l))
      }

  test("Long BytesCodec roundtrip"):
      forall(genLong) { l =>
        expect(testBytesCodecRoundtrip(l))
      }

  test("Long Base64Codec roundtrip"):
      forall(genLong) { l =>
        expect(testBase64CodecRoundtrip(l))
      }

  // ===========================================================================
  // ULID tests
  // ===========================================================================

  test("ULID scodec roundtrip"):
      forall(genULID) { ulid =>
        expect(testScodecRoundtrip(ulid))
      }

  test("ULID circe roundtrip"):
      forall(genULID) { ulid =>
        expect(testCirceRoundtrip(ulid))
      }

  test("ULID BytesCodec roundtrip"):
      forall(genULID) { ulid =>
        expect(testBytesCodecRoundtrip(ulid))
      }

  test("ULID Base64Codec roundtrip"):
      forall(genULID) { ulid =>
        expect(testBase64CodecRoundtrip(ulid))
      }

  // ===========================================================================
  // Uri tests
  // ===========================================================================

  test("Uri scodec roundtrip"):
      forall(genUri) { uri =>
        expect(testScodecRoundtrip(uri))
      }

  test("Uri circe roundtrip"):
      forall(genUri) { uri =>
        expect(testCirceRoundtrip(uri))
      }

  test("Uri BytesCodec roundtrip"):
      forall(genUri) { uri =>
        expect(testBytesCodecRoundtrip(uri))
      }

  test("Uri Base64Codec roundtrip"):
      forall(genUri) { uri =>
        expect(testBase64CodecRoundtrip(uri))
      }

  // ===========================================================================
  // Instant tests
  // ===========================================================================

  test("Instant scodec roundtrip"):
      forall(genInstant) { instant =>
        expect(testScodecRoundtrip(instant))
      }

  test("Instant circe roundtrip"):
      forall(genInstant) { instant =>
        expect(testCirceRoundtrip(instant))
      }

  test("Instant BytesCodec roundtrip"):
      forall(genInstant) { instant =>
        expect(testBytesCodecRoundtrip(instant))
      }

  test("Instant Base64Codec roundtrip"):
      forall(genInstant) { instant =>
        expect(testBase64CodecRoundtrip(instant))
      }

  // ===========================================================================
  // ByteVector tests
  // ===========================================================================

  test("ByteVector scodec roundtrip"):
      forall(genByteVector) { bv =>
        expect(testScodecRoundtrip(bv))
      }

  test("ByteVector circe roundtrip"):
      forall(genByteVector) { bv =>
        expect(testCirceRoundtrip(bv))
      }

  test("ByteVector BytesCodec roundtrip"):
      forall(genByteVector) { bv =>
        expect(testBytesCodecRoundtrip(bv))
      }

  test("ByteVector Base64Codec roundtrip"):
      forall(genByteVector) { bv =>
        expect(testBase64CodecRoundtrip(bv))
      }
