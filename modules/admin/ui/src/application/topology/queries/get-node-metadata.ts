import { Node } from '@/domain/topology'
import { TopologyApiClient } from '@/infrastructure/api/topology-api'
import { TopologyMapper } from '@/infrastructure/mappers/topology-mapper'

/**
 * Query: Get Node Metadata
 * Fetches and maps current node metadata from the topology service
 */
export async function getNodeMetadata(): Promise<Node> {
  const dto = await TopologyApiClient.getNodeMetadata()
  return TopologyMapper.toDomainNode(dto)
}
