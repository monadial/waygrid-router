package com.monadial.waygrid.common.domain.algebra.value

import cats.{ Eq, Order, Show }
import com.monadial.waygrid.common.domain.algebra.TypeEvidence
import com.monadial.waygrid.common.domain.algebra.value.codec.{ Base64Codec, BytesCodec }

import io.circe.{ Decoder as JsonDecoder, Encoder as JsonEncoder }
import scodec.{ Codec as SCodec, Decoder as SDecoder, Encoder as SEncoder }
import monocle.Iso

abstract class Value[V](using
  eqv: Eq[V],
  ord: Order[V],
  shw: Show[V],
  bts: BytesCodec[V],
  b64: Base64Codec[V],
  jenc: JsonEncoder[V],
  jdec: JsonDecoder[V],
  sdec: SDecoder[V],
  senc: SEncoder[V]
):
  opaque type Type = V

  infix inline def apply(value: V): Type = value

  protected inline final def derive[F[_]](using ev: F[V]): F[Type] = ev

  extension (t: Type)
    inline def unwrap: V = t

  given TypeEvidence[V, Type] with
    override def iso: Iso[V, Type] = Iso[V, Type](apply)(_.unwrap)

  given Eq[Type]          = eqv
  given Order[Type]       = ord
  given Show[Type]        = shw
  given Ordering[Type]    = ord.toOrdering
  given BytesCodec[Type]  = bts
  given Base64Codec[Type] = b64
  given JsonEncoder[Type] = jenc
  given JsonDecoder[Type] = jdec
  given SCodec[Type]      = SCodec(senc, sdec)
