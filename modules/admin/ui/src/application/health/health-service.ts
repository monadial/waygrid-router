import { HealthApiClient } from '@/infrastructure/api/health-api'

export interface ServiceHealthStatus {
  name: string
  url: string
  healthy: boolean
}

/**
 * Health Application Service
 * Cross-cutting concern for monitoring all services
 */
export const HealthService = {
  /**
   * Check health of all registered services
   */
  async checkAllServices(): Promise<ServiceHealthStatus[]> {
    return HealthApiClient.checkAllServices()
  },

  /**
   * Check health of a specific service
   */
  async checkService(url: string): Promise<boolean> {
    return HealthApiClient.checkService(url)
  },

  /**
   * Get list of all service endpoints
   */
  getServiceList() {
    return HealthApiClient.getServices()
  },

  /**
   * Get overall system health
   */
  async getSystemHealth(): Promise<'healthy' | 'degraded' | 'unhealthy'> {
    const services = await this.checkAllServices()
    const healthyCount = services.filter((s) => s.healthy).length
    const totalCount = services.length

    if (healthyCount === totalCount) return 'healthy'
    if (healthyCount >= totalCount / 2) return 'degraded'
    return 'unhealthy'
  },
}
