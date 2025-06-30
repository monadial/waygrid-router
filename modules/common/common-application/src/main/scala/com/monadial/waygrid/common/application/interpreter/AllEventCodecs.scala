package com.monadial.waygrid.common.application.interpreter

import com.monadial.waygrid.common.application.`macro`.CirceEventCodecRegistryMacro
import com.monadial.waygrid.common.domain.model.topology.TopologyEvent

object AllEventCodecs:
  inline def codecs(): Unit =
//    CirceEventCodecRegistryMacro.registerAll[NodeEvent]()
    CirceEventCodecRegistryMacro.registerAll[TopologyEvent]()
