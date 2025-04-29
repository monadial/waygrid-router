package com.monadial.waygrid.common.application.algebra

import com.monadial.waygrid.common.application.model.event.Event
import fs2.Pipe

trait EventSink[F[+_]]:
  def pipe: Pipe[F, Event, Unit]



