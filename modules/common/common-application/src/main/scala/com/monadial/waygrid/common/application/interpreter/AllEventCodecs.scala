package com.monadial.waygrid.common.application.interpreter

import com.monadial.waygrid.common.application.`macro`.EventCodecRegistry
import com.monadial.waygrid.common.domain.model.node.NodeEvent
import com.monadial.waygrid.common.domain.model.topology.TopologyEvent

object AllEventCodecs:
  def codecs(): Unit =
    EventCodecRegistry.registerAll[NodeEvent]()
    EventCodecRegistry.registerAll[TopologyEvent]()
