package com.monadial.waygrid.common.application.util.circe.codecs

import cats.data.NonEmptyList
import com.monadial.waygrid.common.application.util.circe.DerivationConfiguration.given
import com.monadial.waygrid.common.domain.model.parameter.*
import io.circe.{ Codec, Decoder, Encoder }

/**
 * Circe codecs for parameter model types.
 *
 * Provides JSON serialization for:
 * - ParameterValue (literals and secret references)
 * - SecretReference and related types
 * - ParameterType and ParameterDef (for schema definitions)
 * - ParameterConstraint
 * - ServiceParameterSchema
 */
object DomainParameterCodecs:

  // ---------------------------------------------------------------------------
  // Secret Reference Codecs
  // ---------------------------------------------------------------------------

  /** SecretPath is an opaque String type */
  given Encoder[SecretPath] = Encoder.encodeString.contramap(_.value)
  given Decoder[SecretPath] = Decoder.decodeString.map(SecretPath(_))
  given Codec[SecretPath]   = Codec.from(summon[Decoder[SecretPath]], summon[Encoder[SecretPath]])

  /** SecretVersion is an opaque String type */
  given Encoder[SecretVersion] = Encoder.encodeString.contramap(_.value)
  given Decoder[SecretVersion] = Decoder.decodeString.map(SecretVersion(_))
  given Codec[SecretVersion]   = Codec.from(summon[Decoder[SecretVersion]], summon[Encoder[SecretVersion]])

  /** SecretReference case class */
  given Codec[SecretReference] = Codec.derived[SecretReference]

  // ---------------------------------------------------------------------------
  // Parameter Type Codecs
  // ---------------------------------------------------------------------------

  /** NonEmptyList codec for enum values */
  given [A: Decoder]: Decoder[NonEmptyList[A]] = Decoder.decodeNonEmptyList[A]
  given [A: Encoder]: Encoder[NonEmptyList[A]] = Encoder.encodeNonEmptyList[A]

  /** ParameterType enum with discriminator */
  given Codec[ParameterType] = Codec.derived[ParameterType]

  // ---------------------------------------------------------------------------
  // Parameter Constraint Codecs
  // ---------------------------------------------------------------------------

  given Codec[ParameterConstraint] = Codec.derived[ParameterConstraint]

  // ---------------------------------------------------------------------------
  // Parameter Value Codecs
  // ---------------------------------------------------------------------------

  /** ParameterValue enum with discriminator */
  given Codec[ParameterValue] = Codec.derived[ParameterValue]

  // ---------------------------------------------------------------------------
  // Parameter Definition Codecs
  // ---------------------------------------------------------------------------

  given Codec[ParameterDef] = Codec.derived[ParameterDef]

  // ---------------------------------------------------------------------------
  // Service Parameter Schema Codecs
  // ---------------------------------------------------------------------------

  given Codec[ServiceParameterSchema] = Codec.derived[ServiceParameterSchema]
