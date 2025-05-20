package com.monadial.waygrid.common.application.model.event

import com.monadial.waygrid.common.domain.model.Waygrid
import com.monadial.waygrid.common.domain.model.event.Event as DomainEvent
import com.monadial.waygrid.common.domain.syntax.StringSyntax.toDomain
import com.monadial.waygrid.common.domain.value.string.StringValue
import com.monadial.waygrid.common.domain.value.ulid.ULIDValue

import cats.implicits.*
import io.circe.Codec

import scala.reflect.ClassTag

type EventId = EventId.Type
object EventId extends ULIDValue

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
object EventStream extends StringValue

type EventTopicComponent = EventTopicComponent.Type
object EventTopicComponent extends StringValue

type EventTopicService = EventTopicService.Type
object EventTopicService extends StringValue

type EventTopicPath = EventTopicPath.Type
object EventTopicPath extends StringValue

final case class RawPayload(eType: EventType, payload: EventPayload)
final case class RawEvent(id: EventId, payload: RawPayload)

final case class EventTopic(
  component: EventTopicComponent,
  service: EventTopicService,
  version: Option[Int] = None
) derives Codec.AsObject:
  def path: EventTopicPath =
    s"${Waygrid.appName}.event.${component.show}.${service.show}.v${version.getOrElse(1)}"
      .toDomain[EventTopicPath]
