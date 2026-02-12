import { topologyApi } from './client'

/**
 * DTO types - Data Transfer Objects matching API responses
 * These are infrastructure concerns, mapped to domain entities
 */
export interface NodeMetadataDTO {
  id: string
  service: string
  component: string
  clusterId: string
  region: string
  address: string
  startedAt: string
  uptime: number
}

export interface HealthCheckDTO {
  // Empty object as per Scala HealthCheckResource
}

/**
 * Topology API client
 * Handles communication with the topology service
 */
export const TopologyApiClient = {
  /**
   * Get metadata for the current node (HomeResource)
   */
  async getNodeMetadata(): Promise<NodeMetadataDTO> {
    const response = await topologyApi.get<NodeMetadataDTO>('/')
    return response.data
  },

  /**
   * Check if the service is healthy
   */
  async healthCheck(): Promise<boolean> {
    try {
      await topologyApi.get<HealthCheckDTO>('/health-check')
      return true
    } catch {
      return false
    }
  },
}
