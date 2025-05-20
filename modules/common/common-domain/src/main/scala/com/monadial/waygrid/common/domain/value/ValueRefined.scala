package com.monadial.waygrid.common.domain.value

import cats.{ Eq, Functor, Order, Show }
import com.monadial.waygrid.common.domain.algebra.TypeEvidence
import com.monadial.waygrid.common.domain.value.codec.{
  Base64Codec,
  BytesCodec
}
import eu.timepit.refined.*
import eu.timepit.refined.api.{ Refined, Validate }
import eu.timepit.refined.auto.*
import io.circe.{ Decoder as JsonDecoder, Encoder as JsonEncoder }
import monocle.Iso

abstract class ValueRefined[V, P](using
  validate: Validate[V, P],
  eqv: Eq[Refined[V, P]],
  ord: Order[Refined[V, P]],
  shw: Show[Refined[V, P]],
  bts: BytesCodec[Refined[V, P]],
  b64: Base64Codec[Refined[V, P]],
  jenc: JsonEncoder[Refined[V, P]],
  jdec: JsonDecoder[Refined[V, P]]
):
  infix opaque type Type = Refined[V, P]

  def unsafeFrom(value: V): Type = refineV[P](value).toOption.get

  def from(value: V): Either[String, Type] = refineV[P].apply(value)

  protected inline final def derive[F[_]](using
    ev: F[V],
    evIso: TypeEvidence[V, Type],
    F: Functor[F]
  ): F[Type] = ev.mapIso

  extension (t: Type) inline def unwrap: V = t

  given TypeEvidence[Refined[V, P], Type] with
    def iso: Iso[Refined[V, P], Type] =
      Iso[Refined[V, P], Type](identity)(identity)

  given Eq[Type]          = eqv
  given Order[Type]       = ord
  given Show[Type]        = shw
  given Ordering[Type]    = ord.toOrdering
  given BytesCodec[Type]  = bts
  given Base64Codec[Type] = b64
  given JsonEncoder[Type] = jenc
  given JsonDecoder[Type] = jdec
