package com.monadial.waygrid.common.application.interpreter

import cats.syntax.all.*
import cats.effect.Async
import io.circe.{ Codec, Decoder, Encoder, parser }
import com.monadial.waygrid.common.application.`macro`.EventCodecRegistry
import com.monadial.waygrid.common.application.algebra.EventCodec
import com.monadial.waygrid.common.application.model.event.*
import com.monadial.waygrid.common.application.syntax.EventSyntax.toEvent
import com.monadial.waygrid.common.application.syntax.EventSyntax.toRawEvent
import com.monadial.waygrid.common.domain.model.event.Event as DomainEvent

object EventCodecInterpreter:

  def apply[F[+_]: Async]: EventCodec[F] =
    fromRegistry[F](EventCodecRegistry.readRegistry())

  private def fromRegistry[F[+_]: Async](registry: Map[EventType, Codec[? <: DomainEvent]]): EventCodec[F] =
    new EventCodec[F]:

      override def decode(raw: RawEvent): F[Event[? <: DomainEvent]] =
        val eventType = raw.payload.eType

        registry.get(eventType) match
          case None =>
            Async[F].raiseError(decodeMissingError(eventType))

          case Some(codec) =>
            Async[F].delay(parser.parse(raw.payload.payload.show)).flatMap {
              case Left(parseErr) =>
                Async[F].raiseError(jsonParseError(eventType, parseErr.message))

              case Right(json) =>
                codec
                  .asInstanceOf[Decoder[DomainEvent]]
                  .decodeJson(json) match
                  case Left(decErr) =>
                    Async[F].raiseError(jsonDecodeError(eventType, decErr.message))
                  case Right(decoded) =>
                    raw.toEvent(decoded).pure[F]
            }

      override def encode[E <: DomainEvent](event: Event[E]): F[RawEvent] =
        val eventType = EventType(event.event.getClass.getName)

        registry.get(eventType) match
          case None =>
            Async[F].raiseError(encodeMissingError(eventType))

          case Some(codec) =>
            Async[F].delay {
              val json = codec
                .asInstanceOf[Encoder[DomainEvent]]
                .apply(event.event)

              event.toRawEvent(RawPayload(eventType, EventPayload(json.noSpaces)))
            }.handleErrorWith { err =>
              Async[F].raiseError(encodeFailure(eventType, err))
            }

  private def decodeMissingError(eventType: EventType) =
    new Exception(s"[EventCodecRegistry] No codec registered for type: ${eventType.show}")

  private def encodeMissingError(eventType: EventType) =
    new Exception(s"[EventCodecRegistry] No codec registered for event type: ${eventType.show}")

  private def jsonParseError(eventType: EventType, msg: String) =
    new Exception(s"[EventCodecRegistry] Failed to parse JSON for $eventType: $msg")

  private def jsonDecodeError(eventType: EventType, msg: String) =
    new Exception(s"[EventCodecRegistry] Failed to decode $eventType: $msg")

  private def encodeFailure(eventType: EventType, err: Throwable) =
    new Exception(s"[EventCodecRegistry] Failed to encode event $eventType: ${err.getMessage}", err)
