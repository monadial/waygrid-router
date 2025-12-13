//package com.monadial.waygrid.common.domain.value.ulid
//
//import cats.effect.IO
//import cats.implicits.*
//import com.monadial.waygrid.common.domain.instances.ULIDInstances.given
//import com.monadial.waygrid.common.domain.value.codec.{Base64Codec, BytesCodec, JsonCodec}
//import weaver.*
//import weaver.scalacheck.Checkers
//import wvlet.airframe.ulid.ULID
//
//object ULIDValueSpec extends SimpleIOSuite with Checkers:
//
//  // Define a concrete implementation of ULIDValue for testing
//  final case class TestULID(value: ULID) extends ULIDValue
//
//  object TestULID:
//    // Create a new TestULID
//    def create: IO[TestULID] =
//      ULID.newULID.pure[IO].map(TestULID.apply)
//
//    // Create a TestULID from a string
//    def fromString(s: String): IO[TestULID] =
//      IO.fromTry(scala.util.Try(ULID.fromString(s))).map(TestULID.apply)
//
//  test("ULID should be generated") {
//    for
//      ulid <- TestULID.create
//      _ <- IO.println(s"Generated ULID: ${ulid.value}")
//    yield expect(ulid.value != null)
//  }
//
//  test("ULID should be convertible to and from string") {
//    for
//      original <- TestULID.create
//      str = original.value.toString
//      _ <- IO.println(s"ULID as string: $str")
//      fromStr <- TestULID.fromString(str)
//    yield expect(original.value == fromStr.value)
//  }
//
//  test("ULID should be encodable to and decodable from bytes") {
//    for
//      original <- TestULID.create
//      bytes = BytesCodec[ULID].encode(original.value)
//      _ <- IO.println(s"ULID as bytes: ${bytes.mkString("[", ", ", "]")}")
//      fromBytes = BytesCodec[ULID].decode(bytes)
//    yield expect(original.value == fromBytes)
//  }
//
//  test("ULID should be encodable to and decodable from Base64") {
//    for
//      original <- TestULID.create
//      base64 = Base64Codec[ULID].encode(original.value)
//      _ <- IO.println(s"ULID as Base64: $base64")
//      fromBase64 = Base64Codec[ULID].decode(base64)
//    yield expect(original.value == fromBase64)
//  }
//
//  test("ULID should be encodable to and decodable from JSON") {
//    for
//      original <- TestULID.create
//      json = JsonCodec[ULID].encode(original.value)
//      _ <- IO.println(s"ULID as JSON: $json")
//      fromJson = JsonCodec[ULID].decode(json)
//    yield expect(original.value == fromJson)
//  }
