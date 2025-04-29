package com.monadial.waygrid.common.domain.value.string

import cats.effect.IO
import cats.implicits.*
import com.monadial.waygrid.common.domain.instances.StringInstances.given
import com.monadial.waygrid.common.domain.value.codec.{Base64Codec, BytesCodec, JsonCodec}
import weaver.*
import weaver.scalacheck.Checkers

object StringValueSpec extends SimpleIOSuite with Checkers:

  // Define a concrete implementation of StringValue for testing
  final case class TestString(value: String) extends StringValue

  object TestString:
    // Create a new TestString
    def create(s: String): IO[TestString] =
      IO.pure(TestString(s))

  test("StringValue should be created") {
    val testValue = "test string value"
    for
      str <- TestString.create(testValue)
      _ <- IO.println(s"Created StringValue: ${str.value}")
    yield expect(str.value == testValue)
  }

  test("StringValue should be encodable to and decodable from bytes") {
    val testValue = "test string value"
    for
      original <- TestString.create(testValue)
      bytes = BytesCodec[String].encode(original.value)
      _ <- IO.println(s"String as bytes: ${bytes.mkString("[", ", ", "]")}")
      fromBytes = BytesCodec[String].decode(bytes)
    yield expect(original.value == fromBytes)
  }

  test("StringValue should be encodable to and decodable from Base64") {
    val testValue = "test string value"
    for
      original <- TestString.create(testValue)
      base64 = Base64Codec[String].encode(original.value)
      _ <- IO.println(s"String as Base64: $base64")
      fromBase64 = Base64Codec[String].decode(base64)
    yield expect(original.value == fromBase64)
  }

  test("StringValue should handle special characters") {
    val testValue = "Special chars: !@#$%^&*()_+{}|:<>?~`-=[]\\;',./€"
    for
      original <- TestString.create(testValue)
      bytes = BytesCodec[String].encode(original.value)
      fromBytes = BytesCodec[String].decode(bytes)
      base64 = Base64Codec[String].encode(original.value)
      fromBase64 = Base64Codec[String].decode(base64)
    yield expect(original.value == fromBytes) and expect(original.value == fromBase64)
  }

  test("StringValue should handle empty strings") {
    val testValue = ""
    for
      original <- TestString.create(testValue)
      bytes = BytesCodec[String].encode(original.value)
      fromBytes = BytesCodec[String].decode(bytes)
      base64 = Base64Codec[String].encode(original.value)
      fromBase64 = Base64Codec[String].decode(base64)
    yield expect(original.value == fromBytes) and expect(original.value == fromBase64)
  }

  test("StringValue should be encodable to and decodable from JSON") {
    val testValue = "test string value"
    for
      original <- TestString.create(testValue)
      json = JsonCodec[String].encode(original.value)
      _ <- IO.println(s"String as JSON: $json")
      fromJson = JsonCodec[String].decode(json)
    yield expect(original.value == fromJson)
  }

  test("StringValue should handle special characters in JSON") {
    val testValue = "Special chars: !@#$%^&*()_+{}|:<>?~`-=[]\\;',./€"
    for
      original <- TestString.create(testValue)
      json = JsonCodec[String].encode(original.value)
      _ <- IO.println(s"Special chars as JSON: $json")
      fromJson = JsonCodec[String].decode(json)
    yield expect(original.value == fromJson)
  }

  test("StringValue should handle empty strings in JSON") {
    val testValue = ""
    for
      original <- TestString.create(testValue)
      json = JsonCodec[String].encode(original.value)
      _ <- IO.println(s"Empty string as JSON: $json")
      fromJson = JsonCodec[String].decode(json)
    yield expect(original.value == fromJson)
  }
