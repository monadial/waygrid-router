package com.monadial.waygrid.common.application.interpreter

import cats.data.Validated.{Invalid, Valid}
import cats.effect.Async
import cats.implicits.catsSyntaxApplicativeId
import cats.syntax.all.*
import com.monadial.waygrid.common.application.`macro`.CirceMessageCodecRegistryMacro
import com.monadial.waygrid.common.application.`macro`.CirceMessageCodecRegistryMacro.CodecMap
import com.monadial.waygrid.common.application.algebra.TransportEnvelopeCodec
import com.monadial.waygrid.common.application.domain.model.envelope.TransportEnvelope
import com.monadial.waygrid.common.application.domain.model.envelope.Value.{MessageContent, MessageContentData, MessageContentType}
import com.monadial.waygrid.common.application.instances.CirceInstances.given
import com.monadial.waygrid.common.domain.algebra.messaging.message.Message
import com.monadial.waygrid.common.domain.model.envelope.DomainEnvelope
import com.monadial.waygrid.common.domain.algebra.messaging.message.Value.MessageType
import com.monadial.waygrid.common.domain.algebra.value.codec.BytesCodec
import io.circe.{Decoder, Encoder, Json}

object TransportEnvelopeCodecInterpreter:

  def default[F[+_]: Async]: TransportEnvelopeCodec[F] =
    fromRegistry(CirceMessageCodecRegistryMacro.readRegistry)

  private def fromRegistry[F[+_]: Async](registry: CodecMap): TransportEnvelopeCodec[F] =
    new TransportEnvelopeCodec[F]:
      override def encode[M <: Message](envelope: DomainEnvelope[M]): F[TransportEnvelope] =
        val messageType = MessageType.fromMessage(envelope.message)

        registry.get(messageType) match
          case None =>
            Async[F].raiseError(encoderMissingError(messageType))

          case Some(codec) => {
            for
              messageContentData <- codec
                  .asInstanceOf[Encoder[M]]
                  .apply(envelope.message)
                  .pure[F]
                  .map(BytesCodec[Json].encodeToValue[MessageContentData])

              transportEnvelope <- TransportEnvelope(
                envelope.id,
                envelope.sender,
                envelope.endpoint,
                MessageContent(
                  MessageContentType(messageType.unwrap),
                  messageContentData
                ),
                envelope.stamps
              ).pure[F]
            yield transportEnvelope
          }.handleErrorWith: err =>
            Async[F].raiseError(encodeFailure(messageType, err))


      override def decode(envelope: TransportEnvelope): F[DomainEnvelope[? <: Message]] =
        val messageType = MessageType(envelope.message.contentType.unwrap)

        registry.get(messageType) match
          case None => Async[F].raiseError(decoderMissingError(messageType))
          case Some(codec) =>
            BytesCodec[Json]
              .decodeFromValue(envelope.message.contentData)
              .pure[F]
              .flatMap:
                case Invalid(err) =>
                  Async[F].raiseError(jsonParseError(messageType, err.message))
                case Valid(a) =>
                  for
                    decodedEvent <- codec
                      .asInstanceOf[Decoder[Message]]
                      .decodeJson(a)
                      .pure[F]
                    result <- decodedEvent match
                      case Left(err) =>
                        Async[F].raiseError(jsonDecodeError(messageType, err.message))
                      case Right(decoded) =>
                        Async[F].pure(decoded)
                    domainEnvelope <-
                      DomainEnvelope(
                        envelope.id,
                        envelope.sender,
                        envelope.endpoint,
                        result,
                        envelope.stamps
                      ).pure[F]
                  yield domainEnvelope

  private def decoderMissingError(eventType: MessageType) =
    new Exception(s"[EventCodecRegistry] No codec registered for type: ${eventType.show}")

  private def encoderMissingError(eventType: MessageType) =
    new Exception(s"[EventCodecRegistry] No codec registered for event type: ${eventType.show}")

  private def encodeFailure(eventType: MessageType, err: Throwable) =
    new Exception(s"[EventCodecRegistry] Failed to encode event $eventType: ${err.getMessage}", err)

  private def jsonParseError(eventType: MessageType, msg: String) =
    new Exception(s"[EventCodecRegistry] Failed to parse JSON for $eventType: $msg")

  private def jsonDecodeError(eventType: MessageType, msg: String) =
    new Exception(s"[EventCodecRegistry] Failed to decode $eventType: $msg")
