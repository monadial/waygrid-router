package com.monadial.waygrid.common.domain.model.node

import com.monadial.waygrid.common.domain.model.Waygrid.Address
import com.monadial.waygrid.common.domain.model.Waygrid
import com.monadial.waygrid.common.domain.model.node.Value.*
import com.monadial.waygrid.common.domain.model.settings.Settings
import com.monadial.waygrid.common.domain.syntax.StringSyntax.toDomain

import java.time.Instant

final case class Node(
  descriptor: NodeDescriptor,
  clusterId: NodeClusterId,
  nodeId: NodeId,
  startedAt: Instant,
  runtime: NodeRuntime,
):
  inline def address: Address = Address(s"${descriptor.component}.${descriptor.service}")

  inline def settingsPath: NodeSettingsPath =
    s"${Waygrid.appName}.${descriptor.component}.${descriptor.service}"
      .toDomain[NodeSettingsPath]
