package com.monadial.waygrid.common.application.util.circe.codecs

import com.monadial.waygrid.common.domain.model.vectorclock.VectorClock
import com.monadial.waygrid.common.domain.value.Address.NodeAddress
import io.circe.*
import org.http4s.Uri

import scala.collection.immutable.SortedMap

object DomainVectorClockCirceCodecs:
  given Codec[SortedMap[NodeAddress, Long]] = Codec.from(
    Decoder[SortedMap[String, Long]].map(_.map { case (k, v) => NodeAddress(Uri.unsafeFromString(k)) -> v }),
    Encoder[SortedMap[String, Long]].contramap(_.map { case (k, v) => k.unwrap.toString -> v })
  )

  given Encoder[VectorClock] = Encoder.derived
  given Decoder[VectorClock] = Decoder.derived
