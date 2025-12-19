package com.monadial.waygrid.common.application.util.vulcan.codecs

import cats.syntax.all.*
import com.monadial.waygrid.common.application.domain.model.envelope.TransportEnvelope
import com.monadial.waygrid.common.application.domain.model.envelope.Value.{
  MessageContent,
  MessageContentData,
  MessageContentType
}
import com.monadial.waygrid.common.application.util.vulcan.VulcanUtils.given
import com.monadial.waygrid.common.application.util.vulcan.codecs.DomainAddressVulcanCodecs.given
import com.monadial.waygrid.common.application.util.vulcan.codecs.DomainPrimitivesVulcanCodecs.given
import com.monadial.waygrid.common.application.util.vulcan.codecs.DomainStampVulcanCodecs.given
import scodec.bits.ByteVector
import vulcan.Codec

/**
 * Vulcan Avro codecs for TransportEnvelope and related application types.
 *
 * TransportEnvelope is the main message wrapper for Kafka transport.
 * It contains:
 * - id: EnvelopeId (ULID)
 * - sender: NodeAddress (URI)
 * - endpoint: Endpoint (union: LogicalEndpoint | PhysicalEndpoint)
 * - message: MessageContent (contentType + contentData)
 * - stamps: EnvelopeStamps (routing context)
 *
 * This is the top-level codec used for Kafka serialization/deserialization.
 *
 * Import `ApplicationTransportEnvelopeVulcanCodecs.given` to bring these codecs into scope.
 */
object ApplicationTransportEnvelopeVulcanCodecs:

  // ---------------------------------------------------------------------------
  // MessageContent value types
  // ---------------------------------------------------------------------------

  /**
   * MessageContentType (MIME type string).
   */
  given Codec[MessageContentType] = Codec.string.imap(str => MessageContentType(str))(ct => ct.unwrap)

  /**
   * MessageContentData (binary content as ByteVector).
   */
  given Codec[MessageContentData] = summon[Codec[ByteVector]].imap(bv => MessageContentData(bv))(mcd => mcd.unwrap)

  /**
   * MessageContent encoded as Avro record.
   */
  given Codec[MessageContent] = Codec.record(
    name = "MessageContent",
    namespace = "com.monadial.waygrid.common.application.domain.model.envelope"
  ) { field =>
    (
      field("contentType", _.contentType),
      field("contentData", _.contentData)
    ).mapN(MessageContent.apply)
  }

  // ---------------------------------------------------------------------------
  // TransportEnvelope
  // ---------------------------------------------------------------------------

  /**
   * TransportEnvelope encoded as Avro record.
   *
   * This is the main message type for Kafka transport.
   * Schema version: 1.0
   *
   * Note: Uses domain EnvelopeId (from DomainPrimitivesVulcanCodecs).
   */
  given Codec[TransportEnvelope] = Codec.record(
    name = "TransportEnvelope",
    namespace = "com.monadial.waygrid.common.application.domain.model.envelope"
  ) { field =>
    (
      field("id", _.id),
      field("sender", _.sender),
      field("endpoint", _.endpoint),
      field("message", _.message),
      field("stamps", _.stamps)
    ).mapN(TransportEnvelope.apply)
  }
