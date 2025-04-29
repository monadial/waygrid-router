package com.monadial.waygrid.common.application.algebra

import com.monadial.waygrid.common.application.model.event.{Event, EventTopic}
import fs2.Stream

trait EventSource[F[+_]]:
  def stream(topic: EventTopic): Stream[F, Event]
