package com.monadial.waygrid.common.application.util.circe

import com.monadial.waygrid.common.application.util.circe.codecs.events.{
  DomainSchedulingEventCirceCodecs,
  DomainTraversalCirceEvents
}

object AllDomainEventCodecs:
  export DomainTraversalCirceEvents.given
  export DomainSchedulingEventCirceCodecs.given
