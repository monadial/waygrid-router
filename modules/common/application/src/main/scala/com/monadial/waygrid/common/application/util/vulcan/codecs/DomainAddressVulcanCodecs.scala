package com.monadial.waygrid.common.application.util.vulcan.codecs

import cats.syntax.all.*
import com.monadial.waygrid.common.application.util.vulcan.VulcanUtils.given
import com.monadial.waygrid.common.application.util.vulcan.codecs.DomainPrimitivesVulcanCodecs.given
import com.monadial.waygrid.common.domain.model.node.Value.NodeDescriptor
import com.monadial.waygrid.common.domain.value.Address.{
  Endpoint,
  EndpointDirection,
  LogicalEndpoint,
  NodeAddress,
  PhysicalEndpoint,
  ServiceAddress
}
import org.http4s.Uri
import vulcan.Codec

/**
 * Vulcan Avro codecs for address-related domain types.
 *
 * Handles:
 * - EndpointDirection (enum: Inbound | Outbound)
 * - NodeDescriptor (component + service)
 * - ServiceAddress (URI-based service address)
 * - NodeAddress (URI-based node address)
 * - Endpoint (sealed trait union: LogicalEndpoint | PhysicalEndpoint)
 *
 * Avro Representation:
 * - EndpointDirection: Avro enum with symbols ["Inbound", "Outbound"]
 * - NodeDescriptor: Record with component and service string fields
 * - Endpoint: Union type with discriminator
 *
 * Import `DomainAddressVulcanCodecs.given` to bring these codecs into scope.
 */
object DomainAddressVulcanCodecs:

  // ---------------------------------------------------------------------------
  // EndpointDirection (enum)
  // ---------------------------------------------------------------------------

  /**
   * EndpointDirection encoded as Avro enum.
   */
  given Codec[EndpointDirection] =
    Codec.enumeration[EndpointDirection](
      name = "EndpointDirection",
      namespace = "com.monadial.waygrid.common.domain.value",
      symbols = List("Inbound", "Outbound"),
      encode = {
        case EndpointDirection.Inbound  => "Inbound"
        case EndpointDirection.Outbound => "Outbound"
      },
      decode = {
        case "Inbound"  => Right(EndpointDirection.Inbound)
        case "Outbound" => Right(EndpointDirection.Outbound)
        case other      => Left(vulcan.AvroError(s"Unknown EndpointDirection: $other"))
      }
    )

  // ---------------------------------------------------------------------------
  // NodeDescriptor (record)
  // ---------------------------------------------------------------------------

  /**
   * NodeDescriptor encoded as Avro record.
   */
  given Codec[NodeDescriptor] = Codec.record(
    name = "NodeDescriptor",
    namespace = "com.monadial.waygrid.common.domain.model.node"
  ) { field =>
    (
      field("component", _.component),
      field("service", _.service)
    ).mapN(NodeDescriptor.apply)
  }

  // ---------------------------------------------------------------------------
  // URI-based address types
  // ---------------------------------------------------------------------------

  /**
   * ServiceAddress encoded as string (URI format).
   */
  given Codec[ServiceAddress] = summon[Codec[Uri]].imap(
    uri => ServiceAddress(uri)
  )(
    addr => addr.unwrap
  )

  /**
   * NodeAddress encoded as string (URI format).
   */
  given Codec[NodeAddress] = summon[Codec[Uri]].imap(
    uri => NodeAddress(uri)
  )(
    addr => addr.unwrap
  )

  // ---------------------------------------------------------------------------
  // Endpoint (sealed trait union)
  // ---------------------------------------------------------------------------

  /**
   * LogicalEndpoint encoded as Avro record.
   */
  given Codec[LogicalEndpoint] = Codec.record(
    name = "LogicalEndpoint",
    namespace = "com.monadial.waygrid.common.domain.value"
  ) { field =>
    (
      field("descriptor", _.descriptor),
      field("direction", _.direction)
    ).mapN(LogicalEndpoint.apply)
  }

  /**
   * PhysicalEndpoint encoded as Avro record.
   */
  given Codec[PhysicalEndpoint] = Codec.record(
    name = "PhysicalEndpoint",
    namespace = "com.monadial.waygrid.common.domain.value"
  ) { field =>
    (
      field("descriptor", _.descriptor),
      field("nodeId", _.nodeId),
      field("direction", _.direction)
    ).mapN(PhysicalEndpoint.apply)
  }

  /**
   * Endpoint sealed trait encoded as Avro union.
   *
   * Avro unions are represented as: ["LogicalEndpoint", "PhysicalEndpoint"]
   * The discriminator is determined by the schema name in the union.
   */
  given Codec[Endpoint] = Codec.union[Endpoint] { alt =>
    alt[LogicalEndpoint] |+| alt[PhysicalEndpoint]
  }
