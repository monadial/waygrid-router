package com.monadial.waygrid.common.application.util.circe.codecs

import cats.syntax.all.*
import com.monadial.waygrid.common.application.domain.model.envelope.TransportEnvelope
import com.monadial.waygrid.common.application.domain.model.envelope.Value.MessageContent
import com.monadial.waygrid.common.domain.model.envelope.EnvelopeStamps
import com.monadial.waygrid.common.domain.model.envelope.Value.Stamp
import com.monadial.waygrid.common.domain.value.Address.{ Endpoint, EndpointDirection }
import com.monadial.waygrid.common.application.util.circe.codecs.DomainStampCodecs.given
import io.circe.*
import io.circe.generic.semiauto
import io.circe.syntax.*

object ApplicationTransportEnvelopeCodecs:
  given Encoder[EnvelopeStamps] = Encoder
      .encodeJson
      .contramap: stamps =>
        Json.obj(
          stamps.map { case (cls, values) =>
            cls.getName -> values.asJson
          }.toSeq *
        )

  given Decoder[EnvelopeStamps] =
    Decoder.decodeJson.emap: json =>
      json.asObject
          .map { obj =>
            obj.toMap.map { case (className, valuesJson) =>
              Either
                  .catchNonFatal(Class.forName(className).asInstanceOf[Class[? <: Stamp]])
                  .leftMap(_.getMessage)
                  .flatMap { clazz =>
                    valuesJson.as[List[Stamp]].leftMap(_.getMessage).map(clazz -> _)
                  }
            }.toList.sequence.map(_.toMap)
          }
          .getOrElse(Left("Expected JSON object for EnvelopeStamps"))

  given Encoder[EndpointDirection] = semiauto.deriveCodec
  given Decoder[EndpointDirection] = semiauto.deriveCodec

  given Encoder[Endpoint] = semiauto.deriveCodec
  given Decoder[Endpoint] = semiauto.deriveCodec

  given Codec[MessageContent] = semiauto.deriveCodec
  given Codec[TransportEnvelope] = semiauto.deriveCodec

