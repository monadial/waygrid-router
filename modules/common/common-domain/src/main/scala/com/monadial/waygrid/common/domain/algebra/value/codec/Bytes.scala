package com.monadial.waygrid.common.domain.algebra.value.codec

import cats.data.Validated
import com.monadial.waygrid.common.domain.algebra.value.bytes.IsBytes
import scodec.bits.ByteVector

trait BytesCodecError extends Throwable:
  def message: String

final case class BytesDecodingError(message: String) extends BytesCodecError

trait BytesEncoder[A]:
  def encodeToScalar(value: A): ByteVector
  def encodeToValue[V: IsBytes](value: A): V =
    IsBytes[V]
      .iso
      .get(encodeToScalar(value))

trait BytesDecoder[A]:
  def decodeFromScalar(value: ByteVector): Validated[BytesDecodingError, A]
  def decodeFromValue[V: IsBytes](value: V): Validated[BytesDecodingError, A] =
    decodeFromScalar(IsBytes[V].iso.reverseGet(value))

trait BytesCodec[A] extends BytesEncoder[A] with BytesDecoder[A]

object BytesCodec:
  def apply[A](using codec: BytesCodec[A]): BytesCodec[A] = codec
