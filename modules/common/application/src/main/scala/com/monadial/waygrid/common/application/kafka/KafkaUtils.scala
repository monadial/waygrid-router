package com.monadial.waygrid.common.application.kafka

import cats.effect.Async
import cats.implicits.*
import com.monadial.waygrid.common.application.algebra.ThisNode
import com.monadial.waygrid.common.domain.model.Waygrid
import com.monadial.waygrid.common.domain.model.node.Value.{ NodeComponent, NodeDescriptor, NodeId, NodeService }
import com.monadial.waygrid.common.domain.value.Address
import com.monadial.waygrid.common.domain.value.Address.{ Endpoint, EndpointDirection }
import org.apache.kafka.common.TopicPartition
import wvlet.airframe.ulid.ULID

object KafkaUtils:

  extension (endpoint: Endpoint)
    def toTopic[F[+_]: {Async, ThisNode}]: F[String] =
      for
        node    <- ThisNode[F].get
        appName <- Waygrid.appName.pure[F]
        region  <- s"${node.clusterId.show}-${node.region.show}".pure[F]
        service <- s"${endpoint.descriptor.component.show}-${endpoint.descriptor.service.show}".pure[F]
        topic   <- s"$appName-$region-$service".pure[F]
      yield endpoint match
        case Address.LogicalEndpoint(_, direction) =>
          s"$topic-${direction.toString}"
        case Address.PhysicalEndpoint(_, nodeId, direction) =>
          s"$topic-${nodeId.show}-${direction.toString}"

  extension (tp: TopicPartition)
    def toEndpoint[F[_]: Async]: F[Endpoint] =
      val pattern =
        raw"""^(?<app>[^-]+)-(?<cluster>[^-]+)-(?<region>[a-z]{2}(?:-[a-z]{1,16})*-[0-9]{1,2})-(?<component>[^-]+)-(?<service>[^-]+)(?:-(?<nodeId>[^-]+))?-(?<direction>[^-]+)$$""".r

      for
        m <- pattern.findFirstMatchIn(tp.topic())
          .liftTo[F](new IllegalArgumentException(s"Invalid topic format: ${tp.topic()}"))

        component <- NodeComponent(m.group("component")).pure[F]
        service   <- NodeService(m.group("service")).pure[F]

        descriptor = NodeDescriptor(component, service)

        direction <- m.group("direction") match
          case "inbound"  => EndpointDirection.Inbound.pure[F]
          case "outbound" => EndpointDirection.Outbound.pure[F]
          case other =>
            Async[F].raiseError(new IllegalArgumentException(s"Invalid direction '$other' in topic: ${tp.topic()}"))

        endpoint <- Option(m.group("nodeId")) match
          case Some(nodeId) =>
            Address.PhysicalEndpoint(descriptor, NodeId(ULID.fromString(nodeId)), direction).pure[F]
          case None =>
            Address.LogicalEndpoint(descriptor, direction).pure[F]
      yield endpoint
