package com.monadial.waygrid.common.domain.value.codec

import cats.data.Validated

trait BytesCodecError:
  def message: String

final case class BytesDecodingError(message: String) extends BytesCodecError

trait BytesEncoder[A]:
  def encode(value: A): Array[Byte]

trait BytesDecoder[A]:
  def decode(value: Array[Byte]): Validated[BytesDecodingError, A]

trait BytesCodec[A] extends BytesEncoder[A] with BytesDecoder[A]

object BytesCodec:
  def apply[A](using codec: BytesCodec[A]): BytesCodec[A] = codec
