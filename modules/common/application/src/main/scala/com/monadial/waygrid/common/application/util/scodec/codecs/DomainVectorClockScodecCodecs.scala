package com.monadial.waygrid.common.application.util.scodec.codecs

import com.monadial.waygrid.common.domain.model.vectorclock.VectorClock
import com.monadial.waygrid.common.domain.value.Address.NodeAddress
import org.http4s.Uri
import scodec.*
import scodec.bits.*
import scodec.codecs.*

import scala.collection.immutable.SortedMap

/**
 * Scodec binary codecs for VectorClock and related types.
 *
 * Binary format:
 * [entry_count: int32]
 *   [key_length: int32][key_uri: utf8][value: int64]*
 */
object DomainVectorClockScodecCodecs:

  /**
   * Codec for SortedMap[NodeAddress, Long].
   * Encodes as: count + repeated (length-prefixed URI string, Long value) pairs.
   */
  given Codec[SortedMap[NodeAddress, Long]] = new Codec[SortedMap[NodeAddress, Long]]:
    private val entryCodec: Codec[(NodeAddress, Long)] =
      (variableSizeBytes(int32, utf8) :: int64).xmap(
        { case (uri, value) => (NodeAddress(Uri.unsafeFromString(uri)), value) },
        { case (addr, value) => (addr.unwrap.renderString, value) }
      )

    override def sizeBound: SizeBound = SizeBound.unknown

    override def encode(value: SortedMap[NodeAddress, Long]): Attempt[BitVector] =
      for
        countBits   <- int32.encode(value.size)
        entriesBits <- value.toList.foldLeft(Attempt.successful(BitVector.empty)) { (acc, entry) =>
                         acc.flatMap(bits => entryCodec.encode(entry).map(bits ++ _))
                       }
      yield countBits ++ entriesBits

    override def decode(bits: BitVector): Attempt[DecodeResult[SortedMap[NodeAddress, Long]]] =
      for
        countResult <- int32.decode(bits)
        count        = countResult.value
        remaining    = countResult.remainder
        result      <- decodeEntries(remaining, count, List.empty)
      yield result.map(entries => SortedMap.from(entries))

    private def decodeEntries(
      bits: BitVector,
      remaining: Int,
      acc: List[(NodeAddress, Long)]
    ): Attempt[DecodeResult[List[(NodeAddress, Long)]]] =
      if remaining <= 0 then Attempt.successful(DecodeResult(acc.reverse, bits))
      else
        entryCodec.decode(bits).flatMap { result =>
          decodeEntries(result.remainder, remaining - 1, result.value :: acc)
        }

  /**
   * Codec for VectorClock.
   * Uses the SortedMap codec for the entries field.
   */
  given Codec[VectorClock] =
    summon[Codec[SortedMap[NodeAddress, Long]]].xmap(
      entries => VectorClock(entries),
      vc => vc.entries
    )
