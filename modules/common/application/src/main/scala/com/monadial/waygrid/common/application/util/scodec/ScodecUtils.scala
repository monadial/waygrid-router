package com.monadial.waygrid.common.application.util.scodec

import scodec.*
import scodec.bits.*
import scodec.codecs.*

/**
 * Utility functions for creating scodec codecs with less boilerplate.
 *
 * Key patterns:
 * - `enumCodec`: Create codecs for Scala 3 enums with explicit discriminators
 * - `setCodec`: Create codecs for Set[A] from element codec
 * - `mapCodec`: Create codecs for Map[K, V] from key/value codecs
 */
object ScodecUtils:

  // ---------------------------------------------------------------------------
  // Enum codec builder (solves Scala 3 enum + discriminated issue)
  // ---------------------------------------------------------------------------

  /**
   * Creates a codec for simple enums (no payload) using explicit pattern matching.
   *
   * Usage:
   * {{{
   * given Codec[Status] = enumCodec[Status](
   *   Status.Pending   -> 0,
   *   Status.Running   -> 1,
   *   Status.Completed -> 2
   * )
   * }}}
   */
  def enumCodec[A](mappings: (A, Int)*): Codec[A] =
    val toDiscriminator   = mappings.toMap
    val fromDiscriminator = mappings.map(_.swap).toMap
    val typeName          = mappings.headOption.map(_._1.getClass.getSimpleName.stripSuffix("$")).getOrElse("Unknown")

    Codec[A](
      (a: A) =>
        toDiscriminator.get(a) match
          case Some(d) => uint8.encode(d)
          case None    => Attempt.failure(Err(s"Unknown $typeName value: $a")),
      (bits: BitVector) =>
        uint8.decode(bits).flatMap { result =>
          fromDiscriminator.get(result.value) match
            case Some(a) => Attempt.successful(DecodeResult(a, result.remainder))
            case None    => Attempt.failure(Err(s"Unknown $typeName discriminator: ${result.value}"))
        }
    )

  /**
   * Creates a codec for ADT/enum with payloads using builder pattern.
   *
   * Usage:
   * {{{
   * given Codec[Result] = discriminatedCodec[Result]
   *   .singleton(0, Result.Timeout)
   *   .variant(1, Result.Success.apply, _.asInstanceOf[Result.Success].output)(outputCodec)
   *   .variant(2, Result.Failure.apply, _.asInstanceOf[Result.Failure].reason)(stringCodec)
   *   .build
   * }}}
   */
  def discriminatedCodec[A]: DiscriminatedCodecBuilder[A] =
    new DiscriminatedCodecBuilder[A](List.empty)

  final class DiscriminatedCodecBuilder[A] private[ScodecUtils] (variants: List[VariantCodec[A]]):
    def singleton(discriminator: Int, value: A): DiscriminatedCodecBuilder[A] =
      val variant = VariantCodec[A](
        discriminator,
        _ == value,
        _ => uint8.encode(discriminator),
        bits => Attempt.successful(DecodeResult(value, bits))
      )
      new DiscriminatedCodecBuilder(variants :+ variant)

    def variant[B](discriminator: Int, construct: B => A, extract: A => B)(using
      codec: Codec[B]
    ): DiscriminatedCodecBuilder[A] =
      val variant = VariantCodec[A](
        discriminator,
        a =>
          scala.util.Try(
            extract(a)
          ).isSuccess && a.getClass.getSimpleName.contains(construct.getClass.getSimpleName.takeWhile(_ != '$')),
        a => uint8.encode(discriminator).flatMap(d => codec.encode(extract(a)).map(d ++ _)),
        bits => codec.decode(bits).map(_.map(construct))
      )
      new DiscriminatedCodecBuilder(variants :+ variant)

    def variantBy[B <: A](discriminator: Int)(using
      codec: Codec[B],
      ct: reflect.ClassTag[B]
    ): DiscriminatedCodecBuilder[A] =
      val variant = VariantCodec[A](
        discriminator,
        a => ct.runtimeClass.isInstance(a),
        a => uint8.encode(discriminator).flatMap(d => codec.encode(a.asInstanceOf[B]).map(d ++ _)),
        bits => codec.decode(bits).map(_.map(identity))
      )
      new DiscriminatedCodecBuilder(variants :+ variant)

    def build: Codec[A] =
      Codec[A](
        (a: A) =>
          variants.find(_.matches(a)) match
            case Some(v) => v.encode(a)
            case None    => Attempt.failure(Err(s"No matching variant for: $a")),
        (bits: BitVector) =>
          uint8.decode(bits).flatMap { result =>
            variants.find(_.discriminator == result.value) match
              case Some(v) => v.decode(result.remainder)
              case None    => Attempt.failure(Err(s"Unknown discriminator: ${result.value}"))
          }
      )

  private[ScodecUtils] case class VariantCodec[A](
    discriminator: Int,
    matches: A => Boolean,
    encode: A => Attempt[BitVector],
    decode: BitVector => Attempt[DecodeResult[A]]
  )

  // ---------------------------------------------------------------------------
  // Collection codecs (Set, Map)
  // ---------------------------------------------------------------------------

  /**
   * Creates a codec for Set[A] using length-prefixed encoding.
   */
  def setCodec[A](using elemCodec: Codec[A]): Codec[Set[A]] =
    listOfN(int32, elemCodec).xmap(_.toSet, _.toList)

  /**
   * Creates a codec for Map[K, V] using length-prefixed encoding.
   */
  def mapCodec[K, V](using keyCodec: Codec[K], valueCodec: Codec[V]): Codec[Map[K, V]] =
    listOfN(int32, keyCodec :: valueCodec).xmap(
      pairs => pairs.map { case (k, v) => k -> v }.toMap,
      map => map.toList
    )

  // ---------------------------------------------------------------------------
  // Optional/Nullable helpers
  // ---------------------------------------------------------------------------

  /**
   * Creates a codec for Option[A] using a boolean flag prefix.
   */
  def optionCodec[A](using codec: Codec[A]): Codec[Option[A]] =
    optional(bool, codec)

  // ---------------------------------------------------------------------------
  // Vector codec
  // ---------------------------------------------------------------------------

  /**
   * Creates a codec for Vector[A] using length-prefixed encoding.
   */
  def vectorCodec[A](using elemCodec: Codec[A]): Codec[Vector[A]] =
    listOfN(int32, elemCodec).xmap(_.toVector, _.toList)
