package com.monadial.waygrid.common.application.util.vulcan.codecs

import cats.syntax.all.*
import com.monadial.waygrid.common.application.util.vulcan.codecs.DomainPrimitivesVulcanCodecs.given
import com.monadial.waygrid.common.application.util.vulcan.codecs.DomainRoutingDagVulcanCodecs.given
import com.monadial.waygrid.common.application.util.vulcan.codecs.DomainTraversalStateVulcanCodecs.given
import com.monadial.waygrid.common.domain.model.envelope.{ EnvelopeStamps, EnvelopeStampsV2 }
import com.monadial.waygrid.common.domain.model.envelope.Value.{ Stamp, TraversalRefStamp, TraversalStamp }
import vulcan.Codec

/**
 * Vulcan Avro codecs for Stamp types and EnvelopeStamps.
 *
 * Handles:
 * - TraversalStamp (contains TraversalState and Dag)
 * - TraversalRefStamp (lightweight reference with just DagHash)
 * - EnvelopeStampsV2 (Avro-compatible stamps container)
 * - EnvelopeStamps (legacy format using Map[Class[_ <: Stamp], List[Stamp]])
 *
 * Note: EnvelopeStamps uses EnvelopeStampsV2 as its wire format since the
 * original Map[Class, List] structure is not Avro-compatible.
 *
 * Import `DomainStampVulcanCodecs.given` to bring these codecs into scope.
 */
object DomainStampVulcanCodecs:

  /**
   * TraversalStamp encoded as Avro record.
   * Contains full traversal state and DAG for routing context.
   */
  given Codec[TraversalStamp] = Codec.record(
    name = "TraversalStamp",
    namespace = "com.monadial.waygrid.common.domain.model.envelope"
  ) { field =>
    (
      field("state", _.state),
      field("dag", _.dag)
    ).mapN(TraversalStamp.apply)
  }

  /**
   * TraversalRefStamp encoded as Avro record.
   * Lightweight stamp for context restoration via DAG hash lookup.
   */
  given Codec[TraversalRefStamp] = Codec.record(
    name = "TraversalRefStamp",
    namespace = "com.monadial.waygrid.common.domain.model.envelope"
  ) { field =>
    field("dagHash", _.dagHash).map(TraversalRefStamp.apply)
  }

  /**
   * Stamp sealed trait as Avro union.
   */
  given Codec[Stamp] = Codec.union[Stamp] { alt =>
    alt[TraversalStamp] |+| alt[TraversalRefStamp]
  }

  /**
   * EnvelopeStampsV2 encoded as Avro record.
   * This is the Avro-compatible stamps container with explicit typed fields.
   */
  given Codec[EnvelopeStampsV2] = Codec.record(
    name = "EnvelopeStampsV2",
    namespace = "com.monadial.waygrid.common.domain.model.envelope"
  ) { field =>
    (
      field("traversalStamps", _.traversalStamps),
      field("traversalRefStamps", _.traversalRefStamps)
    ).mapN(EnvelopeStampsV2.apply)
  }

  /**
   * EnvelopeStamps (legacy format) encoded via EnvelopeStampsV2 conversion.
   *
   * The original Map[Class[_ <: Stamp], List[Stamp]] is not Avro-serializable,
   * so we convert to/from EnvelopeStampsV2 for wire format.
   */
  given Codec[EnvelopeStamps] =
    summon[Codec[EnvelopeStampsV2]].imap(
      v2 => v2.toLegacy
    )(
      legacy => EnvelopeStampsV2.fromLegacy(legacy)
    )
