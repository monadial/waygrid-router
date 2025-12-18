package com.monadial.waygrid.common.application.util.scodec.codecs

import com.monadial.waygrid.common.domain.model.node.Value.{ NodeComponent, NodeDescriptor, NodeId, NodeService }
import com.monadial.waygrid.common.domain.value.Address.{ Endpoint, EndpointDirection, LogicalEndpoint, PhysicalEndpoint }
import scodec.*
import scodec.bits.*
import scodec.codecs.*
import wvlet.airframe.ulid.ULID

/**
 * Scodec binary codecs for Address-related domain types.
 *
 * Type discriminators:
 * - EndpointDirection: 0x00=Inbound, 0x01=Outbound
 * - Endpoint: 0x00=LogicalEndpoint, 0x01=PhysicalEndpoint
 */
object DomainAddressScodecCodecs:

  // ---------------------------------------------------------------------------
  // Value type codecs (private to avoid conflicts with auto-derived codecs)
  // ---------------------------------------------------------------------------

  private val nodeComponentCodec: Codec[NodeComponent] =
    variableSizeBytes(int32, utf8).xmap(NodeComponent(_), _.unwrap)

  private val nodeServiceCodec: Codec[NodeService] =
    variableSizeBytes(int32, utf8).xmap(NodeService(_), _.unwrap)

  // Note: This is node.Value.NodeId (ULID), distinct from dag.Value.NodeId (String)
  private val modelNodeIdCodec: Codec[NodeId] =
    bytes(16).xmap(
      bytes => NodeId(ULID.fromBytes(bytes.toArray)),
      nodeId => ByteVector(nodeId.unwrap.toBytes)
    )

  // Expose as givens for external use
  given Codec[NodeComponent] = nodeComponentCodec
  given Codec[NodeService]   = nodeServiceCodec
  given Codec[NodeId]        = modelNodeIdCodec

  // ---------------------------------------------------------------------------
  // NodeDescriptor codec
  // ---------------------------------------------------------------------------

  private val nodeDescriptorCodec: Codec[NodeDescriptor] =
    (nodeComponentCodec :: nodeServiceCodec).xmap(
      { case (component, service) => NodeDescriptor(component, service) },
      nd => (nd.component, nd.service)
    )

  given Codec[NodeDescriptor] = nodeDescriptorCodec

  // ---------------------------------------------------------------------------
  // EndpointDirection codec
  // ---------------------------------------------------------------------------

  given Codec[EndpointDirection] = Codec[EndpointDirection](
    (dir: EndpointDirection) =>
      dir match
        case EndpointDirection.Inbound  => uint8.encode(0)
        case EndpointDirection.Outbound => uint8.encode(1)
    ,
    (bits: BitVector) =>
      uint8.decode(bits).flatMap { result =>
        result.value match
          case 0 => Attempt.successful(DecodeResult(EndpointDirection.Inbound, result.remainder))
          case 1 => Attempt.successful(DecodeResult(EndpointDirection.Outbound, result.remainder))
          case n => Attempt.failure(Err(s"Unknown EndpointDirection discriminator: $n"))
      }
  )

  // ---------------------------------------------------------------------------
  // LogicalEndpoint codec
  // ---------------------------------------------------------------------------

  private val logicalEndpointCodec: Codec[LogicalEndpoint] =
    (nodeDescriptorCodec :: summon[Codec[EndpointDirection]]).xmap(
      { case (descriptor, direction) => LogicalEndpoint(descriptor, direction) },
      le => (le.descriptor, le.direction)
    )

  // ---------------------------------------------------------------------------
  // PhysicalEndpoint codec
  // ---------------------------------------------------------------------------

  private val physicalEndpointCodec: Codec[PhysicalEndpoint] =
    (nodeDescriptorCodec :: modelNodeIdCodec :: summon[Codec[EndpointDirection]]).xmap(
      { case (descriptor, nodeId, direction) => PhysicalEndpoint(descriptor, nodeId, direction) },
      pe => (pe.descriptor, pe.nodeId, pe.direction)
    )

  // ---------------------------------------------------------------------------
  // Endpoint sealed trait codec
  // ---------------------------------------------------------------------------

  given Codec[Endpoint] = discriminated[Endpoint]
    .by(uint8)
    .typecase(0, logicalEndpointCodec)
    .typecase(1, physicalEndpointCodec)
