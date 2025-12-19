package com.monadial.waygrid.common.application.util.vulcan.codecs

import cats.syntax.all.*
import com.monadial.waygrid.common.application.util.vulcan.codecs.DomainAddressVulcanCodecs.given
import com.monadial.waygrid.common.domain.model.vectorclock.VectorClock
import com.monadial.waygrid.common.domain.value.Address.NodeAddress
import vulcan.Codec

import scala.collection.immutable.SortedMap

/**
 * Vulcan Avro codec for VectorClock.
 *
 * VectorClock is a distributed logical clock implemented as SortedMap[NodeAddress, Long].
 * Since Avro maps require string keys, we encode it as a list of (address, version) pairs.
 *
 * Avro Representation:
 * - Array of VectorClockEntry records
 * - Each entry has nodeAddress (string URI) and version (long)
 *
 * Import `DomainVectorClockVulcanCodecs.given` to bring this codec into scope.
 */
object DomainVectorClockVulcanCodecs:

  /**
   * Helper case class for encoding VectorClock entries.
   */
  private case class VectorClockEntry(
    nodeAddress: NodeAddress,
    version: Long
  )

  /**
   * VectorClockEntry codec.
   */
  private given Codec[VectorClockEntry] = Codec.record(
    name = "VectorClockEntry",
    namespace = "com.monadial.waygrid.common.domain.model.vectorclock"
  ) { field =>
    (
      field("nodeAddress", _.nodeAddress),
      field("version", _.version)
    ).mapN(VectorClockEntry.apply)
  }

  /**
   * VectorClock codec.
   *
   * Encodes as array of VectorClockEntry records, since Avro maps require string keys
   * and NodeAddress is a value type wrapping URI.
   */
  given Codec[VectorClock] =
    Codec.list[VectorClockEntry].imap { entries =>
      VectorClock(SortedMap.from(entries.map(e => e.nodeAddress -> e.version)))
    } { clock =>
      clock.entries.toList.map { case (addr, version) => VectorClockEntry(addr, version) }
    }
