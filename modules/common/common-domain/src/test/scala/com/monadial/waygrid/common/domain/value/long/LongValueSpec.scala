package com.monadial.waygrid.common.domain.value.long

import cats.effect.IO
import cats.implicits.*
import com.monadial.waygrid.common.domain.instances.LongInstances.given
import com.monadial.waygrid.common.domain.value.codec.{Base64Codec, BytesCodec, JsonCodec}
import weaver.*
import weaver.scalacheck.Checkers

object LongValueSpec extends SimpleIOSuite with Checkers:

  // Define a concrete implementation of LongValue for testing
  final case class TestLong(value: Long) extends LongValue

  object TestLong:
    // Create a new TestLong
    def create(l: Long): IO[TestLong] =
      IO.pure(TestLong(l))

  test("LongValue should be created") {
    val testValue = 12345678901234L
    for
      long <- TestLong.create(testValue)
      _ <- IO.println(s"Created LongValue: ${long.value}")
    yield expect(long.value == testValue)
  }

  test("LongValue should be encodable to and decodable from bytes") {
    val testValue = 12345678901234L
    for
      original <- TestLong.create(testValue)
      bytes = BytesCodec[Long].encode(original.value)
      _ <- IO.println(s"Long as bytes: ${bytes.mkString("[", ", ", "]")}")
      fromBytes = BytesCodec[Long].decode(bytes)
    yield expect(original.value == fromBytes)
  }

  test("LongValue should be encodable to and decodable from Base64") {
    val testValue = 12345678901234L
    for
      original <- TestLong.create(testValue)
      base64 = Base64Codec[Long].encode(original.value)
      _ <- IO.println(s"Long as Base64: $base64")
      fromBase64 = Base64Codec[Long].decode(base64)
    yield expect(original.value == fromBase64)
  }

  test("LongValue should handle zero") {
    val testValue = 0L
    for
      original <- TestLong.create(testValue)
      bytes = BytesCodec[Long].encode(original.value)
      fromBytes = BytesCodec[Long].decode(bytes)
      base64 = Base64Codec[Long].encode(original.value)
      fromBase64 = Base64Codec[Long].decode(base64)
    yield expect(original.value == fromBytes) and expect(original.value == fromBase64)
  }

  test("LongValue should handle negative values") {
    val testValue = -9876543210L
    for
      original <- TestLong.create(testValue)
      bytes = BytesCodec[Long].encode(original.value)
      fromBytes = BytesCodec[Long].decode(bytes)
      base64 = Base64Codec[Long].encode(original.value)
      fromBase64 = Base64Codec[Long].decode(base64)
    yield expect(original.value == fromBytes) and expect(original.value == fromBase64)
  }

  test("LongValue should handle min and max values") {
    for
      minOriginal <- TestLong.create(Long.MinValue)
      minBytes = BytesCodec[Long].encode(minOriginal.value)
      minFromBytes = BytesCodec[Long].decode(minBytes)
      minBase64 = Base64Codec[Long].encode(minOriginal.value)
      minFromBase64 = Base64Codec[Long].decode(minBase64)

      maxOriginal <- TestLong.create(Long.MaxValue)
      maxBytes = BytesCodec[Long].encode(maxOriginal.value)
      maxFromBytes = BytesCodec[Long].decode(maxBytes)
      maxBase64 = Base64Codec[Long].encode(maxOriginal.value)
      maxFromBase64 = Base64Codec[Long].decode(maxBase64)
    yield
      expect(minOriginal.value == minFromBytes) and
      expect(minOriginal.value == minFromBase64) and
      expect(maxOriginal.value == maxFromBytes) and
      expect(maxOriginal.value == maxFromBase64)
  }

  test("LongValue should be encodable to and decodable from JSON") {
    val testValue = 12345678901234L
    for
      original <- TestLong.create(testValue)
      json = JsonCodec[Long].encode(original.value)
      _ <- IO.println(s"Long as JSON: $json")
      fromJson = JsonCodec[Long].decode(json)
    yield expect(original.value == fromJson)
  }

  test("LongValue should handle zero in JSON") {
    val testValue = 0L
    for
      original <- TestLong.create(testValue)
      json = JsonCodec[Long].encode(original.value)
      _ <- IO.println(s"Zero as JSON: $json")
      fromJson = JsonCodec[Long].decode(json)
    yield expect(original.value == fromJson)
  }

  test("LongValue should handle negative values in JSON") {
    val testValue = -9876543210L
    for
      original <- TestLong.create(testValue)
      json = JsonCodec[Long].encode(original.value)
      _ <- IO.println(s"Negative value as JSON: $json")
      fromJson = JsonCodec[Long].decode(json)
    yield expect(original.value == fromJson)
  }

  test("LongValue should handle min and max values in JSON") {
    for
      minOriginal <- TestLong.create(Long.MinValue)
      minJson = JsonCodec[Long].encode(minOriginal.value)
      _ <- IO.println(s"Min value as JSON: $minJson")
      minFromJson = JsonCodec[Long].decode(minJson)

      maxOriginal <- TestLong.create(Long.MaxValue)
      maxJson = JsonCodec[Long].encode(maxOriginal.value)
      _ <- IO.println(s"Max value as JSON: $maxJson")
      maxFromJson = JsonCodec[Long].decode(maxJson)
    yield
      expect(minOriginal.value == minFromJson) and
      expect(maxOriginal.value == maxFromJson)
  }
