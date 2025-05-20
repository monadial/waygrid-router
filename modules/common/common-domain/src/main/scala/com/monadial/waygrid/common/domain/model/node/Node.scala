package com.monadial.waygrid.common.domain.model.node

import com.monadial.waygrid.common.domain.model.Waygrid
import com.monadial.waygrid.common.domain.syntax.StringSyntax.toDomain
import io.circe.Codec
import java.time.Instant

final case class Node(
  descriptor: NodeDescriptor,
  clusterId: NodeClusterId,
  startedAt: Instant,
  runtime: NodeRuntime
) derives Codec.AsObject:
  /**
   * Unique client identifier used for Kafka, cluster-wide subscriptions,
   * and distributed system coordination.
   *
   * Example: waygrid-origin-origin-http-cluster-1
   */
  inline def clientId: NodeClientId =
    s"${Waygrid.appName}-${descriptor.component}-${descriptor.service}-${clusterId}"
      .toDomain[NodeClientId]

  /**
   * Logical address of this node used in routing and discovery.
   *
   * Example: waygrid://cluster-1@origin/origin-http
   */
  inline def address: NodeAddress =
    s"${Waygrid.appName}://${clusterId}@${descriptor.component}/${descriptor.service}"
      .toDomain[NodeAddress]

  /**
   * Path to the configuration settings for this node in the shared config tree.
   * Used to resolve HOCON paths, env overrides, etc.
   *
   * Example: waygrid.origin.origin-http
   */
  inline def settingsPath: NodeSettingsPath =
    s"${Waygrid.appName}.${descriptor.component}.${descriptor.service}"
      .toDomain[NodeSettingsPath]

  /**
   * Receive address used to derive subscription topic/channel for incoming events.
   * Commonly used in event-driven systems (e.g., Kafka topic, message bus address).
   *
   * Example: waygrid.events.origin.origin-http
   */
  inline def receiveAddress: NodeReceiveAddress =
    s"${Waygrid.appName}.events.${descriptor.component}.${descriptor.service}"
      .toDomain[NodeReceiveAddress]

  inline def producerName = s"${Waygrid.appName}-producer-${descriptor.component}-${descriptor.service}"

  inline def consumerName = s"${Waygrid.appName}-consumer-${descriptor.component}-${descriptor.service}"

  inline def otelName = s""
