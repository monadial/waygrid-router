package com.monadial.waygrid.common.application.util.circe

import com.monadial.waygrid.common.application.util.circe.codecs.events.{
  DomainSchedulingEventCodecs,
  DomainTraversalEvents
}

object AllDomainEventCodecs:
  export DomainTraversalEvents.given
  export DomainSchedulingEventCodecs.given
