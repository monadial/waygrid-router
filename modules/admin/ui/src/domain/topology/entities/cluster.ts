import { ULID } from '@/domain/shared'
import { Node } from './node'

/**
 * ClusterId - Identifier for a cluster of nodes
 * Mirrors: com.monadial.waygrid.common.domain.model.node.NodeClusterId
 */
export type ClusterId = ULID & { readonly __clusterId: unique symbol }

/**
 * Cluster - A group of nodes providing the same service
 */
export interface Cluster {
  readonly id: ClusterId
  readonly region: string
  readonly nodes: readonly Node[]
}

export function createCluster(id: string, region: string, nodes: Node[]): Cluster {
  return {
    id: id as ClusterId,
    region,
    nodes,
  }
}

export function getClusterHealth(cluster: Cluster): 'healthy' | 'degraded' | 'unhealthy' {
  if (cluster.nodes.length === 0) return 'unhealthy'

  const healthyCount = cluster.nodes.filter((n) => n.uptime > 30).length
  const ratio = healthyCount / cluster.nodes.length

  if (ratio >= 1) return 'healthy'
  if (ratio >= 0.5) return 'degraded'
  return 'unhealthy'
}
