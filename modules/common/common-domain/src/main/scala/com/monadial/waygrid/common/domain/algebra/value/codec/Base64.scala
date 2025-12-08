package com.monadial.waygrid.common.domain.algebra.value.codec

import cats.data.Validated

trait Base64CodecError extends Throwable:
  def message: String

final case class Base64DecodingError(message: String) extends Base64CodecError

trait Base64Encoder[A]:
  def encode(value: A): String

trait Base64Decoder[A]:
  def decode(value: String): Validated[Base64DecodingError, A]

trait Base64Codec[A] extends Base64Encoder[A] with Base64Decoder[A]

object Base64Codec:
  def apply[A](using codec: Base64Codec[A]): Base64Codec[A] = codec
