import { useQuery } from '@tanstack/react-query'
import { TopologyService } from '@/application/topology'

/**
 * Query key factory for topology queries
 */
export const topologyKeys = {
  all: ['topology'] as const,
  nodes: () => [...topologyKeys.all, 'nodes'] as const,
  node: (id: string) => [...topologyKeys.all, 'node', id] as const,
  currentNode: () => [...topologyKeys.all, 'current'] as const,
}

/**
 * Hook: Use current node metadata
 */
export function useCurrentNode() {
  return useQuery({
    queryKey: topologyKeys.currentNode(),
    queryFn: () => TopologyService.getCurrentNode(),
    staleTime: 1000 * 60 * 5, // 5 minutes
  })
}

/**
 * Hook: Use topology service health
 */
export function useTopologyHealth() {
  return useQuery({
    queryKey: [...topologyKeys.all, 'health'],
    queryFn: () => TopologyService.isHealthy(),
    refetchInterval: 10000,
  })
}
