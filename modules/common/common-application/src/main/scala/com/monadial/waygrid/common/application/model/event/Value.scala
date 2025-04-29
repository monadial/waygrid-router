package com.monadial.waygrid.common.application.model.event

import cats.implicits.{toShow, *}
import com.monadial.waygrid.common.domain.model.Waygrid
import com.monadial.waygrid.common.domain.syntax.StringSyntax.toDomain
import com.monadial.waygrid.common.domain.value.string.StringValue
import com.monadial.waygrid.common.domain.value.ulid.ULIDValue
import io.circe.Codec

type EventId = EventId.Type
object EventId extends ULIDValue

type EventTopicComponent = EventTopicComponent.Type
object EventTopicComponent extends StringValue

type EventTopicService = EventTopicService.Type
object EventTopicService extends StringValue

type EventTopicPath = EventTopicPath.Type
object EventTopicPath extends StringValue

final case class EventTopic(
  component: EventTopicComponent,
  service: EventTopicService,
  version: Option[Int] = None,
) derives Codec.AsObject:
  def path: EventTopicPath =
    s"${Waygrid.appName}.event.${component.show}.${service.show}.v${version.getOrElse(1)}"
      .toDomain[EventTopicPath]
