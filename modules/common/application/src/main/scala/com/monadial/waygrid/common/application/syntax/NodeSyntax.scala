package com.monadial.waygrid.common.application.syntax

import com.monadial.waygrid.common.application.domain.model.event.{EventTopic, EventTopicComponent, EventTopicService}
import com.monadial.waygrid.common.domain.model.node.Value.NodeDescriptor
import com.monadial.waygrid.common.domain.syntax.StringSyntax.mapValue

object NodeSyntax:
  extension (descriptor: NodeDescriptor)
    def toEventTopic(version: Option[Int] = None): EventTopic =
      EventTopic(
        descriptor.component.mapValue[EventTopicComponent],
        descriptor.service.mapValue[EventTopicService],
        version
      )
