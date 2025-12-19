package com.monadial.waygrid.common.application.util.vulcan

import java.time.Instant

import org.http4s.Uri
import scodec.bits.ByteVector
import vulcan.Codec
import wvlet.airframe.ulid.ULID

/**
 * Base Vulcan Avro codecs for common JDK and library types.
 *
 * These codecs handle primitive type conversions for types that don't have
 * automatic Vulcan derivation support:
 * - ULID: Encoded as fixed 16-byte array (128-bit binary format)
 * - URI: Encoded as string
 * - Instant: Encoded as long (epoch milliseconds)
 * - ByteVector: Encoded as Avro bytes
 * - FiniteDuration: Encoded as long (nanoseconds)
 *
 * Import `VulcanUtils.given` to bring these codecs into scope.
 */
object VulcanUtils:

  /**
   * ULID codec using fixed 16-byte binary representation.
   *
   * ULIDs are 128-bit identifiers that encode timestamp + randomness.
   * We use the raw binary format for compactness and consistent ordering.
   */
  given Codec[ULID] = Codec.bytes.imap(bytes => ULID.fromBytes(bytes))(ulid => ulid.toBytes)

  /**
   * http4s URI codec using string representation.
   *
   * URIs are stored as their string form for readability and
   * compatibility with other systems consuming the Avro data.
   */
  given Codec[Uri] = Codec.string.imap(str => Uri.unsafeFromString(str))(uri => uri.renderString)

  /**
   * Instant codec using epoch milliseconds.
   *
   * Avro has native timestamp-millis logical type, but we use long directly
   * for simplicity and cross-language compatibility.
   */
  given Codec[Instant] = Codec.long.imap(millis => Instant.ofEpochMilli(millis))(instant => instant.toEpochMilli)

  /**
   * ByteVector codec using Avro bytes.
   *
   * scodec ByteVector maps directly to Avro's bytes type.
   */
  given Codec[ByteVector] = Codec.bytes.imap(bytes => ByteVector(bytes))(bv => bv.toArray)

  /**
   * FiniteDuration codec using nanoseconds as long.
   *
   * We store as nanoseconds to preserve full precision.
   * On decode, we construct a Duration from nanos.
   */
  given Codec[scala.concurrent.duration.FiniteDuration] =
    Codec.long.imap(nanos => scala.concurrent.duration.Duration.fromNanos(nanos))(duration => duration.toNanos)
