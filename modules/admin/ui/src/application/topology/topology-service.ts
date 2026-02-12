import { Node } from '@/domain/topology'
import { getNodeMetadata } from './queries/get-node-metadata'
import { TopologyApiClient } from '@/infrastructure/api/topology-api'

/**
 * Topology Application Service
 * Orchestrates topology-related use cases
 */
export const TopologyService = {
  /**
   * Get metadata for the current node
   */
  async getCurrentNode(): Promise<Node> {
    return getNodeMetadata()
  },

  /**
   * Check if the topology service is healthy
   */
  async isHealthy(): Promise<boolean> {
    return TopologyApiClient.healthCheck()
  },
}
