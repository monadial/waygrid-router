package com.monadial.waygrid.common.application.domain.model.event

import com.monadial.waygrid.common.domain.syntax.StringSyntax.toDomain
import cats.implicits.*
import com.monadial.waygrid.common.domain.algebra.messaging.event.Event as DomainEvent
import com.monadial.waygrid.common.domain.algebra.value.string.StringValue
import com.monadial.waygrid.common.domain.algebra.value.ulid.ULIDValue
import com.monadial.waygrid.common.domain.model.Waygrid
import io.circe.Codec

import scala.reflect.ClassTag

type EventId = EventId.Type
object EventId extends ULIDValue

type EventAddress = EventAddress.Type
object EventAddress extends StringValue

type EventSender = EventSender.type
object EventSender extends StringValue

type EventType = EventType.Type
object EventType extends StringValue:
  def fromEvent[E <: DomainEvent](using ct: ClassTag[E]): EventType =
    ct
      .runtimeClass
      .getName
      .toDomain[EventType]

type EventPayload = EventPayload.Type
object EventPayload extends StringValue

type EventStream = EventStream.Type
object EventStream extends StringValue:
  extension (eventStream: EventStream)
    inline def out: EventStream =
      s"${eventStream.show}.out".toDomain[EventStream]

    inline def in: EventStream =
      s"${eventStream.show}.in".toDomain[EventStream]

type EventTopicComponent = EventTopicComponent.Type
object EventTopicComponent extends StringValue

type EventTopicService = EventTopicService.Type
object EventTopicService extends StringValue

type EventTopicPath = EventTopicPath.Type
object EventTopicPath extends StringValue

final case class RawPayload(eType: EventType, payload: EventPayload) derives Codec.AsObject
final case class RawEvent(id: EventId, address: EventAddress, payload: RawPayload) derives Codec.AsObject

final case class EventTopic(
  component: EventTopicComponent,
  service: EventTopicService,
  version: Option[Int] = None
) derives Codec.AsObject:
  def path: EventTopicPath =
    s"${Waygrid.appName}.event.${component.show}.${service.show}.v${version.getOrElse(1)}"
      .toDomain[EventTopicPath]
