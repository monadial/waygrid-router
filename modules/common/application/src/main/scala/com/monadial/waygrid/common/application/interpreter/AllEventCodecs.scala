package com.monadial.waygrid.common.application.interpreter

import com.monadial.waygrid.common.application.`macro`.CirceMessageCodecRegistryMacro
import com.monadial.waygrid.common.domain.model.scheduling.Event.SchedulingEvent
import com.monadial.waygrid.common.domain.model.traversal.Event.TraversalEvent

object AllEventCodecs:
  /**
   * Registers all Circe codecs for domain events.
   *
   * ⚠️ The imports **must** be placed inside this inline method.
   * Because `registerAll` is an inline macro, implicit `given` definitions
   * must be visible *at the macro expansion site* — not just where this method is defined.
   *
   * Keeping the imports inside ensures that during macro expansion
   * (when `codecs()` is inlined into the caller, e.g. in WaygridApp),
   * all relevant Circe codecs (from DomainRoutingEventCodecs, etc.)
   * are in lexical scope and discoverable by implicit search.
   *
   * If the imports were placed outside this inline method,
   * the macro would expand in a different scope and fail to locate codecs,
   * resulting in compile-time errors such as:
   * “Could not find implicit Codec[Event$.MessageWasRouted]”.
   */
  inline def codecs(): Unit =
    import com.monadial.waygrid.common.application.util.circe.AllDomainEventCodecs.given
    CirceMessageCodecRegistryMacro.registerAll[TraversalEvent]()
    CirceMessageCodecRegistryMacro.registerAll[SchedulingEvent]()
