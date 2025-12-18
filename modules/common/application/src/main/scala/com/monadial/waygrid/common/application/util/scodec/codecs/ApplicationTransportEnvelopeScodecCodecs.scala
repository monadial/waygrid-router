package com.monadial.waygrid.common.application.util.scodec.codecs

import com.monadial.waygrid.common.application.domain.model.envelope.TransportEnvelope
import com.monadial.waygrid.common.application.domain.model.envelope.Value.{ MessageContent, MessageContentData, MessageContentType }
import com.monadial.waygrid.common.application.util.scodec.codecs.DomainAddressScodecCodecs.given
import com.monadial.waygrid.common.application.util.scodec.codecs.DomainStampScodecCodecs.given
import com.monadial.waygrid.common.domain.model.envelope.EnvelopeStamps
import com.monadial.waygrid.common.domain.model.envelope.Value.{ EnvelopeId, Stamp, TraversalRefStamp, TraversalStamp }
import com.monadial.waygrid.common.domain.value.Address.{ Endpoint, NodeAddress }
import org.http4s.Uri
import scodec.*
import scodec.bits.*
import scodec.codecs.*
import wvlet.airframe.ulid.ULID

/**
 * Scodec binary codecs for TransportEnvelope and related application types.
 *
 * Binary format for TransportEnvelope:
 * [id: 16 bytes ULID]
 * [sender: length-prefixed UTF-8 URI]
 * [endpoint: discriminator + data]
 * [message.contentType: length-prefixed UTF-8]
 * [message.contentData: length-prefixed bytes]
 * [stamps: count + repeated (discriminator + stamp_data)]
 *
 * EnvelopeStamps is encoded as a flat list of Stamps (without Class keys)
 * since the type discriminator in each Stamp already identifies its class.
 */
object ApplicationTransportEnvelopeScodecCodecs:

  // ---------------------------------------------------------------------------
  // Value type codecs (private to avoid conflicts with auto-derived codecs)
  // ---------------------------------------------------------------------------

  private val envelopeIdCodec: Codec[EnvelopeId] =
    bytes(16).xmap(
      bytes => EnvelopeId(ULID.fromBytes(bytes.toArray)),
      id => ByteVector(id.unwrap.toBytes)
    )

  private val nodeAddressCodec: Codec[NodeAddress] =
    variableSizeBytes(int32, utf8).xmap(
      str => NodeAddress(Uri.unsafeFromString(str)),
      addr => addr.unwrap.renderString
    )

  private val messageContentTypeCodec: Codec[MessageContentType] =
    variableSizeBytes(int32, utf8).xmap(MessageContentType(_), _.unwrap)

  private val messageContentDataCodec: Codec[MessageContentData] =
    variableSizeBytes(int32, bytes).xmap(MessageContentData(_), _.unwrap)

  // Expose as givens for external use
  given Codec[EnvelopeId]         = envelopeIdCodec
  given Codec[NodeAddress]        = nodeAddressCodec
  given Codec[MessageContentType] = messageContentTypeCodec
  given Codec[MessageContentData] = messageContentDataCodec

  // ---------------------------------------------------------------------------
  // MessageContent codec
  // ---------------------------------------------------------------------------

  private val messageContentCodec: Codec[MessageContent] =
    (messageContentTypeCodec :: messageContentDataCodec).xmap(
      { case (contentType, contentData) => MessageContent(contentType, contentData) },
      mc => (mc.contentType, mc.contentData)
    )

  given Codec[MessageContent] = messageContentCodec

  // ---------------------------------------------------------------------------
  // EnvelopeStamps codec
  //
  // Since we're using type discriminators for Stamps, we can encode them
  // as a simple list. On decode, we reconstruct the Map by grouping stamps
  // by their runtime class.
  // ---------------------------------------------------------------------------

  given Codec[EnvelopeStamps] = new Codec[EnvelopeStamps]:
    private val stampCodec = summon[Codec[Stamp]]

    override def sizeBound: SizeBound = SizeBound.unknown

    override def encode(value: EnvelopeStamps): Attempt[BitVector] =
      // Flatten all stamps into a single list
      val allStamps = value.values.flatten.toList
      for
        countBits  <- int32.encode(allStamps.size)
        stampsBits <- allStamps.foldLeft(Attempt.successful(BitVector.empty)) { (acc, stamp) =>
                        acc.flatMap(bits => stampCodec.encode(stamp).map(bits ++ _))
                      }
      yield countBits ++ stampsBits

    override def decode(bits: BitVector): Attempt[DecodeResult[EnvelopeStamps]] =
      for
        countResult <- int32.decode(bits)
        count        = countResult.value
        remaining    = countResult.remainder
        result      <- decodeStamps(remaining, count, List.empty)
      yield result.map(stamps => groupStampsByClass(stamps))

    private def decodeStamps(
      bits: BitVector,
      remaining: Int,
      acc: List[Stamp]
    ): Attempt[DecodeResult[List[Stamp]]] =
      if remaining <= 0 then Attempt.successful(DecodeResult(acc.reverse, bits))
      else
        stampCodec.decode(bits).flatMap { result =>
          decodeStamps(result.remainder, remaining - 1, result.value :: acc)
        }

    private def groupStampsByClass(stamps: List[Stamp]): EnvelopeStamps =
      stamps.groupBy {
        case _: TraversalStamp    => classOf[TraversalStamp]
        case _: TraversalRefStamp => classOf[TraversalRefStamp]
      }.asInstanceOf[EnvelopeStamps]

  // ---------------------------------------------------------------------------
  // TransportEnvelope codec
  // ---------------------------------------------------------------------------

  given Codec[TransportEnvelope] =
    (envelopeIdCodec ::
      nodeAddressCodec ::
      summon[Codec[Endpoint]] ::
      messageContentCodec ::
      summon[Codec[EnvelopeStamps]]).xmap(
      { case (id, sender, endpoint, message, stamps) =>
        TransportEnvelope(id, sender, endpoint, message, stamps)
      },
      te => (te.id, te.sender, te.endpoint, te.message, te.stamps)
    )
