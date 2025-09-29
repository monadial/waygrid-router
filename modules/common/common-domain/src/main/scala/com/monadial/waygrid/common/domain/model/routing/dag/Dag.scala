package com.monadial.waygrid.common.domain.model.routing.dag

import cats.implicits.*
import com.monadial.waygrid.common.domain.model.routing.Value.RepeatPolicy
import com.monadial.waygrid.common.domain.model.routing.dag.Value.{DagHash, EdgeGuard, NodeId}

final case class Dag(
  hash: DagHash,
  entry: NodeId,
  repeatPolicy: RepeatPolicy,
  nodes: Map[NodeId, Node],
  edges: List[Edge]
):
  def printDotFormat: String =
    val builder = new StringBuilder

    // Start of the DOT graph
    builder ++=
        """digraph DAG {
          |  rankdir=LR;
          |  fontname="Helvetica";
          |  node [fontname="Helvetica", fontsize=10, shape=box, style=filled, fillcolor="#F5F6FA", color="#1F5A93"];
          |  edge [fontname="Helvetica", fontsize=9, color="#FF6B35"];
          |
          |""".stripMargin

    // Add nodes with display labels and entry point styling
    nodes.foreach { case (id, node) =>
      val label = node.label.getOrElse(node.address.show)
      val name  = node.label.getOrElse(id.show) // use label as graph node id when present
      val isEntry = id == entry
      val shape = if isEntry then "doublecircle" else "box"
      val style = if isEntry then "bold,filled" else "filled"
      val fill = if isEntry then "#DFF5FF" else "#F5F6FA"
      builder ++= s"""  "$name" [label="$label", shape=$shape, style="$style", fillcolor="$fill"];\n"""
    }

    builder.append("\n")

    // Add edges with labels and edge ID as a comment
    edges.foreach { edge =>
      val label = edge.guard match
        case EdgeGuard.OnSuccess => "onSuccess"
        case EdgeGuard.OnFailure => "onFailure"

      val fromNode = nodes(edge.from)
      val toNode   = nodes(edge.to)
      val fromName = fromNode.label.getOrElse(edge.from.show)
      val toName   = toNode.label.getOrElse(edge.to.show)

      val edgeId = s"${edge.from.show}_${edge.to.show}"

      builder ++=
          s"""  "$fromName" -> "$toName" [label="$label"] // $edgeId\n"""
    }

    builder.append("}\n")
    builder.toString()
