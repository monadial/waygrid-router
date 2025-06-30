package com.monadial.waygrid.common.domain.model.topology

final case class Topology(nodes: List[String]):
  def addNode(nodeName: String): Topology = copy(nodes = nodeName :: nodes)
  def numberOfRegisteredNodes: Int        = nodes.size

object Topology:
  def initial: Topology = Topology(List.empty)
