import { useQuery } from '@tanstack/react-query'
import { HealthService } from '@/application/health'

/**
 * Query key factory for health queries
 */
export const healthKeys = {
  all: ['health'] as const,
  services: () => [...healthKeys.all, 'services'] as const,
  service: (url: string) => [...healthKeys.all, 'service', url] as const,
  system: () => [...healthKeys.all, 'system'] as const,
}

/**
 * Hook: Use all services health status
 */
export function useServicesHealth(options?: { refetchInterval?: number }) {
  return useQuery({
    queryKey: healthKeys.services(),
    queryFn: () => HealthService.checkAllServices(),
    refetchInterval: options?.refetchInterval ?? 15000, // 15 seconds
  })
}

/**
 * Hook: Use single service health
 */
export function useServiceHealth(url: string) {
  return useQuery({
    queryKey: healthKeys.service(url),
    queryFn: () => HealthService.checkService(url),
    refetchInterval: 10000, // 10 seconds
  })
}

/**
 * Hook: Use overall system health
 */
export function useSystemHealth() {
  return useQuery({
    queryKey: healthKeys.system(),
    queryFn: () => HealthService.getSystemHealth(),
    refetchInterval: 15000,
  })
}
