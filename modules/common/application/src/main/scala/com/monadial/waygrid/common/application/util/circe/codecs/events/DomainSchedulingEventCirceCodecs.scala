package com.monadial.waygrid.common.application.util.circe.codecs.events

import com.monadial.waygrid.common.domain.model.scheduling.Event.TaskSchedulingRequested
import io.circe.Codec
import io.circe.generic.semiauto

object DomainSchedulingEventCirceCodecs:
  given Codec[TaskSchedulingRequested] = semiauto.deriveCodec
