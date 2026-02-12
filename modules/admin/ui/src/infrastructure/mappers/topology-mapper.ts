import { Node, createNode, NodeDescriptor, NodeAddress, NodeComponent } from '@/domain/topology'
import { NodeMetadataDTO } from '../api/topology-api'

/**
 * Mapper: DTO -> Domain Entity
 * Converts API responses to domain entities
 */
export const TopologyMapper = {
  /**
   * Map NodeMetadataDTO to Node entity
   */
  toDomainNode(dto: NodeMetadataDTO): Node {
    const descriptor: NodeDescriptor = {
      component: dto.component as NodeComponent,
      service: dto.service as NodeDescriptor['service'],
    }

    const address: NodeAddress = {
      descriptor,
      region: dto.region,
      clusterId: dto.clusterId,
      nodeId: dto.id as NodeAddress['nodeId'],
    }

    return createNode({
      id: dto.id,
      descriptor,
      clusterId: dto.clusterId,
      region: dto.region,
      address,
      startedAt: dto.startedAt,
      uptime: dto.uptime,
    })
  },
}
