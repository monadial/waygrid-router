package com.monadial.waygrid.common.domain.model.node

import com.monadial.waygrid.common.domain.model.Waygrid
import com.monadial.waygrid.common.domain.model.node.Value.*
import com.monadial.waygrid.common.domain.syntax.StringSyntax.toDomain
import io.circe.Codec

import java.time.Instant

final case class Node(
  id: NodeId,
  descriptor: NodeDescriptor,
  clusterId: NodeClusterId,
  region: NodeRegion,
  startedAt: Instant,
  runtime: NodeRuntime
) derives Codec.AsObject

object Node:
  extension (node: Node)
    inline def settingsPath: NodeSettingsPath =
      s"${Waygrid.appName}.${node.descriptor.component}.${node.descriptor.service}"
        .toDomain[NodeSettingsPath]

    inline def address: NodeAddress = NodeAddress.fromNode(node)

    def uptime: Long = Instant.now().toEpochMilli - node.startedAt.toEpochMilli
